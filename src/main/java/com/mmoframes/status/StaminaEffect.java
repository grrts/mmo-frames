package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.game.SpriteManager;

/**
 * Positive status effect shown on the player frame while a stamina potion is active.
 * Displays the run icon with the remaining buff duration in seconds.
 *
 * Buff duration is read from {@link VarbitID#STAMINA_DURATION} (game ticks × 0.6 s/tick).
 * Active state is from {@link VarbitID#STAMINA_ACTIVE} (non-zero = active).
 */
public class StaminaEffect extends StatusEffect
{
	static final Color COLOR = new Color(160, 124, 72, 255);

	private final Client        client;
	private final SpriteManager spriteManager;

	private BufferedImage icon;
	private boolean       iconFetched;

	public StaminaEffect(Client client, SpriteManager spriteManager)
	{
		this.client        = client;
		this.spriteManager = spriteManager;
	}

	@Override public Type    getType()    { return null; }
	@Override public boolean isPositive() { return true; }
	@Override public Color   getColor()   { return COLOR; }

	@Override
	public boolean isActive()
	{
		return client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0;
	}

	@Override
	public String getDisplayValue()
	{
		int secs = (int) Math.ceil(client.getVarbitValue(VarbitID.STAMINA_DURATION) * 0.6);
		return secs >= 60 ? (secs / 60) + "m" : secs + "s";
	}

	@Override
	public BufferedImage getIcon()
	{
		if (!iconFetched)
		{
			iconFetched = true;
			icon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_RUN_ICON, 0);
		}
		return icon;
	}
}
