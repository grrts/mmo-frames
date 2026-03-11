package com.mmoframes.frame.infrastructure;

import java.util.WeakHashMap;
import javax.inject.Singleton;
import net.runelite.api.Actor;

@Singleton
public class ActorRegistry
{
	private final WeakHashMap<Actor, Boolean> actors = new WeakHashMap<>();

	public void register(Actor actor)
	{
		actors.put(actor, Boolean.TRUE);
	}

	public void unregister(Actor actor)
	{
		actors.remove(actor);
	}

	public boolean isRegistered(Actor actor)
	{
		return actors.containsKey(actor);
	}

	public void clear()
	{
		actors.clear();
	}
}
