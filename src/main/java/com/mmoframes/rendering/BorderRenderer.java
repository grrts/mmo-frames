package com.mmoframes.rendering;

import java.awt.Color;
import java.awt.Graphics2D;

/**
 * Draws the OSRS stone-panel border used by all unit frames and status frames.
 *
 * Colour ramp (outer → inner):
 *   0  rgb( 81, 74, 58)  dim outer edge
 *   1  rgb( 88, 79, 62)  rising
 *   2  rgb( 94, 87, 71)  peak highlight
 *   3  rgb( 46, 39, 29)  inner shadow drop
 *   4  rgb( 38, 34, 25)  inner shadow
 *   5  rgb( 36, 31, 24)  innermost
 *
 * Interior fill: rgb(37, 32, 28) — warm dark stone.
 */
public final class BorderRenderer
{
	/** Standard border thickness for main unit frames. */
	public static final int BORDER = 6;

	/** Thinner border for compact status-effect mini-frames. */
	public static final int STATUS_BORDER = 4;

	public static final Color INTERIOR = new Color(37, 32, 28, 255);

	private static final Color[] STONE = {
		new Color( 81,  74,  58, 255),
		new Color( 88,  79,  62, 255),
		new Color( 94,  87,  71, 255),
		new Color( 46,  39,  29, 255),
		new Color( 38,  34,  25, 255),
		new Color( 36,  31,  24, 255),
	};

	private BorderRenderer() {}

	/** Draw a stone frame using the standard {@link #BORDER} thickness. */
	public static void drawFrame(Graphics2D g, int x, int y, int w, int h)
	{
		drawFrame(g, x, y, w, h, BORDER);
	}

	/**
	 * Draw only the stone border rings without filling the interior.
	 * Use when the inner area is provided by an underlying widget (transparent background).
	 */
	public static void drawFrameBorderOnly(Graphics2D g, int x, int y, int w, int h)
	{
		int levels = Math.min(BORDER, STONE.length);
		for (int i = 0; i < levels; i++)
		{
			g.setColor(STONE[i]);
			g.drawRect(x + i, y + i, w - 1 - i * 2, h - 1 - i * 2);
		}
	}

	/**
	 * Draw a stone frame but leave a rectangular hole in the interior fill unpainted.
	 * The hole coordinates are relative to (x, y), same origin as the frame itself.
	 * Use this when a live widget is rendering inside the portrait area — the hole
	 * prevents the overlay from painting over the widget's pixels.
	 */
	public static void drawFrameWithHole(Graphics2D g, int x, int y, int w, int h,
		int holeX, int holeY, int holeW, int holeH)
	{
		// Interior bounds (inside border)
		int ix = x + BORDER;
		int iy = y + BORDER;
		int iw = w - BORDER * 2;
		int ih = h - BORDER * 2;

		g.setColor(INTERIOR);
		// Top strip: above the hole
		g.fillRect(ix, iy, iw, holeY - iy);
		// Left strip: left of hole, same height as hole
		g.fillRect(ix, holeY, holeX - ix, holeH);
		// Right strip: right of hole to frame edge, same height as hole
		g.fillRect(holeX + holeW, holeY, ix + iw - (holeX + holeW), holeH);
		// Bottom strip: below the hole
		g.fillRect(ix, holeY + holeH, iw, iy + ih - (holeY + holeH));

		// Stone border rings (same as drawFrame)
		int levels = Math.min(BORDER, STONE.length);
		for (int i = 0; i < levels; i++)
		{
			g.setColor(STONE[i]);
			g.drawRect(x + i, y + i, w - 1 - i * 2, h - 1 - i * 2);
		}
	}

	/** Draw a stone frame with a custom border thickness (capped at 6 stone levels). */
	public static void drawFrame(Graphics2D g, int x, int y, int w, int h, int border)
	{
		g.setColor(INTERIOR);
		g.fillRect(x + border, y + border, w - border * 2, h - border * 2);

		int levels = Math.min(border, STONE.length);
		for (int i = 0; i < levels; i++)
		{
			g.setColor(STONE[i]);
			g.drawRect(x + i, y + i, w - 1 - i * 2, h - 1 - i * 2);
		}
	}
}
