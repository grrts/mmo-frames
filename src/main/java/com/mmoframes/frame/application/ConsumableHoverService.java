package com.mmoframes.frame.application;

import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Skill;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.itemstats.Effect;
import net.runelite.client.plugins.itemstats.ItemStatChangesService;
import net.runelite.client.plugins.itemstats.StatChange;

@Singleton
public class ConsumableHoverService
{
	@Inject private Client client;

	@com.google.inject.Inject(optional = true)
	private ItemStatChangesService itemStatChanges;

	public int getHealHp()
	{
		if (itemStatChanges == null)
		{
			return 0;
		}
		return getRestoreValue(Skill.HITPOINTS.getName());
	}

	public int getHealPrayer()
	{
		if (itemStatChanges == null)
		{
			return 0;
		}
		return getRestoreValue(Skill.PRAYER.getName());
	}

	private int getRestoreValue(String skill)
	{
		final MenuEntry[] menu = client.getMenuEntries();
		final int menuSize = menu.length;
		if (menuSize == 0)
		{
			return 0;
		}

		final MenuEntry entry = menu[menuSize - 1];
		final Widget widget = entry.getWidget();
		int restoreValue = 0;

		if (widget != null && widget.getId() == InterfaceID.Inventory.ITEMS)
		{
			final Effect change = itemStatChanges.getItemStatChanges(widget.getItemId());

			if (change != null)
			{
				for (final StatChange c : change.calculate(client).getStatChanges())
				{
					final int value = c.getTheoretical();

					if (value != 0 && c.getStat().getName().equals(skill))
					{
						restoreValue = value;
					}
				}
			}
		}

		return restoreValue;
	}
}
