package com.mmoframes;

import com.google.inject.Provides;
import com.mmoframes.frame.application.PlayerFrameService;
import com.mmoframes.frame.application.TargetFrameService;
import com.mmoframes.frame.infrastructure.ActorRegistry;
import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import com.mmoframes.frame.infrastructure.FrameStore;
import com.mmoframes.frame.infrastructure.listeners.ActorLifecycleListener;
import com.mmoframes.frame.infrastructure.listeners.ChatHeadListener;
import com.mmoframes.frame.infrastructure.listeners.HitsplatListener;
import com.mmoframes.frame.infrastructure.listeners.PlayerFrameListener;
import com.mmoframes.frame.infrastructure.listeners.StatChangeListener;
import com.mmoframes.frame.infrastructure.listeners.TargetFrameListener;
import com.mmoframes.rendering.PlayerFrameOverlay;
import com.mmoframes.rendering.TargetFrameOverlay;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
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
	@Inject private OverlayManager          overlayManager;
	@Inject private PlayerFrameOverlay      playerFrameOverlay;
	@Inject private TargetFrameOverlay      targetFrameOverlay;
	@Inject private PlayerFrameService      playerFrameService;
	@Inject private TargetFrameService      targetFrameService;
	@Inject private ChatHeadAdapter         chatHeadAdapter;
	@Inject private ActorRegistry           actorRegistry;
	@Inject private FrameStore              frameStore;

	// ── Listeners ────────────────────────────────────────────────────────
	@Inject private PlayerFrameListener     playerFrameListener;
	@Inject private TargetFrameListener     targetFrameListener;
	@Inject private StatChangeListener      statChangeListener;
	@Inject private ChatHeadListener        chatHeadListener;
	@Inject private ActorLifecycleListener  actorLifecycleListener;
	@Inject private HitsplatListener        hitsplatListener;

	@Override
	protected void startUp()
	{
		playerFrameService.startUp();
		targetFrameService.startUp();
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
		playerFrameService.shutDown();
		targetFrameService.shutDown();
		actorRegistry.clear();
		frameStore.clear();
		log.info("MMO Frames stopped");
	}

	// ── Game tick ────────────────────────────────────────────────────────

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		playerFrameListener.onGameTick();
		targetFrameListener.onGameTick();
	}

	// ── Game state changes ───────────────────────────────────────────────

	@Subscribe
	public void onGameStateChanged(GameStateChanged e)
	{
		playerFrameListener.onGameStateChanged(e);
		targetFrameListener.onGameStateChanged(e);
		chatHeadListener.onGameStateChanged(e);
	}

	// ── Stat changes ─────────────────────────────────────────────────────

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		statChangeListener.onStatChanged(event);
	}

	// ── Hitsplats ────────────────────────────────────────────────────────

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		hitsplatListener.onHitsplat(
			event.getActor(),
			event.getHitsplat().getHitsplatType(),
			event.getHitsplat().getAmount()
		);
	}

	// ── Actor lifecycle ──────────────────────────────────────────────────

	@Subscribe
	public void onNpcSpawned(NpcSpawned event)
	{
		actorLifecycleListener.onActorSpawned(event.getNpc());
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		actorLifecycleListener.onActorDespawned(event.getNpc());
	}

	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		actorLifecycleListener.onActorSpawned(event.getPlayer());
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		actorLifecycleListener.onActorDespawned(event.getPlayer());
	}

	// ── Client tick ──────────────────────────────────────────────────────

	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		chatHeadListener.onClientTick();
	}

	// ── Config ───────────────────────────────────────────────────────────

	@Provides
	MmoFramesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MmoFramesConfig.class);
	}
}
