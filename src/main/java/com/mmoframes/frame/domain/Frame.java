package com.mmoframes.frame.domain;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class Frame
{
	private FrameType type;
	private String name;
	private int level;
	private Portrait portrait;
	private List<Bar> bars = new ArrayList<>();
	private List<StatusEffect> effects = new ArrayList<>();
	private boolean showName;
	private boolean showHpText;
	private int frameWidth;

	// =====================================================================
	// Effect management (domain logic)
	// =====================================================================

	public void addEffect(StatusEffect effect)
	{
		effects.removeIf(e -> e.getType() == effect.getType());
		effects.add(effect);
	}

	public void removeEffect(StatusEffectType type)
	{
		effects.removeIf(e -> e.getType() == type);
	}

	public void removeExpiredEffects()
	{
		long now = System.currentTimeMillis();
		effects.removeIf(e -> e.getExpiresAtMs() > 0 && now >= e.getExpiresAtMs());
	}

	// =====================================================================
	// Queries
	// =====================================================================

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
