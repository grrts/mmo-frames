package com.mmoframes.rendering;

import com.mmoframes.frame.domain.StatusEffect;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Renders a horizontal row of status-effect mini-frames (poison, venom, …)
 * below a unit frame.
 *
 * Each frame shows (with icon):
 *   ┌──────────────┐
 *   │              │
 *   │  [ICON 20px] │  ← icon perfectly centred in the interior
 *   │     +3       │  ← value overlaps icon bottom, scales to fit width
 *   └──────────────┘
 *
 * Without icon:
 *   ┌──────────────┐
 *   │      6       │  ← value centred, accent colour, scales to fit
 *   └──────────────┘
 *
 * Frames are laid out left-to-right with a {@value #STATUS_GAP} px gap.
 * A {@value #STATUS_ROW_GAP} px gap is added above the row.
 */
public final class StatusFrameRenderer
{
	static final int STATUS_W       = 50;
	static final int STATUS_H       = 52;
	static final int STATUS_BORDER  = BorderRenderer.STATUS_BORDER;
	static final int STATUS_GAP     = 4;
	static final int STATUS_ROW_GAP   = 3;
	static final int STATUS_FRAME_GAP = 5;

	private static final int  ICON_SIZE    = 20;
	/** How many pixels the text top overlaps the bottom edge of the icon. */
	private static final int  TEXT_OVERLAP = 6;
	/** Starting font size — scaled down until text fits available width. */
	private static final int  FONT_MAX_PT  = 12;
	private static final int  FONT_MIN_PT  = 6;
	private static final Font FONT_BASE    = new Font("Dialog", Font.BOLD, FONT_MAX_PT);

	private StatusFrameRenderer() {}

	/**
	 * Returns the vertical space that {@link #renderStatusEffects} will consume
	 * for this list, plus a {@link #STATUS_FRAME_GAP} gap below the row so there
	 * is breathing room between the effects and the unit frame above them.
	 */
	public static int calcHeight(List<StatusEffect> effects)
	{
		if (effects == null) return 0;
		for (StatusEffect e : effects)
		{
			if (e.isActive()) return STATUS_H + STATUS_ROW_GAP + STATUS_FRAME_GAP;
		}
		return 0;
	}

	/**
	 * Renders all active status effects in a horizontal row starting at (x, y + gap).
	 *
	 * @return total height consumed ({@code STATUS_H + STATUS_ROW_GAP}) if at least
	 *         one effect was drawn, otherwise 0.
	 */
	public static int renderStatusEffects(Graphics2D g,
		List<StatusEffect> effects, int x, int y)
	{
		if (effects == null || effects.isEmpty())
		{
			return 0;
		}

		boolean drew = false;
		int bx = x;
		for (StatusEffect effect : effects)
		{
			if (!effect.isActive())
			{
				continue;
			}
			drawStatusFrame(g, bx, y + STATUS_ROW_GAP, effect);
			bx  += STATUS_W + STATUS_GAP;
			drew = true;
		}
		return drew ? STATUS_H + STATUS_ROW_GAP : 0;
	}

	// ── Single status frame ───────────────────────────────────────────────────

	private static void drawStatusFrame(Graphics2D g, int x, int y, StatusEffect effect)
	{
		BorderRenderer.drawFrame(g, x, y, STATUS_W, STATUS_H, STATUS_BORDER);

		int ix = x + STATUS_BORDER;
		int iy = y + STATUS_BORDER;
		int iw = STATUS_W - STATUS_BORDER * 2;
		int ih = STATUS_H - STATUS_BORDER * 2;

		Color col = effect.getColor();

		// Subtle interior tint
		g.setColor(new Color(col.getRed(), col.getGreen(), col.getBlue(), 35));
		g.fillRect(ix, iy, iw, ih);

		// 2 px left stripe
		g.setColor(col);
		g.fillRect(ix, iy, 2, ih);

		String        displayValue = effect.getDisplayValue();
		BufferedImage icon         = effect.getIcon();
		// Usable text width: interior minus the stripe and 1px right padding
		int availW = iw - 3;

		if (icon != null)
		{
			// ── Icon: perfectly centred in the interior ──────────────────────
			int iconX = ix + (iw - ICON_SIZE) / 2;
			int iconY = iy + (ih - ICON_SIZE) / 2;
			g.drawImage(icon, iconX, iconY, ICON_SIZE, ICON_SIZE, null);

			// ── Value: overlaps the icon's bottom edge ───────────────────────
			int textTopY  = iconY + ICON_SIZE - TEXT_OVERLAP;
			int textAreaH = (iy + ih) - textTopY;
			Font font = scaledFont(g, textAreaH, availW, displayValue);
			FontMetrics fm = g.getFontMetrics(font);
			int tx = ix + 2 + (availW - fm.stringWidth(displayValue)) / 2;
			int ty = textTopY + fm.getAscent();
			TextRenderer.shadow(g, displayValue, font, tx, ty, TextRenderer.TEXT_WHITE);
		}
		else
		{
			String label = effect.getLabel();
			boolean hasLabel = label != null && !label.isEmpty();
			boolean hasValue = displayValue != null && !displayValue.isEmpty();

			if (hasLabel && hasValue)
			{
				// ── No icon, label + value: label on top, value on bottom ────
				int halfH = ih / 2;

				Font labelFont = scaledFont(g, halfH, availW, label);
				FontMetrics lfm = g.getFontMetrics(labelFont);
				int lx = ix + 2 + (availW - lfm.stringWidth(label)) / 2;
				int ly = iy + (halfH - lfm.getHeight()) / 2 + lfm.getAscent();
				TextRenderer.shadow(g, label, labelFont, lx, ly, col);

				Font valFont = scaledFont(g, halfH, availW, displayValue);
				FontMetrics vfm = g.getFontMetrics(valFont);
				int vx = ix + 2 + (availW - vfm.stringWidth(displayValue)) / 2;
				int vy = iy + halfH + (halfH - vfm.getHeight()) / 2 + vfm.getAscent();
				TextRenderer.shadow(g, displayValue, valFont, vx, vy, TextRenderer.TEXT_WHITE);
			}
			else
			{
				// ── No icon, single text: centred in full interior ────────────
				String text = hasLabel ? label : displayValue;
				Font font = scaledFont(g, ih, availW, text);
				FontMetrics fm = g.getFontMetrics(font);
				int vx = ix + 2 + (availW - fm.stringWidth(text)) / 2;
				int vy = iy + (ih - fm.getHeight()) / 2 + fm.getAscent();
				TextRenderer.shadow(g, text, font, vx, vy, col);
			}
		}
	}

	/**
	 * Returns the largest bold font starting from {@value #FONT_MAX_PT} pt that
	 * fits {@code text} within {@code availableWidth} px and {@code availableHeight} px.
	 */
	private static Font scaledFont(Graphics2D g, int availableHeight, int availableWidth, String text)
	{
		Font font = FONT_BASE;
		for (int pt = FONT_MAX_PT; pt > FONT_MIN_PT; pt--)
		{
			font = FONT_BASE.deriveFont((float) pt);
			FontMetrics fm = g.getFontMetrics(font);
			if (fm.stringWidth(text) <= availableWidth && fm.getHeight() <= availableHeight)
			{
				break;
			}
		}
		return font;
	}
}
