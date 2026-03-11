package com.mmoframes.frame.domain;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Data;

@Data
public class Bar
{
	private BarType type;
	private int current;
	private int max;
	private double sweepProgress;
	private boolean sweepLighten;
	private int hoverRestore;
	private Color color;
	private Color hoverRestoreColor;
	private BufferedImage icon;
	private HitpointsBarType hitpointsBarType = HitpointsBarType.DEFAULT;
}
