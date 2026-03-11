package com.mmoframes.frame.infrastructure.listeners;

import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;

@Singleton
public class ChatHeadListener
{
	@Inject private ChatHeadAdapter chatHeadAdapter;

	public void onClientTick()
	{
		chatHeadAdapter.onClientTick();
	}

	public void onGameStateChanged(GameStateChanged e)
	{
		if (e.getGameState() == GameState.LOGGED_IN)
		{
			chatHeadAdapter.recreate();
		}
	}
}
