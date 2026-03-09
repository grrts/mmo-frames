package com.mmoframes;

import com.mmoframes.status.StatusEffect;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Target unit frame overlay — thin composition shell.
 *
 * Composes: buffs | frame | debuffs
 * All data sourced from {@link TargetService}.
 */
public class TargetFrameOverlay extends Overlay
{
	private final Client          client;
	private final MmoFramesConfig config;
	private final TargetService   targetService;

	@Inject
	public TargetFrameOverlay(Client client, MmoFramesConfig config,
		TargetService targetService)
	{
		this.client        = client;
		this.config        = config;
		this.targetService = targetService;

		setPosition(OverlayPosition.BOTTOM_RIGHT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
		setMovable(true);
		setResizable(false);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showTargetFrame() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Actor target = targetService.getLingerTarget();
		if (target == null)
		{
			return null;
		}

		// ── Update HP tracking ───────────────────────────────────────────────
		targetService.updateTargetHealth(target);

		int dispCur = targetService.getDisplayHp();
		int dispMax = targetService.getDisplayMaxHp();

		String name  = targetService.getTargetName();
		int    level = targetService.getTargetLevel();
		int    mainW = config.targetFrameWidth();
		int    frameH = UnitFrameRenderer.calcFrameHeight(false, false);

		// ── BUFFS (above) ────────────────────────────────────────────────────
		List<StatusEffect> buffs   = targetService.getBuffs();
		List<StatusEffect> debuffs = targetService.getDebuffs();

		int aboveH = StatusFrameRenderer.calcHeight(buffs);
		if (aboveH > 0)
		{
			StatusFrameRenderer.renderStatusEffects(g, buffs, 0, -aboveH);
		}

		// ── FRAME ────────────────────────────────────────────────────────────
		UnitFrameRenderer.renderFrame(
			g,
			mainW,
			name,
			level,
			dispCur, dispMax,
			-1, 0,     // no prayer
			-1,        // no stamina
			-1,        // no spec
			0.0, 0.0, 0.0,
			config,
			false,     // no prayer bar
			false,     // no stamina bar
			false,     // no stamina active
			targetService.getPortrait(),
			targetService.getPortraitBgColor(),
			0,         // no poison indicator for targets
			0,         // no heal hover for targets
			0,         // no prayer hover for targets
			targetService.getHpIcon(),
			null,      // no prayer bar on targets
			null       // no spec on targets
		);

		// ── DEBUFFS (below) ──────────────────────────────────────────────────
		int belowH = StatusFrameRenderer.renderStatusEffects(
			g, debuffs, 0, frameH);

		return new Dimension(mainW, frameH + belowH);
	}
}
