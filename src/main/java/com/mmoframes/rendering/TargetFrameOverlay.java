package com.mmoframes.rendering;

import com.mmoframes.MmoFramesConfig;
import com.mmoframes.frame.application.TargetFrameService;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.StatusEffect;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
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
 * All data prepared by {@link TargetFrameService#prepareForRender()}.
 */
public class TargetFrameOverlay extends Overlay
{
	private final Client             client;
	private final MmoFramesConfig    config;
	private final TargetFrameService targetFrameService;

	@Inject
	public TargetFrameOverlay(Client client, MmoFramesConfig config,
		TargetFrameService targetFrameService)
	{
		this.client             = client;
		this.config             = config;
		this.targetFrameService = targetFrameService;

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

		Frame frame = targetFrameService.prepareForRender();
		if (frame == null)
		{
			return null;
		}

		int frameW = UnitFrameRenderer.calcFrameWidth(frame);
		int frameH = UnitFrameRenderer.calcFrameHeight(frame);

		// ── BUFFS (above) ────────────────────────────────────────────────────
		List<StatusEffect> buffs = frame.getBuffs();
		int aboveH = StatusFrameRenderer.calcHeight(buffs);
		if (aboveH > 0)
		{
			StatusFrameRenderer.renderStatusEffects(g, buffs, 0, -aboveH);
		}

		// ── FRAME ────────────────────────────────────────────────────────────
		UnitFrameRenderer.renderFrame(g, frame);

		// ── DEBUFFS (below) ──────────────────────────────────────────────────
		List<StatusEffect> debuffs = frame.getDebuffs();
		int belowH = StatusFrameRenderer.renderStatusEffects(g, debuffs, 0, frameH);

		return new Dimension(frameW, frameH + belowH);
	}
}
