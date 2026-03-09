package com.mmoframes.rendering;

import com.mmoframes.frame.domain.Bar;
import com.mmoframes.frame.domain.BarType;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.Portrait;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;

/**
 * Assembles complete unit frames from the sub-renderers in the
 * {@code rendering} package.
 *
 * Layout:
 * ┌──────────────────────────────────────────┬────────┐
 * │ [Portrait]  Name                  Lv.XX  │        │
 * │             [═══ HP ══════════]          │  SPEC  │
 * │             [═══ Prayer ══════]          │ square │
 * │             [═ Run ═]                    │        │
 * └──────────────────────────────────────────┴────────┘
 */
public class UnitFrameRenderer
{
	// ── Layout ────────────────────────────────────────────────────────────────
	/** Alias for {@link BorderRenderer#BORDER} — used by overlays for Dimension calc. */
	public static final int BORDER     = BorderRenderer.BORDER;
	public static final int PAD        = 6;
	public static final int PORTRAIT_W = 60;
	public static final int PORTRAIT_H = 60;
	public static final int MAIN_BAR_H = 20;
	public static final int SWEEP_BAR_H = 4;
	public static final int THIN_BAR_H = 10;
	public static final int BAR_GAP    = 4;
	public static final int NAME_H     = 16;
	public static final int SPEC_W     = 60;
	public static final int SPEC_GAP   = 4;

	// ── Text colours ──────────────────────────────────────────────────────────
	private static final Color TEXT_WHITE     = TextRenderer.TEXT_WHITE;
	private static final Color TEXT_SPEC      = new Color(210, 200, 162, 255);
	private static final Color TEXT_SPEC_FULL = new Color(255, 240, 100, 255);
	private static final Color TEXT_PORT_LETTER = new Color(210, 200, 162, 255);

	// ── Fonts ─────────────────────────────────────────────────────────────────
	private static final Font FONT_NAME     = new Font("Dialog", Font.BOLD,  11);
	private static final Font FONT_BAR      = new Font("Dialog", Font.BOLD,   9);
	private static final Font FONT_PORT     = new Font("Dialog", Font.BOLD,  17);
	private static final Font FONT_LV       = new Font("Dialog", Font.PLAIN,  9);
	private static final Font FONT_SPEC_PCT = new Font("Dialog", Font.BOLD,  10);

	private UnitFrameRenderer() {}

	// =========================================================================
	// Public API
	// =========================================================================

	public static int calcFrameHeight(Frame frame)
	{
		int h = BORDER + PAD + NAME_H + BAR_GAP + MAIN_BAR_H;
		if (frame.hasBar(BarType.PRAYER))    h += BAR_GAP + MAIN_BAR_H;
		if (frame.hasBar(BarType.RUN_ENERGY)) h += BAR_GAP + THIN_BAR_H;
		h += PAD + BORDER;
		return Math.max(h, PORTRAIT_H + (BORDER + PAD) * 2);
	}

	/** Total render width; includes spec square (+ PAD gap) when SPEC bar is present. */
	public static int calcFrameWidth(Frame frame)
	{
		boolean showSpec = frame.hasBar(BarType.SPEC);
		return showSpec ? frame.getFrameWidth() + SPEC_GAP + SPEC_W + PAD : frame.getFrameWidth();
	}

	/**
	 * Render the complete unit frame at the overlay's (0, 0) origin.
	 */
	public static void renderFrame(Graphics2D g, Frame frame)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int mainW    = frame.getFrameWidth();
		boolean showSpec = frame.hasBar(BarType.SPEC);
		int frameH   = calcFrameHeight(frame);

		// ── 1. Main frame body ────────────────────────────────────────────────
		int portX = BORDER + PAD;
		int portY = BORDER + PAD;
		BorderRenderer.drawFrame(g, 0, 0, mainW, frameH);

		// ── 2. Portrait ───────────────────────────────────────────────────────
		Portrait portrait = frame.getPortrait();
		BorderRenderer.drawFrame(g, portX, portY, PORTRAIT_W, PORTRAIT_H);
		drawPortrait(g, portX, portY, frame.getName(),
			portrait != null ? portrait.getBackgroundColor() : null,
			portrait != null ? portrait.getFallbackLetter() : null);

		// ── 3. Content column ─────────────────────────────────────────────────
		int specColW = showSpec ? (SPEC_GAP + SPEC_W + PAD) : 0;
		int cx       = portX + PORTRAIT_W + PAD + 2;
		int cw       = mainW - BORDER - cx - PAD - specColW;
		int cy       = BORDER + PAD;

		// Name row (spans full content width for level badge alignment)
		if (frame.getName() != null)
		{
			drawNameRow(g, cx, cy, (mainW - BORDER - cx - PAD), frame.getName(), frame.getLevel());
		}
		cy += NAME_H + BAR_GAP;

		// Render bars from the Frame's ordered list (spec bar handled separately)
		for (Bar bar : frame.getBars())
		{
			if (bar.getType() == BarType.HP)
			{
				cy = drawHpBar(g, cx, cy, cw, bar, frame.isShowHpText());
			}
			else if (bar.getType() == BarType.PRAYER)
			{
				cy = drawPrayerBar(g, cx, cy, cw, bar);
			}
			else if (bar.getType() == BarType.RUN_ENERGY)
			{
				drawRunEnergyBar(g, cx, cy, cw, bar);
			}
		}

		// ── 4. Spec square ────────────────────────────────────────────────────
		Bar specBar = frame.getBarOfType(BarType.SPEC);
		if (specBar != null)
		{
			int barTopY = BORDER + PAD + NAME_H + BAR_GAP;
			int barBotY = frameH - BORDER - PAD;
			int sx      = mainW - BORDER - PAD - SPEC_W;
			drawSpecSquare(g, sx, barTopY, SPEC_W, barBotY - barTopY, specBar);
		}
	}

	// =========================================================================
	// Bar rendering helpers
	// =========================================================================

	private static int drawHpBar(Graphics2D g, int cx, int cy, int cw, Bar bar, boolean showText)
	{
		double frac = frac(bar.getCurrent(), bar.getMax());
		BarRenderer.drawBar(g, cx, cy, cw, MAIN_BAR_H, frac, bar.getColor());

		if (bar.getSweepProgress() > 0 && frac < 1.0)
		{
			BarRenderer.drawSweepH(g, cx, (cy + MAIN_BAR_H - SWEEP_BAR_H), cw, SWEEP_BAR_H,
				bar.getSweepProgress(), bar.getColor(), bar.isSweepLighten());
		}

		int iconSize = MAIN_BAR_H - 6;
		if (bar.getIcon() != null)
		{
			g.drawImage(bar.getIcon(), cx + 3, cy + (MAIN_BAR_H - iconSize) / 2, iconSize, iconSize, null);
		}

		if (bar.getHoverRestore() > 0 && bar.getMax() > 0)
		{
			BarRenderer.drawRestoreH(g, cx, cy, cw, MAIN_BAR_H,
				frac, (double) bar.getHoverRestore() / bar.getMax(), bar.getHoverRestoreColor());
		}

		if (showText && bar.getMax() > 1)
		{
			drawBarText(g, cx, cy, cw, MAIN_BAR_H, bar.getCurrent() + " / " + bar.getMax());
		}

		return cy + MAIN_BAR_H + BAR_GAP;
	}

	private static int drawPrayerBar(Graphics2D g, int cx, int cy, int cw, Bar bar)
	{
		double frac = frac(bar.getCurrent(), bar.getMax());
		BarRenderer.drawBar(g, cx, cy, cw, MAIN_BAR_H, frac, bar.getColor());

		if (bar.getSweepProgress() > 0 && bar.getCurrent() > 0)
		{
			BarRenderer.drawSweepH(g, cx, (cy + MAIN_BAR_H - SWEEP_BAR_H), cw, SWEEP_BAR_H,
				bar.getSweepProgress(), bar.getColor(), bar.isSweepLighten());
		}

		int iconSize = MAIN_BAR_H - 6;
		if (bar.getIcon() != null)
		{
			g.drawImage(bar.getIcon(), cx + 3, cy + (MAIN_BAR_H - iconSize) / 2, iconSize, iconSize, null);
		}

		if (bar.getHoverRestore() > 0 && bar.getMax() > 0)
		{
			BarRenderer.drawRestoreH(g, cx, cy, cw, MAIN_BAR_H,
				frac, (double) bar.getHoverRestore() / bar.getMax(), bar.getHoverRestoreColor());
		}

		drawBarText(g, cx, cy, cw, MAIN_BAR_H, bar.getCurrent() + " / " + bar.getMax());

		return cy + MAIN_BAR_H + BAR_GAP;
	}

	private static void drawRunEnergyBar(Graphics2D g, int cx, int cy, int cw, Bar bar)
	{
		BarRenderer.drawBar(g, cx, cy, cw, THIN_BAR_H,
			clamp((double) bar.getCurrent() / bar.getMax()), bar.getColor());
	}

	// =========================================================================
	// Portrait
	// =========================================================================

	private static void drawPortrait(Graphics2D g, int portX, int portY,
		String name, Color bgColor, String fallbackLetter)
	{
		int ix = portX + BORDER;
		int iy = portY + BORDER;
		int iw = PORTRAIT_W - BORDER * 2;
		int ih = PORTRAIT_H - BORDER * 2;

		if (bgColor != null)
		{
			g.setColor(bgColor);
			g.fillRect(ix, iy, iw, ih);
		}

		String ch = fallbackLetter;
		if (ch == null && name != null && !name.isEmpty())
		{
			ch = name.substring(0, 1).toUpperCase();
		}

		if (ch != null)
		{
			g.setFont(FONT_PORT);
			FontMetrics fm = g.getFontMetrics();
			int tx = ix + (iw - fm.stringWidth(ch)) / 2;
			int ty = iy + (ih - fm.getHeight()) / 2 + fm.getAscent();
			TextRenderer.shadow(g, ch, FONT_PORT, tx, ty, TEXT_PORT_LETTER);
		}
	}

	// =========================================================================
	// Spec square
	// =========================================================================

	private static void drawSpecSquare(Graphics2D g,
		int x, int y, int w, int h, Bar specBar)
	{
		int specPct = specBar.getCurrent();
		boolean full  = (specPct >= 100);
		Color   sCol  = specBar.getColor();
		Color   textC = full ? TEXT_SPEC_FULL : TEXT_SPEC;

		BorderRenderer.drawFrame(g, x, y, w, h);

		int ix = x + BORDER;
		int iy = y + BORDER;
		int iw = w - BORDER * 2;
		int ih = h - BORDER * 2;

		BarRenderer.drawBarVertical(g, ix, iy, iw, ih, clamp(specPct / 100.0), sCol);

		if (specBar.getSweepProgress() > 0 && !full)
		{
			BarRenderer.drawSweepV(g, ix, iy, iw, ih, specBar.getSweepProgress(), sCol, true);
		}

		String pctStr = specPct + "%";

		if (specBar.getIcon() != null)
		{
			int iconSize  = 20;
			int iconX     = ix + (iw - iconSize) / 2;
			int iconY     = iy + (ih - iconSize) / 2;
			g.drawImage(specBar.getIcon(), iconX, iconY, iconSize, iconSize, null);

			int textTopY  = iconY + iconSize - 6;
			int textAreaH = (iy + ih) - textTopY;
			Font font = scaledFont(g, textAreaH, iw, pctStr);
			FontMetrics fm = g.getFontMetrics(font);
			int tx = ix + (iw - fm.stringWidth(pctStr)) / 2;
			int ty = textTopY + fm.getAscent();
			TextRenderer.shadow(g, pctStr, font, tx, ty, textC);
		}
		else
		{
			g.setFont(FONT_SPEC_PCT);
			FontMetrics fmP = g.getFontMetrics();
			int pctX = ix + (iw - fmP.stringWidth(pctStr)) / 2;
			int pctY = iy + (ih / 2) + 3;
			TextRenderer.shadow(g, pctStr, FONT_SPEC_PCT, pctX, pctY, textC);
		}
	}

	/** Scales bold font from 10 pt down to 6 pt to fit text in the available area. */
	private static Font scaledFont(Graphics2D g, int availH, int availW, String text)
	{
		for (int pt = 10; pt > 6; pt--)
		{
			Font f = FONT_SPEC_PCT.deriveFont((float) pt);
			FontMetrics fm = g.getFontMetrics(f);
			if (fm.stringWidth(text) <= availW && fm.getHeight() <= availH)
			{
				return f;
			}
		}
		return FONT_SPEC_PCT.deriveFont(6f);
	}

	// =========================================================================
	// Text helpers (layout-specific, delegate drawing to TextRenderer)
	// =========================================================================

	private static void drawNameRow(Graphics2D g, int x, int y, int totalW,
		String name, int level)
	{
		g.setFont(FONT_NAME);
		FontMetrics fm = g.getFontMetrics();
		int baseline = y + fm.getAscent();
		TextRenderer.shadow(g, name, FONT_NAME, x, baseline, TEXT_WHITE);

		if (level >= 1)
		{
			String lvl = "Lv." + level;
			g.setFont(FONT_LV);
			fm = g.getFontMetrics();
			int lx = x + totalW - fm.stringWidth(lvl);
			TextRenderer.shadow(g, lvl, FONT_LV, lx, baseline, TextRenderer.TEXT_LEVEL);
		}
	}

	private static void drawBarText(Graphics2D g, int x, int y, int w, int h,
		String text)
	{
		TextRenderer.centeredShadow(g, text, FONT_BAR, x, y, w, h, TEXT_WHITE);
	}

	// =========================================================================
	// Utilities
	// =========================================================================

	static double frac(int current, int max)
	{
		return max > 0 ? clamp((double) current / max) : 0.0;
	}

	static double clamp(double v) { return Math.max(0.0, Math.min(1.0, v)); }
}
