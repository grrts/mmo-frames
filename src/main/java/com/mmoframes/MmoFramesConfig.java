package com.mmoframes;

import java.awt.Color;
import net.runelite.client.config.Alpha;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup("mmoframes")
public interface MmoFramesConfig extends Config
{
	// ── Sections ──────────────────────────────────────────────────────────────

	@ConfigSection(name = "Player Frame",  description = "Player unit frame",            position = 0)
	String playerSection = "playerFrame";

	@ConfigSection(name = "Target Frame",  description = "Target unit frame",            position = 1)
	String targetSection = "targetFrame";

	@ConfigSection(name = "Tick Timers",   description = "Tick sweep animation options", position = 2)
	String tickSection   = "tickTimers";

	@ConfigSection(name = "Colors",        description = "Bar colour overrides",         position = 3)
	String colorSection  = "colors";

	// ── Player Frame ──────────────────────────────────────────────────────────

	@ConfigItem(keyName = "showPlayerFrame",   name = "Show Player Frame",   description = "Show the player unit frame",                    section = playerSection, position = 0)
	default boolean showPlayerFrame()    { return true; }

	@ConfigItem(keyName = "playerFrameWidth",  name = "Frame Width",         description = "Width of the main frame body (px)",             section = playerSection, position = 1)
	@Range(min = 240, max = 420)
	default int playerFrameWidth()       { return 300; }

	@ConfigItem(keyName = "showPlayerName",    name = "Show Name",           description = "Display player name",                          section = playerSection, position = 2)
	default boolean showPlayerName()     { return true; }

	@ConfigItem(keyName = "showPlayerHpText",  name = "Show HP Text",        description = "Show current / max HP on bar",                 section = playerSection, position = 3)
	default boolean showPlayerHpText()   { return true; }

	@ConfigItem(keyName = "showPlayerPrayer",  name = "Show Prayer Bar",     description = "Show prayer points bar",                       section = playerSection, position = 4)
	default boolean showPlayerPrayer()   { return true; }

	@ConfigItem(keyName = "showPlayerStamina", name = "Show Stamina Bar",    description = "Show run energy bar",                          section = playerSection, position = 5)
	default boolean showPlayerStamina()  { return true; }

	@ConfigItem(keyName = "showSpecialAttack", name = "Show Spec Square",    description = "Show special attack square to the right of the frame", section = playerSection, position = 6)
	default boolean showSpecialAttack()  { return true; }

	@ConfigItem(keyName = "showSkillBoosts",   name = "Show Skill Boosts",   description = "Show combat skill boost panel below the player frame", section = playerSection, position = 7)
	default boolean showSkillBoosts()    { return true; }

	// ── Target Frame ──────────────────────────────────────────────────────────

	@ConfigItem(keyName = "showTargetFrame",   name = "Show Target Frame",   description = "Show the target unit frame",                   section = targetSection, position = 0)
	default boolean showTargetFrame()    { return true; }

	@ConfigItem(keyName = "targetFrameWidth",  name = "Frame Width",         description = "Width of the target frame (px)",               section = targetSection, position = 1)
	@Range(min = 240, max = 420)
	default int targetFrameWidth()       { return 300; }

	@ConfigItem(keyName = "showTargetName",    name = "Show Name",           description = "Display target name",                          section = targetSection, position = 2)
	default boolean showTargetName()     { return true; }

	@ConfigItem(keyName = "showTargetHpText",  name = "Show HP %",           description = "Show HP percentage on bar",                    section = targetSection, position = 3)
	default boolean showTargetHpText()   { return true; }

	@ConfigItem(keyName = "showTargetLevel",   name = "Show Level",          description = "Show target combat level",                     section = targetSection, position = 4)
	default boolean showTargetLevel()    { return true; }

	@ConfigItem(keyName = "targetLingerSeconds", name = "Target Linger (s)", description = "Seconds to keep target frame visible after losing target (0 = hide immediately)", section = targetSection, position = 5)
	@Range(min = 0, max = 30)
	default int targetLingerSeconds()    { return 5; }

	// ── Tick Timers ───────────────────────────────────────────────────────────

	@ConfigItem(keyName = "showHpRegenSweep",     name = "HP Regen Sweep",     description = "Lighten sweep on HP bar per regen tick",      section = tickSection, position = 0)
	default boolean showHpRegenSweep()     { return true; }

	@ConfigItem(keyName = "showPrayerDrainSweep", name = "Prayer Drain Sweep", description = "Darken sweep on prayer bar per drain tick",    section = tickSection, position = 1)
	default boolean showPrayerDrainSweep() { return true; }

	@ConfigItem(keyName = "showSpecRegenSweep",   name = "Spec Regen Sweep",   description = "Lighten sweep on spec bar per regen tick",     section = tickSection, position = 2)
	default boolean showSpecRegenSweep()   { return true; }

	// ── Colors ────────────────────────────────────────────────────────────────

	@Alpha
	@ConfigItem(keyName = "colorHpHigh",  name = "HP High",       description = "HP bar colour > 50%",          section = colorSection, position = 0)
	default Color colorHpHigh()  { return new Color(2, 182, 10, 255); }

	@Alpha
	@ConfigItem(keyName = "colorHpMid",   name = "HP Mid",        description = "HP bar colour 25–50%",         section = colorSection, position = 1)
	default Color colorHpMid()   { return new Color(215, 145,   0, 255); }

	@Alpha
	@ConfigItem(keyName = "colorHpLow",   name = "HP Low",        description = "HP bar colour < 25%",          section = colorSection, position = 2)
	default Color colorHpLow()   { return new Color(200,  25,  25, 255); }

	@Alpha
	@ConfigItem(keyName = "colorPrayer",  name = "Prayer",        description = "Prayer bar colour",            section = colorSection, position = 3)
	default Color colorPrayer()  { return new Color(31, 224, 192, 255); }

	@Alpha
	@ConfigItem(keyName = "colorStamina", name = "Stamina / Run", description = "Run energy bar colour",        section = colorSection, position = 4)
	default Color colorStamina() { return new Color(215, 185,  55, 255); }

	@Alpha
	@ConfigItem(keyName = "colorSpec",    name = "Spec",          description = "Special attack bar / square colour (becomes gold at 100%)", section = colorSection, position = 5)
	default Color colorSpec()    { return new Color(0, 135, 244, 255); }
}
