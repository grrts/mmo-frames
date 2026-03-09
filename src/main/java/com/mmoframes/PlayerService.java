package com.mmoframes;

import com.mmoframes.status.ActivePrayerEffect;
import com.mmoframes.status.AntipoisonImmunityEffect;
import com.mmoframes.status.PlayerPoisonEffect;
import com.mmoframes.status.SkillBoostEffect;
import com.mmoframes.status.StaminaEffect;
import com.mmoframes.status.StatusEffect;
import com.mmoframes.status.VarbitTimerEffect;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuEntry;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.StatChange;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class PlayerService
{
	// HP regenerates 1 point every 100 ticks (60 seconds out of combat)
	private static final int HP_REGEN_TICKS = 100;

	// Special attack recharges 10% every 33 ticks (~20s per 10%)
	private static final int SPEC_REGEN_TICKS = 33;

	// Venom encoding threshold (OSRS varp 102 >= this value = venomed)
	private static final int VENOM_THRESHOLD = 1_000_000;

	// ── Varbit IDs for potion / spell effects (not all are in VarbitID) ──────
	private static final int VARBIT_ANTIFIRE              = 3981;
	private static final int VARBIT_SUPER_ANTIFIRE        = 6101;
	private static final int VARBIT_DIVINE_SUPER_ATTACK   = 8429;
	private static final int VARBIT_DIVINE_SUPER_STRENGTH = 8430;
	private static final int VARBIT_DIVINE_SUPER_DEFENCE  = 8431;
	private static final int VARBIT_DIVINE_RANGING        = 8432;
	private static final int VARBIT_DIVINE_MAGIC          = 8433;
	private static final int VARBIT_DIVINE_SUPER_COMBAT   = 13663;
	private static final int VARBIT_DIVINE_BASTION        = 13664;
	private static final int VARBIT_DIVINE_BATTLEMAGE     = 13665;
	private static final int VARBIT_NMZ_OVERLOAD          = 3955;
	private static final int VARBIT_COX_OVERLOAD          = 5418;
	private static final int VARBIT_VENGEANCE_ACTIVE      = 2450;
	private static final int VARBIT_MAGIC_IMBUE           = 5438;
	private static final int VARBIT_GOADING_POTION        = 11294;
	private static final int VARBIT_PRAYER_REGEN          = 11361;
	private static final int VARBIT_MENAPHITE_REMEDY      = 14448;
	private static final int VARBIT_LIQUID_ADRENALINE     = 14361;

	// ── Colours for potion / spell effects ───────────────────────────────────
	private static final Color CLR_ANTIFIRE       = new Color(220, 120,   0, 255);
	private static final Color CLR_SUPER_ANTIFIRE = new Color(220,  60,   0, 255);
	private static final Color CLR_DIVINE         = new Color(220, 180,  40, 255);
	private static final Color CLR_OVERLOAD       = new Color(180,  40,  40, 255);
	private static final Color CLR_VENGEANCE      = new Color(200,  50,  50, 255);
	private static final Color CLR_MAGIC_IMBUE    = new Color( 80,  80, 200, 255);
	private static final Color CLR_GOADING        = new Color(200, 140,  40, 255);
	private static final Color CLR_PRAYER_REGEN   = new Color( 80, 200, 200, 255);
	private static final Color CLR_MENAPHITE      = new Color( 60, 180, 140, 255);
	private static final Color CLR_ADRENALINE     = new Color(180, 200,  40, 255);

	// Combat skills tracked for skill boosts panel
	static final Skill[] TRACKED_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.PRAYER, Skill.HITPOINTS
	};

	@Inject private Client          client;
	@Inject private MmoFramesConfig config;
	@Inject private ItemManager     itemManager;
	@Inject private SpriteManager   spriteManager;

	// Optional — available only when the ItemStats plugin is active
	@com.google.inject.Inject(optional = true)
	private ItemStatChangesService itemStatChanges;

	// ---- Tick sweep state (0.0-1.0) -----------------------------------------

	@Getter private double hpRegenProgress;
	@Getter private double prayerDrainProgress;
	@Getter private double specRegenProgress;

	private int    hpRegenTick;
	private double prayerDrainTick;
	private int    specRegenTick;

	private int lastHp   = -1;
	private int lastPray = -1;
	private int lastSpec = -1;

	// ---- Poison / venom state -----------------------------------------------

	@Getter private int poisonState;

	// ---- Skill boosts -------------------------------------------------------

	@Getter private Map<Skill, Integer> skillBoosts;

	// ---- Status effects (lazily populated) ----------------------------------

	private PlayerPoisonEffect           playerPoisonEffect;
	private AntipoisonImmunityEffect     antipoisonImmunityEffect;
	private StaminaEffect                staminaEffect;
	private final List<VarbitTimerEffect>  potionEffects     = new ArrayList<>();
	private final List<ActivePrayerEffect> prayerEffects     = new ArrayList<>();
	private final List<SkillBoostEffect>   skillBoostEffects = new ArrayList<>();

	// ---- Icons (lazy loaded) ------------------------------------------------

	private BufferedImage hpIconNormal;
	private BufferedImage hpIconPoison;
	private BufferedImage hpIconVenom;
	private boolean       hpAltIconsLoaded;
	private BufferedImage prayerBarIcon;
	private BufferedImage specBarIcon;

	// =========================================================================
	// Lifecycle
	// =========================================================================

	public void startUp()
	{
		skillBoosts = new EnumMap<>(Skill.class);
		playerPoisonEffect       = new PlayerPoisonEffect(client);
		antipoisonImmunityEffect = new AntipoisonImmunityEffect(client);
		staminaEffect            = new StaminaEffect(client, spriteManager);
		initPotionEffects();
		resetState();
	}

	public void shutDown()
	{
		// nothing to clean up currently
	}

	// =========================================================================
	// Event handlers (called by MmoFramesPlugin)
	// =========================================================================

	public void onGameTick()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		poisonState = client.getVarpValue(VarPlayerID.POISON);

		tickHpRegen();
		tickPrayerDrain();
		tickSpecRegen();
	}

	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		for (Skill tracked : TRACKED_SKILLS)
		{
			if (tracked == skill)
			{
				if (skillBoosts != null)
				{
					skillBoosts.put(skill, event.getBoostedLevel() - event.getLevel());
				}
				break;
			}
		}
	}

	// =========================================================================
	// State reset
	// =========================================================================

	public void resetState()
	{
		hpRegenTick = specRegenTick = 0;
		prayerDrainTick = 0.0;
		hpRegenProgress = prayerDrainProgress = specRegenProgress = 0.0;
		lastHp = lastPray = lastSpec = -1;
		poisonState = 0;
		if (skillBoosts != null) skillBoosts.clear();
	}

	public void resetForTeleport()
	{
		hpRegenTick = specRegenTick = 0;
		prayerDrainTick = 0.0;
		hpRegenProgress = prayerDrainProgress = specRegenProgress = 0.0;
		lastHp = lastPray = lastSpec = -1;
		// skillBoosts preserved — boosts persist through teleports
		// poisonState preserved — poison/venom persists through teleports
	}

	// =========================================================================
	// Status effects
	// =========================================================================

	public List<StatusEffect> getBuffs()
	{
		List<StatusEffect> buffs = new ArrayList<>();

		// Antipoison / anti-venom immunity (positive — shows remaining duration)
		buffs.add(antipoisonImmunityEffect);

		// Stamina potion buff (positive)
		buffs.add(staminaEffect);

		// Potion / spell timer effects (positive)
		buffs.addAll(potionEffects);

		// Active prayers (positive) — one effect per prayer, lazily populated
		ensurePrayerEffects();

		buffs.addAll(prayerEffects);

		// Skill boosts — lazily populate effect wrappers on first call
		if (config.showSkillBoosts())
		{
			Map<Skill, Integer> positiveBoosts = skillBoosts.entrySet()
				.stream()
				.filter(e -> e.getValue() > 0)
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

			if (positiveBoosts != null)
			{
				for (Skill s : TRACKED_SKILLS)
				{
					buffs.add(new SkillBoostEffect(s, positiveBoosts.getOrDefault(s, 0), spriteManager));
				}
			}
		}

		return buffs;
	}

	public List<StatusEffect> getDebuffs()
	{
		List<StatusEffect> debuffs = new ArrayList<>();

		// Poison / venom
		debuffs.add(playerPoisonEffect);

		// Skill boosts — lazily populate effect wrappers on first call
		if (config.showSkillBoosts())
		{
			Map<Skill, Integer> negativeBoosts = skillBoosts.entrySet()
				.stream()
				.filter(e -> e.getValue() < 0)
				.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

			if (negativeBoosts != null)
			{
				for (Skill s : TRACKED_SKILLS)
				{
					debuffs.add(new SkillBoostEffect(s, negativeBoosts.getOrDefault(s, 0), spriteManager));
				}
			}
		}

		return debuffs;
	}

	private void ensurePrayerEffects()
	{
		if (!prayerEffects.isEmpty())
		{
			return;
		}
		for (Prayer p : Prayer.values())
		{
			prayerEffects.add(new ActivePrayerEffect(p, client, spriteManager));
		}
	}

	private void initPotionEffects()
	{
		// Dragonfire protection
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_ANTIFIRE, 30, "AFR", CLR_ANTIFIRE, -1));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_SUPER_ANTIFIRE, 20, "S.AFR", CLR_SUPER_ANTIFIRE, -1));

		// Divine potions (value = raw ticks remaining)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_SUPER_ATTACK, 1, "D.ATK", CLR_DIVINE, SpriteID.SKILL_ATTACK));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_SUPER_STRENGTH, 1, "D.STR", CLR_DIVINE, SpriteID.SKILL_STRENGTH));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_SUPER_DEFENCE, 1, "D.DEF", CLR_DIVINE, SpriteID.SKILL_DEFENCE));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_RANGING, 1, "D.RNG", CLR_DIVINE, SpriteID.SKILL_RANGED));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_MAGIC, 1, "D.MAG", CLR_DIVINE, SpriteID.SKILL_MAGIC));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_SUPER_COMBAT, 1, "D.CMB", CLR_DIVINE, SpriteID.SKILL_ATTACK));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_BASTION, 1, "D.BAS", CLR_DIVINE, SpriteID.SKILL_RANGED));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_DIVINE_BATTLEMAGE, 1, "D.BAT", CLR_DIVINE, SpriteID.SKILL_MAGIC));

		// Overloads (value = refreshes remaining, each refresh = 25 ticks)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_NMZ_OVERLOAD, 25, "OVL", CLR_OVERLOAD, -1));
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_COX_OVERLOAD, 25, "OVL", CLR_OVERLOAD, -1));

		// Vengeance (active flag, no timer)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_VENGEANCE_ACTIVE, 0, "VNG", CLR_VENGEANCE, -1));

		// Magic Imbue (value × 10 = ticks)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_MAGIC_IMBUE, 10, "IMB", CLR_MAGIC_IMBUE, SpriteID.SKILL_MAGIC));

		// Goading Potion (value × 6 = ticks)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_GOADING_POTION, 6, "GOD", CLR_GOADING, -1));

		// Prayer Regeneration Potion (value × 12 = ticks)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_PRAYER_REGEN, 12, "P.RGN", CLR_PRAYER_REGEN, SpriteID.SKILL_PRAYER));

		// Menaphite Remedy (value × 25 = ticks)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_MENAPHITE_REMEDY, 25, "MEN", CLR_MENAPHITE, -1));

		// Liquid Adrenaline (active flag, no timer)
		potionEffects.add(new VarbitTimerEffect(client, spriteManager,
			VARBIT_LIQUID_ADRENALINE, 0, "ADR", CLR_ADRENALINE, SpriteID.MINIMAP_ORB_SPECIAL_ICON));
	}

	// =========================================================================
	// Consumable hover
	// =========================================================================

	public int getHealHp()
	{
		if (itemStatChanges == null)
		{
			return 0;
		}
		return getRestoreValue(Skill.HITPOINTS.getName());
	}

	public int getHealPrayer()
	{
		if (itemStatChanges == null)
		{
			return 0;
		}
		return getRestoreValue(Skill.PRAYER.getName());
	}

	private int getRestoreValue(String skill)
	{
		final MenuEntry[] menu = client.getMenuEntries();
		final int menuSize = menu.length;
		if (menuSize == 0)
		{
			return 0;
		}

		final MenuEntry entry = menu[menuSize - 1];
		final Widget widget = entry.getWidget();
		int restoreValue = 0;

		if (widget != null && widget.getId() == InterfaceID.Inventory.ITEMS)
		{
			final Effect change = itemStatChanges.getItemStatChanges(widget.getItemId());

			if (change != null)
			{
				for (final StatChange c : change.calculate(client).getStatChanges())
				{
					final int value = c.getTheoretical();

					if (value != 0 && c.getStat().getName().equals(skill))
					{
						restoreValue = value;
					}
				}
			}
		}

		return restoreValue;
	}

	// =========================================================================
	// Icons (lazy loaded)
	// =========================================================================

	public BufferedImage getHpIcon()
	{
		if (!hpAltIconsLoaded)
		{
			hpAltIconsLoaded = true;
			hpIconPoison = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
			hpIconVenom  = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);
		}
		if (hpIconNormal == null)
		{
			hpIconNormal = spriteManager.getSprite(SpriteID.MINIMAP_ORB_HITPOINTS_ICON, 0);
		}

		return poisonState >= VENOM_THRESHOLD ? hpIconVenom
			: poisonState > 0                ? hpIconPoison
			                                 : hpIconNormal;
	}

	public BufferedImage getPrayerBarIcon()
	{
		if (prayerBarIcon == null)
		{
			prayerBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_PRAYER_ICON, 0);
		}
		return prayerBarIcon;
	}

	public BufferedImage getSpecBarIcon()
	{
		if (specBarIcon == null)
		{
			specBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_SPECIAL_ICON, 0);
		}
		return specBarIcon;
	}

	// =========================================================================
	// Tick logic
	// =========================================================================

	private void tickHpRegen()
	{
		int hp    = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (hp >= maxHp)
		{
			hpRegenProgress = 0.0;
			hpRegenTick = 0;
			lastHp = hp;
			return;
		}

		// Any HP change (damage / food) resets the regen clock
		if (hp != lastHp)
		{
			lastHp = hp;
			hpRegenTick = 0;
		}

		hpRegenTick = (hpRegenTick + 1) % HP_REGEN_TICKS;
		hpRegenProgress = (double) hpRegenTick / HP_REGEN_TICKS;
	}

	private void tickPrayerDrain()
	{
		int prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);

		int totalDrainEffect = PrayerDrainRates.getTotalDrainEffect(client);

		if (totalDrainEffect == 0 || prayerPoints <= 0)
		{
			prayerDrainTick = 0;
			prayerDrainProgress = 0.0;
			lastPray = prayerPoints;
			return;
		}

		// Compute prayer bonus from equipment
		int prayerBonus = 0;
		ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equip != null)
		{
			for (Item item : equip.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					ItemStats stats = itemManager.getItemStats(item.getId());
					if (stats != null && stats.getEquipment() != null)
					{
						prayerBonus += stats.getEquipment().getPrayer();
					}
				}
			}
		}

		// Drain interval in ticks (float): resistance / drainEffect
		double drainInterval = Math.max(1.0, (60.0 + 2.0 * prayerBonus) / totalDrainEffect);

		// On observed prayer drop: drain just happened — reset cycle
		if (lastPray >= 0 && prayerPoints < lastPray)
		{
			prayerDrainTick = 0;
			prayerDrainProgress = 0.0;
			lastPray = prayerPoints;
			return;
		}
		lastPray = prayerPoints;

		// Advance elapsed ticks, wrapping at drain interval
		prayerDrainTick = (prayerDrainTick + 1) % drainInterval;

		// Progress = 1 - elapsed/interval: full right after drain, zero just before next
		prayerDrainProgress = 1.0 - prayerDrainTick / drainInterval;
	}

	private void tickSpecRegen()
	{
		// VarPlayerID.SA_ENERGY stores spec as 0-1000 (1000 = 100%)
		int specRaw = client.getVarpValue(VarPlayerID.SA_ENERGY);
		int spec    = specRaw / 10;

		if (spec >= 100)
		{
			specRegenProgress = 0.0;
			specRegenTick = 0;
			lastSpec = spec;
			return;
		}

		if (spec != lastSpec)
		{
			lastSpec = spec;
			specRegenTick = 0;
		}

		specRegenTick = (specRegenTick + 1) % SPEC_REGEN_TICKS;
		specRegenProgress = (double) specRegenTick / SPEC_REGEN_TICKS;
	}
}
