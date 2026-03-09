package com.mmoframes.frame.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Frame
{
	FrameType type;
	String name;
	int level;
	Portrait portrait;
	List<Bar> bars;
	List<StatusEffect> effects;
	boolean showName;
	boolean showHpText;
	int frameWidth;

	public boolean hasBar(BarType barType)
	{
		for (Bar b : bars)
		{
			if (b.getType() == barType) return true;
		}
		return false;
	}

	public Bar getBarOfType(BarType barType)
	{
		for (Bar b : bars)
		{
			if (b.getType() == barType) return b;
		}
		return null;
	}

	public List<StatusEffect> getBuffs()
	{
		List<StatusEffect> result = new ArrayList<>();
		for (StatusEffect e : effects)
		{
			if (e.getCategory() == StatusEffectCategory.BUFF) result.add(e);
		}
		return result;
	}

	public List<StatusEffect> getDebuffs()
	{
		List<StatusEffect> result = new ArrayList<>();
		for (StatusEffect e : effects)
		{
			if (e.getCategory() == StatusEffectCategory.DEBUFF) result.add(e);
		}
		return result;
	}
}
