package com.mmoframes;

import com.mmoframes.status.ActivePrayerEffect;
import com.mmoframes.status.PlayerPoisonEffect;
import com.mmoframes.status.SkillBoostEffect;
import com.mmoframes.status.StaminaEffect;
import com.mmoframes.status.StatusEffect;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
	private StaminaEffect                staminaEffect;
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
		playerPoisonEffect = new PlayerPoisonEffect(client);
		staminaEffect      = new StaminaEffect(client, spriteManager);
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

		// Stamina potion buff (positive)
		buffs.add(staminaEffect);

		// Active prayers (positive) — one effect per prayer, lazily populated
		ensurePrayerEffects();
		buffs.addAll(prayerEffects);

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
			Map<Skill, Integer> boosts = skillBoosts;
			if (boosts != null)
			{
				ensureBoostEffects(boosts);
				debuffs.addAll(skillBoostEffects);
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

	private void ensureBoostEffects(Map<Skill, Integer> boosts)
	{
		if (!skillBoostEffects.isEmpty())
		{
			return;
		}
		for (Skill s : TRACKED_SKILLS)
		{
			skillBoostEffects.add(new SkillBoostEffect(s, boosts, spriteManager));
		}
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
