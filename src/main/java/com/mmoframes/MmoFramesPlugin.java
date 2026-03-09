package com.mmoframes;

import com.google.inject.Provides;
import com.mmoframes.frame.application.PlayerFrameService;
import com.mmoframes.frame.application.TargetFrameService;
import com.mmoframes.frame.infrastructure.ActorRegistry;
import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.listeners.GameEventListener;
import com.mmoframes.frame.infrastructure.listeners.HitsplatListener;
import com.mmoframes.rendering.PlayerFrameOverlay;
import com.mmoframes.rendering.TargetFrameOverlay;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
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
	@Inject private OverlayManager     overlayManager;
	@Inject private PlayerFrameOverlay playerFrameOverlay;
	@Inject private TargetFrameOverlay targetFrameOverlay;
	@Inject private PlayerFrameService playerFrameService;
	@Inject private TargetFrameService targetFrameService;
	@Inject private ChatHeadAdapter    chatHeadAdapter;
	@Inject private ActorRegistry      actorRegistry;
	@Inject private FrameStore         frameStore;
	@Inject private GameEventListener  gameEventListener;
	@Inject private HitsplatListener   hitsplatListener;

	@Override
	protected void startUp()
	{
		playerFrameService.startUp();
		chatHeadAdapter.startUp();
		overlayManager.add(playerFrameOverlay);
		overlayManager.add(targetFrameOverlay);
		log.info("MMO Frames started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(playerFrameOverlay);
		overlayManager.remove(targetFrameOverlay);
		chatHeadAdapter.shutDown();
		actorRegistry.clear();
		frameStore.clear();
		log.info("MMO Frames stopped");
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		gameEventListener.onGameTick();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		gameEventListener.onGameStateChanged(e);
	}

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		gameEventListener.onStatChanged(event);
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		hitsplatListener.onHitsplat(
			event.getActor(),
			event.getHitsplat().getHitsplatType(),
			event.getHitsplat().getAmount()
		);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		hitsplatListener.onNpcDespawned(event.getNpc());
	}

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		gameEventListener.onClientTick();
	}

	@Provides
	MmoFramesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MmoFramesConfig.class);
	}
}
