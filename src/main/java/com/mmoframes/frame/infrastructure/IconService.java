package com.mmoframes.frame.infrastructure;

import java.awt.image.BufferedImage;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.HeadIcon;
import net.runelite.api.Prayer;
import net.runelite.api.Skill;
import net.runelite.api.SpriteID;
import net.runelite.client.game.AlternateSprites;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.util.ImageUtil;

@Singleton
public class IconService
{
	@Inject private SpriteManager spriteManager;

	// ── Bar icons ───────────────────────────────────────────────────────────
	private BufferedImage hpIconNormal;
	private BufferedImage hpIconPoison;
	private BufferedImage hpIconVenom;
	private boolean       hpAltIconsLoaded;
	private BufferedImage prayerBarIcon;
	private BufferedImage specBarIcon;

	// ── Poison/venom heart icons (shared across multiple effect types) ───
	private BufferedImage poisonHeartIcon;
	private BufferedImage venomHeartIcon;
	private boolean       heartIconsLoaded;

	// ── General sprite cache ────────────────────────────────────────────────
	private final Map<Integer, BufferedImage> spriteCache = new HashMap<>();

	// ── Skill icon cache ────────────────────────────────────────────────────
	private final Map<Skill, BufferedImage> skillIcons = new EnumMap<>(Skill.class);
	private final Map<Skill, Boolean> skillIconFetched = new EnumMap<>(Skill.class);

	// ── Prayer icon cache ───────────────────────────────────────────────────
	private final Map<Prayer, BufferedImage> prayerIcons = new EnumMap<>(Prayer.class);
	private final Map<Prayer, Boolean> prayerIconFetched = new EnumMap<>(Prayer.class);

	// =====================================================================
	// Bar icons
	// =====================================================================

	public BufferedImage getHpIcon(int poisonState)
	{
		ensureHeartIcons();
		if (hpIconNormal == null)
		{
			hpIconNormal = spriteManager.getSprite(SpriteID.MINIMAP_ORB_HITPOINTS_ICON, 0);
		}

		return poisonState >= 1_000_000 ? hpIconVenom
			: poisonState > 0           ? hpIconPoison
			                            : hpIconNormal;
	}

	public BufferedImage getPrayerBarIcon()
	{
		if (prayerBarIcon == null)
		{
			prayerBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_PRAYER_ICON, 0);
		}
		return prayerBarIcon;
	}

	public BufferedImage getSpecBarIcon()
	{
		if (specBarIcon == null)
		{
			specBarIcon = spriteManager.getSprite(SpriteID.MINIMAP_ORB_SPECIAL_ICON, 0);
		}
		return specBarIcon;
	}

	// =====================================================================
	// Heart icons (poison/venom)
	// =====================================================================

	public BufferedImage getPoisonHeartIcon()
	{
		ensureHeartIcons();
		return poisonHeartIcon;
	}

	public BufferedImage getVenomHeartIcon()
	{
		ensureHeartIcons();
		return venomHeartIcon;
	}

	private void ensureHeartIcons()
	{
		if (!heartIconsLoaded)
		{
			heartIconsLoaded = true;
			poisonHeartIcon = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.POISON_HEART);
			venomHeartIcon  = ImageUtil.loadImageResource(AlternateSprites.class, AlternateSprites.VENOM_HEART);
		}
		if (!hpAltIconsLoaded)
		{
			hpAltIconsLoaded = true;
			hpIconPoison = poisonHeartIcon;
			hpIconVenom  = venomHeartIcon;
		}
	}

	// =====================================================================
	// General sprite cache
	// =====================================================================

	public BufferedImage getSprite(int spriteId)
	{
		if (spriteId < 0)
		{
			return null;
		}
		return spriteCache.computeIfAbsent(spriteId, id -> spriteManager.getSprite(id, 0));
	}

	// =====================================================================
	// Skill icons
	// =====================================================================

	public BufferedImage getSkillIcon(Skill skill)
	{
		if (skillIconFetched.getOrDefault(skill, false))
		{
			return skillIcons.get(skill);
		}
		skillIconFetched.put(skill, true);

		int spriteId = skillSpriteId(skill);
		if (spriteId >= 0)
		{
			BufferedImage icon = spriteManager.getSprite(spriteId, 0);
			skillIcons.put(skill, icon);
			return icon;
		}
		return null;
	}

	private static int skillSpriteId(Skill s)
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

	// =====================================================================
	// Prayer icons
	// =====================================================================

	public BufferedImage getPrayerIcon(Prayer prayer)
	{
		if (prayerIconFetched.getOrDefault(prayer, false))
		{
			return prayerIcons.get(prayer);
		}
		prayerIconFetched.put(prayer, true);

		int spriteId = prayerSpriteId(prayer);
		if (spriteId >= 0)
		{
			BufferedImage icon = spriteManager.getSprite(spriteId, 0);
			prayerIcons.put(prayer, icon);
			return icon;
		}
		return null;
	}

	public static int prayerSpriteId(Prayer p)
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

	// =====================================================================
	// Target overhead prayer icons
	// =====================================================================

	public BufferedImage getHeadIconSprite(HeadIcon icon)
	{
		int spriteId = headIconSpriteId(icon);
		return spriteId >= 0 ? getSprite(spriteId) : null;
	}

	public BufferedImage getNpcOverheadSprite(int rawSpriteId)
	{
		return rawSpriteId != 0 ? getSprite(rawSpriteId & 0xFFFF) : null;
	}

	private static int headIconSpriteId(HeadIcon icon)
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
