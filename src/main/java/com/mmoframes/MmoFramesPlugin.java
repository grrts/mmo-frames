package com.mmoframes;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.StatChanged;
import com.mmoframes.status.NpcStatusTracker;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
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
	// HP regenerates 1 point every 100 ticks (60 seconds out of combat)
	private static final int HP_REGEN_TICKS = 100;

	// Special attack recharges 10% every 33 ticks (~20s per 10%)
	private static final int SPEC_REGEN_TICKS = 33;

	// Venom encoding threshold (OSRS varp 102 >= this value = venomed)
	private static final int VENOM_THRESHOLD = 1000000;

	/**
	 * OSRS prayer drain effects per prayer, sourced from the OSRS wiki.
	 *
	 * Ticks between each prayer point drain = floor(6000 / totalDrainEffect).
	 * Note: client.isPrayerActive() is deprecated for a small subset of prayers
	 * (Deadeye/Eagle Eye, Mystic Vigour/Might overlap detection) but remains
	 * functional for drain-rate tracking purposes.
	 */
	private static final Map<Prayer, Integer> DRAIN_EFFECT = new EnumMap<>(Prayer.class);

	static
	{
		// Drain effect values sourced from custom-vital-bars PrayerType enum.
		// These are the actual in-game values (≈3× the OSRS wiki "drain rate" column).
		DRAIN_EFFECT.put(Prayer.THICK_SKIN,             3);
		DRAIN_EFFECT.put(Prayer.BURST_OF_STRENGTH,      3);
		DRAIN_EFFECT.put(Prayer.CLARITY_OF_THOUGHT,     3);
		DRAIN_EFFECT.put(Prayer.SHARP_EYE,              3);
		DRAIN_EFFECT.put(Prayer.MYSTIC_WILL,            3);
		DRAIN_EFFECT.put(Prayer.ROCK_SKIN,              6);
		DRAIN_EFFECT.put(Prayer.SUPERHUMAN_STRENGTH,    6);
		DRAIN_EFFECT.put(Prayer.IMPROVED_REFLEXES,      6);
		DRAIN_EFFECT.put(Prayer.RAPID_RESTORE,          1);
		DRAIN_EFFECT.put(Prayer.RAPID_HEAL,             2);
		DRAIN_EFFECT.put(Prayer.PROTECT_ITEM,           2);
		DRAIN_EFFECT.put(Prayer.HAWK_EYE,               6);
		DRAIN_EFFECT.put(Prayer.MYSTIC_LORE,            6);
		DRAIN_EFFECT.put(Prayer.STEEL_SKIN,            12);
		DRAIN_EFFECT.put(Prayer.ULTIMATE_STRENGTH,     12);
		DRAIN_EFFECT.put(Prayer.INCREDIBLE_REFLEXES,   12);
		DRAIN_EFFECT.put(Prayer.PROTECT_FROM_MAGIC,    12);
		DRAIN_EFFECT.put(Prayer.PROTECT_FROM_MISSILES, 12);
		DRAIN_EFFECT.put(Prayer.PROTECT_FROM_MELEE,    12);
		DRAIN_EFFECT.put(Prayer.EAGLE_EYE,             12);
		DRAIN_EFFECT.put(Prayer.MYSTIC_MIGHT,          12);
		DRAIN_EFFECT.put(Prayer.RETRIBUTION,            3);
		DRAIN_EFFECT.put(Prayer.REDEMPTION,             6);
		DRAIN_EFFECT.put(Prayer.SMITE,                 18);
		DRAIN_EFFECT.put(Prayer.PRESERVE,               2);
		DRAIN_EFFECT.put(Prayer.CHIVALRY,              24);
		DRAIN_EFFECT.put(Prayer.PIETY,                 24);
		DRAIN_EFFECT.put(Prayer.RIGOUR,                24);
		DRAIN_EFFECT.put(Prayer.AUGURY,                24);
		// DEADEYE and MYSTIC_VIGOUR are omitted: isPrayerActive() returns true
		// for both the old (EAGLE_EYE/MYSTIC_MIGHT) and new name simultaneously,
		// so including both would double the drain effect.
		// Ruinous Powers — from custom-vital-bars PrayerType enum
		DRAIN_EFFECT.put(Prayer.RP_REJUVENATION,        4);
		DRAIN_EFFECT.put(Prayer.RP_ANCIENT_STRENGTH,   18);
		DRAIN_EFFECT.put(Prayer.RP_ANCIENT_SIGHT,      18);
		DRAIN_EFFECT.put(Prayer.RP_ANCIENT_WILL,       18);
		DRAIN_EFFECT.put(Prayer.RP_PROTECT_ITEM,       18);
		DRAIN_EFFECT.put(Prayer.RP_RUINOUS_GRACE,       1);
		DRAIN_EFFECT.put(Prayer.RP_DAMPEN_MAGIC,       14);
		DRAIN_EFFECT.put(Prayer.RP_DAMPEN_RANGED,      14);
		DRAIN_EFFECT.put(Prayer.RP_DAMPEN_MELEE,       14);
		DRAIN_EFFECT.put(Prayer.RP_TRINITAS,           22);
		DRAIN_EFFECT.put(Prayer.RP_BERSERKER,           2);
		DRAIN_EFFECT.put(Prayer.RP_PURGE,              18);
		DRAIN_EFFECT.put(Prayer.RP_METABOLISE,         12);
		DRAIN_EFFECT.put(Prayer.RP_REBUKE,             12);
		DRAIN_EFFECT.put(Prayer.RP_VINDICATION,         9);
		DRAIN_EFFECT.put(Prayer.RP_DECIMATE,           28);
		DRAIN_EFFECT.put(Prayer.RP_ANNIHILATE,         28);
		DRAIN_EFFECT.put(Prayer.RP_VAPORISE,           28);
		DRAIN_EFFECT.put(Prayer.RP_FUMUS_VOW,          14);
		DRAIN_EFFECT.put(Prayer.RP_UMBRA_VOW,          14);
		DRAIN_EFFECT.put(Prayer.RP_CRUORS_VOW,         14);
		DRAIN_EFFECT.put(Prayer.RP_GLACIES_VOW,        14);
		DRAIN_EFFECT.put(Prayer.RP_WRATH,               3);
		DRAIN_EFFECT.put(Prayer.RP_INTENSIFY,          28);
	}

	// Combat skills tracked for skill boosts panel
	static final Skill[] TRACKED_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.PRAYER, Skill.HITPOINTS
	};

	@Inject private Client          client;
	@Inject private ClientThread    clientThread;
	@Inject private MmoFramesConfig config;
	@Inject private OverlayManager  overlayManager;
	@Inject private ItemManager     itemManager;
	@Inject private PlayerFrameOverlay playerFrameOverlay;
	@Inject private TargetFrameOverlay targetFrameOverlay;
	@Inject private ChatHeadService    chatHeadService;

	// ---- Tick sweep state (0.0–1.0), read by overlays ----------------------

	@Getter private double hpRegenProgress;
	@Getter private double prayerDrainProgress;
	@Getter private double specRegenProgress;

	private int    hpRegenTick;
	private double prayerDrainTick; // elapsed ticks as double for float-interval modulo
	private int    specRegenTick;

	private int lastHp   = -1;
	private int lastPray = -1;
	private int lastSpec = -1;

	// ---- Linger target (Feature 3) -----------------------------------------

	@Getter private Actor lingerTarget;
	private int lingerTicksRemaining;

	// ---- Poison / venom state (Feature 6) ----------------------------------

	@Getter private int poisonState;

	// ---- Game state tracking ------------------------------------------------

	private GameState lastGameState = GameState.UNKNOWN;

	// ---- NPC status tracking (Feature 9/10) --------------------------------

	@Getter private final NpcStatusTracker npcStatusTracker = new NpcStatusTracker();


	// ---- Skill boosts (Feature 8) ------------------------------------------

	@Getter private Map<Skill, Integer> skillBoosts;

	// -------------------------------------------------------------------------

	@Override
	protected void startUp()
	{
		skillBoosts = new EnumMap<>(Skill.class);
		overlayManager.add(playerFrameOverlay);
		overlayManager.add(targetFrameOverlay);
		resetState();
		chatHeadService.startUp();
		log.info("MMO Frames started");
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(playerFrameOverlay);
		overlayManager.remove(targetFrameOverlay);
		chatHeadService.shutDown();
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
				// Teleport / region change — preserve skill boosts and poison state
				resetForTeleport();
			}
			else
			{
				resetState();
			}

			chatHeadService.recreate();
		}
		lastGameState = newState;
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		// Poison / venom state
		poisonState = client.getVarpValue(VarPlayerID.POISON);

		// Linger target
		Actor currentTarget = client.getLocalPlayer() != null
			? client.getLocalPlayer().getInteracting()
			: null;

		if (currentTarget != null)
		{
			lingerTarget = currentTarget;
			lingerTicksRemaining = config.targetLingerSeconds() * 100 / 60;
			refreshTargetPortrait(currentTarget);
		}
		else if (lingerTicksRemaining > 0)
		{
			lingerTicksRemaining--;
			if (lingerTicksRemaining == 0)
			{
				lingerTarget = null;
			}
		}
		else
		{
			lingerTarget = null;
		}

		tickHpRegen();
		tickPrayerDrain();
		tickSpecRegen();
	}

	// ---- Skill boosts (Feature 8) ------------------------------------------

	@Subscribe
	public void onStatChanged(StatChanged event)
	{
		Skill skill = event.getSkill();
		for (Skill tracked : TRACKED_SKILLS)
		{
			if (tracked == skill)
			{
				if (skillBoosts != null)
				{
					skillBoosts.put(skill, event.getBoostedLevel() - event.getLevel());
				}
				break;
			}
		}
	}

	// ---- Hitsplat tracking (Feature 9/10) ----------------------------------

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		npcStatusTracker.onHitsplat(
			event.getActor(),
			event.getHitsplat().getHitsplatType(),
			event.getHitsplat().getAmount()
		);
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		npcStatusTracker.remove(event.getNpc());
	}

	// ---- Tick logic ---------------------------------------------------------

	private void tickHpRegen()
	{
		int hp    = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (hp >= maxHp)
		{
			hpRegenProgress = 0.0;
			hpRegenTick = 0;
			lastHp = hp;
			return;
		}

		// Any HP change (damage / food) resets the regen clock
		if (hp != lastHp)
		{
			lastHp = hp;
			hpRegenTick = 0;
		}

		hpRegenTick = (hpRegenTick + 1) % HP_REGEN_TICKS;
		hpRegenProgress = (double) hpRegenTick / HP_REGEN_TICKS;
	}

	private void tickPrayerDrain()
	{
		int prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);

		// Sum drain effect per active prayer
		int totalDrainEffect = 0;
		for (Prayer p : Prayer.values())
		{
			if (client.isPrayerActive(p))
			{
				totalDrainEffect += DRAIN_EFFECT.getOrDefault(p, 0);
			}
		}

		if (totalDrainEffect == 0 || prayerPoints <= 0)
		{
			prayerDrainTick = 0;
			prayerDrainProgress = 0.0;
			lastPray = prayerPoints;
			return;
		}

		// Compute prayer bonus from equipment
		int prayerBonus = 0;
		ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equip != null)
		{
			for (Item item : equip.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					ItemStats stats = itemManager.getItemStats(item.getId());
					if (stats != null && stats.getEquipment() != null)
					{
						prayerBonus += stats.getEquipment().getPrayer();
					}
				}
			}
		}

		// Drain interval in ticks (float): resistance / drainEffect
		// resistance = 60 + 2*prayerBonus  (matches custom-vital-bars formula)
		double drainInterval = Math.max(1.0, (60.0 + 2.0 * prayerBonus) / totalDrainEffect);

		// On observed prayer drop: drain just happened — reset cycle (progress = 0 → will rise to ~1)
		if (lastPray >= 0 && prayerPoints < lastPray)
		{
			prayerDrainTick = 0;
			prayerDrainProgress = 0.0;
			lastPray = prayerPoints;
			return;
		}
		lastPray = prayerPoints;

		// Advance elapsed ticks, wrapping at drain interval
		prayerDrainTick = (prayerDrainTick + 1) % drainInterval;

		// Progress = 1 - elapsed/interval: full (entire bar) right after drain, zero just before next
		prayerDrainProgress = 1.0 - prayerDrainTick / drainInterval;
	}

	private void tickSpecRegen()
	{
		// VarPlayerID.SA_ENERGY stores spec as 0–1000 (1000 = 100%)
		int specRaw = client.getVarpValue(VarPlayerID.SA_ENERGY);
		int spec    = specRaw / 10; // normalise to 0–100

		if (spec >= 100)
		{
			specRegenProgress = 0.0;
			specRegenTick = 0;
			lastSpec = spec;
			return;
		}

		if (spec != lastSpec)
		{
			lastSpec = spec;
			specRegenTick = 0;
		}

		specRegenTick = (specRegenTick + 1) % SPEC_REGEN_TICKS;
		specRegenProgress = (double) specRegenTick / SPEC_REGEN_TICKS;
	}

	// ---- Chat-head portrait (delegated to ChatHeadService) -----------------

	/** Forwards client tick to the service so it can update widget position + schedule capture. */
	@Subscribe
	public void onClientTick(ClientTick tick)
	{
		chatHeadService.onClientTick();
	}

	// ---- Target portrait (item icon for player targets) --------------------

	private void refreshTargetPortrait(Actor target)
	{

	}

	// ---- State reset --------------------------------------------------------

	private void resetState()
	{
		hpRegenTick = specRegenTick = 0;
		prayerDrainTick = 0.0;
		hpRegenProgress = prayerDrainProgress = specRegenProgress = 0.0;
		lastHp = lastPray = lastSpec = -1;
		lingerTarget = null;
		lingerTicksRemaining = 0;
		poisonState = 0;
		if (skillBoosts != null) skillBoosts.clear();
	}

	/**
	 * Partial reset for teleports (LOADING → LOGGED_IN).
	 * Clears positional state (linger target, target portrait) but preserves
	 * skill boosts and poison state, which persist through within-world teleports.
	 * The chat-head widget persists through teleports since the interface stays loaded.
	 */
	private void resetForTeleport()
	{
		hpRegenTick = specRegenTick = 0;
		prayerDrainTick = 0.0;
		hpRegenProgress = prayerDrainProgress = specRegenProgress = 0.0;
		lastHp = lastPray = lastSpec = -1;
		lingerTarget = null;
		lingerTicksRemaining = 0;
		// chatHeadWidget preserved — interface stays loaded through teleports
		// skillBoosts preserved — boosts persist through teleports
		// poisonState preserved — poison/venom persists through teleports
	}

	@Provides
	MmoFramesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MmoFramesConfig.class);
	}
}
