package com.mmoframes.frame.domain;

import java.awt.Color;
import java.awt.image.BufferedImage;
import lombok.Data;

@Data
public class StatusEffect
{
	private StatusEffectType type;
	private boolean active;
	private StatusEffectCategory category;
	private String displayValue;
	private String label;
	private Color color;
	private BufferedImage icon;
	private long expiresAtMs;
}
