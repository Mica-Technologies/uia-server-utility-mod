# SUM merge master plan — absorbing simple mods from Alto

Status: **All planned phases shipped (M1-M8).** M7 Custom Signposts shipped as a v1 (chat-driven editor, angle-only mode); upstream's in-GUI editor and target-XZ-coords mode are deferred polish. Last updated 2026-05-09.

This doc is the execution plan for absorbing 7 small server-utility mods from the Alto modpack into SUM, mirroring the EconomyInc → SUM pattern. Each phase replaces one third-party jar with native SUM code so that jar can be pulled from `manifest.json`.

**Source review history:** First pass (2026-05-09) name-guessed several descriptions; Alex caught one (Moving Quickly) and a verified re-research surfaced more hallucinations. **All descriptions in this doc are sourced from the actual project page** (CurseForge / Modrinth / GitHub) — see "Verified sources" at the bottom. If you want to re-verify, fetch those URLs directly. Do not trust mod names.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up the merge work:

> I'm picking up the SUM merge master plan: absorbing simple mods from the Alto modpack into SUM-native code, the same way EconomyInc was replaced. Working directory `E:\gitRepos\uia-server-utility-mod`, 1.12.2 Forge, mod ID `sum`.
>
> **Read first, in this order:**
> 1. `docs/agent_progress/MERGE_MASTER_PLAN.md` — this doc. Phase tables + checklists below.
> 2. `docs/agent_progress/FEATURE_ROADMAP.md` — prior feature work (Sections A–D shipped). Especially the "Reuse, don't rebuild" inventory of available infrastructure (`SumNetwork` slots, `SumGuiHandler` IDs, `SumTab.initTabElements`, `SumRegistry`, `EconomyBridge`, etc.).
> 3. `docs/ECONOMYINC_MIGRATION.md` — pattern for replacing a third-party mod's data/items.
> 4. `CLAUDE.md` and project memory at `~/.claude/projects/E--gitRepos-uia-server-utility-mod/memory/`. Standing prefs: never `git push`; include `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` on every commit; JDK at `~/.jdks/azul-17.0.18`; `<username>` placeholder in committed files; LDW2 fork at `E:\gitRepos\LDW2` is not this repo.
> 5. **Memory: `feedback_verify_mod_descriptions.md`** — when researching upstream mods, always WebFetch the project page; never guess from the jar name.
>
> **Working agreement (locked unless Alex says otherwise):**
> - **Absorption, not compat.** Each phase replaces a third-party mod with SUM-native code; once a phase ships and is playtested, the original jar gets pulled from `manifest.json`. No `@Optional` bridges. (EconomyInc was the exception because of pre-existing player data.)
> - **Registry names use `sum:` prefix only.** Don't reuse upstream mod IDs.
> - **Reuse existing infrastructure.** New blocks slot into `SumTab.initTabElements`; new commands extend `CommandSum`; money ops go through `EconomyBridge`; new packets claim the next slot in `SumNetwork`; new GUIs claim the next ID in `SumGuiHandler`.
> - **Texture generators in `tools/`.** Each new block/item gets a Python+PIL idempotent generator script alongside the existing ones.
> - **Per-phase commit cadence.** Each XS/S phase = one commit. Build with `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build` between commits.
>
> **Recommended phase order (easiest-win-first):** ~~M2 already resolved via UT config flip — no SUM code.~~ M1 Pretty Beaches → M3 Server Pauser → M4 Loyalty Rewards → M5 Auto Dropper → M6 World Border → M7 Custom Signposts.

---

## Working agreement

- **Absorption, not compat.** Drop the third-party jar from the modpack manifest after each phase ships and is playtested. Don't ship `@Optional` bridges (EconomyInc was the exception, justified by pre-existing player balances).
- **One package per phase** under `com.micatechnologies.minecraft.sum.<feature>`, mirroring existing convention (`roamer`, `roadrunner`, `economy`, `plots`, etc.).
- **Config category per phase** in `SumConfig.java`. Default-on for cosmetic/QoL features, default-off for anything that changes vanilla mechanics broadly or is admin-only.
- **No new dependencies.** Use what Forge 1.12.2 already gives you. Where an upstream mod uses CraftTweaker for configuration, replace with a SUM string-array config to stay self-contained.
- **Each phase must include:** code, lang strings (`en_us.lang`), texture generator (if it has visuals), config defaults, and one paragraph in `FEATURE_ROADMAP.md`'s shipped table.

---

## Phase table

| # | Mod | Effort | Status | Commit |
|---|---|---|---|---|
| M1 | Pretty Beaches | XS | ✅ shipped | `023da78` |
| M2 | Dark Redstone | — | ✅ RESOLVED via UT config flip — no SUM code | n/a |
| M3 | Server Pauser | S | ✅ shipped | `22f1106` |
| M4 | Loyalty Rewards | S | ✅ shipped | `e1150e6` |
| M5 | Auto Dropper | S | ✅ shipped | `72a47f2` |
| M6 | World Border | S | ✅ shipped | `59a693e` |
| M7 | Custom Signposts | M | ✅ shipped (block + TE + TESR + chat editor + GUI editor) | (M7 v1 + GUI follow-up) |
| M8 | Moving Quickly (Mixin absorption) | XS | ✅ shipped | `d33312e` |

Effort scale: XS (≤50 LOC, <1h), S (~100 LOC, 1-2h), M (~300 LOC, half-day), L (≥500 LOC, full day+).

---

## M1 — Pretty Beaches (XS)

**Replaces:** `pretty-beaches.jar`
**Source:** https://modrinth.com/mod/pretty-beaches
**Verified behavior:** When the player breaks a sand block adjacent to water, the mod **places water source blocks** to fill in/level the resulting hole, preventing the ugly partial-flow artifact you get when digging a beach. (NOTE: prior planning had this as "consumes flow" — wrong direction. The mod **creates** sources, doesn't remove flow.)

- [ ] New class `com.micatechnologies.minecraft.sum.beaches.BeachesHandler`
- [ ] `@SubscribeEvent` on `BlockEvent.BreakEvent`. If the broken block is sand AND any horizontal neighbor is a water source/flow, schedule placement of water sources in the appropriate adjacent positions to flood the column to the surrounding water level
- [ ] **Don't fight vanilla:** vanilla water already flows after sand removal — the mod's value is making the resulting state look clean, not letting it stay as a partial-flow well. Place sources only where it produces a flat surface, not arbitrary holes
- [ ] Config category `beaches`: `enabled` (default true)
- [ ] Register in `Sum.preInit`
- [ ] Pull `pretty-beaches.jar` from `manifest.json` after playtest
- [ ] Done-when: digging a row of sand blocks at a beach edge leaves a flat water surface, not stair-stepped flow; doesn't trigger anywhere except adjacent to existing water

---

## M2 — Dark Redstone — ✅ RESOLVED via UT config flip (no SUM code)

**Replaces:** `dark-redstone.jar`
**Source:** https://modrinth.com/mod/darkredstone
**Verified behavior:** Disables light emission from active redstone wire, repeaters, comparators, and redstone torches to skip light-update calculations.

**Resolution (2026-05-09):** Universal Tweaks (`universal-tweaks.jar`, already shipped in Alto) has the **exact same feature** under `B:"No Redstone Lighting"` in `Universal-Tweaks---Tweaks.cfg`. Verified by reading UT's source at `example-source/universal-tweaks/`:

- `UTConfigTweaks.java:2809-2811` — config option "No Redstone Lighting", comment: "Disables lighting of active redstone, repeaters, and comparators to improve performance" (default `false`).
- `UTRedstoneLightingMixin.java:26-31` — `isRedstoneComponent` matches `BlockRedstoneTorch || BlockRedstoneWire || BlockRedstoneRepeater || BlockRedstoneComparator`. Covers everything Dark Redstone does.
- `UTRedstoneLightingOreMixin.java` — bonus, also kills `BlockRedstoneOre` glow.
- `UTObsoleteModsHandler.java:94` — UT itself explicitly lists `"darkstone"` (Dark Redstone's mod ID) as a mod its own toggle replaces.
- Alto's current `Universal-Tweaks---Tweaks.cfg` line 1541: `B:"No Redstone Lighting"=false` — toggle exists but is currently disabled.

**Action (no SUM code required):**

- [ ] Flip `Universal-Tweaks---Tweaks.cfg` line 1541 to `B:"No Redstone Lighting"=true`
- [ ] Pull `dark-redstone.jar` from `manifest.json`
- [ ] Bump `packVersion` in `manifest.json`
- [ ] Done.

This is a modpack edit, not a SUM commit. Nothing to ship from this repo.

---

## M3 — Server Pauser (S; coremod approach)

**Replaces:** `serverpauser.jar`
**Source:** https://modrinth.com/mod/serverpauser (verified behavior); upstream source at `example-source/server-pauser/` (LGPL-2.1)
**Verified behavior:** Mixin on `WorldServer.tick()` HEAD that cancels the entire tick when zero players online. Upstream's Mixin (`MixinWorldServer`) is one-line: `if (mcServer.getCurrentPlayerCount() <= 0) callback.cancel();`. This halts everything — time, weather, random ticks, mob spawning, scheduled block updates, TE ticks (so chunk-loaded farms freeze), entity ticks.

**Implementation note (2026-05-09):** Originally shipped with a non-coremod gamerule approach (toggling `doDaylightCycle`/`doWeatherCycle` on player count transitions). That left chunk-loaded farms running, which diverged from the upstream behavior in a way Alex flagged. **Reworked to use the same Mixin approach as upstream** — SUM is now coremod-flavoured via MixinBooter:
- `buildscript.properties`: `usesMixins=true`, `mixinsPackage=mixin`
- `mixins.sum.json` registers `MixinWorldServer`
- `addon.gradle` adds `MixinConfigs: mixins.sum.json` to MANIFEST so MixinBooter discovers the config
- Old `ServerPauserHandler` deleted; the Mixin is the only mechanism. `pauser.enabled` config flag is checked inside the Mixin.

This unlocks future Mixin-based phases without further build-config work.

- [ ] New package `com.micatechnologies.minecraft.sum.pauser`
- [ ] `ServerPauserHandler`: `@SubscribeEvent` on `TickEvent.WorldTickEvent` (START phase). If `world.getMinecraftServer().getPlayerList().getCurrentPlayerCount() == 0`, increment that world's `worldInfo` total time without advancing the time-of-day, OR cancel scheduled ticks for the dim. Cleanest: store original `gameRule doDaylightCycle` value, force-set `false` while empty, restore on first login
- [ ] **Decision per dim:** pause overworld + nether + end? Default yes
- [ ] **Decision on weather:** also force-set `doWeatherCycle false` while empty? Default yes (matches the spirit of the mod even if upstream doesn't explicitly do it). Restore on login
- [ ] **Decision on chunk ticks:** don't try to pause chunk ticks (random ticks, mob spawn, etc.) — too invasive and risk-prone. Just gamerules
- [ ] Config category `pauser`: `enabled` (default true), `pauseDaylight` (default true), `pauseWeather` (default true), `affectedDims` (string array of dim IDs, default `["0","-1","1"]`)
- [ ] Lifecycle: track previous gamerule values per-dim on first activation so they restore to user intent, not hardcoded defaults
- [ ] Register in `Sum.preInit`
- [ ] Pull `serverpauser.jar` from `manifest.json` after playtest
- [ ] Done-when: with zero players online, world's `/time query gametime` advances but `daytime` doesn't; on player login, daytime resumes from where it stopped

---

## M4 — Loyalty Rewards (S)

**Replaces:** `loyalty-rewards.jar`
**Source:** https://modrinth.com/mod/loyalty-rewards
**Verified behavior:** Grants an item and/or runs a command after a player crosses a configured playtime milestone. Upstream uses **CraftTweaker scripts** for reward configuration (no defaults shipped) — SUM will replace this with native string-array config to stay self-contained.

- [ ] New package `com.micatechnologies.minecraft.sum.loyalty`
- [ ] `LoyaltyTracker` — tracks per-player ticks-online via `PlayerLoggedInEvent` / `PlayerTickEvent`. Persist via player NBT capability OR `WorldSavedData` keyed by UUID
- [ ] `LoyaltyRewardsHandler` — on milestone crossing, applies the reward. Two reward types supported: **money** (calls `EconomyBridge.adjustBalance`) and **command** (server-runs a command as console with `{player}` substitution)
- [ ] Config category `loyalty`: `enabled` (default true), `milestones` as string array — format `<minutes>=<type>:<value>` (e.g. `30=money:10`, `60=command:give {player} minecraft:diamond 1`, `120=money:50`). Multiple rewards per milestone allowed by repeating the milestone key
- [ ] Lang strings: notification message `"Loyalty bonus: %s"` etc.
- [ ] Register in `Sum.preInit`
- [ ] Smoke test in dev: log in, fast-forward play time, verify reward fires + chat message + economy delta
- [ ] Pull `loyalty-rewards.jar` from `manifest.json` after playtest
- [ ] Done-when: a `30=money:10` milestone fires after 30 minutes of online play, deposits $10 via `EconomyBridge`, chat-notifies the player, and doesn't re-fire on logout/login (one-shot per milestone per player)

---

## M5 — Auto Dropper (S)

**Replaces:** `auto-dropper.jar`
**Source:** https://modrinth.com/mod/auto-dropper
**Verified behavior:** Adds a **new** block (NOT a mod of the vanilla dropper) that drops items continuously unless redstone-powered (inverse of vanilla). Items always land in the same spot — no scatter.

- [ ] New package `com.micatechnologies.minecraft.sum.dropper`
- [ ] `BlockAutoDropper extends BlockDropper` (or `Block` + manual container) — registry name `sum:auto_dropper`
- [ ] `TileEntityAutoDropper` — implements `ITickable`. On tick (every N ticks, default 8): if `!world.isBlockPowered(pos)`, dispense one item with zero spread (`motionX/Z = 0`, place at facing-block center)
- [ ] Use vanilla Dropper's container/GUI for slot UI — no new GUI needed
- [ ] Crafting recipe: vanilla dropper + redstone block (or similar) → 1 auto dropper
- [ ] Texture generator `tools/auto_dropper_textures/generate.py` (variant of dropper texture with a distinct accent — small green LED pip in the corner, etc.)
- [ ] Lang strings; register in `SumTab.initTabElements` and `SumRegistry`
- [ ] Config category `autodropper`: `tickInterval` (default 8)
- [ ] Pull `auto-dropper.jar` from `manifest.json` after playtest
- [ ] Done-when: place auto dropper, fill with items, items dispense on cadence in a predictable single-tile spot; powering with redstone halts dispense

---

## M6 — World Border (S)

**Replaces:** `world-border.jar`
**Source:** https://github.com/Serilum/World-Border
**Verified behavior:** Configurable per-dimension (Overworld / Nether / End) world border with optional **looping** (player crossing positive border teleports to negative, like Pac-Man). Vanilla `/worldborder` only supports the overworld in 1.12.2 — this mod's value is per-dim configs and the loop mechanic.

- [ ] New package `com.micatechnologies.minecraft.sum.border`
- [ ] `BorderHandler` — `@SubscribeEvent` on `PlayerTickEvent` (END phase, server side). For each player, look up the configured border for their current dim. If `|x| > radius` or `|z| > radius`:
  - If looping enabled: teleport to opposite axis (`-x` becomes `+radius - 1`, etc.)
  - Else: nudge player back inside with a damage-free pushback + chat message
- [ ] Config category `border`: per-dim entries as string array — format `<dimId>=<radius>:<mode>` (e.g. `0=10000:bounce`, `-1=2000:loop`, `1=5000:bounce`). Empty entry = no border for that dim
- [ ] Lang strings for bounce-back message
- [ ] Register in `Sum.preInit`
- [ ] Compatibility: respect vanilla `/worldborder` if it's been set in the overworld (optional — note in config that SUM border supersedes if both are configured)
- [ ] Pull `world-border.jar` from `manifest.json` after playtest
- [ ] Done-when: walking past the configured radius in nether triggers bounce-back or loop per config; overworld and end behave per their independent configs

---

## M8 — Moving Quickly (XS, Mixin absorption)

**Replaces:** `moving-quickly.jar`
**Source:** https://www.curseforge.com/minecraft/mc-mods/movingquickly (no public source); reference behavior confirmed against https://github.com/thiakil/GottaGoFast (sister coremod that does the same thing)
**Verified behavior:** ASM-patches the hardcoded `100.0F` (player), `300.0F` (elytra), and `100.0D` (vehicle) thresholds in `NetHandlerPlayServer` so lag-induced position desyncs don't trigger the "moved too quickly" rubberband-back-to-last-good-position path.

**SUM implementation:**
- `MixinNetHandlerPlayServer` with three `@ModifyConstant` injections:
  - `processPlayer` 100.0F (normal) and 300.0F (elytra) — both multiplied by config multiplier
  - `processVehicleMove` 100.0D — multiplied by config multiplier
- Gated by `movement.toleranceEnabled` (default true). When disabled, original returns through unchanged → vanilla behavior.
- Config: `movement.toleranceMultiplier` (default 10.0, range 1.0-1000.0).
- All three constants use the same multiplier so player ↔ elytra ↔ vehicle ratios stay vanilla.

**Replaces** `moving-quickly.jar`; modpack jar can be removed after playtest.

---

## M7 — Custom Signposts (M; v1 shipped without GUI)

**Replaces:** `custom-sign-posts.jar`
**Source:** https://www.curseforge.com/minecraft/mc-mods/custom-signposts (no public repo; CurseForge-only)
**Verified behavior:** Directional signpost block with up to 7 arms per block. Each arm has its own GUI configuration: angle in degrees OR a target XZ coord (the arm auto-rotates to point at it). NOT street-name signs — pointer/wayfinding.

**SUM v1 implementation (shipped 2026-05-09):**
- `BlockSignpost` — placeable wooden post (4×16×4 bbox inside a normal block cell), inherits vanilla `log_oak` texture for the visual.
- `TileEntitySignpost` — list of up to `MAX_ARMS=7` `SignpostArm`s, NBT-persisted, synced to clients via `getUpdateTag`/`onDataPacket`.
- `SignpostArm` — `{label, angleDegrees}`. Compass convention: 0=N, 90=E, 180=S, 270=W.
- `TESRSignpost` — renders each arm's label as floating text at its compass angle, stacked vertically up the post by arm index. Text reads outward along the arm direction.
- `/sum signpost add <angle> <label>` / `remove <index>` / `edit <index> <angle> <label>` / `clear` / `list` — chat-driven editor; uses ray-trace targeting like the vault commands.
- Right-click the post to dump arm list to chat.

**v1 deferred items shipped after initial M7 commit:**
- ✅ In-world GUI editor — claims `SumGuiHandler.GUI_SIGNPOST = 8` and `SumNetwork` slot 6 (`PacketSignpostUpdate`). Right-click opens; sneak+right-click falls back to chat dump. Save sends the full arm list to the server, which validates reach (≤8 blocks) before applying.

**Still deferred:**
- Target-XZ-coords mode where the arm auto-rotates to point at fixed coordinates as the player moves around.
- Custom textures (currently reuses vanilla `log_oak`).
- Per-arm rendered "plank" geometry under the text label.

- [ ] New package `com.micatechnologies.minecraft.sum.signpost`
- [ ] `BlockSignpost` (post block) + `TileEntitySignpost` (stores up to 7 arms; each arm has `text`, `angleDegrees`, optional `targetX/Z`)
- [ ] Right-click → `GuiSignpost`: paginated arm list, per-arm fields for label text, angle (0-359), or target XZ coords (radio button for mode). Save button persists to TE
- [ ] Network: claim **slot 6** in `SumNetwork` for `PacketSignpostUpdate`. Verify next-slot in `SumNetwork.java` before claiming
- [ ] GUI handler: claim **id 8** in `SumGuiHandler`. Verify next-id in `SumGuiHandler.java` before claiming
- [ ] TESR `TESRSignpost` — renders each configured arm as a sign protruding at the configured angle, with the label rendered as text
- [ ] Texture generators: `tools/signpost_textures/generate.py` (post + arm base — wood + brass tone matching existing aesthetic)
- [ ] Lang strings; register in `SumTab.initTabElements` and `SumRegistry`
- [ ] Cap arms at 7 per block (matches upstream); GUI "Add arm" button disables at 7
- [ ] Pull `custom-sign-posts.jar` from `manifest.json` after playtest
- [ ] Done-when: place signpost, configure 3 arms (one with angle, one with XZ target, one default), each renders pointing in the right direction with the correct label visible from a few blocks away

---

## Considered but not absorbing

These were evaluated and rejected for the reasons noted. **Do not re-investigate without explicit ask** — the rationale is documented to prevent loops.

| Mod | Effort | Reason |
|---|---|---|
| ~~Moving Quickly~~ | ~~XS~~ | ~~Originally rejected for coremod gap~~. **Now shipped — see M8 below.** |
| The Beeper | XS | Decorative beeping computer blocks marketed as a prank tool. Doesn't fit SUM's server-utility / RP-economy charter. Out of scope. |
| World Buoyancy | XS | (a) Wooden item stacks rise through inventory grid while submerged, (b) liquids can't be displaced by placing a block — only removed via container. Niche survival-realism, not server utility. Out of scope. |
| GymCraft | M | Multi-block content pack (treadmill, weights, etc.) granting buffs. Too large to absorb cleanly even trimmed; original is abandoned by author. |
| Custom Sign Posts (street-name interpretation) | L | First-pass description of "street-name signs" was wrong — see M7 for the actual mod. The hypothetical street-name-sign mod doesn't exist in this pack. |
| Just a Few Fish | L | Fish entities + fishing rods + fish tanks. Content-heavy, not utility. |
| Glassential | M | Decorative glass variants (one-way, glowing, etc.). Pure decorative content. |
| DebugServerInfo | — | KEEP-AS-IS. Useful admin HUD, but orthogonal to SUM's feature charter. Doesn't hurt anything to leave it. |
| Server Tab Info | — | KEEP-AS-IS. Same reasoning — useful, orthogonal. |
| World Utils | — | KEEP-AS-IS. Op-only world-repair commands; large surface, sensitive operations. |
| Chunkt | — | KEEP-AS-IS. Chunkloader item — useful, but chunkloading is sensitive and out of charter. |
| Ping | L+ | V-key ping wheel. UI/HUD/networking feature; ~1-2 days of work and overlaps with channel infrastructure SUM doesn't need. Defer indefinitely. |
| Better Placement | — | Pure client-side block-placement smoothness tweak. No server-side effect. |
| Clear Water | — | Pure client-side fog tweak. |
| NonConflictKeys | — | Client-side coremod for keybinding behavior. |
| Panoramica | — | Client-side panorama screenshot capture. |
| PipeBlocker | — | Security coremod patching `ObjectInputStream` deserialization. Infrastructure, not a feature. |
| Traffic Control | M+ | Road signs/cones/lights content blocks. Larger content pack — partially overlaps with what M7 (signposts) covers; revisit only after M7 ships if Alex wants more road furniture. |

---

## Critical decisions (locked)

- **Replacement, not compat.** Drop each upstream jar after the phase ships and playtests cleanly.
- **One commit per XS/S phase.**
- **`sum:` namespace only** for new registry entries. No upstream mod-ID reuse.
- **Reuse existing infrastructure** (`EconomyBridge`, `SumNetwork`, `SumGuiHandler`, `SumTab`, `SumRegistry`) — no new singletons.
- **Default-on** for cosmetic / QoL features (M1, M3, M4, M6, M7); **default-on with per-component toggles** for M2 (Dark Redstone) once unblocked; **default-on** for M5 (Auto Dropper).
- **Loyalty Rewards: native config, not CraftTweaker.** SUM stays self-contained — no CraftTweaker dependency.
- **Server Pauser: gamerule-toggle approach, not chunk-tick interception.** Lower risk.
- **Moving Quickly: shipped as M8.** `@ModifyConstant` Mixins on `NetHandlerPlayServer.processPlayer` (100.0F + 300.0F) and `processVehicleMove` (100.0D), gated by `movement.toleranceEnabled` config flag, multiplier default 10×.
- ~~**No new coremod for SUM.** If a phase requires ASM patching, reject the phase rather than make SUM a coremod.~~ Reversed 2026-05-09; SUM is now coremod-flavoured.

---

## Watch out for

- **`SumNetwork` slot:** FEATURE_ROADMAP says next is **6** (M7 will claim it). Verify by reading `SumNetwork.java` before any phase that adds a packet.
- **`SumGuiHandler` IDs:** FEATURE_ROADMAP says next is **8** (M7 will claim it). Verify before adding.
- **Roadrunner config format:** strings like `block=multiplier`. Don't accidentally break it when adding similar string-array configs (M4 milestones, M6 borders).
- **TileEntity tick perf:** M5 (Auto Dropper) iterates per-TE on tick. Gate by interval; the Alto pack runs heavy.
- **Texture generators are idempotent** — running twice must not produce different output. Match the existing `tools/*_textures/generate.py` style.
- **Don't touch LDW2.** Weather/snow features go in `E:\gitRepos\LDW2`, not here.
- ~~**Don't make SUM a coremod.** If a phase needs ASM, reject the phase.~~ Reversed 2026-05-09 during M3 rework: SUM is now a coremod-flavoured mod via MixinBooter. New phases CAN use Mixin where it's the right tool, but should still default to vanilla Forge events when those suffice.

---

## Verified sources

These project pages were fetched on 2026-05-09 to verify mod behavior. Re-fetch before relying on these descriptions if more than ~6 months have passed; mod behavior can change across versions.

- Pretty Beaches — https://modrinth.com/mod/pretty-beaches
- Dark Redstone — https://modrinth.com/mod/darkredstone
- Auto Dropper — https://modrinth.com/mod/auto-dropper
- Custom Signposts — https://www.curseforge.com/minecraft/mc-mods/custom-signposts
- Loyalty Rewards — https://modrinth.com/mod/loyalty-rewards
- Server Pauser — https://modrinth.com/mod/serverpauser
- World Border (Serilum) — https://github.com/Serilum/World-Border
- Moving Quickly (rejected) — https://www.curseforge.com/minecraft/mc-mods/movingquickly
- The Beeper (rejected) — https://www.curseforge.com/minecraft/mc-mods/the-beeper
- World Buoyancy (rejected) — https://modrinth.com/mod/worldbuoyancy
- Forge forum thread confirming Moving Quickly is coremod-only — https://forums.minecraftforge.net/topic/74474-can-the-moved-too-quicklymoved-wrongly-checks-be-disabled-without-coremodding/

---

## Done-with-everything criteria

- All M1, M3, M4, M5, M6, M7 shipped, playtested, and their upstream jars removed from `manifest.json`
- M2 (Dark Redstone) — **resolved via UT config flip** (no SUM code; modpack edit only)
- `FEATURE_ROADMAP.md` Section M (or equivalent) table updated with shipped commits
- One follow-up commit bumping `manifest.json` `packVersion` once the merge wave is done
