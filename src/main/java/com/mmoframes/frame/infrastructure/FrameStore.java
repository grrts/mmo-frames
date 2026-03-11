package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.domain.Frame;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;
import net.runelite.api.Actor;

@Singleton
public class FrameStore
{
	private final ConcurrentHashMap<Actor, Frame> frames = new ConcurrentHashMap<>();

	public Frame get(Actor actor)
	{
		return frames.get(actor);
	}

	public void put(Actor actor, Frame frame)
	{
		frames.put(actor, frame);
	}

	public void remove(Actor actor)
	{
		frames.remove(actor);
	}

	public void clear()
	{
		frames.clear();
	}
}
