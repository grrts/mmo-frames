package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.application.EffectColors;
import com.mmoframes.frame.application.TickConstants;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.domain.StatusEffectCategory;
import com.mmoframes.frame.domain.StatusEffectType;
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.IconResolver;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.HitsplatID;

@Singleton
public class HitsplatListener
{
	@Inject private FrameStore   frameStore;
	@Inject private IconResolver iconResolver;

	public void onHitsplat(Actor actor, int hitsplatType, int damage)
	{
		StatusEffectType type = typeOf(hitsplatType);
		if (type == null)
		{
			return;
		}

		Frame frame = frameStore.get(actor);
		if (frame == null)
		{
			return;
		}

		StatusEffect effect = new StatusEffect();
		effect.setType(type);
		effect.setActive(true);
		effect.setCategory(StatusEffectCategory.DEBUFF);
		effect.setDisplayValue(String.valueOf(damage));
		effect.setColor(type == StatusEffectType.VENOM ? EffectColors.VENOM : EffectColors.POISON);
		effect.setIcon(iconResolver.resolve(type));
		effect.setExpiresAtMs(System.currentTimeMillis() + TickConstants.NPC_STATUS_DISPLAY_DURATION_MS);

		frame.addEffect(effect);
	}

	private static StatusEffectType typeOf(int hitsplatType)
	{
		if (hitsplatType == HitsplatID.POISON) return StatusEffectType.POISON;
		if (hitsplatType == HitsplatID.VENOM)  return StatusEffectType.VENOM;
		return null;
	}
}
