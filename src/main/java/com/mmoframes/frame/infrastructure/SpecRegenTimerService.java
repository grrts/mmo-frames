package com.mmoframes.frame.infrastructure;

import com.mmoframes.frame.application.TickConstants;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.gameval.VarPlayerID;

@Singleton
public class SpecRegenTimerService
{
	@Inject private Client client;

	@Getter private double progress;

	private int tick;
	private int lastSpec = -1;

	public void tick()
	{
		int specRaw = client.getVarpValue(VarPlayerID.SA_ENERGY);
		int spec    = specRaw / 10;

		if (spec >= 100)
		{
			progress = 0.0;
			tick = 0;
			lastSpec = spec;
			return;
		}

		if (spec != lastSpec)
		{
			lastSpec = spec;
			tick = 0;
		}

		tick = (tick + 1) % TickConstants.SPEC_REGEN_TICKS;
		progress = (double) tick / TickConstants.SPEC_REGEN_TICKS;
	}

	public void resetState()
	{
		tick = 0;
		progress = 0.0;
		lastSpec = -1;
	}

	public void resetForTeleport()
	{
		resetState();
	}
}
