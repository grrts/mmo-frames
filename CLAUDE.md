# CLAUDE.md

## Project Overview

**MMO Frames** is a RuneLite external plugin for Old School RuneScape (OSRS) that renders WoW-style unit frames (player and target) as overlays, styled to match the native OSRS interface.

Java 11 (`options.release.set(11)` in `build.gradle`). Uses Lombok for `@Getter`/`@Slf4j`.

## Build Commands

**NOTE: You are NOT allowed to use the `run` command (`./gradlew run`).**

```bash
./gradlew build          # Full build
./gradlew compileJava    # Compile only (fast check)
./gradlew shadowJar      # Fat JAR → build/libs/mmo-frames-1.0-SNAPSHOT-all.jar
```

There are no automated unit tests — `MmoFramesPluginTest` is the developer launch harness, not a test suite.

## Research: GitHub First

When you need to understand a RuneLite API, **search the GitHub repos first** before attempting to open JAR files or guess at APIs.

- RuneLite core: https://github.com/runelite/runelite
- Status bars plugin (HP/prayer hover reference): https://github.com/runelite/runelite/tree/master/runelite-client/src/main/java/net/runelite/client/plugins/statusbars
- Custom vital bars (regen/drain formulas): https://github.com/qt31415926535-femboy/custom-vital-bars/blob/main/src/main/java/com/neur0tox1n_/customvitalbars/CustomVitalBarsPlugin.java

## Architecture

### Domain Structure (DDD)

The codebase is organised around **bounded contexts** with clear separation of concerns:

```
com.mmoframes/
├── MmoFramesPlugin.java          # Plugin entry point — lifecycle & event routing only
├── MmoFramesConfig.java          # Configuration interface (4 sections)
│
├── PlayerService.java            # Domain: player state, timers, status effects, consumables
├── TargetService.java            # Domain: target tracking, portraits, linger, NPC lookup
├── NpcTrackingService.java       # Domain: actor poison/venom observation via hitsplats
├── ChatHeadService.java          # Domain: 3D player head portrait widget
├── PrayerDrainRates.java         # Domain: prayer drain lookup table
│
├── PlayerFrameOverlay.java       # Presentation: thin shell — reads PlayerService, renders
├── TargetFrameOverlay.java       # Presentation: thin shell — reads TargetService, renders
├── UnitFrameRenderer.java        # Presentation: assembles portrait + bars + spec square
├── StatusFrameRenderer.java      # Presentation: renders buff/debuff status rows
│
├── rendering/                    # Presentation primitives
│   ├── BarRenderer.java          #   HP/prayer/spec/stamina bar drawing + sweep animations
│   ├── BorderRenderer.java       #   OSRS stone-panel border rendering
│   └── TextRenderer.java         #   Shadow text rendering
│
└── status/                       # Domain: status effect model
    ├── StatusEffect.java          #   Abstract base — getType(), isActive(), getIcon(), etc.
    ├── PlayerPoisonEffect.java    #   Player poison/venom (VarPlayerID.POISON)
    ├── AntipoisonImmunityEffect.java  # Antipoison/anti-venom immunity
    ├── SkillBoostEffect.java      #   Combat skill boost/drain display
    ├── ActivePrayerEffect.java    #   Active prayer indicator
    ├── VarbitTimerEffect.java     #   Generic varbit-backed timer (potions, spells)
    ├── StaminaEffect.java         #   Stamina potion buff
    ├── TargetPrayerEffect.java    #   Target's active prayers
    ├── NpcStatusEffect.java       #   NPC poison/venom from hitsplats
    └── SlayerTaskEffect.java      #   Active slayer task display
```

### Layer Responsibilities

| Layer | Classes | Responsibility |
|-------|---------|----------------|
| **Entry point** | `MmoFramesPlugin` | Lifecycle, event subscription, routing events to services |
| **Domain services** | `*Service.java` | All game state, business logic, tick calculations, effect management |
| **Domain model** | `status/*` | Status effect abstractions and implementations |
| **Presentation** | `*Overlay.java`, `*Renderer.java`, `rendering/*` | Layout, drawing, composition — no business logic |

### Key Principle: Overlays are Thin Shells

Overlays (`PlayerFrameOverlay`, `TargetFrameOverlay`) must contain **zero business logic**. They:
- Read data from their respective service
- Pass it to renderers
- Return dimensions

All state, calculations, effect management, and data preparation belong in services.

## Code Standards

### Domain-Driven Design (DDD)

- **Services own domain state.** Game state (HP, prayer, poison, timers, skill boosts) lives in domain services, never in overlays or renderers.
- **Status effects are domain objects.** Each effect type extends `StatusEffect` and encapsulates its own activation logic, display value, and icon loading.
- **Bounded contexts.** Player state vs target state vs NPC tracking are separate services with their own lifecycle. Don't leak concerns across boundaries.
- **Ubiquitous language.** Use OSRS game terminology: "boosted level", "drain effect", "varp/varbit", "hitsplat", "combat level", "prayer bonus".

### SOLID Principles

- **Single Responsibility:** Each class has one reason to change. Services manage state. Overlays compose layout. Renderers draw primitives. Effects encapsulate activation logic.
- **Open/Closed:** New status effects are added by creating a new `StatusEffect` subclass and registering it in the appropriate service's `getBuffs()`/`getDebuffs()` — no modification to overlays or renderers needed.
- **Liskov Substitution:** All `StatusEffect` subclasses are interchangeable — renderers work with the abstract type only.
- **Interface Segregation:** `MmoFramesConfig` exposes focused config groups. Services expose only the getters overlays need.
- **Dependency Inversion:** Overlays depend on service abstractions (getters), not on how state is computed. Optional dependencies use `@com.google.inject.Inject(optional = true)`.

### General Practices

- **No logic in overlays.** If you're writing an `if` that checks game state in an overlay, it belongs in a service.
- **Lazy initialisation for icons.** Sprite loading uses null-check patterns since `SpriteManager` may not have sprites ready immediately.
- **Tick-based timers.** HP regen = 100 ticks, spec regen = 33 ticks, prayer drain = observation-based reset on detected drain tick.
- **Optional plugin dependencies.** Use `@PluginDependency` + `@com.google.inject.Inject(optional = true)` for cross-plugin features (e.g., `ItemStatChangesService`). Note: `javax.inject.Inject` has no `optional` param — use the fully-qualified Guice form.
- **WeakHashMap for actor tracking.** NPC status effects use `WeakHashMap<Actor, ...>` for automatic cleanup when actors are garbage collected.
