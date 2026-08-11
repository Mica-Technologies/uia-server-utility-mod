# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build Commands

Set `JAVA_HOME` to a **Java 21+** install before each `./gradlew` invocation (CI runs Java 21).
**21 is the recommended sweet spot:** RetroFuturaGradle requires the Gradle process to run on
Java 21+ (older is deprecated and slated for removal), and the pinned Gradle 8.9 officially
supports running only on Java ≤ 22 — so 21/22 are both in-support *and* match CI. Either way the
compiler and mod code target **Java 8** — only the JVM that runs Gradle changes.

**Do not use JDK 23–26.** They run Gradle past its supported ceiling, and the failure is not
cosmetic: Gradle 8.9 bundles a Groovy whose ASM cannot read Java 25 class files, so any
`.gradle` script that makes Groovy resolve a JDK type — a typed closure parameter such as
`{ File f -> ... }` is enough — dies at configuration time with

```
BUG! exception in phase 'semantic analysis' in source unit '_BuildScript_'
Unsupported class file major version 69
```

This bit on 2026-08-09 when a guard was added to `addon.gradle`: the same script built fine under
17 and failed outright under 25. The `native-access` / restricted-method warnings you also see on
those JDKs *are* harmless; this is the part that isn't. Java 17 still works in practice despite
RFG's deprecation warning, so 17 is a usable fallback when no 21 is installed.

IntelliJ manages these JDKs (point `JAVA_HOME` at your install; exact patch version varies):
- Windows: `C:/Users/<username>/.jdks/azul-21.x` (managed by IntelliJ)
- macOS:   `/Users/<username>/Library/Java/JavaVirtualMachines/azul-21.x/Contents/Home`

```bash
# Setup workspace (required first time, or after clean)
JAVA_HOME="..." ./gradlew setupDecompWorkspace

# Build the mod
JAVA_HOME="..." ./gradlew build

# Run Minecraft client in dev (x86_64 / Windows)
JAVA_HOME="..." ./gradlew runClient

# Run Minecraft client in dev (Apple Silicon Mac — arm64-native via lwjgl3ify)
# NOTE: launches + loads mods, but the window is currently broken on macOS (see below).
JAVA_HOME="..." ./gradlew runClient17

# Run Minecraft client in dev on Apple Silicon with a WORKING window (LWJGL2 under Rosetta 2)
JAVA_HOME="..." ./gradlew runClient -Prosetta

# Run Minecraft server in dev
JAVA_HOME="..." ./gradlew runServer

# Clean build artifacts
JAVA_HOME="..." ./gradlew clean

# Run tests (JUnit 5)
JAVA_HOME="..." ./gradlew test
```

**Requirements:** Java 21+ (Azul Zulu Community; CI uses 21). The project uses Jabel to allow modern Java syntax while targeting JVM 8 — so mod code stays Java 8 regardless of which JDK runs Gradle. Heap is set to `-Xmx3G` in `gradle.properties` for decompilation.

### Apple Silicon (macOS) dev client

MC 1.12.2 ships LWJGL2 with x86_64-only natives, so the dev client needs help on Apple Silicon. Two paths:

- **`runClient17`** — arm64-native via lwjgl3ify (LWJGL3 on JDK 17). It launches and loads mods, but on macOS the client window is currently **broken** (tiny, non-resizable; Apple's OpenGL-over-Metal driver SIGSEGVs on the first draw call). Cause: lwjgl3ify `1.0.1` (RetroFuturaBootstrap 1.0.6) skips the relauncher re-exec in the gradle dev launch, so GLFW never gets macOS's required `-XstartOnFirstThread` main-thread handling. The proper fix lives in RFB 1.1.0, which is currently 1.7.10-only. (`MixinMinecraft` re-adds the `org.lwjgl.` classloader exclusion that OneConfig's tweaker strips — that's what lets `runClient17` get past GLFW init at all; without it you get a `LinkageError: loader constraint violation` on `org.lwjgl.glfw.GLFWVidMode`.)
- **`runClient -Prosetta`** — the reliable working client. Runs vanilla LWJGL2 under x86_64 (Rosetta 2). Requires Rosetta 2 (`softwareupdate --install-rosetta --agree-to-license`) and an x86_64 Java 8 JDK that Gradle auto-detects (e.g. Temurin 8 in `~/Library/Java/JavaVirtualMachines/`). The `-Prosetta` flag (wired in `addon.gradle`) selects that JDK via a `Java 8 + Adoptium` toolchain spec and points LWJGL2 at x86_64 natives in `.rosetta-natives/` (gitignored — repopulate per the comment in `addon.gradle`). An IntelliJ run config **"Run Client (Rosetta x86_64)"** is provided.

The RetroFuturaGradle plugin is pinned to `1.4.7` (build.gradle): `1.4.0` was removed from all public repos and survives only in stale caches.

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
├── roadrunner/           # Speed boost on configured blocks
├── api/                  # PUBLIC economy API for other mods — interfaces + value types ONLY
│   └── event/            # Forge events posted after money moves
├── economy/apiimpl/      # Implementation behind api/ — authorization, handle, escrow
└── omceapi/              # Open MCEconomic API — remote authoritative economy
    ├── *.java            # Protocol constants + models (no Minecraft imports)
    ├── client/           # HTTP/TLS/HMAC protocol client (no Minecraft imports)
    └── service/          # SUM wiring: lifecycle, balance cache, event polling
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
- **`economy_api`** -- Open MCEconomic API client (off by default). Full protocol spec in
  `docs/OPEN_MCECONOMIC_API_SPECIFICATION.md` (`.tex`/`.pdf` are the same content typeset).
  Operator setup -- what to put in this config category and why -- is
  `docs/OPEN_MCECONOMIC_API_SETUP.tex`/`.pdf`.
- **`economy_integration`** -- which *other mods* may move money through SUM's public economy
  API, and what each may do. **Not the same thing as `economy_api`**, which is about where SUM
  *keeps* money; this is about who may *move* it. Empty by default, which denies every mod.
  See `docs/SUM_ECONOMY_API.md`.

### Public economy API

`com.micatechnologies.minecraft.sum.api` is SUM's supported surface for other mods (a casino, a
shop mod, anything that spends player money). It ships as a separate slim `-api` jar built by the
`apiJar` task, which `addon.gradle` wires into `build` and publishes on every release.

- **The api jar is the contract.** If a class is not in it, it is not API. `addon.gradle` fails
  the build if anything in `api/` imports SUM internals, because such a leak compiles fine here
  and only breaks in a *dependent* mod's build.
- Implementation lives in `economy/apiimpl/` and is installed as a `SumEconomy.Provider` on
  `FMLServerStartingEvent`, after the economy backend is chosen.
- Authorization is deny-by-default per mod id, with scopes. It is an **operator control and audit
  trail, not a security boundary** -- any mod in the JVM can reach SUM's internals directly.
- Full plan and rationale: `docs/agent-plans/ECONOMY_API_PLAN.md`.

### Economy: wallet vs bank

Two distinct balances. Getting these confused is the single easiest way to break the economy.

| | What it is | Backed by | Spent by |
|---|---|---|---|
| **Wallet** | Invisible balance (`ISumMoney`) **plus** the face value of bills carried in inventory/pocket | Always local (world save) | Shops, plots, job escrow, `/pay`, loyalty rewards |
| **Bank** | A savings account | Remote OMCE service if configured, otherwise `BankSavedData` in the world save | Nothing directly -- only reachable at an ATM or with a debit card |

- `WalletService` is the wallet API. `spend()` drains the invisible balance first, then breaks
  carried bills, returning change to the invisible balance.
- `EconomyBridge` handles only the wallet's **invisible balance** and is always local and
  synchronous. It does *not* route to the remote service.
- `BankService` is the bank API. It settles inline on the local backend and after a round trip on
  the remote one, so every caller passes a callback and nothing is granted before the money moves.
- The ATM is the only crossing point: wallet <-> bank, plus cash <-> bank. Converting wallet money
  to physical bills is deposit-then-withdraw-as-cash.

A remote economy is therefore **optional in every sense** -- without one configured, the bank runs
on the world save and everything still works.

The remote backend is server-side only and never runs on a Minecraft client; on an integrated
(single-player/LAN) server it stays off unless `economy_api.allowIntegratedServer` is set, so a
local save cannot spend from a shared economy.

Three HUDs, easily confused: **Wallet** (carried money), **Bank** (account balance, synced via
`PlayerStatusSnapshot` because the client cannot read a remote account), **Vault** (face value of
bills stored in safe-deposit boxes).

### Key Patterns

- Feature code goes in its own subpackage (e.g. `roamer/`, `roadrunner/`)
- Event handlers are registered on `MinecraftForge.EVENT_BUS` in `Sum.preInit()`
- Config uses `"block=value"` string arrays for map-like data (Forge 1.12.2 limitation)
- Registry names use `sum:` prefix (e.g. `sum:roamer`, `sum:roamer_spawn_egg`)
- Package: `com.micatechnologies.minecraft.sum`

### Version

Version is derived from Git tags (format: `YYYY.MM.DD` for releases). No manual version setting needed.
