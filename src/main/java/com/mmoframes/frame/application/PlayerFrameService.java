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
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.HpRegenTimerService;
import com.mmoframes.frame.infrastructure.IconService;
import com.mmoframes.frame.infrastructure.PrayerDrainTimerService;
import com.mmoframes.frame.infrastructure.SpecRegenTimerService;
import static com.mmoframes.frame.application.EffectColors.*;
import static com.mmoframes.frame.application.TickConstants.*;
import static com.mmoframes.frame.application.TrackedSkills.COMBAT_SKILLS;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

@Slf4j
@Singleton
public class PlayerFrameService
{
	@Inject private Client                client;
	@Inject private MmoFramesConfig       config;
	@Inject private FrameStore            frameStore;
	@Inject private IconService           iconService;
	@Inject private HpRegenTimerService     hpRegenTimer;
	@Inject private PrayerDrainTimerService prayerDrainTimer;
	@Inject private SpecRegenTimerService   specRegenTimer;
	@Inject private ConsumableHoverService consumableHoverService;

	private Map<Skill, Integer> skillBoosts;
	private int poisonState;

	// =====================================================================
	// Lifecycle
	// =====================================================================

	public void startUp()
	{
		skillBoosts = new EnumMap<>(Skill.class);
		resetState();
	}

	public void shutDown()
	{
		// nothing to clean up
	}

	// =====================================================================
	// Event handlers
	// =====================================================================

	public void updatePlayerFrame()
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		poisonState = client.getVarpValue(VarPlayerID.POISON);
		hpRegenTimer.tick();
		prayerDrainTimer.tick();
		specRegenTimer.tick();

		int hp       = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp    = client.getRealSkillLevel(Skill.HITPOINTS);
		int pray     = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPray  = client.getRealSkillLevel(Skill.PRAYER);
		int energy   = client.getEnergy();
		int specRaw  = client.getVarpValue(VarPlayerID.SA_ENERGY);
		int spec     = specRaw / 10;
		String name  = client.getLocalPlayer().getName();
		int level    = client.getLocalPlayer().getCombatLevel();

		int healHp   = consumableHoverService.getHealHp();
		int healPray = consumableHoverService.getHealPrayer();

		Frame frame = Frame.builder()
			.type(FrameType.PLAYER)
			.name(name != null ? name : "Unknown")
			.level(level)
			.portrait(new Portrait(null, null))
			.bars(buildPlayerBars(hp, maxHp, pray, maxPray, energy, spec, healHp, healPray))
			.effects(buildAllEffects())
			.showName(config.showPlayerName())
			.showHpText(config.showPlayerHpText())
			.frameWidth(config.playerFrameWidth())
			.build();

		frameStore.put(FrameType.PLAYER, frame);
	}

	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		for (Skill tracked : COMBAT_SKILLS)
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

	// =====================================================================
	// State reset
	// =====================================================================

	public void resetState()
	{
		hpRegenTimer.resetState();
		prayerDrainTimer.resetState();
		specRegenTimer.resetState();
		poisonState = 0;
		if (skillBoosts != null) skillBoosts.clear();
	}

	public void resetForTeleport()
	{
		hpRegenTimer.resetForTeleport();
		prayerDrainTimer.resetForTeleport();
		specRegenTimer.resetForTeleport();
	}

	// =====================================================================
	// Bar builders
	// =====================================================================

	private static final Color RESTORE_HP_COLOR   = new Color(216, 255, 139, 130);
	private static final Color RESTORE_PRAY_COLOR = new Color(130, 180, 255, 130);

	private List<Bar> buildPlayerBars(int hp, int maxHp, int pray, int maxPray,
		int energy, int spec, int healHp, int healPray)
	{
		List<Bar> bars = new ArrayList<>();

		// HP bar (always present)
		double hpFrac = maxHp > 0 ? Math.min(1.0, (double) hp / maxHp) : 0;
		bars.add(Bar.builder()
			.type(BarType.HP)
			.current(hp)
			.max(maxHp)
			.sweepProgress(config.showHpRegenSweep() ? hpRegenTimer.getProgress() : 0)
			.sweepLighten(true)
			.hoverRestore(healHp)
			.color(hpColor(hpFrac))
			.hoverRestoreColor(healHp > 0 ? RESTORE_HP_COLOR : null)
			.icon(iconService.getHpIcon(poisonState))
			.poisonState(poisonState)
			.build());

		// Prayer bar (config-gated)
		if (config.showPlayerPrayer())
		{
			bars.add(Bar.builder()
				.type(BarType.PRAYER)
				.current(pray)
				.max(maxPray)
				.sweepProgress(config.showPrayerDrainSweep() ? prayerDrainTimer.getProgress() : 0)
				.sweepLighten(false)
				.hoverRestore(healPray)
				.color(config.colorPrayer())
				.hoverRestoreColor(healPray > 0 ? RESTORE_PRAY_COLOR : null)
				.icon(iconService.getPrayerBarIcon())
				.poisonState(0)
				.build());
		}

		// Run energy bar (config-gated)
		if (config.showPlayerStamina())
		{
			bars.add(Bar.builder()
				.type(BarType.RUN_ENERGY)
				.current(energy)
				.max(10000)
				.sweepProgress(0)
				.sweepLighten(true)
				.hoverRestore(0)
				.color(isStaminaActive() ? STAMINA : config.colorStamina())
				.hoverRestoreColor(null)
				.icon(null)
				.poisonState(0)
				.build());
		}

		// Spec bar (config-gated)
		if (config.showSpecialAttack())
		{
			Color specColor = spec >= 100
				? new Color(31, 224, 192, 255)
				: config.colorSpec();

			bars.add(Bar.builder()
				.type(BarType.SPEC)
				.current(spec)
				.max(100)
				.sweepProgress(config.showSpecRegenSweep() ? specRegenTimer.getProgress() : 0)
				.sweepLighten(true)
				.hoverRestore(0)
				.color(specColor)
				.hoverRestoreColor(null)
				.icon(iconService.getSpecBarIcon())
				.poisonState(0)
				.build());
		}

		return bars;
	}

	private Color hpColor(double frac)
	{
		if (frac > 0.5) return config.colorHpHigh();
		if (frac > 0.25) return config.colorHpMid();
		return config.colorHpLow();
	}

	private boolean isStaminaActive()
	{
		return client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
	}

	// =====================================================================
	// Status effect builders
	// =====================================================================

	private List<StatusEffect> buildAllEffects()
	{
		List<StatusEffect> effects = new ArrayList<>();

		// ── Buffs ────────────────────────────────────────────────────────────
		StatusEffect antipoison = buildAntipoisonEffect();
		if (antipoison != null) effects.add(antipoison);

		StatusEffect stamina = buildStaminaEffect();
		if (stamina != null) effects.add(stamina);

		effects.addAll(buildVarbitTimerEffects());
		effects.addAll(buildPrayerEffects());

		if (config.showSkillBoosts())
		{
			effects.addAll(buildSkillBoostEffects(StatusEffectCategory.BUFF));
		}

		// ── Debuffs ──────────────────────────────────────────────────────────
		StatusEffect poison = buildPoisonEffect();
		if (poison != null) effects.add(poison);

		if (config.showSkillBoosts())
		{
			effects.addAll(buildSkillBoostEffects(StatusEffectCategory.DEBUFF));
		}

		return effects;
	}

	private StatusEffect buildPoisonEffect()
	{
		if (poisonState <= 0)
		{
			return null;
		}

		boolean isVenom = poisonState >= VENOM_THRESHOLD;
		int damage = isVenom ? poisonState - VENOM_THRESHOLD : poisonState;

		return StatusEffect.builder()
			.type(isVenom ? StatusEffectType.VENOM : StatusEffectType.POISON)
			.active(true)
			.category(StatusEffectCategory.DEBUFF)
			.displayValue(String.valueOf(damage))
			.label(null)
			.color(isVenom ? VENOM : POISON)
			.icon(isVenom ? iconService.getVenomHeartIcon() : iconService.getPoisonHeartIcon())
			.build();
	}

	private StatusEffect buildAntipoisonEffect()
	{
		int v = client.getVarpValue(VarPlayerID.POISON);
		if (v >= 0)
		{
			return null;
		}

		boolean isAntiVenom = v < ANTIVENOM_THRESHOLD;
		int secs;
		if (isAntiVenom)
		{
			secs = (int) Math.ceil((Math.abs(v) - 38) * 30 * 0.6);
		}
		else
		{
			secs = (int) Math.ceil(Math.abs(v) * 30 * 0.6);
		}
		String display = secs >= 60 ? (secs / 60) + "m" : secs + "s";

		return StatusEffect.builder()
			.type(isAntiVenom ? StatusEffectType.ANTIVENOM_IMMUNITY : StatusEffectType.ANTIPOISON_IMMUNITY)
			.active(true)
			.category(StatusEffectCategory.BUFF)
			.displayValue(display)
			.label(null)
			.color(isAntiVenom ? ANTIVENOM : ANTIPOISON)
			.icon(isAntiVenom ? iconService.getVenomHeartIcon() : iconService.getPoisonHeartIcon())
			.build();
	}

	private StatusEffect buildStaminaEffect()
	{
		if (client.getVarbitValue(VarbitID.STAMINA_ACTIVE) == 0)
		{
			return null;
		}

		int secs = (int) Math.ceil(client.getVarbitValue(VarbitID.STAMINA_DURATION) * 6.0);
		String display = secs >= 60 ? (secs / 60) + "m" : secs + "s";

		return StatusEffect.builder()
			.type(StatusEffectType.STAMINA)
			.active(true)
			.category(StatusEffectCategory.BUFF)
			.displayValue(display)
			.label(null)
			.color(EffectColors.STAMINA)
			.icon(iconService.getSprite(SpriteID.MINIMAP_ORB_RUN_ICON))
			.build();
	}

	private List<StatusEffect> buildVarbitTimerEffects()
	{
		List<StatusEffect> effects = new ArrayList<>();

		for (VarbitTimerDef def : VarbitTimerDefs.ALL)
		{
			int raw = client.getVarbitValue(def.varbitId);
			if (raw == 0)
			{
				continue;
			}

			String display;
			if (def.tickMultiplier <= 0)
			{
				display = def.label;
			}
			else
			{
				int secs = (int) Math.ceil(raw * def.tickMultiplier * 0.6);
				display = secs >= 60 ? (secs / 60) + "m" : secs + "s";
			}

			effects.add(StatusEffect.builder()
				.type(StatusEffectType.VARBIT_TIMER)
				.active(true)
				.category(StatusEffectCategory.BUFF)
				.displayValue(display)
				.label(def.label)
				.color(def.color)
				.icon(iconService.getSprite(def.spriteId))
				.build());
		}

		return effects;
	}

	private List<StatusEffect> buildPrayerEffects()
	{
		List<StatusEffect> effects = new ArrayList<>();

		for (Prayer p : Prayer.values())
		{
			int spriteId = IconService.prayerSpriteId(p);
			if (spriteId < 0 || !client.isPrayerActive(p))
			{
				continue;
			}

			effects.add(StatusEffect.builder()
				.type(StatusEffectType.ACTIVE_PRAYER)
				.active(true)
				.category(StatusEffectCategory.BUFF)
				.displayValue("")
				.label(null)
				.color(EffectColors.PRAYER_ACTIVE)
				.icon(iconService.getPrayerIcon(p))
				.build());
		}

		return effects;
	}

	private List<StatusEffect> buildSkillBoostEffects(StatusEffectCategory category)
	{
		if (skillBoosts == null || skillBoosts.isEmpty())
		{
			return Collections.emptyList();
		}

		List<StatusEffect> effects = new ArrayList<>();

		for (Skill s : COMBAT_SKILLS)
		{
			int boost = skillBoosts.getOrDefault(s, 0);

			// Filter: prayer/hp negative boosts suppressed
			if (s == Skill.PRAYER && boost < 0) continue;
			if (s == Skill.HITPOINTS && boost < 0) continue;

			if (category == StatusEffectCategory.BUFF && boost > 0)
			{
				effects.add(StatusEffect.builder()
					.type(StatusEffectType.SKILL_BOOST)
					.active(true)
					.category(StatusEffectCategory.BUFF)
					.displayValue("+" + boost)
					.label(null)
					.color(BOOST)
					.icon(iconService.getSkillIcon(s))
					.build());
			}
			else if (category == StatusEffectCategory.DEBUFF && boost < 0)
			{
				effects.add(StatusEffect.builder()
					.type(StatusEffectType.SKILL_DRAIN)
					.active(true)
					.category(StatusEffectCategory.DEBUFF)
					.displayValue(String.valueOf(boost))
					.label(null)
					.color(DRAIN)
					.icon(iconService.getSkillIcon(s))
					.build());
			}
		}

		return effects;
	}
}
