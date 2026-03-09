package com.mmoframes;

import com.mmoframes.StatusFrameRenderer;
import com.mmoframes.status.SlayerTaskEffect;
import com.mmoframes.status.StatusEffect;
import com.mmoframes.status.TargetPrayerEffect;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.SpriteID;
import net.runelite.client.game.NPCManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Target unit frame overlay.
 *
 * HP is resolved via the same inverse-healthRatio calculation used by the
 * RuneLite OpponentInfo plugin:
 *   - For NPCs: exact HP is back-calculated from healthRatio/healthScale using
 *     the NPC's true max HP from {@link NPCManager}.
 *   - For Players / unknown max HP: falls back to ratio/scale as a proportion.
 *
 * No prayer / stamina / spec bars — those values are not available for targets.
 */
public class TargetFrameOverlay extends Overlay
{
	private final Client          client;
	private final MmoFramesConfig config;
	private final MmoFramesPlugin plugin;
	private final NPCManager      npcManager;
	private final SpriteManager   spriteManager;

	// Tracked state — updated each frame when healthScale > 0 (matches OpponentInfo pattern)
	private int     lastRatio       = 0;
	private int     lastHealthScale = 0;
	private Integer lastMaxHealth   = null;
	private Actor   lastTrackedTarget = null;

	private TargetPrayerEffect targetPrayerEffect = null;
	private SlayerTaskEffect   slayerTaskEffect   = null;

	private BufferedImage hpIconNormal;

	@Inject
	public TargetFrameOverlay(Client client, MmoFramesConfig config, MmoFramesPlugin plugin,
		NPCManager npcManager, SpriteManager spriteManager)
	{
		this.client        = client;
		this.config        = config;
		this.plugin        = plugin;
		this.npcManager    = npcManager;
		this.spriteManager = spriteManager;

		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
		setMovable(true);
		setResizable(false);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showTargetFrame() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Actor target = plugin.getLingerTarget();
		if (target == null)
		{
			return null;
		}

		// Reset tracked state when target changes
		if (target != lastTrackedTarget)
		{
			lastTrackedTarget  = target;
			lastRatio          = 0;
			lastHealthScale    = 0;
			lastMaxHealth      = null;
			targetPrayerEffect = new TargetPrayerEffect(target, spriteManager);
			slayerTaskEffect   = (target instanceof NPC)
				? new SlayerTaskEffect((NPC) target, client, spriteManager)
				: null;
		}

		String name = target.getName();
		if (name == null) name = "Unknown";

		// Combat level
		int level = -1;
		if (config.showTargetLevel())
		{
			if (target instanceof NPC)
			{
				NPCComposition comp = ((NPC) target).getComposition();
				if (comp != null) level = comp.getCombatLevel();
			}
			else if (target instanceof Player)
			{
				level = ((Player) target).getCombatLevel();
			}
		}

		// ── HP calculation — matches OpponentInfo plugin exactly ────────────────
		// Only update ratio/scale/maxHP when the health bar is actively visible
		if (target.getHealthScale() > 0)
		{
			lastRatio       = target.getHealthRatio();
			lastHealthScale = target.getHealthScale();

			// Look up true max HP for NPCs via NPCManager (same as OpponentInfo)
			lastMaxHealth = null;
			if (target instanceof NPC)
			{
				lastMaxHealth = npcManager.getHealth(((NPC) target).getId());
			}
		}

		int dispCur, dispMax;
		if (lastHealthScale <= 0)
		{
			// No health data yet — show full bar as placeholder
			dispCur = dispMax = 1;
		}
		else if (lastMaxHealth != null)
		{
			// Reverse the server's healthRatio formula to recover actual HP.
			// Server formula: healthRatio = 1 + (healthScale - 1) * health / maxHealth  (if health > 0)
			// Reverse (from OpponentInfo):
			dispMax = lastMaxHealth;
			dispCur = reverseHealthRatio(lastRatio, lastHealthScale, lastMaxHealth);
		}
		else
		{
			// Max HP unknown — use ratio/scale as a proportion (bar is correct, numbers are estimates)
			dispCur = lastRatio;
			dispMax = lastHealthScale;
		}

		// ── Portrait ─────────────────────────────────────────────────────────────
		BufferedImage portrait     = null;
		Color         portraitBgColor = null;

		if (target instanceof Player)
		{
			portrait = null;
		}
		else if (target instanceof NPC)
		{
			portraitBgColor = npcPortraitColor(level);
		}

		int mainW  = config.targetFrameWidth();
		int frameH = UnitFrameRenderer.calcFrameHeight(false, false);

		// ── Split target status effects: positive (above) / negative (below) ───
		List<StatusEffect> allEffects      = plugin.getNpcStatusTracker().getActiveEffects(target);
		List<StatusEffect> positiveEffects = new ArrayList<>();
		List<StatusEffect> negativeEffects = new ArrayList<>();
		if (targetPrayerEffect != null)
		{
			positiveEffects.add(targetPrayerEffect);
		}
		if (slayerTaskEffect != null)
		{
			positiveEffects.add(slayerTaskEffect);
		}
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

		// ── HP orb icon (normal, targets are never poisoned) ─────────────────
		if (hpIconNormal == null)
		{
			hpIconNormal = spriteManager.getSprite(SpriteID.MINIMAP_ORB_HITPOINTS_ICON, 0);
		}

		// ── Main frame always at (0,0) — anchor is stable regardless of effects ─
		UnitFrameRenderer.renderFrame(
			g,
			mainW,
			name,
			level,
			dispCur, dispMax,
			-1, 0,     // no prayer
			-1,        // no stamina
			-1,        // no spec (-1 hides the spec square)
			0.0, 0.0, 0.0,
			config,
			false,     // no prayer bar
			false,     // no stamina bar
			false,     // no stamina active
			portrait,
			portraitBgColor,
			0,         // no poison indicator for targets
			0,         // no heal hover for targets
			0,         // no prayer hover for targets
			hpIconNormal,
			null,      // no prayer bar on targets
			null       // no spec on targets
		);

		// ── Negative effects below the frame ─────────────────────────────────
		int belowH = StatusFrameRenderer.renderStatusEffects(
			g, negativeEffects, 0, frameH);

		return new Dimension(mainW, frameH + belowH);
	}

	/**
	 * Reverses the server's healthRatio → health calculation.
	 *
	 * Server encodes: healthRatio = 1 + (healthScale - 1) * health / maxHealth  (health > 0)
	 *                 healthRatio = 0                                             (health = 0)
	 *
	 * This is an exact copy of the OpponentInfo plugin's reverse calculation,
	 * which recovers the precise HP when maxHealth <= healthScale.
	 */
	private static int reverseHealthRatio(int ratio, int scale, int maxHealth)
	{
		if (ratio <= 0)
		{
			return 0;
		}

		int minHealth = 1;
		int maxHealthCalc;

		if (scale > 1)
		{
			if (ratio > 1)
			{
				minHealth = (maxHealth * (ratio - 1) + scale - 2) / (scale - 1);
			}
			maxHealthCalc = (maxHealth * ratio - 1) / (scale - 1);
			if (maxHealthCalc > maxHealth)
			{
				maxHealthCalc = maxHealth;
			}
		}
		else
		{
			// healthScale == 1: ratio is always 1 unless dead, upper bound is unknown
			maxHealthCalc = maxHealth;
		}

		return (minHealth + maxHealthCalc + 1) / 2;
	}

	/** Returns a combat-level-based tint colour for NPC portrait backgrounds. */
	private static Color npcPortraitColor(int level)
	{
		if (level <= 0)   return new Color(60, 60, 60, 200);
		if (level <= 50)  return new Color(20, 80, 20, 200);
		if (level <= 100) return new Color(80, 70, 10, 200);
		if (level <= 150) return new Color(90, 45, 10, 200);
		return new Color(80, 15, 15, 200);
	}
}
