package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Map;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.client.game.SpriteManager;

/**
 * Represents a single combat-skill boost or drain as a {@link StatusEffect}.
 *
 * Reads its value live from the shared {@code boosts} map maintained by
 * {@code MmoFramesPlugin.onStatChanged}.  Positive values are coloured green
 * (boosted), negative values red (drained).
 *
 * The skill icon is fetched once from {@link SpriteManager} and cached.
 */
public class SkillBoostEffect extends StatusEffect
{
	private static final Color BOOST_COLOR = new Color( 80, 255,  80, 255);
	private static final Color DRAIN_COLOR = new Color(255,  80,  80, 255);

	private final Skill               skill;
	private final int boost;
	private final SpriteManager       spriteManager;

	private BufferedImage cachedIcon;
	private boolean       iconFetched;

	public SkillBoostEffect(Skill skill, int boost, SpriteManager spriteManager)
	{
		this.skill         = skill;
		this.boost        = boost;
		this.spriteManager = spriteManager;
	}

	@Override public Type getType() { return null; }

	@Override
	public boolean isActive()
	{
		if (skill == Skill.PRAYER    && boost < 0) return false;
		if (skill == Skill.HITPOINTS && boost < 0) return false;
		return boost != 0;
	}

	@Override
	public boolean isPositive()
	{
		return boost > 0;
	}

	@Override
	public String getDisplayValue()
	{
		return (boost >= 0 ? "+" : "") + boost;
	}

	@Override
	public Color getColor()
	{
		return boost >= 0 ? BOOST_COLOR : DRAIN_COLOR;
	}

	@Override
	public BufferedImage getIcon()
	{
		if (!iconFetched)
		{
			iconFetched = true;
			int spriteId = spriteId(skill);
			if (spriteId >= 0)
			{
				cachedIcon = spriteManager.getSprite(spriteId, 0);
			}
		}
		return cachedIcon;
	}

	private static int spriteId(Skill s)
	{
		switch (s)
		{
			case ATTACK:    return SpriteID.SKILL_ATTACK;
			case STRENGTH:  return SpriteID.SKILL_STRENGTH;
			case DEFENCE:   return SpriteID.SKILL_DEFENCE;
			case RANGED:    return SpriteID.SKILL_RANGED;
			case MAGIC:     return SpriteID.SKILL_MAGIC;
			case PRAYER:    return SpriteID.SKILL_PRAYER;
			case HITPOINTS: return SpriteID.SKILL_HITPOINTS;
			default:        return -1;
		}
	}
}
