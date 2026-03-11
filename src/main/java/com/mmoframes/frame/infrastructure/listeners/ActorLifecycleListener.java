package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.infrastructure.ActorRegistry;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Actor;

@Singleton
public class ActorLifecycleListener
{
	@Inject private ActorRegistry actorRegistry;

	public void onActorSpawned(Actor actor)
	{
		actorRegistry.register(actor);
	}

	public void onActorDespawned(Actor actor)
	{
		actorRegistry.unregister(actor);
	}
}
