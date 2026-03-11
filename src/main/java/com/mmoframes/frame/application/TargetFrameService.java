package com.mmoframes.frame.application;

import com.mmoframes.MmoFramesConfig;
import com.mmoframes.frame.domain.Bar;
import com.mmoframes.frame.domain.BarType;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.FrameType;
import com.mmoframes.frame.domain.HitpointsBarType;
import com.mmoframes.frame.domain.Portrait;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.domain.StatusEffectCategory;
import com.mmoframes.frame.domain.StatusEffectType;
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.IconResolver;
import com.mmoframes.frame.infrastructure.NpcHealthLookup;
import java.awt.Color;
import java.util.ArrayList;
import java.util.regex.Pattern;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.NPCComposition;
import net.runelite.api.Player;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;

@Slf4j
@Singleton
public class TargetFrameService
{
	@Inject private Client          client;
	@Inject private MmoFramesConfig config;
	@Inject private FrameStore      frameStore;
	@Inject private IconResolver    iconResolver;
	@Inject private NpcHealthLookup npcHealthLookup;

	// ── Persistent frame + bar ──────────────────────────────────────────
	@Getter private Frame targetFrame;
	private final Bar hpBar = new Bar();

	// ── Linger state ────────────────────────────────────────────────────
	@Getter private Actor lingerTarget;
	private int lingerTicksRemaining;
	private Actor lastActor;

	// ── HP tracking ─────────────────────────────────────────────────────
	private int     lastRatio         = 0;
	private int     lastHealthScale   = 0;
	private Integer lastMaxHealth     = null;
	private Actor   lastTrackedTarget = null;

	// ── Slayer task pattern cache ───────────────────────────────────────
	private int     cachedTaskRow = -2;
	private Pattern cachedPattern = null;
	private boolean cachedMatch   = false;

	// =====================================================================
	// Lifecycle
	// =====================================================================

	public void startUp()
	{
		targetFrame = new Frame();
		targetFrame.setType(FrameType.TARGET);
		targetFrame.setPortrait(new Portrait());
		targetFrame.setBars(new ArrayList<>());
		targetFrame.setEffects(new ArrayList<>());

		hpBar.setType(BarType.HP);

		resetState();
	}

	public void shutDown()
	{
		// nothing to clean up
	}

	// =====================================================================
	// Event handlers
	// =====================================================================

	public void updateTargetFrame()
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
		}
		else if (lingerTicksRemaining > 0)
		{
			lingerTicksRemaining--;
			if (lingerTicksRemaining == 0)
			{
				lingerTarget = null;
			}
		}
		else
		{
			lingerTarget = null;
		}

		// No target — remove from FrameStore
		if (lingerTarget == null)
		{
			if (lastActor != null)
			{
				frameStore.remove(lastActor);
				lastActor = null;
			}
			return;
		}

		// Re-register in FrameStore if actor reference changed
		if (lingerTarget != lastActor)
		{
			if (lastActor != null)
			{
				frameStore.remove(lastActor);
			}
			lastActor = lingerTarget;
			frameStore.put(lingerTarget, targetFrame);
		}

		// Remove expired hitsplat effects
		targetFrame.removeExpiredEffects();

		// Mutate frame properties
		String name = getTargetName();
		int level = getTargetLevel();

		targetFrame.setName(name);
		targetFrame.setLevel(level);
		targetFrame.setShowName(config.showTargetName());
		targetFrame.setShowHpText(config.showTargetHpText());
		targetFrame.setFrameWidth(config.targetFrameWidth());

		updatePortrait(name, level);
		updateEffects();
	}

	// =====================================================================
	// Render-ready frame (called from overlay render thread)
	// =====================================================================

	public Frame prepareForRender()
	{
		if (lingerTarget == null)
		{
			return null;
		}

		Actor target = lingerTarget;
		if (target != null)
		{
			updateTargetHealth(target);
		}

		int dispCur = getDisplayHp();
		int dispMax = getDisplayMaxHp();

		// Update HP bar in place
		hpBar.setCurrent(dispCur);
		hpBar.setMax(dispMax);
		hpBar.setSweepProgress(0);
		hpBar.setSweepLighten(true);
		hpBar.setHitpointsBarType(HitpointsBarType.DEFAULT);
		hpBar.setColor(hpColor(dispCur, dispMax));
		hpBar.setIcon(iconResolver.resolve(hpBar));

		// Rebuild bars list
		targetFrame.getBars().clear();
		targetFrame.getBars().add(hpBar);

		return targetFrame;
	}

	// =====================================================================
	// HP tracking
	// =====================================================================

	private void updateTargetHealth(Actor target)
	{
		if (target != lastTrackedTarget)
		{
			lastTrackedTarget = target;
			lastRatio       = 0;
			lastHealthScale = 0;
			lastMaxHealth   = null;
		}

		if (target.getHealthScale() > 0)
		{
			lastRatio       = target.getHealthRatio();
			lastHealthScale = target.getHealthScale();

			lastMaxHealth = null;
			if (target instanceof NPC)
			{
				lastMaxHealth = npcHealthLookup.getHealth(((NPC) target).getId());
			}
		}
	}

	private int getDisplayHp()
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

	private int getDisplayMaxHp()
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

	private Color hpColor(int cur, int max)
	{
		double frac = max > 0 ? Math.min(1.0, (double) cur / max) : 0.0;
		if (frac <= 0.25) return config.colorHpLow();
		if (frac <= 0.50) return config.colorHpMid();
		return config.colorHpHigh();
	}

	// =====================================================================
	// State reset
	// =====================================================================

	public void resetState()
	{
		lingerTarget = null;
		lingerTicksRemaining = 0;
		lastRatio = 0;
		lastHealthScale = 0;
		lastMaxHealth = null;
		lastTrackedTarget = null;
		cachedTaskRow = -2;
		cachedPattern = null;
		cachedMatch = false;

		if (lastActor != null)
		{
			frameStore.remove(lastActor);
			lastActor = null;
		}
	}

	public void resetForTeleport()
	{
		resetState();
	}

	// =====================================================================
	// Target info
	// =====================================================================

	private String getTargetName()
	{
		Actor target = lingerTarget;
		if (target == null) return "Unknown";
		String name = target.getName();
		return name != null ? name : "Unknown";
	}

	private int getTargetLevel()
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

	// =====================================================================
	// Portrait
	// =====================================================================

	private void updatePortrait(String name, int level)
	{
		Portrait portrait = targetFrame.getPortrait();
		Actor target = lingerTarget;

		if (target instanceof NPC)
		{
			portrait.setBackgroundColor(npcPortraitColor(level));
		}
		else
		{
			portrait.setBackgroundColor(null);
		}

		if (name != null && !name.isEmpty())
		{
			portrait.setFallbackLetter(String.valueOf(Character.toUpperCase(name.charAt(0))));
		}
		else
		{
			portrait.setFallbackLetter(null);
		}
	}

	private static Color npcPortraitColor(int level)
	{
		if (level <= 0)   return new Color(60, 60, 60, 200);
		if (level <= 50)  return new Color(20, 80, 20, 200);
		if (level <= 100) return new Color(80, 70, 10, 200);
		if (level <= 150) return new Color(90, 45, 10, 200);
		return new Color(80, 15, 15, 200);
	}

	// =====================================================================
	// Status effects
	// =====================================================================

	private void updateEffects()
	{
		// Keep hitsplat-sourced effects (POISON, VENOM) — they persist via addEffect from HitsplatListener
		// Remove non-hitsplat effects that we'll rebuild
		targetFrame.getEffects().removeIf(e ->
			e.getType() != StatusEffectType.POISON && e.getType() != StatusEffectType.VENOM);

		// Add prayer overhead effect
		addTargetPrayerEffect();

		// Add slayer task effect
		addSlayerTaskEffect();
	}

	private void addTargetPrayerEffect()
	{
		Actor target = lingerTarget;
		if (target == null)
		{
			return;
		}

		boolean active = false;
		java.awt.image.BufferedImage icon = null;

		if (target instanceof Player)
		{
			HeadIcon headIcon = ((Player) target).getOverheadIcon();
			if (headIcon != null)
			{
				icon = iconResolver.resolve(headIcon);
				active = true;
			}
		}
		else if (target instanceof NPC)
		{
			short[] ids = ((NPC) target).getOverheadSpriteIds();
			if (ids != null && ids.length > 0 && ids[0] != 0)
			{
				icon = iconResolver.resolveNpcOverhead(ids[0]);
				active = true;
			}
		}

		if (!active)
		{
			return;
		}

		StatusEffect effect = new StatusEffect();
		effect.setType(StatusEffectType.TARGET_PRAYER);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.BUFF);
		effect.setDisplayValue("");
		effect.setColor(EffectColors.PRAYER_ACTIVE);
		effect.setIcon(icon);

		targetFrame.addEffect(effect);
	}

	private void addSlayerTaskEffect()
	{
		Actor target = lingerTarget;
		if (!(target instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) target;
		int taskRow = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		if (taskRow <= 0)
		{
			return;
		}

		if (taskRow != cachedTaskRow)
		{
			cachedTaskRow = taskRow;
			cachedPattern = buildSlayerPattern(taskRow);
			cachedMatch   = matchesSlayer(npc, cachedPattern);
		}

		int count = client.getVarpValue(VarPlayerID.SLAYER_COUNT);
		if (!cachedMatch || count <= 0)
		{
			return;
		}

		StatusEffect effect = new StatusEffect();
		effect.setType(StatusEffectType.SLAYER_TASK);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.BUFF);
		effect.setDisplayValue(String.valueOf(count));
		effect.setColor(EffectColors.SLAYER);
		effect.setIcon(iconResolver.resolveSprite(SpriteID.SKILL_SLAYER));

		targetFrame.addEffect(effect);
	}

	private Pattern buildSlayerPattern(int taskRow)
	{
		try
		{
			Object[] field = client.getDBTableField(
				DBTableID.SlayerTask.ID, taskRow, DBTableID.SlayerTask.COL_NAME_LOWERCASE);
			if (field == null || field.length == 0 || !(field[0] instanceof String))
			{
				return null;
			}
			String taskName = (String) field[0];
			return Pattern.compile("(?:\\s|^)" + Pattern.quote(taskName) + "(?:\\s|$)",
				Pattern.CASE_INSENSITIVE);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private static boolean matchesSlayer(NPC npc, Pattern pattern)
	{
		if (pattern == null || npc == null)
		{
			return false;
		}
		String npcName = npc.getComposition() != null
			? npc.getComposition().getName()
			: npc.getName();
		if (npcName == null)
		{
			return false;
		}
		npcName = npcName.replace('\u00A0', ' ');
		return pattern.matcher(npcName).find();
	}

	// =====================================================================
	// HP reverse calculation (from OpponentInfo plugin)
	// =====================================================================

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
