package com.mmoframes.frame.application;

import net.runelite.api.Skill;

public final class TrackedSkills
{
	private TrackedSkills() {}

	public static final Skill[] COMBAT_SKILLS = {
		Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE,
		Skill.RANGED, Skill.MAGIC, Skill.PRAYER, Skill.HITPOINTS
	};
}
