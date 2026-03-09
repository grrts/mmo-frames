package com.mmoframes.frame.infrastructure;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;

@Singleton
public class PrayerDrainTimerService
{
	@Inject private Client      client;
	@Inject private ItemManager itemManager;

	@Getter private double progress;

	private double tick;
	private int    lastPray = -1;

	public void tick()
	{
		int prayerPoints = client.getBoostedSkillLevel(Skill.PRAYER);

		int totalDrainEffect = PrayerDrainRates.getTotalDrainEffect(client);

		if (totalDrainEffect == 0 || prayerPoints <= 0)
		{
			tick = 0;
			progress = 0.0;
			lastPray = prayerPoints;
			return;
		}

		int prayerBonus = 0;
		ItemContainer equip = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equip != null)
		{
			for (Item item : equip.getItems())
			{
				if (item != null && item.getId() > 0)
				{
					ItemStats stats = itemManager.getItemStats(item.getId());
					if (stats != null && stats.getEquipment() != null)
					{
						prayerBonus += stats.getEquipment().getPrayer();
					}
				}
			}
		}

		double drainInterval = Math.max(1.0, (60.0 + 2.0 * prayerBonus) / totalDrainEffect);

		if (lastPray >= 0 && prayerPoints < lastPray)
		{
			tick = 0;
			progress = 0.0;
			lastPray = prayerPoints;
			return;
		}
		lastPray = prayerPoints;

		tick = (tick + 1) % drainInterval;
		progress = 1.0 - tick / drainInterval;
	}

	public void resetState()
	{
		tick = 0.0;
		progress = 0.0;
		lastPray = -1;
	}

	public void resetForTeleport()
	{
		resetState();
	}
}
