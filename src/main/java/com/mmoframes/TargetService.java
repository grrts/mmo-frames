package com.mmoframes;

import com.mmoframes.status.SlayerTaskEffect;
import com.mmoframes.status.StatusEffect;
import com.mmoframes.status.TargetPrayerEffect;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.PlayerComposition;
import net.runelite.api.SpriteID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.NPCManager;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.AsyncBufferedImage;

@Slf4j
@Singleton
public class TargetService
{
	@Inject private Client             client;
	@Inject private MmoFramesConfig    config;
	@Inject private ItemManager        itemManager;
	@Inject private NPCManager         npcManager;
	@Inject private SpriteManager      spriteManager;
	@Inject private NpcTrackingService npcTrackingService;

	// ---- Linger target ------------------------------------------------------

	@Getter private Actor lingerTarget;
	private int lingerTicksRemaining;
	private Actor lastPortraitTarget;

	// ---- Target portrait ----------------------------------------------------

	@Getter private volatile BufferedImage targetPortrait;

	// ---- HP tracking (updated per render frame) -----------------------------

	private int     lastRatio       = 0;
	private int     lastHealthScale = 0;
	private Integer lastMaxHealth   = null;
	private Actor   lastTrackedTarget = null;

	// ---- Status effects -----------------------------------------------------

	private TargetPrayerEffect targetPrayerEffect = null;
	private SlayerTaskEffect   slayerTaskEffect   = null;

	// ---- Icons (lazy loaded) ------------------------------------------------

	private BufferedImage hpIconNormal;

	// =========================================================================
	// Lifecycle
	// =========================================================================

	public void startUp()
	{
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

		Actor currentTarget = client.getLocalPlayer() != null
			? client.getLocalPlayer().getInteracting()
			: null;

		if (currentTarget != null)
		{
			lingerTarget = currentTarget;
			lingerTicksRemaining = config.targetLingerSeconds() * 100 / 60;
			if (currentTarget != lastPortraitTarget)
			{
				lastPortraitTarget = currentTarget;
				refreshTargetPortrait(currentTarget);
			}
		}
		else if (lingerTicksRemaining > 0)
		{
			lingerTicksRemaining--;
			if (lingerTicksRemaining == 0)
			{
				lingerTarget = null;
				lastPortraitTarget = null;
				targetPortrait = null;
			}
		}
		else
		{
			lingerTarget = null;
		}
	}

	// =========================================================================
	// State reset
	// =========================================================================

	public void resetState()
	{
		lingerTarget = null;
		lingerTicksRemaining = 0;
		lastPortraitTarget = null;
		targetPortrait = null;
		lastRatio = 0;
		lastHealthScale = 0;
		lastMaxHealth = null;
		lastTrackedTarget = null;
		targetPrayerEffect = null;
		slayerTaskEffect = null;
	}

	public void resetForTeleport()
	{
		lingerTarget = null;
		lingerTicksRemaining = 0;
		lastPortraitTarget = null;
		targetPortrait = null;
		lastRatio = 0;
		lastHealthScale = 0;
		lastMaxHealth = null;
		lastTrackedTarget = null;
		targetPrayerEffect = null;
		slayerTaskEffect = null;
	}

	// =========================================================================
	// Target HP tracking
	// =========================================================================

	/**
	 * Updates HP tracking state for the current target. Should be called each
	 * render frame when a target is visible.
	 */
	public void updateTargetHealth(Actor target)
	{
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

		// Only update ratio/scale/maxHP when the health bar is actively visible
		if (target.getHealthScale() > 0)
		{
			lastRatio       = target.getHealthRatio();
			lastHealthScale = target.getHealthScale();

			lastMaxHealth = null;
			if (target instanceof NPC)
			{
				lastMaxHealth = npcManager.getHealth(((NPC) target).getId());
			}
		}
	}

	public int getDisplayHp()
	{
		if (lastHealthScale <= 0)
		{
			return 1;
		}
		else if (lastMaxHealth != null)
		{
			return reverseHealthRatio(lastRatio, lastHealthScale, lastMaxHealth);
		}
		else
		{
			return lastRatio;
		}
	}

	public int getDisplayMaxHp()
	{
		if (lastHealthScale <= 0)
		{
			return 1;
		}
		else if (lastMaxHealth != null)
		{
			return lastMaxHealth;
		}
		else
		{
			return lastHealthScale;
		}
	}

	// =========================================================================
	// Target info
	// =========================================================================

	public String getTargetName()
	{
		Actor target = lingerTarget;
		if (target == null) return "Unknown";
		String name = target.getName();
		return name != null ? name : "Unknown";
	}

	public int getTargetLevel()
	{
		Actor target = lingerTarget;
		if (target == null || !config.showTargetLevel()) return -1;

		if (target instanceof NPC)
		{
			NPCComposition comp = ((NPC) target).getComposition();
			if (comp != null) return comp.getCombatLevel();
		}
		else if (target instanceof Player)
		{
			return ((Player) target).getCombatLevel();
		}
		return -1;
	}

	// =========================================================================
	// Portrait
	// =========================================================================

	public BufferedImage getPortrait()
	{
		Actor target = lingerTarget;
		if (target instanceof Player)
		{
			return targetPortrait;
		}
		return null;
	}

	public Color getPortraitBgColor()
	{
		Actor target = lingerTarget;
		if (target instanceof NPC)
		{
			return npcPortraitColor(getTargetLevel());
		}
		return null;
	}

	private void refreshTargetPortrait(Actor target)
	{
		if (target instanceof Player)
		{
			PlayerComposition pc = ((Player) target).getPlayerComposition();
			if (pc != null)
			{
				int kitId = pc.getEquipmentIds()[net.runelite.api.kit.KitType.HEAD.getIndex()];
				if (kitId >= PlayerComposition.ITEM_OFFSET)
				{
					int itemId = kitId - PlayerComposition.ITEM_OFFSET;
					AsyncBufferedImage img = itemManager.getImage(itemId, 1, false);
					img.onLoaded(() -> targetPortrait = img);
					return;
				}
			}
		}
		targetPortrait = null;
	}

	private static Color npcPortraitColor(int level)
	{
		if (level <= 0)   return new Color(60, 60, 60, 200);
		if (level <= 50)  return new Color(20, 80, 20, 200);
		if (level <= 100) return new Color(80, 70, 10, 200);
		if (level <= 150) return new Color(90, 45, 10, 200);
		return new Color(80, 15, 15, 200);
	}

	// =========================================================================
	// Status effects
	// =========================================================================

	public List<StatusEffect> getBuffs()
	{
		List<StatusEffect> buffs = new ArrayList<>();
		if (targetPrayerEffect != null)
		{
			buffs.add(targetPrayerEffect);
		}
		if (slayerTaskEffect != null)
		{
			buffs.add(slayerTaskEffect);
		}
		return buffs;
	}

	public List<StatusEffect> getDebuffs()
	{
		Actor target = lingerTarget;
		if (target == null)
		{
			return new ArrayList<>();
		}
		List<StatusEffect> allNpc = npcTrackingService.getActiveEffects(target);
		List<StatusEffect> debuffs = new ArrayList<>();
		for (StatusEffect e : allNpc)
		{
			if (!e.isPositive())
			{
				debuffs.add(e);
			}
		}
		return debuffs;
	}

	// =========================================================================
	// Icons
	// =========================================================================

	public BufferedImage getHpIcon()
	{
		if (hpIconNormal == null)
		{
			hpIconNormal = spriteManager.getSprite(SpriteID.MINIMAP_ORB_HITPOINTS_ICON, 0);
		}
		return hpIconNormal;
	}

	// =========================================================================
	// HP reverse calculation (from OpponentInfo plugin)
	// =========================================================================

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
			maxHealthCalc = maxHealth;
		}

		return (minHealth + maxHealthCalc + 1) / 2;
	}
}
