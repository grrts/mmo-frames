package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.application.TickConstants;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.Skill;

@Singleton
public class HpRegenTimerService
{
	@Inject private Client client;

	@Getter private double progress;

	private int tick;
	private int lastHp = -1;

	public void tick()
	{
		int hp    = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp = client.getRealSkillLevel(Skill.HITPOINTS);

		if (hp >= maxHp)
		{
			progress = 0.0;
			tick = 0;
			lastHp = hp;
			return;
		}

		if (hp != lastHp)
		{
			lastHp = hp;
			tick = 0;
		}

		tick = (tick + 1) % TickConstants.HP_REGEN_TICKS;
		progress = (double) tick / TickConstants.HP_REGEN_TICKS;
	}

	public void resetState()
	{
		tick = 0;
		progress = 0.0;
		lastHp = -1;
	}

	public void resetForTeleport()
	{
		resetState();
	}
}
