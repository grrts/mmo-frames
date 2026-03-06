package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.util.ImageUtil;

/**
 * Tracks a single poison or venom status on an NPC (or other player) based on
 * observed hitsplats.
 *
 * The effect remains "active" for {@value #DISPLAY_DURATION_MS} ms after the
 * last matching hitsplat — slightly longer than the OSRS 18-second (30-tick)
 * poison / venom cycle so the frame does not flicker between hits.
 */
public class NpcStatusEffect extends StatusEffect
{
	/** Keep the frame visible for ~25 s after the last hitsplat (18 s cycle + buffer). */
	private static final long DISPLAY_DURATION_MS = 25_000L;

	private static BufferedImage ICON_POISON;
	private static BufferedImage ICON_VENOM;
	private static boolean       ICONS_LOADED;

	private final Type type;
	private int  damage;
	private long expiresAt;

	public NpcStatusEffect(Type type)
	{
		this.type = type;
	}

	/** Called each time a new matching hitsplat is observed. */
	public void update(int damage)
	{
		this.damage    = damage;
		this.expiresAt = System.currentTimeMillis() + DISPLAY_DURATION_MS;
	}

	@Override public Type    getType()         { return type; }
	@Override public boolean isActive()        { return System.currentTimeMillis() < expiresAt; }
	@Override public String  getDisplayValue() { return String.valueOf(damage); }
	@Override public Color   getColor()        { return type.color; }

	@Override
	public BufferedImage getIcon()
	{
		if (!ICONS_LOADED)
		{
			ICONS_LOADED = true;
			ICON_POISON  = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
			ICON_VENOM   = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);
		}
		return type == Type.VENOM ? ICON_VENOM : ICON_POISON;
	}
}
