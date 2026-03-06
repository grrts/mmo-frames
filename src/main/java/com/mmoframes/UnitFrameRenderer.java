package com.mmoframes;

import com.mmoframes.rendering.BarRenderer;
import com.mmoframes.rendering.BorderRenderer;
import com.mmoframes.rendering.TextRenderer;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

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
	private static final Color STAMINA_COLOR      = new Color(160, 124, 72, 255);
	// Restore bar section colours — semi-transparent, matching StatusBars plugin
	private static final Color RESTORE_HP_COLOR   = new Color(216, 255, 139, 130);
	private static final Color RESTORE_PRAY_COLOR = new Color(130, 180, 255, 130);

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

	public static int calcFrameHeight(boolean showPrayer, boolean showStamina)
	{
		int h = BORDER + PAD + NAME_H + BAR_GAP + MAIN_BAR_H;
		if (showPrayer)  h += BAR_GAP + MAIN_BAR_H;
		if (showStamina) h += BAR_GAP + THIN_BAR_H;
		h += PAD + BORDER;
		return Math.max(h, PORTRAIT_H + (BORDER + PAD) * 2);
	}

	/** Total render width; includes spec square (+ PAD gap) when showSpec is true. */
	public static int calcFrameWidth(int barColumnWidth, boolean showSpec)
	{
		return showSpec ? barColumnWidth + SPEC_GAP + SPEC_W + PAD : barColumnWidth;
	}

	/**
	 * Render the complete unit frame at the overlay's (0, 0) origin.
	 */
	public static void renderFrame(
		Graphics2D g,
		int mainW,
		String name,
		int level,
		int curHp,   int maxHp,
		int curPray, int maxPray,
		int runEnergy,
		int specPct,
		double hpSweep,
		double praySweep,
		double specSweep,
		MmoFramesConfig cfg,
		boolean showPrayer,
		boolean showStamina,
		boolean staminaActive,
		BufferedImage portrait,
		Color portraitBgColor,
		int poisonState,
		int hoveredHealHp,
		int hoveredRestorePrayer,
		BufferedImage hpBarIcon,
		BufferedImage prayerBarIcon,
		BufferedImage specIcon)
	{
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

		int     frameH   = calcFrameHeight(showPrayer, showStamina);
		boolean showSpec = specPct >= 0;

		// ── 1. Main frame body ────────────────────────────────────────────────
		BorderRenderer.drawFrame(g, 0, 0, mainW, frameH);

		// ── 2. Portrait ───────────────────────────────────────────────────────
		int portX = BORDER + PAD;
		int portY = BORDER + PAD;
		BorderRenderer.drawFrame(g, portX, portY, PORTRAIT_W, PORTRAIT_H);
		drawPortrait(g, portX, portY, name, portrait, portraitBgColor);

		// ── 3. Content column ─────────────────────────────────────────────────
		int specColW = showSpec ? (SPEC_GAP + SPEC_W + PAD) : 0;
		int cx       = portX + PORTRAIT_W + PAD + 2;
		int cw       = mainW - BORDER - cx - PAD - specColW;
		int cy       = BORDER + PAD;

		// Name row (spans full content width for level badge alignment)
		if (name != null)
		{
			drawNameRow(g, cx, cy, (mainW - BORDER - cx - PAD), name, level);
		}
		cy += NAME_H + BAR_GAP;

		// HP bar
		double hpFrac = frac(curHp, maxHp);
		Color  hpCol  = hpColor(hpFrac, cfg);
		BarRenderer.drawBar(g, cx, cy, cw, MAIN_BAR_H, hpFrac, hpCol);
		if (cfg.showHpRegenSweep() && hpSweep > 0 && hpFrac < 1.0)
		{
			BarRenderer.drawSweepH(g, cx, cy, cw, MAIN_BAR_H, hpSweep, hpCol, true);
		}
		// Heart icon at the left of the HP bar (normal / poison / venom)
		int iconSize = MAIN_BAR_H - 6; // 14 px — slightly smaller with padding
		if (hpBarIcon != null)
		{
			g.drawImage(hpBarIcon, cx + 3, cy + (MAIN_BAR_H - iconSize) / 2, iconSize, iconSize, null);
		}
		if (hoveredHealHp > 0 && maxHp > 0)
		{
			BarRenderer.drawRestoreH(g, cx, cy, cw, MAIN_BAR_H,
				hpFrac, (double) hoveredHealHp / maxHp, RESTORE_HP_COLOR);
		}
		if (maxHp > 1)
		{
			drawBarText(g, cx, cy, cw, MAIN_BAR_H, curHp + " / " + maxHp);
		}
		cy += MAIN_BAR_H + BAR_GAP;

		// Prayer bar
		if (showPrayer && curPray >= 0 && maxPray > 0)
		{
			double pFrac = frac(curPray, maxPray);
			Color  pCol  = cfg.colorPrayer();
			BarRenderer.drawBar(g, cx, cy, cw, MAIN_BAR_H, pFrac, pCol);
			if (cfg.showPrayerDrainSweep() && praySweep > 0 && curPray > 0)
			{
				BarRenderer.drawSweepH(g, cx, cy, cw, MAIN_BAR_H, praySweep, pCol, false);
			}
			if (prayerBarIcon != null)
			{
				g.drawImage(prayerBarIcon, cx + 3, cy + (MAIN_BAR_H - iconSize) / 2, iconSize, iconSize, null);
			}
			if (hoveredRestorePrayer > 0)
			{
				BarRenderer.drawRestoreH(g, cx, cy, cw, MAIN_BAR_H,
					pFrac, (double) hoveredRestorePrayer / maxPray, RESTORE_PRAY_COLOR);
			}
			drawBarText(g, cx, cy, cw, MAIN_BAR_H, curPray + " / " + maxPray);
			cy += MAIN_BAR_H + BAR_GAP;
		}

		// Run energy bar (thin)
		if (showStamina && runEnergy >= 0)
		{
			Color runCol = staminaActive ? STAMINA_COLOR : cfg.colorStamina();
			BarRenderer.drawBar(g, cx, cy, cw, THIN_BAR_H,
				clamp(runEnergy / 10000.0), runCol);
		}

		// ── 4. Spec square ────────────────────────────────────────────────────
		if (showSpec)
		{
			int barTopY = BORDER + PAD + NAME_H + BAR_GAP;
			int barBotY = frameH - BORDER - PAD;
			int sx      = mainW - BORDER - PAD - SPEC_W; // PAD breathing room on right
			drawSpecSquare(g, sx, barTopY, SPEC_W, barBotY - barTopY,
				specPct, specSweep, cfg, specIcon);
		}
	}

	// =========================================================================
	// Portrait
	// =========================================================================

	private static void drawPortrait(Graphics2D g, int portX, int portY,
		String name, BufferedImage portrait, Color bgColor)
	{
		int ix = portX + BORDER;
		int iy = portY + BORDER;
		int iw = PORTRAIT_W - BORDER * 2;
		int ih = PORTRAIT_H - BORDER * 2;

		if (portrait != null && portrait.getWidth() > 0 && portrait.getHeight() > 0)
		{
			g.drawImage(portrait, ix, iy, iw, ih, null);
		}
		else
		{
			if (bgColor != null)
			{
				g.setColor(bgColor);
				g.fillRect(ix, iy, iw, ih);
			}
			if (name != null && !name.isEmpty())
			{
				g.setFont(FONT_PORT);
				FontMetrics fm = g.getFontMetrics();
				String ch = name.substring(0, 1).toUpperCase();
				int tx = ix + (iw - fm.stringWidth(ch)) / 2;
				int ty = iy + (ih - fm.getHeight()) / 2 + fm.getAscent();
				TextRenderer.shadow(g, ch, FONT_PORT, tx, ty, TEXT_PORT_LETTER);
			}
		}
	}

	// =========================================================================
	// Spec square
	// =========================================================================

	private static void drawSpecSquare(Graphics2D g,
		int x, int y, int w, int h,
		int specPct, double specSweep,
		MmoFramesConfig cfg, BufferedImage specIcon)
	{
		boolean full  = (specPct == 100);
		Color   sCol  = full ? new Color(31, 224, 192, 255) : cfg.colorSpec();
		Color   textC = full ? TEXT_SPEC_FULL : TEXT_SPEC;

		BorderRenderer.drawFrame(g, x, y, w, h);

		int ix = x + BORDER;
		int iy = y + BORDER;
		int iw = w - BORDER * 2;
		int ih = h - BORDER * 2;

		BarRenderer.drawBarVertical(g, ix, iy, iw, ih, clamp(specPct / 100.0), sCol);

		if (cfg.showSpecRegenSweep() && specSweep > 0 && !full)
		{
			BarRenderer.drawSweepV(g, ix, iy, iw, ih, specSweep, sCol, true);
		}

		String pctStr = specPct + "%";

		if (specIcon != null)
		{
			// ── StatusEffect-style layout: icon centred, text overlapping icon bottom ──
			int iconSize  = 20;
			int iconX     = ix + (iw - iconSize) / 2;
			int iconY     = iy + (ih - iconSize) / 2;
			g.drawImage(specIcon, iconX, iconY, iconSize, iconSize, null);

			int textTopY  = iconY + iconSize - 6; // TEXT_OVERLAP = 6
			int textAreaH = (iy + ih) - textTopY;
			Font font = scaledFont(g, textAreaH, iw, pctStr);
			FontMetrics fm = g.getFontMetrics(font);
			int tx = ix + (iw - fm.stringWidth(pctStr)) / 2;
			int ty = textTopY + fm.getAscent();
			TextRenderer.shadow(g, pctStr, font, tx, ty, textC);
		}
		else
		{
			// Fallback: percentage text vertically centred
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

	private static Color hpColor(double frac, MmoFramesConfig cfg)
	{
		if (frac <= 0.25) return cfg.colorHpLow();
		if (frac <= 0.50) return cfg.colorHpMid();
		return cfg.colorHpHigh();
	}
}
