# OneConfig + EvergreenHUD integration for SUM

Status: OneConfig `runClient` launch crash is **RESOLVED**. The fix landed in a follow-up commit (see "Resolved crash" section below) that (a) removed `oneconfig-1.12.2-forge` from the dev runtime classpath, since its manifest declares a `TweakClass` that doesn't exist as an artifact (OneConfig downloads its stage1 loader at runtime), and (b) injected the wrapper's real tweaker (`cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker`) into the dev launch args via `minecraft.extraTweakClasses` in `addon.gradle`. Verified locally: `./gradlew runClient` and `runClient17` both successfully invoke the OneConfig stage0 loader, which downloads OneConfig into `run/client/OneConfig/Loader/` and progresses into FML mod loading without ClassNotFoundException.

In the same session: CSM is now resolved as a regular Gradle dep via a GitHub Releases Ivy repo (`repositories.gradle`) pinned by `gradle.properties#csmVersion`, replacing the brittle sibling-checkout / `gh release download` setup. CI workflows were trimmed accordingly. See "Dependency layering" below.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up where we left off:

> I'm continuing the OneConfig + EvergreenHUD integration pass for the SUM mod.
>
> Read `docs/agent_progress/OC_EGHUD_DEV.md` first — it has the full plan, every commit, the architectural decisions, and the known-broken `runClient` launch crash that we left unsolved.
>
> The integration's code surface is complete: pocket HUD migrated to OneConfig, the favorites-star preference migrated, 23 HUD modules ported from EvergreenHUD (information / player-state / performance / counters / 5 custom text slots / TPS), OneConfig declared as a hard required-after dependency in `Sum.@Mod` and `mcmod.info`. SUM-settings UI is gated to singleplayer or operator-level players on multiplayer. The pocket / phone / desk-phone work from earlier in the session is fully validated by the user.
>
> The blocker before any user testing: `./gradlew runClient` crashes at launch with `ClassNotFoundException: cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker`. Commit `9757cea` added `oneconfig-wrapper-launchwrapper` as `runtimeOnlyNonPublishable` which puts it on `runtimeClasspath` per the build.gradle's `runtimeClasspath.extendsFrom(runtimeOnlyNonPublishable)` line, but the user still hits the same error. See the "Known crash" section for the debug ladder — start with daemon stop + `--refresh-dependencies`, then promote the wrapper to `devOnlyNonPublishable`, then to `embed`, in that order.
>
> Once runClient launches: there are also four deferred follow-ups in the "What remains" checklist — jar-side bundling (so end users don't need OneConfig installed separately), full HudList custom-text framework, server-config-to-OneConfig migration for settings that could reasonably be client-visible, and a small set of EvergreenHUD elements I deliberately didn't port (Reach, BedwarsResource, etc.) that the user said they don't need.

---

## What this project is

The pre-existing SUM ("Server Utility Mod") had its own bespoke client-side preference system:

- A Forge `Configuration` file (`config/sum.cfg`) for server-side toggles + a few client toggles.
- A native pocket-HUD overlay written from scratch (RenderGameOverlayEvent handler + custom JSON store + drag-to-reposition GuiScreen).

The goal of this integration pass is to **replace the bespoke client systems with OneConfig (Polyfrost) so SUM shares HUD layout / settings UX with EvergreenHUD**, which the UIA Minecraft server's standard modpack already includes. Specifically:

1. SUM's HUDs (pocket inventory display + everything else we add) should be OneConfig HUD modules — auto-discovered, persisted, drag-to-reposition via OneConfig's editor.
2. SUM's client-side preferences should live in a `Config`-subclass entry point that OneConfig renders.
3. SUM should ship the same EvergreenHUD-style "information / player-state / performance / counters" widget catalog so SUM users don't *also* need EvergreenHUD for the basics.
4. Server-side settings (roamer walkable blocks, world border, etc.) stay in the Forge Configuration file — OneConfig is client-only and there's no server-config-sync system to safely move them.
5. The SUM settings page is gated on multiplayer: only operators see it, since some toggles may grow server-affecting later.

---

## Status snapshot

| # | Phase | Status | Commit |
|---|---|---|---|
| 1 | Add OneConfig as a hard runtime dep | ✅ landed | `db8bed0` |
| 2 | Migrate pocket HUD → OneConfig `BasicHud` | ✅ landed | `db8bed0` |
| 3 | Delete native pocket-HUD scaffolding (overlay / config / editor) | ✅ landed | `db8bed0` |
| 4 | SUM-settings keybind gated to SP or op | ✅ landed | `db8bed0` |
| 5 | Port EvergreenHUD info elements (Coords / FPS / Direction / Time / Biome / Day) | ✅ landed | `7ffb5b4` |
| 6 | Migrate favorites-star toggle to OneConfig | ✅ landed | `a4037b5` |
| 7 | Port player-state HUDs (Pitch / Yaw / Speed / Saturation / Armor) | ✅ landed | `a58e274` |
| 8 | Port performance HUDs (Memory / Ping) | ✅ landed | `a58e274` |
| 9 | Port counter HUDs (CPS / Click Counter / Blocks Placed / Session Playtime) | ✅ landed | `a58e274` |
| 10 | Port info HUDs round 2 (Real-Life Date / Server IP / Resource Pack / Game Mode / Block Above / Height Limit) | ✅ landed | `a58e274` |
| 11 | Add TPS HUD (SPacketTimeUpdate timing) | ✅ landed | `5f90ed2` |
| 12 | Add 5 Custom Text HUD slots | ✅ landed | `5f90ed2` |
| 13 | Add OneConfig launchwrapper bootstrap to dev runtime classpath | ⚠️ landed but still failing | `9757cea` |
| 13b | Fix OneConfig runClient: gate main jar from runtime classpath + inject tweaker via `extraTweakClasses` | ✅ landed (this session) | (uncommitted at time of writing) |
| 13c | CSM Ivy/GitHub-Releases dep (replaces sibling-checkout + CI `gh release download`) | ✅ landed (this session) | (uncommitted at time of writing) |
| 14 | Bundle OneConfig wrapper into SUM jar (embed + manifest TweakClass entry) | ✅ landed | (this commit) |
| 15 | Full HudList dynamic custom-text scaffolding | ⏳ deferred | — |
| 16 | Server-config bridge (read-only OneConfig mirror of server settings) | ⏳ deferred | — |

---

## Architectural overview

```
src/main/java/com/micatechnologies/minecraft/sum/
├── pocket/
│   ├── SumOneConfig.java              # Root @Config; declares every @HUD field
│   ├── PocketHud.java                 # extends BasicHud — renders pocket slot row
│   ├── PocketKeybinds.java            # 'P' opens pocket GUI; SUM-settings keybind
│   │                                  #   gated to SP-or-op via canUseCommand(2, "")
│   ├── PocketInventory.java           # capability-backed 3-slot ItemStackHandler
│   ├── CapabilityPocket.java          # cap registration
│   ├── PocketProvider.java            # cap attach provider
│   ├── PocketEvents.java              # AttachCapabilities + login/dim/clone sync
│   ├── PacketSyncPocket.java          # S→C full sync, channel id 12
│   ├── PacketOpenPocketGui.java       # C→S open-GUI request, channel id 13
│   ├── ContainerPocket.java           # filtered SlotItemHandler for each slot
│   └── GuiPocket.java                 # 3-slot GUI + "Edit HUD…" → OneConfigGui
└── huds/
    ├── HudStateTracker.java           # shared event subscriber for the counter HUDs
    ├── ArmourHud.java                 # lowest-durability armor piece %
    ├── BiomeHud.java                  # biome name at player position
    ├── BlockAboveHud.java             # display name of block at eye+1
    ├── ClickCounterHud.java           # session lifetime clicks
    ├── CoordsHud.java                 # X / Y / Z (Y optional)
    ├── CpsHud.java                    # clicks in last 1000ms
    ├── CustomTextHud.java             # user-typed string (5 slots registered)
    ├── DayCounterHud.java             # in-game day number
    ├── DirectionHud.java              # cardinal direction (short or long names)
    ├── FpsHud.java                    # Minecraft.getDebugFPS()
    ├── GameModeHud.java               # Survival / Creative / Adventure / Spectator
    ├── HeightLimitHud.java            # blocks to build cap (or absolute)
    ├── MemoryHud.java                 # JVM heap (GB or %)
    ├── PingHud.java                   # NetworkPlayerInfo.responseTime
    ├── PitchHud.java                  # rotationPitch
    ├── PlaceCountHud.java             # session lifetime blocks placed
    ├── PlaytimeHud.java               # HH:MM:SS since session start
    ├── RealLifeDateHud.java           # ISO / US / EU / Long formats
    ├── ResourcePackHud.java           # top-of-stack pack name
    ├── SaturationHud.java             # food saturation level
    ├── ServerIpHud.java               # connected server / "Singleplayer" / "LAN"
    ├── SpeedHud.java                  # horizontal movement b/s or b/t
    ├── TimeHud.java                   # real-world OR in-game time, 12h or 24h
    ├── TpsHud.java                    # SPacketTimeUpdate-derived TPS
    └── YawHud.java                    # rotationYaw normalized to [0, 360)
```

External wiring (modified, not new):

- `Sum.java` — registers `SumOneConfig` instance in client proxy preInit; `@Mod(dependencies = "required-after:oneconfig;after:csm")`.
- `SumClientProxy.java` — instantiates `new SumOneConfig()`; registers `HudStateTracker` on Forge bus; registers pocket keybinds.
- `SumConfig.java` — `isFavoritesStarOverlayEnabled()` reads from `SumOneConfig.favoritesStarOverlay` if loaded, falls back to legacy Forge field.
- `atm/SumNetwork.java` — registers packet ids 12 (SyncPocket S→C), 13 (OpenPocketGui C→S).
- `atm/SumGuiHandler.java` — adds GUI_POCKET routing (id 10).
- `mcmod.info` — adds `"oneconfig"` to `dependencies` + `requiredMods`.
- `dependencies.gradle` — declares the OneConfig API + bootstrap deps.
- `repositories.gradle` — adds Polyfrost Releases maven.
- `README.md` — documents the OneConfig dep + HUD catalog in the Inspirations table.

OneConfig artifacts pulled:

| Coordinate | Scope | Purpose |
|---|---|---|
| `cc.polyfrost:oneconfig-1.12.2-forge:0.2.2-alpha+` | `compileOnly` + `runtimeOnlyNonPublishable` | API: `Config`, `BasicHud`, `SingleTextHud`, annotations, `EventManager`, `OneConfigGui` |
| `cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+` | `runtimeOnlyNonPublishable` | The "OneConfig Bootstrap" jar. Contains the `OneConfigTweaker` class that the main jar's `MANIFEST.MF` references as its `TweakClass`. Polyfrost ships this to users as a separate download. |

---

## Commit roster (this session, OneConfig/HUD slice)

```
9757cea Add OneConfig launchwrapper bootstrap to dev runtime classpath   ⚠️ still failing
5f90ed2 Add TPS + 5 Custom Text HUDs
a58e274 Port remaining EvergreenHUD elements (17 new + 1 tracker)
a4037b5 Migrate favorites-star toggle from Forge config to OneConfig
7ffb5b4 Port EvergreenHUD information elements as SUM HUD modules
db8bed0 Migrate pocket HUD to OneConfig + add OneConfig as a hard dep
```

(For commits earlier in the session — pocket capability, ATM fix, roamer AI fix, area codes, desk phones — see the main git log; those are validated by the user and unaffected by the runClient crash.)

---

## Resolved crash

### Symptom (what was happening before)

```
[main/INFO] [LaunchWrapper]: Loading tweak class name cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker
[main/ERROR] [LaunchWrapper]: Unable to launch
java.lang.ClassNotFoundException: cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker
```

### Actual root cause

The pre-fix `dependencies.gradle` put `oneconfig-1.12.2-forge` on the dev runtime classpath via `runtimeOnlyNonPublishable`. That jar's `META-INF/MANIFEST.MF` declares:

```
TweakClass: cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker
TweakOrder: 0
ForceLoadAsMod: true
MixinConfigs: mixins.oneconfig.json
```

GradleStart (FG12 `GradleStartCommon.java:204-249`) walks `java.class.path` at launch, opens every jar's `META-INF/MANIFEST.MF`, and pushes any `TweakClass` value into the LaunchWrapper `--tweakClass` arg list. LaunchWrapper then tries to load the class.

**`OneConfigTweaker` does not exist in any published OneConfig artifact**:

| Artifact | Contains tweaker class? |
|---|---|
| `oneconfig-1.12.2-forge-0.2.2-alpha228.jar` | ❌ (declares it in manifest, doesn't ship it) |
| `oneconfig-wrapper-launchwrapper-1.0.0-beta9..17.jar` | ❌ (ships `cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker` instead) |

The OneConfig main jar's `TweakClass` entry refers to a class that gets downloaded at runtime by the wrapper's stage0 loader (into `./OneConfig/Loader/`). That class is never in any artifact on disk at build/launch time. Putting the main jar on the dev classpath ⇒ guaranteed crash.

### The fix

Two coordinated changes:

1. `dependencies.gradle` — keep `oneconfig-1.12.2-forge` as `compileOnly` only; remove it from `runtimeOnlyNonPublishable`. The OneConfig wrapper moves to `devOnlyNonPublishable` (compile + dev runtime, not published).

   ```groovy
   compileOnly 'cc.polyfrost:oneconfig-1.12.2-forge:0.2.2-alpha+'
   devOnlyNonPublishable 'cc.polyfrost:oneconfig-wrapper-launchwrapper:1.0.0-beta+'
   ```

2. `addon.gradle` — inject the wrapper's real tweaker name into the dev launch args via the RFG `MinecraftExtension`. The wrapper jar's own `MANIFEST.MF` is empty (it's designed to be embedded in a host mod whose jar manifest declares the tweaker), so the dev environment has nothing to discover via GradleStart's scan. `extraTweakClasses.add(...)` solves it:

   ```groovy
   minecraft {
       extraTweakClasses.add('cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker')
   }
   ```

### Verified launch sequence (dev)

```
GradleStart: Extra: [..., --tweakClass, cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker]
LaunchWrapper: Loading tweak class name cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker
Attempting to load Polyfrost certificate.
OneConfig has detected the version 1.12.2.
Updating OneConfig Loader...
Updated OneConfig Loader!
OneConfigLoader: Downloading new version of OneConfig... (17477.086KB)
OneConfigLoader: Download finished successfully
Updated OneConfig Jar!
[mixin]: SpongePowered MIXIN Subsystem Version=0.8.7 ... Env=CLIENT
[FML]: Forge Mod Loader version 14.23.5.2847 for Minecraft 1.12.2 loading
[FML]: Searching .../run/mods for mods
```

Subsequent runs skip the download (cached at `run/client/OneConfig/Loader/`). Mac arm64 dev should use `runClient17` (lwjgl3ify) — `runClient` works on Windows/x86_64 but hits lwjgl2 security-seal errors on Apple Silicon.

### Benign "Mod will NOT work" message

Stage0 logs `Not able to determine current file. Mod will NOT work` twice during dev launch. This is the loader trying to find what jar IT was loaded from — in dev it's loaded from the build classpath, not a single jar in `mods/`. End users (with OneConfig-Bootstrap installed as a real mod jar) don't see this. It does not block functionality; OneConfig still bootstraps and loads.

---

## What remains

### Deferred (in priority order)

- [x] **Task 14 — Bundle the OneConfig bootstrap inside SUM's published jar.** ✅ Shipped. `oneconfig-wrapper-launchwrapper` switched from `devOnlyNonPublishable` to `embed` in `dependencies.gradle`, so the wrapper classes (LaunchWrapperTweaker + OneConfigWrapperBase + SSLStore + ssl/polyfrost.der) are unpacked into SUM's published jar via the `embed` configuration's `zipTree` mechanism. `addon.gradle` appends `TweakClass: cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker` and `TweakOrder: 0` to SUM's `META-INF/MANIFEST.MF` via an `afterEvaluate` jar-task config block — same pattern already used for the `MixinConfigs` attribute. `ForceLoadAsMod=true` is already set by the GTNH buildscript's `getManifestAttributes()` (since `containsMixinsAndOrCoreModOnly=false`), so it's not re-set. `extraTweakClasses` in addon.gradle continues to handle dev launches (which load SUM from build outputs, not the packed jar, so the manifest entry is ignored in dev). End users installing SUM alone now get OneConfig auto-bootstrapped without a separate OneConfig-Bootstrap install. **Important nit:** the doc's earlier sketch referenced `cc.polyfrost.oneconfig.internal.plugin.asm.OneConfigTweaker` as the TweakClass, but that class lives in the main OneConfig jar (which the wrapper downloads at runtime, not present at build time). The class actually shipping inside SUM after embedding is `LaunchWrapperTweaker`, so that's what the manifest now declares.

- [ ] **Task 15 — Full HudList dynamic custom-text scaffolding.** Replace the five fixed `CustomTextHud` slots with EvergreenHUD's "add as many as you want" UI. Requires porting their `HudList` framework + a custom OneConfig option type for dynamic-sized lists. Five slots covers ~all realistic UIA use cases so this is low priority.

- [ ] **Task 16 — Read-only OneConfig mirror of server-side settings.** SUM's roamer walkable list, roadrunner multipliers, world border config, etc. live in `config/sum.cfg` (Forge Configuration). Right now there's no UI for them on the client. A read-only OneConfig page that shows the current server values (received via a small server→client info packet on login) would make SUM's config story uniform without breaking the "server controls server settings" invariant.

- [ ] **EvergreenHUD elements we intentionally skipped, in case priorities change:**
  - `BedwarsResource`, `Combo`, `Map`, `MapType`, `GameType` — Hypixel/Bedwars-specific. Out of scope unless UIA targets Hypixel-compat someday.
  - `Reach` — needs attack-distance tracking via PlayerInteractEvent + LivingAttackEvent + math. Doable, just unbudgeted; user said no.
  - `CustomImages` — user-supplied texture uploads + caching. Nontrivial.
  - `Inventory` — full inventory grid render in HUD. Complex layout code.
  - `PlayerPreview` — player-model render in HUD. Needs EntityRenderer in a HUD context, GL state juggling.
  - `HeldItemLore` — multi-line `TextHud` not `SingleTextHud`. Easy if needed.
  - `ECounter` — semantics unclear; never figured out what "E" stands for.

### Validation needed after the blocker is solved

- [ ] runClient launches and OneConfig's R-SHIFT brings up the OneConfig UI.
- [ ] "Server Utility Mod" appears as a mod card in OneConfig.
- [ ] Pocket HUD module's drag-to-reposition editor works (open in OneConfig → drag the pocket overlay).
- [ ] Pocket HUD slot icons sync from the server (open the pocket GUI via `P`, drop a phone in slot 0, close the GUI, HUD shows it).
- [ ] Each of the 23 ported HUDs displays the expected info when enabled.
- [ ] TPS HUD shows ~20.00 on a healthy server, drops on a lagging server.
- [ ] Custom Text 1-5 surface as separately-positionable HUDs with their own @Text fields.
- [ ] Favorites-star toggle in the OneConfig "Favorites" category correctly enables/disables the gold-star overlay in the creative menu.
- [ ] SUM-settings keybind opens the OneConfig page in singleplayer.
- [ ] SUM-settings keybind shows "op-only on this server" status message on a multiplayer server where the local player isn't op (test with a friend or by /deop self).

---

## Other deferred items from this session (not part of the HUD pass)

These are unrelated to the OneConfig/HUD work but were also queued during the session. Listed here for completeness so we don't lose track:

- [ ] **Verify the SUM cross-cutting fixes work end-to-end:** ATM GUI on dedicated server (commit `a432ced`), roamer AI fire/storm response with no player nearby (commit `1368f79`), personal-item auto-rebind + favorites sync (commit `14fe681`), pocket capability (commit `49fe0d6`), area codes on player phone numbers (commit `383e049`), desk-phone TE + chunk-derived numbers (commit `2fb3682`). These compile and unit-pass but Alex hasn't done in-world testing yet.
- [ ] These items also block on `runClient` working since dev testing is how they'd be validated locally.

---

## Tunable constants summary

For future tuning passes, these are the meaningful constants per HUD:

| HUD | Constant | Default | Notes |
|---|---|---|---|
| `CpsHud` | `HudStateTracker` recent-clicks window | 1000ms | Hardcoded in `HudStateTracker.evict`. Could promote to a per-HUD @Slider. |
| `TpsHud` | TPS clamp range | [0.0, 20.0] | Hardcoded; rejects outliers caused by network hiccups. |
| `MemoryHud` | display mode | GB | `asPercent: false` default; per-HUD @Switch. |
| `SpeedHud` | unit | blocks/sec | per-HUD @Dropdown. |
| `HeightLimitHud` | display mode | relative | `absolute: false` default; per-HUD @Switch. |
| `DirectionHud` | naming | short | `longNames: false` default; per-HUD @Switch. |
| `CoordsHud` | show Y | true | per-HUD @Switch. |
| `RealLifeDateHud` | format | ISO | per-HUD @Dropdown, 4 options. |
| `TimeHud` | source / format | real-time, 24h | two per-HUD @Switch fields. |

---

## File-by-file inventory of changes

### Newly added (huds/)

`huds/HudStateTracker.java`, `huds/ArmourHud.java`, `huds/BiomeHud.java`, `huds/BlockAboveHud.java`, `huds/ClickCounterHud.java`, `huds/CoordsHud.java`, `huds/CpsHud.java`, `huds/CustomTextHud.java`, `huds/DayCounterHud.java`, `huds/DirectionHud.java`, `huds/FpsHud.java`, `huds/GameModeHud.java`, `huds/HeightLimitHud.java`, `huds/MemoryHud.java`, `huds/PingHud.java`, `huds/PitchHud.java`, `huds/PlaceCountHud.java`, `huds/PlaytimeHud.java`, `huds/RealLifeDateHud.java`, `huds/ResourcePackHud.java`, `huds/SaturationHud.java`, `huds/ServerIpHud.java`, `huds/SpeedHud.java`, `huds/TimeHud.java`, `huds/TpsHud.java`, `huds/YawHud.java`.

### Newly added (pocket/)

`pocket/SumOneConfig.java`, `pocket/PocketHud.java`.

### Removed (pocket/)

`pocket/PocketHudConfig.java`, `pocket/PocketHudOverlay.java`, `pocket/GuiPocketHudEditor.java` — replaced by the OneConfig BasicHud + editor.

### Modified

- `Sum.java` — `@Mod(dependencies = "required-after:oneconfig;after:csm")` + `CapabilityPocket.register()` + register `PocketEvents` + `GameRegistry.registerTileEntity(TileEntityDeskPhone.class, …)`.
- `SumClientProxy.java` — `new SumOneConfig()` + register `HudStateTracker` + register `PocketKeybinds`. Dropped the native `PocketHudOverlay` / `PocketHudConfig` registration.
- `SumConfig.java` — `isFavoritesStarOverlayEnabled()` reads from SumOneConfig when available.
- `atm/SumNetwork.java` — registers `PacketSyncPocket` (id 12), `PacketOpenPocketGui` (id 13), `PacketAddFavorite` (id 11).
- `atm/SumGuiHandler.java` — adds GUI_POCKET (id 10) routing + desk-phone TE lookup for GUI_DESK_PHONE.
- `atm/BlockAtmBase.java` — drop `!world.isRemote` guard.
- `phone/BlockDeskPhone.java` — `hasTileEntity`/`createTileEntity`/`onBlockPlacedBy`/`breakBlock` for the new TE.
- `phone/GuiSumPhone.java` — accept a `deskPhoneNumberOverride` constructor param.
- `phone/cloud/PhoneCloudSavedData.java` — 10-digit area+exchange+subscriber format + weighted area code + `allocateNumberWithFixedExchange` + `releaseNumber` + reservations NBT.
- `roamer/EntityRoamer.java` — `emergencyNearby` override for the idle gate.
- `economy/ItemAccountAccess.java` — `isPhone()` getter + `onUpdate` auto-rebind + bindToPlayer rewires to roster + favorites packet.
- `mcmod.info` — adds `oneconfig` to deps + requiredMods.
- `dependencies.gradle` — OneConfig API + wrapper deps + comments explaining each.
- `repositories.gradle` — Polyfrost Releases maven.
- `README.md` — pocket feature blurb, OneConfig row in Inspirations table, EvergreenHUD attribution updated to enumerate the ported catalog.

### Newly added (other slices not part of HUD pass but in the same session)

`economy/PersonalItemsSavedData.java`, `favorites/PacketAddFavorite.java`, `pocket/CapabilityPocket.java`, `pocket/PocketProvider.java`, `pocket/PocketInventory.java`, `pocket/PocketEvents.java`, `pocket/PacketSyncPocket.java`, `pocket/PacketOpenPocketGui.java`, `pocket/ContainerPocket.java`, `pocket/GuiPocket.java`, `pocket/PocketKeybinds.java`, `phone/TileEntityDeskPhone.java`.
