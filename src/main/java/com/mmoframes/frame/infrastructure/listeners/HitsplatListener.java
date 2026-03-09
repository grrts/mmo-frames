package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.infrastructure.ActorRegistry;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;
import net.runelite.api.NPC;

@Singleton
public class HitsplatListener
{
	@Inject private ActorRegistry actorRegistry;

	public void onHitsplat(Actor actor, int hitsplatType, int damage)
	{
		actorRegistry.onHitsplat(actor, hitsplatType, damage);
	}

	public void onNpcDespawned(NPC npc)
	{
		actorRegistry.remove(npc);
	}
}
