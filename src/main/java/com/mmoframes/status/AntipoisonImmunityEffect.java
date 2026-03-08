package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.util.ImageUtil;

/**
 * Positive status effect showing antipoison or anti-venom immunity.
 *
 * VarPlayer POISON negative value encoding:
 * <ul>
 *   <li>{@code < -38}: anti-venom immunity; duration = {@code (|value| - 38) × 30} game ticks</li>
 *   <li>{@code >= -38 and < 0}: antipoison immunity; duration = {@code |value| × 30} game ticks</li>
 * </ul>
 */
public class AntipoisonImmunityEffect extends StatusEffect
{
	private static final int ANTIVENOM_THRESHOLD = -38;
	private static final Color COLOR_ANTIPOISON = new Color(120, 200, 80, 255);
	private static final Color COLOR_ANTIVENOM  = new Color(60, 200, 100, 255);

	private static BufferedImage ICON_POISON;
	private static BufferedImage ICON_VENOM;
	private static boolean       ICONS_LOADED;

	private final Client client;

	public AntipoisonImmunityEffect(Client client)
	{
		this.client = client;
	}

	@Override public Type    getType()    { return null; }
	@Override public boolean isPositive() { return true; }

	@Override
	public boolean isActive()
	{
		return client.getVarpValue(VarPlayerID.POISON) < 0;
	}

	private boolean isAntiVenom()
	{
		return client.getVarpValue(VarPlayerID.POISON) < ANTIVENOM_THRESHOLD;
	}

	@Override
	public Color getColor()
	{
		return isAntiVenom() ? COLOR_ANTIVENOM : COLOR_ANTIPOISON;
	}

	@Override
	public String getDisplayValue()
	{
		int v = client.getVarpValue(VarPlayerID.POISON);
		int secs;
		if (v < ANTIVENOM_THRESHOLD)
		{
			secs = (int) Math.ceil((Math.abs(v) - 38) * 30 * 0.6);
		}
		else
		{
			secs = (int) Math.ceil(Math.abs(v) * 30 * 0.6);
		}
		return secs >= 60 ? (secs / 60) + "m" : secs + "s";
	}

	@Override
	public BufferedImage getIcon()
	{
		if (!ICONS_LOADED)
		{
			ICONS_LOADED = true;
			ICON_POISON = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
			ICON_VENOM  = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);
		}
		return isAntiVenom() ? ICON_VENOM : ICON_POISON;
	}
}
