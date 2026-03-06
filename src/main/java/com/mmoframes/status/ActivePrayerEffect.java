package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.Prayer;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;

/**
 * Positive status effect for a single active prayer.
 * One instance is created per prayer in {@code Prayer.values()};
 * inactive prayers are filtered by {@link #isActive()}.
 */
public class ActivePrayerEffect extends StatusEffect
{
	private static final Color COLOR = new Color(50, 200, 200, 255);

	private final Prayer        prayer;
	private final Client        client;
	private final SpriteManager spriteManager;

	private BufferedImage cachedIcon;
	private boolean       iconFetched;

	public ActivePrayerEffect(Prayer prayer, Client client, SpriteManager spriteManager)
	{
		this.prayer       = prayer;
		this.client       = client;
		this.spriteManager = spriteManager;
	}

	@Override public Type    getType()         { return null; }
	@Override public boolean isPositive()      { return true; }
	@Override public boolean isActive()        { return spriteId(prayer) >= 0 && client.isPrayerActive(prayer); }
	@Override public String  getDisplayValue() { return ""; }
	@Override public Color   getColor()        { return COLOR; }

	@Override
	public BufferedImage getIcon()
	{
		if (!iconFetched)
		{
			iconFetched = true;
			int id = spriteId(prayer);
			if (id >= 0)
			{
				cachedIcon = spriteManager.getSprite(id, 0);
			}
		}
		return cachedIcon;
	}

	private static int spriteId(Prayer p)
	{
		switch (p)
		{
			case THICK_SKIN:            return SpriteID.PRAYER_THICK_SKIN;
			case BURST_OF_STRENGTH:     return SpriteID.PRAYER_BURST_OF_STRENGTH;
			case CLARITY_OF_THOUGHT:    return SpriteID.PRAYER_CLARITY_OF_THOUGHT;
			case SHARP_EYE:             return SpriteID.PRAYER_SHARP_EYE;
			case MYSTIC_WILL:           return SpriteID.PRAYER_MYSTIC_WILL;
			case ROCK_SKIN:             return SpriteID.PRAYER_ROCK_SKIN;
			case SUPERHUMAN_STRENGTH:   return SpriteID.PRAYER_SUPERHUMAN_STRENGTH;
			case IMPROVED_REFLEXES:     return SpriteID.PRAYER_IMPROVED_REFLEXES;
			case RAPID_RESTORE:         return SpriteID.PRAYER_RAPID_RESTORE;
			case RAPID_HEAL:            return SpriteID.PRAYER_RAPID_HEAL;
			case PROTECT_ITEM:          return SpriteID.PRAYER_PROTECT_ITEM;
			case HAWK_EYE:              return SpriteID.PRAYER_HAWK_EYE;
			case MYSTIC_LORE:           return SpriteID.PRAYER_MYSTIC_LORE;
			case STEEL_SKIN:            return SpriteID.PRAYER_STEEL_SKIN;
			case ULTIMATE_STRENGTH:     return SpriteID.PRAYER_ULTIMATE_STRENGTH;
			case INCREDIBLE_REFLEXES:   return SpriteID.PRAYER_INCREDIBLE_REFLEXES;
			case PROTECT_FROM_MAGIC:    return SpriteID.PRAYER_PROTECT_FROM_MAGIC;
			case PROTECT_FROM_MISSILES: return SpriteID.PRAYER_PROTECT_FROM_MISSILES;
			case PROTECT_FROM_MELEE:    return SpriteID.PRAYER_PROTECT_FROM_MELEE;
			// DEADEYE / MYSTIC_VIGOUR are the current names for what was EAGLE_EYE /
			// MYSTIC_MIGHT; isPrayerActive() returns true for both simultaneously,
			// so we map only the new names here and skip the old ones.
			case DEADEYE:               return SpriteID.PRAYER_DEADEYE;
			case MYSTIC_VIGOUR:         return SpriteID.PRAYER_MYSTIC_VIGOUR;
			case RETRIBUTION:           return SpriteID.PRAYER_RETRIBUTION;
			case REDEMPTION:            return SpriteID.PRAYER_REDEMPTION;
			case SMITE:                 return SpriteID.PRAYER_SMITE;
			case PRESERVE:              return SpriteID.PRAYER_PRESERVE;
			case CHIVALRY:              return SpriteID.PRAYER_CHIVALRY;
			case PIETY:                 return SpriteID.PRAYER_PIETY;
			case RIGOUR:                return SpriteID.PRAYER_RIGOUR;
			case AUGURY:                return SpriteID.PRAYER_AUGURY;
			default:                    return -1;
		}
	}
}
