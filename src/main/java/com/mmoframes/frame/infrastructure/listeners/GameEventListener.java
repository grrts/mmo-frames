package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.application.PlayerFrameService;
import com.mmoframes.frame.application.TargetFrameService;
import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;

@Slf4j
@Singleton
public class GameEventListener
{
	@Inject private PlayerFrameService playerFrameService;
	@Inject private TargetFrameService targetFrameService;
	@Inject private ChatHeadAdapter    chatHeadAdapter;

	private GameState lastGameState = GameState.UNKNOWN;

	public void onGameTick()
	{
		playerFrameService.updatePlayerFrame();
		targetFrameService.updateTargetFrame();
	}

	public void onGameStateChanged(GameStateChanged e)
	{
		GameState newState = e.getGameState();
		if (newState == GameState.LOGGED_IN)
		{
			if (lastGameState == GameState.LOADING)
			{
				playerFrameService.resetForTeleport();
				targetFrameService.resetForTeleport();
			}
			else
			{
				playerFrameService.resetState();
				targetFrameService.resetState();
			}

			chatHeadAdapter.recreate();
		}
		lastGameState = newState;
	}

	public void onStatChanged(StatChanged event)
	{
		playerFrameService.onStatChanged(event);
	}

	public void onClientTick()
	{
		chatHeadAdapter.onClientTick();
	}
}
