package com.mmoframes.frame.infrastructure;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.game.NPCManager;

@Singleton
public class NpcHealthLookup
{
	@Inject private NPCManager npcManager;

	public Integer getHealth(int npcId)
	{
		return npcManager.getHealth(npcId);
	}
}
