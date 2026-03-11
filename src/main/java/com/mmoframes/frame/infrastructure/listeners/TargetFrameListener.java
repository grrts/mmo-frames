package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.application.TargetFrameService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

@Singleton
public class TargetFrameListener
{
	@Inject private TargetFrameService targetFrameService;

	private GameState lastGameState = GameState.UNKNOWN;

	public void onGameTick()
	{
		targetFrameService.updateTargetFrame();
	}

	public void onGameStateChanged(GameStateChanged e)
	{
		GameState newState = e.getGameState();
		if (newState == GameState.LOGGED_IN)
		{
			if (lastGameState == GameState.LOADING)
			{
				targetFrameService.resetForTeleport();
			}
			else
			{
				targetFrameService.resetState();
			}
		}
		lastGameState = newState;
	}
}
