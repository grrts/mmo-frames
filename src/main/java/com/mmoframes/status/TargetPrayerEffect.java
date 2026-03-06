package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Actor;
import net.runelite.api.HeadIcon;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;

/**
 * Positive status effect showing a target's active overhead prayer icon.
 *
 * - Player targets: resolved via {@link Player#getOverheadIcon()} → {@link HeadIcon} enum.
 * - NPC targets:    resolved via {@link NPC#getOverheadSpriteIds()} (e.g. Demonic Gorillas).
 *                   The first non-zero sprite ID is used directly with SpriteManager.
 */
public class TargetPrayerEffect extends StatusEffect
{
	private static final Color COLOR = new Color(50, 200, 200, 255);

	private final Actor         target;
	private final SpriteManager spriteManager;

	/** Last resolved sprite ID; -2 = not yet resolved. */
	private int           lastSpriteId = -2;
	private BufferedImage cachedIcon;

	public TargetPrayerEffect(Actor target, SpriteManager spriteManager)
	{
		this.target        = target;
		this.spriteManager = spriteManager;
	}

	@Override public Type    getType()         { return null; }
	@Override public boolean isPositive()      { return true; }
	@Override public String  getDisplayValue() { return ""; }
	@Override public Color   getColor()        { return COLOR; }

	@Override
	public boolean isActive()
	{
		if (target instanceof Player)
		{
			return ((Player) target).getOverheadIcon() != null;
		}
		if (target instanceof NPC)
		{
			short[] ids = ((NPC) target).getOverheadSpriteIds();
			return ids != null && ids.length > 0 && ids[0] != 0;
		}
		return false;
	}

	@Override
	public BufferedImage getIcon()
	{
		int spriteId = resolveSpriteId();
		if (spriteId != lastSpriteId)
		{
			lastSpriteId = spriteId;
			cachedIcon = spriteId >= 0 ? spriteManager.getSprite(spriteId, 0) : null;
		}
		return cachedIcon;
	}

	private int resolveSpriteId()
	{
		if (target instanceof Player)
		{
			HeadIcon icon = ((Player) target).getOverheadIcon();
			return icon != null ? playerSpriteId(icon) : -1;
		}
		if (target instanceof NPC)
		{
			short[] ids = ((NPC) target).getOverheadSpriteIds();
			if (ids != null && ids.length > 0 && ids[0] != 0)
			{
				return ids[0] & 0xFFFF;
			}
		}
		return -1;
	}

	private static int playerSpriteId(HeadIcon icon)
	{
		switch (icon)
		{
			case MAGIC:       return SpriteID.PRAYER_PROTECT_FROM_MAGIC;
			case MELEE:       return SpriteID.PRAYER_PROTECT_FROM_MELEE;
			case RANGED:      return SpriteID.PRAYER_PROTECT_FROM_MISSILES;
			case SMITE:       return SpriteID.PRAYER_SMITE;
			case REDEMPTION:  return SpriteID.PRAYER_REDEMPTION;
			case RETRIBUTION: return SpriteID.PRAYER_RETRIBUTION;
			default:          return -1;
		}
	}
}
