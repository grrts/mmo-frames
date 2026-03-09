package com.mmoframes.frame.domain;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class Bar
{
	BarType type;
	int current;
	int max;
	double sweepProgress;
	boolean sweepLighten;
	int hoverRestore;
	Color color;
	Color hoverRestoreColor;
	BufferedImage icon;
	int poisonState;
}
