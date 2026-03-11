package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.domain.Bar;
import com.mmoframes.frame.domain.BarType;
import com.mmoframes.frame.domain.HitpointsBarType;
import com.mmoframes.frame.domain.StatusEffectType;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.HeadIcon;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;

@Singleton
public class IconResolver
{
	@Inject private IconService iconService;

	// ── Bar icon (reads type + hitpointsBarType from the Bar) ────────────

	public BufferedImage resolve(Bar bar)
	{
		switch (bar.getType())
		{
			case HP:         return resolve(bar.getHitpointsBarType());
			case PRAYER:     return iconService.getPrayerBarIcon();
			case SPEC:       return iconService.getSpecBarIcon();
			case RUN_ENERGY: return null;
			default:         return null;
		}
	}

	// ── HitpointsBarType ────────────────────────────────────────────────

	public BufferedImage resolve(HitpointsBarType type)
	{
		switch (type)
		{
			case POISON: return iconService.getPoisonHeartIcon();
			case VENOM:  return iconService.getVenomHeartIcon();
			default:     return iconService.getSprite(SpriteID.MINIMAP_ORB_HITPOINTS_ICON);
		}
	}

	// ── Fixed status effect icon ────────────────────────────────────────

	public BufferedImage resolve(StatusEffectType type)
	{
		switch (type)
		{
			case POISON:
			case ANTIPOISON_IMMUNITY:
				return iconService.getPoisonHeartIcon();
			case VENOM:
			case ANTIVENOM_IMMUNITY:
				return iconService.getVenomHeartIcon();
			case STAMINA:
				return iconService.getSprite(SpriteID.MINIMAP_ORB_RUN_ICON);
			case SLAYER_TASK:
				return iconService.getSprite(SpriteID.SKILL_SLAYER);
			default:
				return null;
		}
	}

	// ── Contextual overloads ────────────────────────────────────────────

	public BufferedImage resolve(Prayer prayer)
	{
		return iconService.getPrayerIcon(prayer);
	}

	public BufferedImage resolve(Skill skill)
	{
		return iconService.getSkillIcon(skill);
	}

	public BufferedImage resolve(HeadIcon headIcon)
	{
		return iconService.getHeadIconSprite(headIcon);
	}

	// ── Named methods (int params can't overload) ───────────────────────

	public BufferedImage resolveSprite(int spriteId)
	{
		return iconService.getSprite(spriteId);
	}

	public BufferedImage resolveNpcOverhead(int rawSpriteId)
	{
		return iconService.getNpcOverheadSprite(rawSpriteId);
	}
}
