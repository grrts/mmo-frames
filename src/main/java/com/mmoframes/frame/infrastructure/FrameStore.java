package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.FrameType;
import java.util.concurrent.ConcurrentHashMap;
import javax.inject.Singleton;

@Singleton
public class FrameStore
{
	private final ConcurrentHashMap<FrameType, Frame> frames = new ConcurrentHashMap<>();

	public Frame get(FrameType type)
	{
		return frames.get(type);
	}

	public void put(FrameType type, Frame frame)
	{
		frames.put(type, frame);
	}

	public void remove(FrameType type)
	{
		frames.remove(type);
	}

	public void clear()
	{
		frames.clear();
	}
}
