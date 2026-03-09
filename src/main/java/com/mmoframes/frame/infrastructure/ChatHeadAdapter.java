package com.mmoframes.frame.infrastructure;

import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModelType;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;

@Slf4j
@Singleton
public class ChatHeadAdapter
{
	static final int SIZE = 48;

	private static final int MODEL_ZOOM       = 1500;
	private static final int MODEL_ROTATION_Z = 1900;
	private static final int MODEL_ROTATION_X = 100;
	private static final int CHATHEAD_IDLE_ANIM = 588;

	@Inject private Client       client;
	@Inject private ClientThread clientThread;

	private Widget widget;

	private volatile int pendingX = 0;
	private volatile int pendingY = 0;

	public void startUp()
	{
		clientThread.invokeLater(this::createWidget);
	}

	public void shutDown()
	{
		clientThread.invokeLater(this::destroyWidget);
	}

	public void requestPosition(int screenX, int screenY)
	{
		pendingX = screenX;
		pendingY = screenY;
	}

	public void onClientTick()
	{
		if (widget == null)
		{
			createWidget();
			return;
		}

		widget.setOriginalX(pendingX);
		widget.setOriginalY(pendingY);
		widget.revalidate();
	}

	public void recreate()
	{
		if (widget != null)
		{
			widget.setHidden(true);
			widget.revalidate();
			widget = null;
		}

		createWidget();
	}

	private void createWidget()
	{
		if (widget != null)
		{
			return;
		}

		Widget parent = client.getWidget(164, 66);
		if (parent == null)
		{
			return;
		}

		widget = parent.createChild(-1, WidgetType.MODEL);
		widget.setType(WidgetType.MODEL);
		widget.setModelType(WidgetModelType.LOCAL_PLAYER_CHATHEAD);
		widget.setModelId(client.getLocalPlayer().getId());
		widget.setAnimationId(CHATHEAD_IDLE_ANIM);
		widget.setModelZoom(MODEL_ZOOM);
		widget.setRotationX(MODEL_ROTATION_X);
		widget.setRotationY(0);
		widget.setRotationZ(MODEL_ROTATION_Z);
		widget.setOriginalX(0);
		widget.setOriginalY(0);
		widget.setOriginalWidth(SIZE);
		widget.setOriginalHeight(SIZE);
		widget.setWidthMode(0);
		widget.setHeightMode(0);
		widget.setYPositionMode(0);
		widget.setXPositionMode(0);
		widget.revalidate();

		log.debug("ChatHeadAdapter: widget created under parent gid={}", parent.getId());
	}

	private void destroyWidget()
	{
		if (widget != null)
		{
			widget.setHidden(true);
			widget.revalidate();
			widget = null;
		}

		pendingX = 0;
		pendingY = 0;
	}
}
