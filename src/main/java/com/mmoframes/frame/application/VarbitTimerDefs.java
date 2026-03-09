package com.mmoframes.frame.application;

import net.runelite.api.SpriteID;

public final class VarbitTimerDefs
{
	private VarbitTimerDefs() {}

	public static final VarbitTimerDef[] ALL = {
		new VarbitTimerDef(VarbitIds.ANTIFIRE,              30, "AFR",   EffectColors.ANTIFIRE,       -1),
		new VarbitTimerDef(VarbitIds.SUPER_ANTIFIRE,        20, "S.AFR", EffectColors.SUPER_ANTIFIRE, -1),
		new VarbitTimerDef(VarbitIds.DIVINE_SUPER_ATTACK,    1, "D.ATK", EffectColors.DIVINE, SpriteID.SKILL_ATTACK),
		new VarbitTimerDef(VarbitIds.DIVINE_SUPER_STRENGTH,  1, "D.STR", EffectColors.DIVINE, SpriteID.SKILL_STRENGTH),
		new VarbitTimerDef(VarbitIds.DIVINE_SUPER_DEFENCE,   1, "D.DEF", EffectColors.DIVINE, SpriteID.SKILL_DEFENCE),
		new VarbitTimerDef(VarbitIds.DIVINE_RANGING,         1, "D.RNG", EffectColors.DIVINE, SpriteID.SKILL_RANGED),
		new VarbitTimerDef(VarbitIds.DIVINE_MAGIC,           1, "D.MAG", EffectColors.DIVINE, SpriteID.SKILL_MAGIC),
		new VarbitTimerDef(VarbitIds.DIVINE_SUPER_COMBAT,    1, "D.CMB", EffectColors.DIVINE, SpriteID.SKILL_ATTACK),
		new VarbitTimerDef(VarbitIds.DIVINE_BASTION,         1, "D.BAS", EffectColors.DIVINE, SpriteID.SKILL_RANGED),
		new VarbitTimerDef(VarbitIds.DIVINE_BATTLEMAGE,      1, "D.BAT", EffectColors.DIVINE, SpriteID.SKILL_MAGIC),
		new VarbitTimerDef(VarbitIds.NMZ_OVERLOAD,          25, "OVL",   EffectColors.OVERLOAD, -1),
		new VarbitTimerDef(VarbitIds.COX_OVERLOAD,          25, "OVL",   EffectColors.OVERLOAD, -1),
		new VarbitTimerDef(VarbitIds.VENGEANCE_ACTIVE,       0, "VNG",   EffectColors.VENGEANCE, -1),
		new VarbitTimerDef(VarbitIds.MAGIC_IMBUE,           10, "IMB",   EffectColors.MAGIC_IMBUE, SpriteID.SKILL_MAGIC),
		new VarbitTimerDef(VarbitIds.GOADING_POTION,         6, "GOD",   EffectColors.GOADING, -1),
		new VarbitTimerDef(VarbitIds.PRAYER_REGEN,          12, "P.RGN", EffectColors.PRAYER_REGEN, SpriteID.SKILL_PRAYER),
		new VarbitTimerDef(VarbitIds.MENAPHITE_REMEDY,      25, "MEN",   EffectColors.MENAPHITE, -1),
		new VarbitTimerDef(VarbitIds.LIQUID_ADRENALINE,      0, "ADR",   EffectColors.ADRENALINE, SpriteID.MINIMAP_ORB_SPECIAL_ICON),
	};
}
