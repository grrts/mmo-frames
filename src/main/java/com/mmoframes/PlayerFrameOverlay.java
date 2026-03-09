package com.mmoframes;

import com.mmoframes.status.StatusEffect;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.util.List;
import javax.inject.Inject;

import net.runelite.api.*;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

/**
 * Player unit frame overlay — thin composition shell.
 *
 * Composes: buffs | frame | debuffs
 * All data sourced from {@link PlayerService}.
 */
public class PlayerFrameOverlay extends Overlay
{
	private final Client          client;
	private final MmoFramesConfig config;
	private final PlayerService   playerService;
	private final ChatHeadService chatHeadService;

	@Inject
	public PlayerFrameOverlay(Client client, MmoFramesConfig config,
		PlayerService playerService, ChatHeadService chatHeadService)
	{
		this.client          = client;
		this.config          = config;
		this.playerService   = playerService;
		this.chatHeadService = chatHeadService;

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

		// ── Read player stats ────────────────────────────────────────────────
		int hp      = client.getBoostedSkillLevel(Skill.HITPOINTS);
		int maxHp   = client.getRealSkillLevel(Skill.HITPOINTS);
		int pray    = client.getBoostedSkillLevel(Skill.PRAYER);
		int maxPray = client.getRealSkillLevel(Skill.PRAYER);
		int energy  = config.showPlayerStamina() ? client.getEnergy() : -1;
		int spec    = config.showSpecialAttack()
			? client.getVarpValue(VarPlayerID.SA_ENERGY) / 10
			: -1;

		String name  = client.getLocalPlayer() != null
			? client.getLocalPlayer().getName()
			: "Player";
		int    level = client.getLocalPlayer() != null
			? client.getLocalPlayer().getCombatLevel()
			: -1;

		boolean showPrayer  = config.showPlayerPrayer();
		boolean showStamina = config.showPlayerStamina();

		int mainW  = config.playerFrameWidth();
		int frameW = UnitFrameRenderer.calcFrameWidth(mainW, config.showSpecialAttack());
		int frameH = UnitFrameRenderer.calcFrameHeight(showPrayer, showStamina);

		// ── Consumable hover ─────────────────────────────────────────────────
		int healHp     = playerService.getHealHp();
		int healPrayer = playerService.getHealPrayer();

		// ── BUFFS (above) ────────────────────────────────────────────────────
		List<StatusEffect> buffs   = playerService.getBuffs();
		List<StatusEffect> debuffs = playerService.getDebuffs();

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
		chatHeadService.requestPosition(overlayX + innerOff, overlayY + innerOff + 4);

		// ── FRAME ────────────────────────────────────────────────────────────
		UnitFrameRenderer.renderFrame(
			g,
			mainW,
			name,
			level,
			hp,   maxHp,
			showPrayer ? pray : -1,  maxPray,
			energy,
			spec,
			playerService.getHpRegenProgress(),
			playerService.getPrayerDrainProgress(),
			playerService.getSpecRegenProgress(),
			config,
			showPrayer,
			showStamina,
			client.getVarbitValue(VarbitID.STAMINA_ACTIVE) != 0,
			null,
			null,
			playerService.getPoisonState(),
			healHp,
			healPrayer,
			playerService.getHpIcon(),
			playerService.getPrayerBarIcon(),
			playerService.getSpecBarIcon()
		);

		// ── DEBUFFS (below) ──────────────────────────────────────────────────
		int belowH = StatusFrameRenderer.renderStatusEffects(
			g, debuffs, 0, frameH);

		return new Dimension(frameW, frameH + belowH);
	}

}
