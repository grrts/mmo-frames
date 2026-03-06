package com.mmoframes;

import com.mmoframes.StatusFrameRenderer;
import com.mmoframes.status.ActivePrayerEffect;
import com.mmoframes.status.PlayerPoisonEffect;
import com.mmoframes.status.SkillBoostEffect;
import com.mmoframes.status.StaminaEffect;
import com.mmoframes.status.StatusEffect;
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

	// Optional — available only when the ItemStats plugin is active
	@com.google.inject.Inject(optional = true)
	private ItemStatChangesService itemStatChanges;

	private final PlayerPoisonEffect     playerPoisonEffect;
	private final StaminaEffect          staminaEffect;
	private final List<ActivePrayerEffect> prayerEffects   = new ArrayList<>();
	private final List<SkillBoostEffect>   skillBoostEffects = new ArrayList<>();

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
		SpriteManager spriteManager)
	{
		this.client             = client;
		this.config             = config;
		this.plugin             = plugin;
		this.spriteManager      = spriteManager;
		this.playerPoisonEffect = new PlayerPoisonEffect(client);
		this.staminaEffect      = new StaminaEffect(client, spriteManager);

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
			plugin.getPlayerPortrait(),
			null,  // no tint for player portrait fallback
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
	 * Builds the combined list of status effects to render below the player frame.
	 * Order: poison/venom first, then any active skill boosts/drains.
	 */
	private List<StatusEffect> buildStatusEffects()
	{
		List<StatusEffect> list = new ArrayList<>();

		// Poison / venom
		list.add(playerPoisonEffect);

		// Stamina potion buff (positive)
		list.add(staminaEffect);

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
