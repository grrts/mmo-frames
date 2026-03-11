package com.mmoframes.rendering;

import com.mmoframes.MmoFramesConfig;
import com.mmoframes.frame.application.ConsumableHoverService;
import com.mmoframes.frame.application.PlayerFrameService;
import com.mmoframes.frame.domain.Bar;
import com.mmoframes.frame.domain.BarType;
import com.mmoframes.frame.domain.Frame;
import com.mmoframes.frame.domain.StatusEffect;
import com.mmoframes.frame.infrastructure.ChatHeadAdapter;
import java.awt.Color;
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
 * All data sourced from {@link PlayerFrameService}.
 */
public class PlayerFrameOverlay extends Overlay
{
	private static final Color RESTORE_HP_COLOR   = new Color(216, 255, 139, 130);
	private static final Color RESTORE_PRAY_COLOR = new Color(130, 180, 255, 130);

	private final Client                client;
	private final MmoFramesConfig       config;
	private final PlayerFrameService    playerFrameService;
	private final ChatHeadAdapter       chatHeadAdapter;
	private final ConsumableHoverService consumableHoverService;

	@Inject
	public PlayerFrameOverlay(Client client, MmoFramesConfig config,
		PlayerFrameService playerFrameService, ChatHeadAdapter chatHeadAdapter,
		ConsumableHoverService consumableHoverService)
	{
		this.client                = client;
		this.config                = config;
		this.playerFrameService    = playerFrameService;
		this.chatHeadAdapter       = chatHeadAdapter;
		this.consumableHoverService = consumableHoverService;

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

		Frame frame = playerFrameService.getPlayerFrame();
		if (frame == null)
		{
			return null;
		}

		applyConsumableHover(frame);

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

		// ── Clear hover state after render ───────────────────────────────────
		clearConsumableHover(frame);

		return new Dimension(frameW, frameH + belowH);
	}

	private void applyConsumableHover(Frame frame)
	{
		int healHp   = consumableHoverService.getHealHp();
		int healPray = consumableHoverService.getHealPrayer();

		if (healHp == 0 && healPray == 0)
		{
			return;
		}

		for (Bar bar : frame.getBars())
		{
			if (bar.getType() == BarType.HP && healHp > 0)
			{
				bar.setHoverRestore(healHp);
				bar.setHoverRestoreColor(RESTORE_HP_COLOR);
			}
			else if (bar.getType() == BarType.PRAYER && healPray > 0)
			{
				bar.setHoverRestore(healPray);
				bar.setHoverRestoreColor(RESTORE_PRAY_COLOR);
			}
		}
	}

	private void clearConsumableHover(Frame frame)
	{
		for (Bar bar : frame.getBars())
		{
			if (bar.getHoverRestore() != 0)
			{
				bar.setHoverRestore(0);
				bar.setHoverRestoreColor(null);
			}
		}
	}
}
