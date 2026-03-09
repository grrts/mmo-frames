package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.application.EffectColors;
import com.mmoframes.frame.application.TickConstants;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.domain.StatusEffectCategory;
import com.mmoframes.frame.domain.StatusEffectType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.WeakHashMap;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HitsplatID;

@Slf4j
@Singleton
public class ActorRegistry
{
	private final WeakHashMap<Actor, EnumMap<StatusEffectType, NpcStatusRecord>> effects
		= new WeakHashMap<>();

	public void onHitsplat(Actor actor, int hitsplatType, int damage)
	{
		StatusEffectType type = typeOf(hitsplatType);
		if (type == null)
		{
			return;
		}

		effects.computeIfAbsent(actor, k -> new EnumMap<>(StatusEffectType.class))
			.computeIfAbsent(type, t -> new NpcStatusRecord(t))
			.update(damage);
	}

	public void remove(Actor actor)
	{
		effects.remove(actor);
	}

	public List<StatusEffect> getActiveEffects(Actor actor, IconService iconService)
	{
		if (actor == null)
		{
			return Collections.emptyList();
		}
		EnumMap<StatusEffectType, NpcStatusRecord> actorEffects = effects.get(actor);
		if (actorEffects == null)
		{
			return Collections.emptyList();
		}

		List<StatusEffect> result = new ArrayList<>();
		for (NpcStatusRecord record : actorEffects.values())
		{
			if (record.isActive())
			{
				result.add(StatusEffect.builder()
					.type(record.getType())
					.active(true)
					.category(StatusEffectCategory.DEBUFF)
					.displayValue(String.valueOf(record.getDamage()))
					.label(null)
					.color(record.getType() == StatusEffectType.NPC_VENOM
						? EffectColors.VENOM
						: EffectColors.POISON)
					.icon(record.getType() == StatusEffectType.NPC_VENOM
						? iconService.getVenomHeartIcon()
						: iconService.getPoisonHeartIcon())
					.build());
			}
		}
		return result;
	}

	public void clear()
	{
		effects.clear();
	}

	private static StatusEffectType typeOf(int hitsplatType)
	{
		if (hitsplatType == HitsplatID.POISON) return StatusEffectType.NPC_POISON;
		if (hitsplatType == HitsplatID.VENOM)  return StatusEffectType.NPC_VENOM;
		return null;
	}

	static class NpcStatusRecord
	{
		private final StatusEffectType type;
		private int  damage;
		private long expiresAt;

		NpcStatusRecord(StatusEffectType type)
		{
			this.type = type;
		}

		void update(int damage)
		{
			this.damage    = damage;
			this.expiresAt = System.currentTimeMillis() + TickConstants.NPC_STATUS_DISPLAY_DURATION_MS;
		}

		boolean isActive()
		{
			return System.currentTimeMillis() < expiresAt;
		}

		StatusEffectType getType() { return type; }
		int getDamage() { return damage; }
	}
}
