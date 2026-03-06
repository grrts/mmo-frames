package com.mmoframes.rendering;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

/**
 * Low-level text drawing utilities: shadow text, centred shadow text.
 */
public final class TextRenderer
{
	public static final Color TEXT_WHITE  = new Color(255, 255, 255, 255);
	public static final Color TEXT_SHADOW = new Color(  0,   0,   0, 200);
	public static final Color TEXT_LEVEL  = new Color(178, 168, 130, 255);

	private TextRenderer() {}

	/** Draw {@code text} at (x, y) with a 1-pixel black shadow offset. */
	public static void shadow(Graphics2D g, String text, Font font, int x, int y, Color color)
	{
		g.setFont(font);
		g.setColor(TEXT_SHADOW);
		g.drawString(text, x + 1, y + 1);
		g.setColor(color);
		g.drawString(text, x, y);
	}

	/**
	 * Draw {@code text} horizontally and vertically centred within the rectangle
	 * (rx, ry, rw, rh) with a 1-pixel shadow.
	 */
	public static void centeredShadow(Graphics2D g, String text, Font font,
		int rx, int ry, int rw, int rh, Color color)
	{
		g.setFont(font);
		FontMetrics fm = g.getFontMetrics();
		int tx = rx + (rw - fm.stringWidth(text)) / 2;
		int ty = ry + (rh - fm.getHeight()) / 2 + fm.getAscent();
		shadow(g, text, font, tx, ty, color);
	}
}
