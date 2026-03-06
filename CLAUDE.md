# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**MMO Frames** is a RuneLite external plugin for Old School RuneScape (OSRS) that renders WoW-style unit frames (player and target) as overlays, styled to match the native OSRS interface.


## Build & Run Commands\
NOTE: YOU ARE NOT TO ALLOW TO USE THE 'RUN' command

```bash
# Build the plugin
./gradlew build

# Run in RuneLite developer mode (launches full OSRS client with the plugin loaded)
./gradlew run

# Build a fat JAR for distribution
./gradlew shadowJar
# Output: build/libs/mmo-frames-1.0-SNAPSHOT-all.jar

# Compile only (fast check)
./gradlew compileJava
```

The `run` task launches `MmoFramesPluginTest.main()`, which calls `ExternalPluginManager.loadBuiltin(MmoFramesPlugin.class)` then `RuneLite.main(args)` with `--developer-mode --debug`.

There are no automated unit tests — `MmoFramesPluginTest` is the developer launch harness, not a test suite.

## Architecture

### Plugin entry point: `MmoFramesPlugin`
Extends `net.runelite.client.plugins.Plugin`. Registered via `@PluginDescriptor`. Responsible for:
- Registering/unregistering both overlays with `OverlayManager`
- Tracking tick-based timers each `@Subscribe onGameTick`: HP regen (100-tick cycle), prayer drain (variable cycle based on active prayers + equipment bonus), spec regen (33-tick cycle per 10%)
- Exposing `@Getter` progress values (`hpRegenProgress`, `prayerDrainProgress`, `specRegenProgress`) as `double` 0.0–1.0 for overlays to read

### Resources:
If you need to know more about an API try and search the githubs first, before trying to open jar files.

You can use github extensively to gather information
- Runelite for all runelite related apis: https://github.com/runelite/runelite/blob/master/README.md
- Status bars for checking how much hp / prayer should be shown when hovering an item: https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/statusbars
- Custom vital bars for the correct calculations for regeneration / drainage for hitpoints and paryer: https://github.com/qt31415926535-femboy/custom-vital-bars/blob/main/src/main/java/com/neur0tox1n_/customvitalbars/CustomVitalBarsPlugin.java

### Config: `MmoFramesConfig`
RuneLite `@ConfigGroup("mmoframes")` interface. Four sections: Player Frame, Target Frame, Tick Timers, Colors. Injected into both overlays and the renderer.

## Java Version

Target: Java 11 (`options.release.set(11)` in `build.gradle`). Uses Lombok for `@Getter`/`@Slf4j`.
