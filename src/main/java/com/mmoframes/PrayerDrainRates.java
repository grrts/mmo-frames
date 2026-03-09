package com.mmoframes;

import java.util.EnumMap;
import java.util.Map;
import net.runelite.api.Client;
import net.runelite.api.Prayer;

/**
 * Static lookup table of OSRS prayer drain effects.
 *
 * Values sourced from the custom-vital-bars plugin's PrayerType enum. These are
 * the actual in-game drain effect values (approximately 3x the OSRS wiki
 * "drain rate" column).
 *
 * Drain interval formula:
 *   {@code drainInterval = max(1, (60 + 2 * prayerBonus) / totalDrainEffect)}
 */
public final class PrayerDrainRates
{
	private static final Map<Prayer, Integer> DRAIN_EFFECT = new EnumMap<>(Prayer.class);

	static
	{
		// Standard prayers
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
		// DEADEYE and MYSTIC_VIGOUR omitted: isPrayerActive() returns true
		// for both the old (EAGLE_EYE/MYSTIC_MIGHT) and new name simultaneously,
		// so including both would double the drain effect.

		// Ruinous Powers
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

	private PrayerDrainRates() {}

	/** Returns the drain effect for a single prayer, or 0 if unmapped. */
	public static int getDrainEffect(Prayer prayer)
	{
		return DRAIN_EFFECT.getOrDefault(prayer, 0);
	}

	/**
	 * Sums the drain effects of all currently active prayers.
	 *
	 * @return total drain effect, or 0 if no prayers are active
	 */
	public static int getTotalDrainEffect(Client client)
	{
		int total = 0;
		for (Prayer p : Prayer.values())
		{
			if (client.isPrayerActive(p))
			{
				total += DRAIN_EFFECT.getOrDefault(p, 0);
			}
		}
		return total;
	}
}
