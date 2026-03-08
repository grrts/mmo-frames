package com.mmoframes;

import com.mmoframes.status.NpcStatusEffect;
import com.mmoframes.status.StatusEffect;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.WeakHashMap;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.HitsplatID;

/**
 * Tracks poison and venom status effects on NPCs (and other actors) based on
 * observed hitsplats.
 *
 * Uses a {@link WeakHashMap} so entries are automatically eligible for GC when
 * the game removes the actor object. Explicit {@link #remove(Actor)} calls via
 * {@code NpcDespawned} events provide deterministic cleanup.
 */
@Slf4j
@Singleton
public class NpcTrackingService
{
	private final WeakHashMap<Actor, EnumMap<StatusEffect.Type, NpcStatusEffect>> effects
		= new WeakHashMap<>();

	// =========================================================================
	// Lifecycle
	// =========================================================================

	public void startUp()
	{
		effects.clear();
	}

	public void shutDown()
	{
		effects.clear();
	}

	// =========================================================================
	// Event handlers (called by MmoFramesPlugin)
	// =========================================================================

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

	/** Explicitly removes tracking data for a despawned actor. */
	public void remove(Actor actor)
	{
		effects.remove(actor);
	}

	// =========================================================================
	// Queries
	// =========================================================================

	/** Returns all currently active status effects for the given actor. */
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

	// =========================================================================
	// Internal
	// =========================================================================

	private static StatusEffect.Type typeOf(int hitsplatType)
	{
		if (hitsplatType == HitsplatID.POISON) return StatusEffect.Type.POISON;
		if (hitsplatType == HitsplatID.VENOM)  return StatusEffect.Type.VENOM;
		return null;
	}
}
