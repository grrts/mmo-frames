package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.application.PlayerFrameService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.events.StatChanged;

@Singleton
public class StatChangeListener
{
	@Inject private PlayerFrameService playerFrameService;

	public void onStatChanged(StatChanged event)
	{
		playerFrameService.onStatChanged(event);
	}
}
