package com.mmoframes.frame.application;

import com.mmoframes.MmoFramesConfig;
import com.mmoframes.frame.domain.Bar;
import com.mmoframes.frame.domain.BarType;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.FrameType;
import com.mmoframes.frame.domain.Portrait;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.domain.StatusEffectCategory;
import com.mmoframes.frame.domain.StatusEffectType;
import com.mmoframes.frame.infrastructure.ActorRegistry;
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.IconService;
import com.mmoframes.frame.infrastructure.NpcHealthLookup;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;

@Slf4j
@Singleton
public class TargetFrameService
{
	@Inject private Client          client;
	@Inject private MmoFramesConfig config;
	@Inject private FrameStore      frameStore;
	@Inject private ActorRegistry   actorRegistry;
	@Inject private IconService     iconService;
	@Inject private NpcHealthLookup npcHealthLookup;

	// ── Linger state ────────────────────────────────────────────────────────
	@Getter private Actor lingerTarget;
	private int lingerTicksRemaining;

	// ── HP tracking ─────────────────────────────────────────────────────────
	private int     lastRatio         = 0;
	private int     lastHealthScale   = 0;
	private Integer lastMaxHealth     = null;
	private Actor   lastTrackedTarget = null;

	// ── Slayer task pattern cache ───────────────────────────────────────────
	private int     cachedTaskRow = -2;
	private Pattern cachedPattern = null;
	private boolean cachedMatch   = false;

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

		// Build frame if we have a target
		if (lingerTarget == null)
		{
			frameStore.remove(FrameType.NPC_TARGET);
			frameStore.remove(FrameType.PLAYER_TARGET);
			return;
		}

		FrameType type = lingerTarget instanceof Player ? FrameType.PLAYER_TARGET : FrameType.NPC_TARGET;
		String name = getTargetName();
		int level = getTargetLevel();
		Portrait portrait = buildPortrait(name, level);

		Frame frame = Frame.builder()
			.type(type)
			.name(name)
			.level(level)
			.portrait(portrait)
			.bars(Collections.emptyList())
			.effects(buildEffects())
			.showName(config.showTargetName())
			.showHpText(config.showTargetHpText())
			.frameWidth(config.targetFrameWidth())
			.build();

		frameStore.put(type, frame);
		if (type == FrameType.PLAYER_TARGET)
		{
			frameStore.remove(FrameType.NPC_TARGET);
		}
		else
		{
			frameStore.remove(FrameType.PLAYER_TARGET);
		}
	}

	// =====================================================================
	// Render-ready frame (called from overlay render thread)
	// =====================================================================

	public Frame prepareForRender()
	{
		Frame base = frameStore.get(FrameType.PLAYER_TARGET);
		if (base == null)
		{
			base = frameStore.get(FrameType.NPC_TARGET);
		}
		if (base == null)
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

		Bar hpBar = Bar.builder()
			.type(BarType.HP)
			.current(dispCur)
			.max(dispMax)
			.sweepProgress(0)
			.sweepLighten(true)
			.hoverRestore(0)
			.color(hpColor(dispCur, dispMax))
			.hoverRestoreColor(null)
			.icon(iconService.getHpIcon(0))
			.poisonState(0)
			.build();

		return Frame.builder()
			.type(base.getType())
			.name(base.getName())
			.level(base.getLevel())
			.portrait(base.getPortrait())
			.bars(Collections.singletonList(hpBar))
			.effects(base.getEffects())
			.showName(base.isShowName())
			.showHpText(base.isShowHpText())
			.frameWidth(base.getFrameWidth())
			.build();
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

	private Portrait buildPortrait(String name, int level)
	{
		Actor target = lingerTarget;

		Color bgColor = null;
		String letter = null;

		if (target instanceof NPC)
		{
			bgColor = npcPortraitColor(level);
		}

		if (name != null && !name.isEmpty())
		{
			letter = String.valueOf(Character.toUpperCase(name.charAt(0)));
		}

		return new Portrait(bgColor, letter);
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

	private List<StatusEffect> buildEffects()
	{
		List<StatusEffect> effects = new ArrayList<>();

		StatusEffect prayer = buildTargetPrayerEffect();
		if (prayer != null) effects.add(prayer);

		StatusEffect slayer = buildSlayerTaskEffect();
		if (slayer != null) effects.add(slayer);

		Actor target = lingerTarget;
		if (target != null)
		{
			effects.addAll(actorRegistry.getActiveEffects(target, iconService));
		}

		return effects;
	}

	private StatusEffect buildTargetPrayerEffect()
	{
		Actor target = lingerTarget;
		if (target == null)
		{
			return null;
		}

		BufferedImage icon = null;
		boolean active = false;

		if (target instanceof Player)
		{
			HeadIcon headIcon = ((Player) target).getOverheadIcon();
			if (headIcon != null)
			{
				icon = iconService.getHeadIconSprite(headIcon);
				active = true;
			}
		}
		else if (target instanceof NPC)
		{
			short[] ids = ((NPC) target).getOverheadSpriteIds();
			if (ids != null && ids.length > 0 && ids[0] != 0)
			{
				icon = iconService.getNpcOverheadSprite(ids[0]);
				active = true;
			}
		}

		if (!active)
		{
			return null;
		}

		return StatusEffect.builder()
			.type(StatusEffectType.TARGET_PRAYER)
			.active(true)
			.category(StatusEffectCategory.BUFF)
			.displayValue("")
			.label(null)
			.color(EffectColors.PRAYER_ACTIVE)
			.icon(icon)
			.build();
	}

	private StatusEffect buildSlayerTaskEffect()
	{
		Actor target = lingerTarget;
		if (!(target instanceof NPC))
		{
			return null;
		}

		NPC npc = (NPC) target;
		int taskRow = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		if (taskRow <= 0)
		{
			return null;
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
			return null;
		}

		return StatusEffect.builder()
			.type(StatusEffectType.SLAYER_TASK)
			.active(true)
			.category(StatusEffectCategory.BUFF)
			.displayValue(String.valueOf(count))
			.label(null)
			.color(EffectColors.SLAYER)
			.icon(iconService.getSprite(net.runelite.api.SpriteID.SKILL_SLAYER))
			.build();
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
