package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.application.PlayerFrameService;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

@Singleton
public class PlayerFrameListener
{
	@Inject private PlayerFrameService playerFrameService;

	private GameState lastGameState = GameState.UNKNOWN;

	public void onGameTick()
	{
		playerFrameService.updatePlayerFrame();
	}

	public void onGameStateChanged(GameStateChanged e)
	{
		GameState newState = e.getGameState();
		if (newState == GameState.LOGGED_IN)
		{
			if (lastGameState == GameState.LOADING)
			{
				playerFrameService.resetForTeleport();
			}
			else
			{
				playerFrameService.resetState();
			}
		}
		lastGameState = newState;
	}
}
