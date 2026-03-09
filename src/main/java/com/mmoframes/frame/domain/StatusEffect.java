package com.mmoframes.frame.domain;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class StatusEffect
{
	StatusEffectType type;
	boolean active;
	StatusEffectCategory category;
	String displayValue;
	String label;
	Color color;
	BufferedImage icon;
}
