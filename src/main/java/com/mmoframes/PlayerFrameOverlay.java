package com.mmoframes;

import com.mmoframes.StatusFrameRenderer;
import com.mmoframes.status.ActivePrayerEffect;
import com.mmoframes.status.AntipoisonImmunityEffect;
import com.mmoframes.status.PlayerPoisonEffect;
import com.mmoframes.status.SkillBoostEffect;
import com.mmoframes.status.StaminaEffect;
import com.mmoframes.status.StatusEffect;
import com.mmoframes.status.VarbitTimerEffect;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.MenuEntry;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.StatChange;
import java.awt.image.BufferedImage;
import net.runelite.api.SpriteID;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Player unit frame overlay.
 *
 * Stats source:
 *   HP / Prayer  → client.getBoostedSkillLevel / getRealSkillLevel
 *   Run energy   → client.getEnergy()                    (0–10000)
 *   Spec         → client.getVarpValue(VarPlayerID.SA_ENERGY) / 10  (0–100)
 *   Combat level → client.getLocalPlayer().getCombatLevel()
 */
public class PlayerFrameOverlay extends Overlay
{
	private final Client          client;
	private final MmoFramesConfig config;
	private final MmoFramesPlugin plugin;
	private final SpriteManager   spriteManager;
	private final ChatHeadService chatHeadService;

	// Optional — available only when the ItemStats plugin is active
	@com.google.inject.Inject(optional = true)
	private ItemStatChangesService itemStatChanges;

	private final PlayerPoisonEffect        playerPoisonEffect;
	private final AntipoisonImmunityEffect antipoisonImmunityEffect;
	private final StaminaEffect            staminaEffect;
	private final List<VarbitTimerEffect>   potionEffects = new ArrayList<>();
	private final List<ActivePrayerEffect>  prayerEffects   = new ArrayList<>();
	private final List<SkillBoostEffect>    skillBoostEffects = new ArrayList<>();

	// HP bar heart icons — lazy loaded
	private static final int VENOM_THRESHOLD = 1_000_000;
	private BufferedImage hpIconNormal;
	private BufferedImage hpIconPoison;
	private BufferedImage hpIconVenom;
	private boolean       hpAltIconsLoaded;

	// Prayer bar icon — lazy loaded
	private BufferedImage prayerBarIcon;

	// Special attack icon — lazy loaded
	private BufferedImage specBarIcon;

	@Inject
	public PlayerFrameOverlay(Client client, MmoFramesConfig config, MmoFramesPlugin plugin,
		SpriteManager spriteManager, ChatHeadService chatHeadService)
	{
		this.client          = client;
		this.config          = config;
		this.plugin          = plugin;
		this.spriteManager   = spriteManager;
		this.chatHeadService = chatHeadService;
		this.playerPoisonEffect       = new PlayerPoisonEffect(client);
		this.antipoisonImmunityEffect = new AntipoisonImmunityEffect(client);
		this.staminaEffect            = new StaminaEffect(client, spriteManager);
		initPotionEffects();

		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
		setMovable(true);
		setResizable(false);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showPlayerFrame() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		int hp      = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp   = client.getRealSkillLevel(Skill.HITPOINTS);
		int pray    = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPray = client.getRealSkillLevel(Skill.PRAYER);
		int energy  = config.showPlayerStamina() ? client.getEnergy() : -1;
		int spec    = config.showSpecialAttack()
			? client.getVarpValue(VarPlayerID.SA_ENERGY) / 10
			: -1;

		String name  = client.getLocalPlayer() != null
			? client.getLocalPlayer().getName()
			: "Player";
		int    level = client.getLocalPlayer() != null
			? client.getLocalPlayer().getCombatLevel()
			: -1;

		boolean showPrayer  = config.showPlayerPrayer();
		boolean showStamina = config.showPlayerStamina();

		int mainW  = config.playerFrameWidth();
		int frameW = UnitFrameRenderer.calcFrameWidth(mainW, config.showSpecialAttack());
		int frameH = UnitFrameRenderer.calcFrameHeight(showPrayer, showStamina);

		// Feature 7: consumable hover — matches RuneLite StatusBars getRestoreValue exactly
		int healHp = 0, healPrayer = 0;
		if (itemStatChanges != null)
		{
			healHp     = getRestoreValue(Skill.HITPOINTS.getName());
			healPrayer = getRestoreValue(Skill.PRAYER.getName());
		}

		// ── Split status effects: positive (above) / negative (below) ──────────
		List<StatusEffect> allEffects      = buildStatusEffects();
		List<StatusEffect> positiveEffects = new ArrayList<>();
		List<StatusEffect> negativeEffects = new ArrayList<>();
		for (StatusEffect e : allEffects)
		{
			(e.isPositive() ? positiveEffects : negativeEffects).add(e);
		}

		int aboveH = StatusFrameRenderer.calcHeight(positiveEffects);

		// ── Positive effects ABOVE the frame — drawn at negative Y so the
		//    main frame always stays at (0,0) regardless of which effects are active.
		if (aboveH > 0)
		{
			StatusFrameRenderer.renderStatusEffects(g, positiveEffects, 0, -aboveH);
		}

		// ── HP bar icon: normal / poison / venom heart ───────────────────────
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
		if (prayerBarIcon == null)
		{
			prayerBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_PRAYER_ICON, 0);
		}
		if (specBarIcon == null)
		{
			specBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_SPECIAL_ICON, 0);
		}
		int           pState = plugin.getPoisonState();
		BufferedImage hpIcon = pState >= VENOM_THRESHOLD ? hpIconVenom
		                     : pState > 0               ? hpIconPoison
		                                                : hpIconNormal;

		// ── Position the chat-head widget at the portrait inner area ─────────
		// The overlay avoids painting over that area (portraitWidget=true), so the
		// widget's pixels show through the hole left in the frame's interior fill.
		AffineTransform tx = g.getTransform();
		int overlayX = (int) Math.round(tx.getTranslateX());
		int overlayY = (int) Math.round(tx.getTranslateY());
		// Portrait inner origin = overlay origin + (BORDER + PAD) + BORDER = 2*BORDER + PAD = 18
		int innerOff = UnitFrameRenderer.BORDER * 2 + UnitFrameRenderer.PAD;
		chatHeadService.requestPosition(overlayX + innerOff, overlayY + innerOff);

		// ── Main frame always at (0,0) — anchor is stable regardless of effects ─
		UnitFrameRenderer.renderFrame(
			g,
			mainW,
			name,
			level,
			hp,   maxHp,
			showPrayer ? pray : -1,  maxPray,
			energy,
			spec,
			plugin.getHpRegenProgress(),
			plugin.getPrayerDrainProgress(),
			plugin.getSpecRegenProgress(),
			config,
			showPrayer,
			showStamina,
			client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0,
			chatHeadService.getImage(), // captured chathead (or null → fallback letter)
			null,
			pState,
			healHp,
			healPrayer,
			hpIcon,
			prayerBarIcon,
			specBarIcon
		);

		// ── Negative effects below the frame ─────────────────────────────────
		int belowH = StatusFrameRenderer.renderStatusEffects(
			g, negativeEffects, 0, frameH);

		return new Dimension(frameW, frameH + belowH);
	}

	/**
	 * Returns the theoretical restore value for the given skill name from the
	 * hovered inventory item. Matches the RuneLite StatusBars plugin implementation.
	 */
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

	/**
	 * Builds the combined list of status effects to render on the player frame.
	 * Order: poison/venom, antipoison immunity, stamina, potion buffs, prayers, skill boosts.
	 */
	private List<StatusEffect> buildStatusEffects()
	{
		List<StatusEffect> list = new ArrayList<>();

		// Poison / venom (negative)
		list.add(playerPoisonEffect);

		// Antipoison / anti-venom immunity (positive)
		list.add(antipoisonImmunityEffect);

		// Stamina potion buff (positive)
		list.add(staminaEffect);

		// All potion / spell buff effects (positive)
		list.addAll(potionEffects);

		// Active prayers (positive) — one effect per prayer, lazily populated
		ensurePrayerEffects();
		list.addAll(prayerEffects);

		// Skill boosts — lazily populate effect wrappers on first call
		if (config.showSkillBoosts())
		{
			Map<Skill, Integer> boosts = plugin.getSkillBoosts();
			if (boosts != null)
			{
				ensureBoostEffects(boosts);
				list.addAll(skillBoostEffects);
			}
		}

		return list;
	}

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

	/**
	 * Creates {@link SkillBoostEffect} wrappers once, reusing the same shared map
	 * reference so values stay live each frame without re-allocation.
	 */
	private void ensureBoostEffects(Map<Skill, Integer> boosts)
	{
		if (!skillBoostEffects.isEmpty())
		{
			return;
		}
		for (Skill s : MmoFramesPlugin.TRACKED_SKILLS)
		{
			skillBoostEffects.add(new SkillBoostEffect(s, boosts, spriteManager));
		}
	}
}
