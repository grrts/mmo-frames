package com.mmoframes.frame.application;

import java.awt.Color;

public class VarbitTimerDef
{
	public final int varbitId;
	public final int tickMultiplier;
	public final String label;
	public final Color color;
	public final int spriteId;

	public VarbitTimerDef(int varbitId, int tickMultiplier, String label, Color color, int spriteId)
	{
		this.varbitId       = varbitId;
		this.tickMultiplier = tickMultiplier;
		this.label          = label;
		this.color          = color;
		this.spriteId       = spriteId;
	}
}
