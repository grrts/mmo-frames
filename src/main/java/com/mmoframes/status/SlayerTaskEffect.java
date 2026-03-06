package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.regex.Pattern;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.SpriteID;
import net.runelite.api.gameval.DBTableID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.game.SpriteManager;

/**
 * Positive status effect shown on a target NPC when it matches the player's current
 * slayer task. Displays the remaining kill count with the slayer skill icon.
 *
 * Task name is read from the SlayerTask DB table via {@link VarPlayerID#SLAYER_TARGET}.
 * NPC name matching follows the same word-boundary pattern RuneLite's slayer plugin uses.
 */
public class SlayerTaskEffect extends StatusEffect
{
	private static final Color COLOR = new Color(200, 100, 20, 255);

	private final Client        client;
	private final NPC           npc;
	private final SpriteManager spriteManager;

	/** Last seen task row; -2 = uninitialized, -1 = no task. */
	private int     cachedTaskRow    = -2;
	/** Compiled word-boundary pattern for the current task name. */
	private Pattern cachedPattern    = null;
	/** Whether this NPC matches the cached pattern. */
	private boolean cachedMatch      = false;

	private BufferedImage icon;
	private boolean       iconFetched;

	public SlayerTaskEffect(NPC npc, Client client, SpriteManager spriteManager)
	{
		this.npc          = npc;
		this.client       = client;
		this.spriteManager = spriteManager;
	}

	@Override public Type    getType()         { return null; }
	@Override public boolean isPositive()      { return true; }
	@Override public Color   getColor()        { return COLOR; }
	@Override public String  getDisplayValue() { return String.valueOf(client.getVarpValue(VarPlayerID.SLAYER_COUNT)); }

	@Override
	public boolean isActive()
	{
		int taskRow = client.getVarpValue(VarPlayerID.SLAYER_TARGET);
		if (taskRow <= 0)
		{
			return false;
		}

		// Recompute pattern only when task changes
		if (taskRow != cachedTaskRow)
		{
			cachedTaskRow = taskRow;
			cachedPattern = buildPattern(taskRow);
			cachedMatch   = matches(cachedPattern);
		}

		return cachedMatch && client.getVarpValue(VarPlayerID.SLAYER_COUNT) > 0;
	}

	@Override
	public BufferedImage getIcon()
	{
		if (!iconFetched)
		{
			iconFetched = true;
			icon = spriteManager.getSprite(SpriteID.SKILL_SLAYER, 0);
		}
		return icon;
	}

	// ── Helpers ──────────────────────────────────────────────────────────────

	private Pattern buildPattern(int taskRow)
	{
		try
		{
			Object[] field = client.getDBTableField(
				DBTableID.SlayerTask.ID, taskRow, DBTableID.SlayerTask.COL_NAME_LOWERCASE);
			if (field == null || field.length == 0 || !(field[0] instanceof String))
			{
				return null;
			}
			String taskName = (String) field[0];
			// Word-boundary regex — matches RuneLite's slayer plugin approach
			return Pattern.compile("(?:\\s|^)" + Pattern.quote(taskName) + "(?:\\s|$)",
				Pattern.CASE_INSENSITIVE);
		}
		catch (Exception e)
		{
			return null;
		}
	}

	private boolean matches(Pattern pattern)
	{
		if (pattern == null || npc == null)
		{
			return false;
		}
		String npcName = npc.getComposition() != null
			? npc.getComposition().getName()
			: npc.getName();
		if (npcName == null)
		{
			return false;
		}
		// Normalise non-breaking spaces (used in some NPC names)
		npcName = npcName.replace('\u00A0', ' ');
		return pattern.matcher(npcName).find();
	}
}
