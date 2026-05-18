# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Set `JAVA_HOME` to a Java 17 install before each `./gradlew` invocation:
- Windows: `C:/Users/<username>/.jdks/azul-17.0.18` (managed by IntelliJ)
- macOS:   `/Users/<username>/Library/Java/JavaVirtualMachines/azul-17.0.19/Contents/Home`

```bash
# Setup workspace (required first time, or after clean)
JAVA_HOME="..." ./gradlew setupDecompWorkspace

# Build the mod
JAVA_HOME="..." ./gradlew build

# Run Minecraft client in dev (x86_64 / Windows)
JAVA_HOME="..." ./gradlew runClient

# Run Minecraft client in dev (Apple Silicon Mac — uses lwjgl3ify)
JAVA_HOME="..." ./gradlew runClient17

# Run Minecraft server in dev
JAVA_HOME="..." ./gradlew runServer

# Clean build artifacts
JAVA_HOME="..." ./gradlew clean

# Run tests (JUnit 5)
JAVA_HOME="..." ./gradlew test
```

**Requirements:** Java 17 (Azul Zulu Community). The project uses Jabel to allow modern Java syntax while targeting JVM 8. Heap is set to `-Xmx3G` in `gradle.properties` for decompilation.

## Dependencies

Two non-Maven dependencies are pulled automatically by Gradle — no manual jar wrangling on a fresh checkout:

- **CSM (City Super Mod)** — resolved via the GitHub Releases Ivy repo in `repositories.gradle`. Pin lives in `gradle.properties` as `csmVersion`; bump it when SUM needs API surface from a newer CSM release. CI and fresh checkouts get the same binary with no `gh release download` glue.
- **OneConfig (Polyfrost)** — `oneconfig-1.12.2-forge` is `compileOnly` (API surface only); the actual runtime is bootstrapped by `oneconfig-wrapper-launchwrapper` (`devOnlyNonPublishable`) whose `LaunchWrapperTweaker` downloads the real OneConfig binary into `run/client/OneConfig/Loader/` on first launch. The tweaker name is injected into the dev launch args via `addon.gradle`'s `minecraft.extraTweakClasses`. **Do not put `oneconfig-1.12.2-forge` on the dev runtime classpath** — its manifest references a tweaker class that's not in the jar, and GradleStart's coremod scan will crash on it before OneConfig ever loads.

## Architecture Overview

This is a **Minecraft 1.12.2 Forge mod** (mod ID: `sum`) providing server utility features for the Alto Minecraft server. The build system is GregTechCEu Buildscripts (RetroFuturaGradle wrapper).

### Source Layout

```
src/main/java/com/micatechnologies/minecraft/sum/
├── Sum.java              # Main @Mod class; preInit/init/postInit lifecycle
├── SumConstants.java     # Mod ID, name, version constants
├── SumConfig.java        # Forge Configuration system (all config categories)
├── SumRegistry.java      # Block/item registration
├── SumTab.java           # Creative inventory tab
├── SumProxy.java         # Proxy interface
├── SumCommonProxy.java   # Server-side proxy
├── SumClientProxy.java   # Client-side proxy
├── roamer/               # Roamer NPC entity subsystem
│   ├── EntityRoamer.java
│   ├── EntityAIRoamerWander.java
│   ├── ItemRoamerSpawnEgg.java
│   ├── RenderRoamer.java
│   ├── RoamerWalkableBlocksNavigator.java
│   └── RoamerWalkableBlocksPathNodeProcessor.java
└── roadrunner/           # Speed boost on configured blocks
    └── RoadRunnerHandler.java

src/main/resources/
├── mcmod.info
├── pack.mcmeta
└── assets/sum/
    ├── models/item/
    ├── textures/entity/
    ├── textures/items/
    └── lang/en_us.lang
```

### Registration Flow

1. **`Sum.java`** -- Main `@Mod` class; handles `preInit`, `init`, `postInit` lifecycle events
2. **`SumConfig.java`** -- Loaded in `preInit` via `SumConfig.init(configFile)`
3. **`SumRegistry.java`** -- Block/item registration via static maps
4. **`SumTab.java`** -- Creative tab with `initTabElements()`
5. **`SumClientProxy` / `SumCommonProxy`** -- Client vs. server proxy pattern

### Configuration

All config is in `SumConfig.java` using Forge's `Configuration` class. Categories:
- **`roamer`** -- Walkable block list for Roamer NPCs (string array of registry names)
- **`roadrunner`** -- Speed boost block mappings (string array of `block=multiplier` entries)

### Key Patterns

- Feature code goes in its own subpackage (e.g. `roamer/`, `roadrunner/`)
- Event handlers are registered on `MinecraftForge.EVENT_BUS` in `Sum.preInit()`
- Config uses `"block=value"` string arrays for map-like data (Forge 1.12.2 limitation)
- Registry names use `sum:` prefix (e.g. `sum:roamer`, `sum:roamer_spawn_egg`)
- Package: `com.micatechnologies.minecraft.sum`

### Version

Version is derived from Git tags (format: `YYYY.MM.DD` for releases). No manual version setting needed.
