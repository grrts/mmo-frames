package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.client.game.SpriteManager;

/**
 * Generic positive status effect backed by a single varbit.
 *
 * <ul>
 *   <li><b>Timer effects</b> ({@code tickMultiplier > 0}): the varbit value × multiplier
 *       gives remaining game ticks; displayed as {@code Xm} / {@code Xs}.</li>
 *   <li><b>Active-flag effects</b> ({@code tickMultiplier == 0}): non-zero = active;
 *       the {@link #getLabel()} is shown as the display value.</li>
 * </ul>
 *
 * Reused for antifire, super antifire, divine potions, overloads,
 * vengeance, magic imbue, goading, prayer regen, menaphite remedy, etc.
 */
public class VarbitTimerEffect extends StatusEffect
{
	private final Client        client;
	private final SpriteManager spriteManager;
	private final int           varbitId;
	private final int           tickMultiplier;
	private final String        label;
	private final Color         color;
	private final int           spriteId;

	private BufferedImage icon;
	private boolean       iconFetched;

	/**
	 * @param client         game client
	 * @param spriteManager  for icon loading
	 * @param varbitId       raw varbit ID to read
	 * @param tickMultiplier varbit value × this = remaining game ticks (0 for active-flag effects)
	 * @param label          short label (e.g. "AFR", "VNG")
	 * @param color          stripe / accent colour
	 * @param spriteId       SpriteID constant for the icon, or {@code -1} for no icon
	 */
	public VarbitTimerEffect(Client client, SpriteManager spriteManager,
		int varbitId, int tickMultiplier, String label, Color color, int spriteId)
	{
		this.client         = client;
		this.spriteManager  = spriteManager;
		this.varbitId       = varbitId;
		this.tickMultiplier = tickMultiplier;
		this.label          = label;
		this.color          = color;
		this.spriteId       = spriteId;
	}

	@Override public Type    getType()    { return null; }
	@Override public boolean isPositive() { return true; }
	@Override public Color   getColor()   { return color; }

	@Override
	public boolean isActive()
	{
		return client.getVarbitValue(varbitId) != 0;
	}

	@Override
	public String getLabel()
	{
		return label;
	}

	@Override
	public String getDisplayValue()
	{
		int raw = client.getVarbitValue(varbitId);
		if (tickMultiplier <= 0)
		{
			return label;
		}
		int secs = (int) Math.ceil(raw * tickMultiplier * 0.6);
		return secs >= 60 ? (secs / 60) + "m" : secs + "s";
	}

	@Override
	public BufferedImage getIcon()
	{
		if (spriteId < 0)
		{
			return null;
		}
		if (!iconFetched)
		{
			iconFetched = true;
			icon = spriteManager.getSprite(spriteId, 0);
		}
		return icon;
	}
}
