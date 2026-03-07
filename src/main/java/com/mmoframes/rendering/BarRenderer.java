package com.mmoframes.rendering;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;

/**
 * Draws filled bars (horizontal and vertical) and their tick-sweep animations.
 *
 * Sweeps use AlphaComposite — no grey tint, pure brightness shift:
 *   lighten = true  → regen ticks (HP, Spec):   SRC_OVER white @ 22 %
 *   lighten = false → drain ticks (Prayer):      SRC_OVER black @ 25 %
 */
public final class BarRenderer
{
	public static final Color BAR_BG     = new Color( 10,   8,   5, 225);
	public static final Color BAR_BORDER = new Color( 22,  18,  10, 255);

	private BarRenderer() {}

	// ── Horizontal bar (left-to-right fill) ──────────────────────────────────

	public static void drawBar(Graphics2D g, int x, int y, int w, int h,
		double frac, Color color)
	{
		g.setColor(BAR_BG);
		g.fillRect(x, y, w, h);

		int fillW = (int) Math.round(w * clamp(frac));
		if (fillW > 0)
		{
			g.setColor(color);
			g.fillRect(x, y, fillW, h);
			g.setColor(highlight(color));
			g.drawLine(x, y, x + fillW - 1, y); // 1px top highlight
		}

		g.setColor(BAR_BORDER);
		g.drawRect(x, y, w - 1, h - 1);
	}

	/**
	 * Draws a semi-transparent restore/heal section immediately after the current fill,
	 * capped so it never overflows the bar bounds. Matches StatusBars BarRenderer behaviour.
	 */
	public static void drawRestoreH(Graphics2D g, int x, int y, int w, int h,
		double currentFrac, double restoreFrac, Color color)
	{
		int currentW = (int) Math.round(w * clamp(currentFrac));
		int restoreW = (int) Math.round(w * clamp(restoreFrac));
		restoreW = Math.min(restoreW, w - currentW);
		if (restoreW <= 0)
		{
			return;
		}
		Composite orig = g.getComposite();
		g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, color.getAlpha() / 255f));
		g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue()));
		g.fillRect(x + currentW, y, restoreW, h);
		g.setComposite(orig);
	}

	/** Horizontal sweep (left-to-right advancing front). */
	public static void drawSweepH(Graphics2D g, int x, int y, int w, int h,
		double progress, Color barColor, boolean lighten)
	{
		int sw = Math.max(1, (int) Math.round(w * clamp(progress)));
		Composite orig = g.getComposite();
		g.setColor(pulse(barColor, lighten));
		g.fillRect(x, y, sw - 1, h);
		g.setComposite(orig);
		// Vertical leading-edge pulse line
		g.drawLine(x + sw - 1, y, x + sw - 1, y + h - 1);
	}

	// ── Vertical bar (bottom-to-top fill) ────────────────────────────────────

	public static void drawBarVertical(Graphics2D g, int x, int y, int w, int h,
		double frac, Color color)
	{
		g.setColor(BAR_BG);
		g.fillRect(x, y, w, h);

		int fillH = (int) Math.round(h * clamp(frac));
		int fillY = y + h - fillH;
		if (fillH > 0)
		{
			g.setColor(color);
			g.fillRect(x, fillY, w, fillH);
			g.setColor(highlight(color));
			g.drawLine(x + w - 1, fillY, x + w - 1, fillY + fillH - 1); // 1px right highlight
		}

		g.setColor(BAR_BORDER);
		g.drawRect(x, y, w - 1, h - 1);
	}

	/** Vertical sweep (bottom-to-top advancing front). */
	public static void drawSweepV(Graphics2D g, int x, int y, int w, int h,
		double progress, Color barColor, boolean lighten)
	{
		int sh  = Math.max(1, (int) Math.round(h * clamp(progress)));
		int sy  = y + h - sh;
		applyCompositeRect(g, x, sy, w, sh, lighten);
		// Horizontal leading-edge pulse line at top of sweep
		g.setColor(pulse(barColor, lighten));
		g.drawLine(x, sy, x + w - 1, sy);
	}

	// ── Helpers ───────────────────────────────────────────────────────────────

	private static void applyCompositeRect(Graphics2D g,
		int x, int y, int w, int h, boolean lighten)
	{
		Composite orig = g.getComposite();
		if (lighten)
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.22f));
			g.setColor(Color.WHITE);
		}
		else
		{
			g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
			g.setColor(Color.BLACK);
		}
		g.fillRect(x, y, w, h);
		g.setComposite(orig);
	}

	private static Color highlight(Color c)
	{
		return new Color(
			Math.min(255, c.getRed()   + 55),
			Math.min(255, c.getGreen() + 40),
			Math.min(255, c.getBlue()  + 30),
			c.getAlpha());
	}

	private static Color pulse(Color c, boolean lighten)
	{
		if (lighten)
			return new Color(
				Math.min(255, c.getRed()   + 80),
				Math.min(255, c.getGreen() + 80),
				Math.min(255, c.getBlue()  + 80), 200);
		return new Color(
			Math.max(0, c.getRed()   - 40),
			Math.max(0, c.getGreen() - 40),
			Math.max(0, c.getBlue()  - 40), 200);
	}

	private static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
