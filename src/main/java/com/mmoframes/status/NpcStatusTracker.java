package com.mmoframes.status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.WeakHashMap;
import net.runelite.api.Actor;
import net.runelite.api.HitsplatID;

/**
 * Maintains a per-actor map of active poison / venom {@link NpcStatusEffect}s
 * populated from {@code HitsplatApplied} events.
 *
 * Uses a {@link WeakHashMap} so that entries are automatically eligible for GC
 * when the game removes the actor object (NPC despawn, etc.).  Explicit
 * {@link #remove(Actor)} calls via the {@code NpcDespawned} event provide
 * deterministic cleanup.
 */
public class NpcStatusTracker
{
	private final WeakHashMap<Actor, EnumMap<StatusEffect.Type, NpcStatusEffect>> effects
		= new WeakHashMap<>();

	/**
	 * Called on every {@code HitsplatApplied} event.
	 * Only {@link HitsplatID#POISON} and {@link HitsplatID#VENOM} are tracked.
	 */
	public void onHitsplat(Actor actor, int hitsplatType, int damage)
	{
		StatusEffect.Type type = typeOf(hitsplatType);
		if (type == null)
		{
			return;
		}

		effects.computeIfAbsent(actor, k -> new EnumMap<>(StatusEffect.Type.class))
			.computeIfAbsent(type, NpcStatusEffect::new)
			.update(damage);
	}

	/** Returns all currently active status effects for {@code actor}. */
	public List<StatusEffect> getActiveEffects(Actor actor)
	{
		if (actor == null)
		{
			return Collections.emptyList();
		}
		EnumMap<StatusEffect.Type, NpcStatusEffect> actorEffects = effects.get(actor);
		if (actorEffects == null)
		{
			return Collections.emptyList();
		}
		List<StatusEffect> result = new ArrayList<>();
		for (NpcStatusEffect e : actorEffects.values())
		{
			if (e.isActive()) result.add(e);
		}
		return result;
	}

	/** Explicitly removes tracking data for a despawned actor. */
	public void remove(Actor actor)
	{
		effects.remove(actor);
	}

	private static StatusEffect.Type typeOf(int hitsplatType)
	{
		if (hitsplatType == HitsplatID.POISON) return StatusEffect.Type.POISON;
		if (hitsplatType == HitsplatID.VENOM)  return StatusEffect.Type.VENOM;
		return null;
	}
}
