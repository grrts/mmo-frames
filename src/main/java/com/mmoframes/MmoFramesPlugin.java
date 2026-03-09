package com.mmoframes;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.itemstats.ItemStatPlugin;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDependency(ItemStatPlugin.class)
@PluginDescriptor(
	name = "MMO Frames",
	description = "WoW-style player and target unit frames with health bars",
	tags = {"unit", "frame", "health", "target", "wow", "mmo", "overlay"}
)
public class MmoFramesPlugin extends Plugin
{
	@Inject private Client             client;
	@Inject private OverlayManager     overlayManager;
	@Inject private PlayerFrameOverlay playerFrameOverlay;
	@Inject private TargetFrameOverlay targetFrameOverlay;
	@Inject private PlayerService      playerService;
	@Inject private TargetService      targetService;
	@Inject private NpcTrackingService npcTrackingService;
	@Inject private ChatHeadService    chatHeadService;

	private GameState lastGameState = GameState.UNKNOWN;

	@Override
	protected void startUp()
	{
		playerService.startUp();
		targetService.startUp();
		npcTrackingService.startUp();
		chatHeadService.startUp();
		overlayManager.add(playerFrameOverlay);
		overlayManager.add(targetFrameOverlay);
		log.info("MMO Frames started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(playerFrameOverlay);
		overlayManager.remove(targetFrameOverlay);
		chatHeadService.shutDown();
		npcTrackingService.shutDown();
		playerService.shutDown();
		targetService.shutDown();
		log.info("MMO Frames stopped");
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		GameState newState = e.getGameState();
		if (newState == GameState.LOGGED_IN)
		{
			if (lastGameState == GameState.LOADING)
			{
				// Teleport / region change
				playerService.resetForTeleport();
				targetService.resetForTeleport();
			}
			else
			{
				// True login or world-hop
				playerService.resetState();
				targetService.resetState();
			}

			chatHeadService.recreate();
		}
		lastGameState = newState;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		playerService.onGameTick();
		targetService.onGameTick();
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		playerService.onStatChanged(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		npcTrackingService.onHitsplat(
			event.getActor(),
			event.getHitsplat().getHitsplatType(),
			event.getHitsplat().getAmount()
		);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		npcTrackingService.remove(event.getNpc());
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		chatHeadService.onClientTick();
	}

	@Provides
	MmoFramesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MmoFramesConfig.class);
	}
}
