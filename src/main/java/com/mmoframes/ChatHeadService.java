package com.mmoframes;

import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.ui.DrawManager;

/**
 * Pipeline:
 *   1. createWidget()   — LOCAL_PLAYER_CHATHEAD MODEL widget, starts off-screen.
 *   2. onClientTick()   — positions widget at the portrait inner area (client thread,
 *                         safe to call revalidate()), then schedules a DrawManager capture.
 *   3. DrawManager fires after the game renders (widgets included, overlays not yet)
 *                       — crops the 48×48 portrait region into a BufferedImage.
 *   4. getImage()       — overlay reads the BufferedImage and draws it as a normal portrait.
 */
@Slf4j
@Singleton
public class ChatHeadService
{
	/** Inner portrait cell size in pixels (PORTRAIT_W - 2×BORDER = 60 - 12 = 48). */
	static final int SIZE = 48;

	private static final int MODEL_ZOOM = 600;
	private static final int CHATHEAD_IDLE_ANIM = 588;

	/** Interior fill colour matching {@link com.mmoframes.rendering.BorderRenderer#INTERIOR}. */
	private static final int BG_COLOR_RGB = 0x25201C; // rgb(37, 32, 28)

	@Inject private Client       client;
	@Inject private ClientThread clientThread;
	@Inject private DrawManager  drawManager;

	// The MODEL widget the game renders every frame
	private Widget widget;
	private Widget widgetBg;     // solid background behind the chathead model
	private Widget widgetParent;

	// Captured portrait image — written on the render thread, read by the overlay
	@Getter private volatile BufferedImage image;

	// Screen-absolute portrait position, written by render thread, consumed by onClientTick
	private volatile int     pendingX   = -SIZE * 2;
	private volatile int     pendingY   = -SIZE * 2;
	private volatile boolean pendingSet = false;

	// Screen position at which the last DrawManager capture was scheduled
	private int captureX = -1;
	private int captureY = -1;

	// Prevent queuing more than one DrawManager request at a time
	private boolean capturePending = false;

	// -------------------------------------------------------------------------

	public void startUp()
	{
		clientThread.invokeLater(this::createWidget);
	}

	public void shutDown()
	{
		clientThread.invokeLater(this::destroyWidget);
	}

	/** Must be called on the client thread (e.g. from onGameStateChanged). */
	public void resetOnClientThread()
	{
		destroyWidget();
		createWidget();
	}

	/**
	 * Thread-safe — stores the desired screen position of the portrait inner area.
	 * Called from the render thread each frame before the portrait is drawn.
	 */
	public void requestPosition(int screenX, int screenY)
	{
		pendingX  = screenX;
		pendingY  = screenY;
		pendingSet = true;
	}

	/**
	 * Called by the plugin's onClientTick subscriber — runs on the client thread.
	 *
	 * Step 1: retry widget creation if it was missing.
	 * Step 2: apply pending position from render thread (revalidate is safe here).
	 * Step 3: schedule one DrawManager capture for this render frame.
	 */
	public void onClientTick()
	{
		// Step 1 — recreate widget if missing
		if (widget == null)
		{
			createWidget();
			return; // no position or capture until widget exists
		}

		// Step 2 — apply pending screen position to widget
		if (pendingSet)
		{
			pendingSet = false;

			// setOriginalX/Y is parent-relative, subtract the parent's canvas origin
			int offsetX = 0;
			int offsetY = 0;
			if (widgetParent != null)
			{
				Point loc = widgetParent.getCanvasLocation();
				if (loc != null)
				{
					offsetX = loc.getX();
					offsetY = loc.getY();
				}
			}

			int relX = pendingX - offsetX;
			int relY = pendingY - offsetY;

			// Position background fill at the same location
			if (widgetBg != null)
			{
				widgetBg.setOriginalX(relX);
				widgetBg.setOriginalY(relY);
				widgetBg.setOriginalWidth(SIZE);
				widgetBg.setOriginalHeight(SIZE);
				widgetBg.revalidate();
			}

			widget.setOriginalX(relX);
			widget.setOriginalY(relY);
			widget.setOriginalWidth(SIZE);
			widget.setOriginalHeight(SIZE);
			widget.revalidate();

			captureX = pendingX;
			captureY = pendingY;
		}

		// Step 3 — schedule a DrawManager capture for this frame's render output.
		// DrawManager fires AFTER the game renders (widgets included) and BEFORE
		// RuneLite overlays paint — so the widget's pixels are in the image.
		if (!capturePending && captureX >= 0 && captureY >= 0)
		{
			capturePending = true;
			final int sx = captureX;
			final int sy = captureY;
			drawManager.requestNextFrameListener(img -> captureFrame(img, sx, sy));
		}
	}

	// -------------------------------------------------------------------------

	/**
	 * DrawManager callback — fires on the render thread after the game frame
	 * is fully drawn (widgets included) and before RuneLite overlays render.
	 *
	 * Handles potential resolution differences between canvas coordinates (used
	 * for widget positioning / overlay transforms) and the DrawManager frame
	 * buffer (which may differ with GPU plugin or stretched mode).
	 */
	private void captureFrame(Image img, int sx, int sy)
	{
		capturePending = false;

		if (img == null)
		{
			return;
		}

		int imgW = img.getWidth(null);
		int imgH = img.getHeight(null);
		if (imgW <= 0 || imgH <= 0)
		{
			return;
		}

		// Scale capture coordinates if frame buffer resolution differs from canvas
		int canvasW = client.getCanvasWidth();
		int canvasH = client.getCanvasHeight();
		double scaleX = canvasW > 0 ? (double) imgW / canvasW : 1.0;
		double scaleY = canvasH > 0 ? (double) imgH / canvasH : 1.0;

		int adjSx = (int) Math.round(sx * scaleX);
		int adjSy = (int) Math.round(sy * scaleY);
		int adjW  = (int) Math.round(SIZE * scaleX);
		int adjH  = (int) Math.round(SIZE * scaleY);

		// Bounds check — skip if capture region falls outside the image
		if (adjSx < 0 || adjSy < 0 || adjSx + adjW > imgW || adjSy + adjH > imgH)
		{
			return; // keep previous captured image
		}

		BufferedImage copy = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = copy.createGraphics();
		try
		{
			// drawImage(img, dx1,dy1,dx2,dy2, sx1,sy1,sx2,sy2, observer)
			// Copies the source rectangle from img into the full destination BufferedImage.
			// Works with any Image subtype — no cast to BufferedImage needed.
			g.drawImage(img,
				0, 0, SIZE, SIZE,
				adjSx, adjSy, adjSx + adjW, adjSy + adjH,
				null);
		}
		finally
		{
			g.dispose();
		}

		image = copy;
	}

	// -------------------------------------------------------------------------

	private void createWidget()
	{
		if (widget != null)
		{
			return;
		}

		Widget parent = null;
		for (int gid : new int[]{548, 161, 164})
		{
			Widget w = client.getWidget(gid, 0);
			if (w != null)
			{
				parent = w;
				break;
			}
		}

		if (parent == null)
		{
			log.debug("ChatHeadService: no viewport parent found — will retry next tick");
			return;
		}

		widgetParent = parent;

		// Background fill — renders behind the chathead model so the captured region
		// shows the head on a dark background instead of game scene bleed-through.
		widgetBg = parent.createChild(-1, WidgetType.GRAPHIC);
		widgetBg.setFilled(true);
		widgetBg.setOpacity(255);
		widgetBg.setTextColor(BG_COLOR_RGB);
		widgetBg.setOriginalX(-SIZE * 2);
		widgetBg.setOriginalY(-SIZE * 2);
		widgetBg.setOriginalWidth(SIZE);
		widgetBg.setOriginalHeight(SIZE);
		widgetBg.revalidate();

		// Chathead model widget — created after background so it renders on top.
		widget = parent.createChild(-1, WidgetType.MODEL);
		widget.setModelType(WidgetModelType.LOCAL_PLAYER_CHATHEAD);
		widget.setModelId(0);
		widget.setAnimationId(CHATHEAD_IDLE_ANIM);
		widget.setModelZoom(MODEL_ZOOM);
		widget.setRotationX(0);
		widget.setRotationY(0);
		widget.setRotationZ(0);
		// Start off-screen — onClientTick moves it to the portrait area on the next tick
		widget.setOriginalX(-SIZE * 2);
		widget.setOriginalY(-SIZE * 2);
		widget.setOriginalWidth(SIZE);
		widget.setOriginalHeight(SIZE);
		widget.revalidate();

		log.debug("ChatHeadService: widget created under parent gid={}", parent.getId());
	}

	private void destroyWidget()
	{
		if (widgetBg != null)
		{
			widgetBg.setHidden(true);
			widgetBg.revalidate();
			widgetBg = null;
		}
		if (widget != null)
		{
			widget.setHidden(true);
			widget.revalidate();
			widget = null;
		}
		widgetParent   = null;
		image          = null;
		capturePending = false;
		pendingSet     = false;
		captureX       = -1;
		captureY       = -1;
	}
}
