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
import com.mmoframes.frame.infrastructure.HpRegenTimerService;
import com.mmoframes.frame.infrastructure.IconResolver;
import com.mmoframes.frame.infrastructure.PrayerDrainTimerService;
import com.mmoframes.frame.infrastructure.SpecRegenTimerService;
import static com.mmoframes.frame.application.EffectColors.*;
import static com.mmoframes.frame.application.TickConstants.*;
import static com.mmoframes.frame.application.TrackedSkills.COMBAT_SKILLS;
import java.awt.Color;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.events.StatChanged;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;

@Slf4j
@Singleton
public class PlayerFrameService
{
	@Inject private Client                   client;
	@Inject private MmoFramesConfig          config;
	@Inject private FrameStore               frameStore;
	@Inject private IconResolver             iconResolver;
	@Inject private HpRegenTimerService      hpRegenTimer;
	@Inject private PrayerDrainTimerService  prayerDrainTimer;
	@Inject private SpecRegenTimerService    specRegenTimer;

	@Getter private Frame playerFrame;
	private Map<Skill, Integer> skillBoosts;
	private int poisonState;
	private Actor lastActor;

	// ── Persistent bar objects ──────────────────────────────────────────
	private final Bar hpBar        = new Bar();
	private final Bar prayerBar    = new Bar();
	private final Bar runEnergyBar = new Bar();
	private final Bar specBar      = new Bar();

	// =====================================================================
	// Lifecycle
	// =====================================================================

	public void startUp()
	{
		skillBoosts = new EnumMap<>(Skill.class);
		playerFrame = new Frame();
		playerFrame.setType(FrameType.PLAYER);
		playerFrame.setPortrait(new Portrait());
		playerFrame.setBars(new ArrayList<>());
		playerFrame.setEffects(new ArrayList<>());

		hpBar.setType(BarType.HP);
		prayerBar.setType(BarType.PRAYER);
		runEnergyBar.setType(BarType.RUN_ENERGY);
		specBar.setType(BarType.SPEC);

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

		Actor localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Re-register in FrameStore if actor reference changed (e.g. after login)
		if (localPlayer != lastActor)
		{
			if (lastActor != null)
			{
				frameStore.remove(lastActor);
			}
			lastActor = localPlayer;
			frameStore.put(localPlayer, playerFrame);
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
		String name  = localPlayer.getName();

		playerFrame.setName(name != null ? name : "Unknown");
		playerFrame.setLevel(localPlayer.getCombatLevel());
		playerFrame.setShowName(config.showPlayerName());
		playerFrame.setShowHpText(config.showPlayerHpText());
		playerFrame.setFrameWidth(config.playerFrameWidth());

		updateBars(hp, maxHp, pray, maxPray, energy, spec);
		updateEffects();
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
	// Bar updates
	// =====================================================================

	private void updateBars(int hp, int maxHp, int pray, int maxPray,
		int energy, int spec)
	{
		double hpFrac = maxHp > 0 ? Math.min(1.0, (double) hp / maxHp) : 0;
		HitpointsBarType hpType = toHitpointsBarType(poisonState);

		// HP bar (always present)
		hpBar.setCurrent(hp);
		hpBar.setMax(maxHp);
		hpBar.setSweepProgress(config.showHpRegenSweep() ? hpRegenTimer.getProgress() : 0);
		hpBar.setSweepLighten(true);
		hpBar.setColor(hpColor(hpFrac));
		hpBar.setHitpointsBarType(hpType);
		hpBar.setIcon(iconResolver.resolve(hpBar));

		// Rebuild bars list based on config
		playerFrame.getBars().clear();
		playerFrame.getBars().add(hpBar);

		if (config.showPlayerPrayer())
		{
			prayerBar.setCurrent(pray);
			prayerBar.setMax(maxPray);
			prayerBar.setSweepProgress(config.showPrayerDrainSweep() ? prayerDrainTimer.getProgress() : 0);
			prayerBar.setSweepLighten(false);
			prayerBar.setColor(config.colorPrayer());
			prayerBar.setIcon(iconResolver.resolve(prayerBar));
			playerFrame.getBars().add(prayerBar);
		}

		if (config.showPlayerStamina())
		{
			runEnergyBar.setCurrent(energy);
			runEnergyBar.setMax(10000);
			runEnergyBar.setColor(isStaminaActive() ? STAMINA : config.colorStamina());
			playerFrame.getBars().add(runEnergyBar);
		}

		if (config.showSpecialAttack())
		{
			specBar.setCurrent(spec);
			specBar.setMax(100);
			specBar.setSweepProgress(config.showSpecRegenSweep() ? specRegenTimer.getProgress() : 0);
			specBar.setSweepLighten(true);
			specBar.setColor(spec >= 100 ? new Color(31, 224, 192, 255) : config.colorSpec());
			specBar.setIcon(iconResolver.resolve(specBar));
			playerFrame.getBars().add(specBar);
		}
	}

	private static HitpointsBarType toHitpointsBarType(int poisonState)
	{
		if (poisonState >= VENOM_THRESHOLD) return HitpointsBarType.VENOM;
		if (poisonState > 0) return HitpointsBarType.POISON;
		return HitpointsBarType.DEFAULT;
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
	// Status effect updates
	// =====================================================================

	private void updateEffects()
	{
		playerFrame.getEffects().clear();

		// ── Buffs ────────────────────────────────────────────────────────────
		addAntipoisonEffect();
		addStaminaEffect();
		addVarbitTimerEffects();
		addPrayerEffects();

		if (config.showSkillBoosts())
		{
			addSkillBoostEffects(StatusEffectCategory.BUFF);
		}

		// ── Debuffs ──────────────────────────────────────────────────────────
		addPoisonEffect();

		if (config.showSkillBoosts())
		{
			addSkillBoostEffects(StatusEffectCategory.DEBUFF);
		}
	}

	private void addPoisonEffect()
	{
		if (poisonState <= 0)
		{
			return;
		}

		boolean isVenom = poisonState >= VENOM_THRESHOLD;
		StatusEffectType type = isVenom ? StatusEffectType.VENOM : StatusEffectType.POISON;
		int damage = isVenom ? poisonState - VENOM_THRESHOLD : poisonState;

		StatusEffect effect = new StatusEffect();
		effect.setType(type);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.DEBUFF);
		effect.setDisplayValue(String.valueOf(damage));
		effect.setColor(isVenom ? VENOM : POISON);
		effect.setIcon(iconResolver.resolve(type));
		playerFrame.getEffects().add(effect);
	}

	private void addAntipoisonEffect()
	{
		int v = client.getVarpValue(VarPlayerID.POISON);
		if (v >= 0)
		{
			return;
		}

		boolean isAntiVenom = v < ANTIVENOM_THRESHOLD;
		StatusEffectType type = isAntiVenom ? StatusEffectType.ANTIVENOM_IMMUNITY : StatusEffectType.ANTIPOISON_IMMUNITY;
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

		StatusEffect effect = new StatusEffect();
		effect.setType(type);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.BUFF);
		effect.setDisplayValue(display);
		effect.setColor(isAntiVenom ? ANTIVENOM : ANTIPOISON);
		effect.setIcon(iconResolver.resolve(type));
		playerFrame.getEffects().add(effect);
	}

	private void addStaminaEffect()
	{
		if (client.getVarbitValue(VarbitID.STAMINA_ACTIVE) == 0)
		{
			return;
		}

		int secs = (int) Math.ceil(client.getVarbitValue(VarbitID.STAMINA_DURATION) * 6.0);
		String display = secs >= 60 ? (secs / 60) + "m" : secs + "s";

		StatusEffect effect = new StatusEffect();
		effect.setType(StatusEffectType.STAMINA);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.BUFF);
		effect.setDisplayValue(display);
		effect.setColor(EffectColors.STAMINA);
		effect.setIcon(iconResolver.resolve(StatusEffectType.STAMINA));
		playerFrame.getEffects().add(effect);
	}

	private void addVarbitTimerEffects()
	{
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

			StatusEffect effect = new StatusEffect();
			effect.setType(StatusEffectType.VARBIT_TIMER);
			effect.setActive(true);
			effect.setCategory(StatusEffectCategory.BUFF);
			effect.setDisplayValue(display);
			effect.setLabel(def.label);
			effect.setColor(def.color);
			effect.setIcon(iconResolver.resolveSprite(def.spriteId));
			playerFrame.getEffects().add(effect);
		}
	}

	private void addPrayerEffects()
	{
		for (Prayer p : Prayer.values())
		{
			if (!client.isPrayerActive(p))
			{
				continue;
			}

			StatusEffect effect = new StatusEffect();
			effect.setType(StatusEffectType.ACTIVE_PRAYER);
			effect.setActive(true);
			effect.setCategory(StatusEffectCategory.BUFF);
			effect.setDisplayValue("");
			effect.setColor(EffectColors.PRAYER_ACTIVE);
			effect.setIcon(iconResolver.resolve(p));

			if (effect.getIcon() != null)
			{
				playerFrame.getEffects().add(effect);
			}
		}
	}

	private void addSkillBoostEffects(StatusEffectCategory category)
	{
		if (skillBoosts == null || skillBoosts.isEmpty())
		{
			return;
		}

		for (Skill s : COMBAT_SKILLS)
		{
			int boost = skillBoosts.getOrDefault(s, 0);

			// Filter: prayer/hp negative boosts suppressed
			if (s == Skill.PRAYER && boost < 0) continue;
			if (s == Skill.HITPOINTS && boost < 0) continue;

			if (category == StatusEffectCategory.BUFF && boost > 0)
			{
				StatusEffect effect = new StatusEffect();
				effect.setType(StatusEffectType.SKILL_BOOST);
				effect.setActive(true);
				effect.setCategory(StatusEffectCategory.BUFF);
				effect.setDisplayValue("+" + boost);
				effect.setColor(BOOST);
				effect.setIcon(iconResolver.resolve(s));
				playerFrame.getEffects().add(effect);
			}
			else if (category == StatusEffectCategory.DEBUFF && boost < 0)
			{
				StatusEffect effect = new StatusEffect();
				effect.setType(StatusEffectType.SKILL_DRAIN);
				effect.setActive(true);
				effect.setCategory(StatusEffectCategory.DEBUFF);
				effect.setDisplayValue(String.valueOf(boost));
				effect.setColor(DRAIN);
				effect.setIcon(iconResolver.resolve(s));
				playerFrame.getEffects().add(effect);
			}
		}
	}
}
