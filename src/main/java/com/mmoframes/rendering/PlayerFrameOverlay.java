package com.mmoframes.rendering;

import com.mmoframes.MmoFramesConfig;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.FrameType;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import com.mmoframes.frame.infrastructure.FrameStore;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Player unit frame overlay — thin composition shell.
 *
 * Composes: buffs | frame | debuffs
 * All data sourced from {@link FrameStore}.
 */
public class PlayerFrameOverlay extends Overlay
{
	private final Client          client;
	private final MmoFramesConfig config;
	private final FrameStore      frameStore;
	private final ChatHeadAdapter chatHeadAdapter;

	@Inject
	public PlayerFrameOverlay(Client client, MmoFramesConfig config,
		FrameStore frameStore, ChatHeadAdapter chatHeadAdapter)
	{
		this.client          = client;
		this.config          = config;
		this.frameStore      = frameStore;
		this.chatHeadAdapter = chatHeadAdapter;

		setPosition(OverlayPosition.BOTTOM_LEFT);
		setLayer(OverlayLayer.ABOVE_SCENE);
		setPriority(OverlayPriority.LOW);
		setMovable(true);
		setResizable(false);
		setSnappable(true);
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		if (!config.showPlayerFrame() || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}

		Frame frame = frameStore.get(FrameType.PLAYER);
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

		// ── Position chat-head widget ────────────────────────────────────────
		AffineTransform tx = g.getTransform();
		int overlayX = (int) Math.round(tx.getTranslateX());
		int overlayY = (int) Math.round(tx.getTranslateY());
		int innerOff = UnitFrameRenderer.BORDER * 2 + UnitFrameRenderer.PAD;
		chatHeadAdapter.requestPosition(overlayX + innerOff, overlayY + innerOff + 4);

		// ── FRAME ────────────────────────────────────────────────────────────
		UnitFrameRenderer.renderFrame(g, frame);

		// ── DEBUFFS (below) ──────────────────────────────────────────────────
		List<StatusEffect> debuffs = frame.getDebuffs();
		int belowH = StatusFrameRenderer.renderStatusEffects(g, debuffs, 0, frameH);

		return new Dimension(frameW, frameH + belowH);
	}
}
