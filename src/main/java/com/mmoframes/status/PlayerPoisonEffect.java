package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.util.ImageUtil;

/**
 * Reports the local player's poison or venom status from {@code VarPlayerID.POISON}.
 *
 * VarPlayer 102 encoding:
 *   0                    → not affected
 *   1 … VENOM_THRESHOLD  → poisoned; value = damage of next hit
 *   ≥ VENOM_THRESHOLD    → venomed;  (value − VENOM_THRESHOLD) = venom damage
 */
public class PlayerPoisonEffect extends StatusEffect
{
	private static final int VENOM_THRESHOLD = 1_000_000;

	private static BufferedImage ICON_POISON;
	private static BufferedImage ICON_VENOM;
	private static boolean       ICONS_LOADED;

	private final Client client;

	public PlayerPoisonEffect(Client client)
	{
		this.client = client;
	}

	@Override
	public Type getType()
	{
		return client.getVarpValue(VarPlayerID.POISON) >= VENOM_THRESHOLD
			? Type.VENOM
			: Type.POISON;
	}

	@Override
	public boolean isActive()
	{
		return client.getVarpValue(VarPlayerID.POISON) > 0;
	}

	@Override
	public String getDisplayValue()
	{
		int v = client.getVarpValue(VarPlayerID.POISON);
		return String.valueOf(v >= VENOM_THRESHOLD ? v - VENOM_THRESHOLD : v);
	}

	@Override
	public Color getColor()
	{
		return getType().color;
	}

	@Override
	public BufferedImage getIcon()
	{
		if (!ICONS_LOADED)
		{
			ICONS_LOADED = true;
			ICON_POISON  = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
			ICON_VENOM   = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);
		}
		return getType() == Type.VENOM ? ICON_VENOM : ICON_POISON;
	}
}
