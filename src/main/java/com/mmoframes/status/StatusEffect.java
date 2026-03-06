package com.mmoframes.status;

import java.awt.Color;
import java.awt.image.BufferedImage;

/**
 * Abstract base for a buff / debuff / status effect shown on a unit frame.
 *
 * Concrete implementations:
 *   {@link PlayerPoisonEffect} — player poison/venom read from VarPlayerID.POISON
 *   {@link NpcStatusEffect}    — NPC or other-player status tracked from hitsplats
 *   {@link SkillBoostEffect}   — combat skill boost/drain from StatChanged events
 */
public abstract class StatusEffect
{
	/**
	 * Known status effect types with their OSRS-accurate colour.
	 * Colours match the OSRS in-game poison / venom hitsplat palette.
	 */
	public enum Type
	{
		POISON(new Color( 89, 188,  44, 255)),  // OSRS poison green
		VENOM (new Color(  0, 168,  56, 255));  // OSRS venom green (deeper)

		public final Color color;

		Type(Color color)
		{
			this.color = color;
		}
	}

	/** Type of this effect — used internally by NPC tracking. May be {@code null} for non-typed effects. */
	public abstract Type getType();

	public abstract boolean isActive();

	/** Returns {@code true} for buffs (display above frame), {@code false} for debuffs (display below). */
	public boolean isPositive() { return false; }

	/** Text shown in the status frame (damage number, signed boost level, etc.). */
	public abstract String getDisplayValue();

	/** Stripe / accent colour for this effect. */
	public abstract Color getColor();

	/** Icon rendered above the value. Returns {@code null} when no icon is available. */
	public BufferedImage getIcon() { return null; }
}
