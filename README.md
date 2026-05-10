# UIA SUM (Server Utility Mod)

The UIA SUM (Server Utility Mod) is a server-specific mod developed for and intended for use only on the UIA/Alto Minecraft server.

![GitHub Release](https://img.shields.io/github/v/release/Mica-Technologies/uia-server-utility-mod?sort=semver&display_name=tag&style=for-the-badge&logo=GitHub)
![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/Mica-Technologies/uia-server-utility-mod/latest/total?style=for-the-badge&label=Downloads)
![GitHub Downloads (all assets, all releases)](https://img.shields.io/github/downloads/Mica-Technologies/uia-server-utility-mod/total?style=for-the-badge&label=Downloads%20(All))

## Features

### Economy

- SUM-native player balance capability (`ISumMoney`) with NBT persistence and login/respawn sync.
- Eight bill items ($1, $2, $5, $10, $20, $50, $100, $200, $500) plus matching packet items (each packet = 64 bills of one denomination).
- ATM block family — kiosk, wall-mounted, and drive-thru variants.
- Bank counter, safe deposit box (per-player 3×3 inventory), passcode-locked vault door, decorative velvet rope stanchions.
- Bill changer (bundle/unbundle bills into packets) and decorative bills display tray (TESR-rendered).
- Player-shop block (vending-machine style, NBT-exact item matching, optional admin infinite-stock flag).
- Bills appear naturally in 13 vanilla chest loot tables (small denominations weighted heavily).
- `/balance` (self) and `/sum econ <balance|add|set>` (admin) commands.
- EconomyInc compatibility bridge plus `/sum migrate-economy` to move balances + bill items off EconomyInc.

### Server-utility blocks

- Auto dropper — vanilla dropper inventory/UI but ticks on its own and dispenses without item scatter; halts when redstone-powered (inverse of vanilla).
- Wayfinding signpost — wooden post block holding up to 7 labeled arms at configurable compass angles. TESR renders each arm's text in-world along its direction. Right-click the post to open the GUI editor (Add/Remove/Save inline); sneak+right-click for a chat list. Chat commands also work: `/sum signpost <add|remove|edit|clear|list>`.
- Trash can with ephemeral 9-slot inventory (contents destroyed on close).
- Wall-mounted storm-shelter sign that registers itself with the Roamer storm-AI's preferred-shelter cache.
- Sleep voting — configurable percentage of online players asleep ends the night.
- Business cards (NBT-bound profile exchange item).
- Mailbox + postal system (claim-on-first-right-click, 9-slot inbox with deposit-only slots for non-owners).
- Job board (server-wide listings, paginated browse GUI, `/sum job post` to add).

### Plots / land claims

- 3D bounding-box claims with per-dimension persistence and chunk-indexed fast lookup.
- Plot wand — sneak+right-click for corner A, right-click for corner B; selection persists in NBT.
- `/sum plots <create|delete|list|info|buy|sell|trust|untrust|transfer>` commands.
- Protection handler cancels block break/place from non-owners, mob-driven destruction, and filters owned blocks out of explosions.
- Op bypass via `sum.plots.bypass` permission node (auto-granted at op-2+).

### NPCs

- Roamer wandering passive entity with role enum (GENERIC, BANK_TELLER) and theme-aware greetings.
- Storm-shelter AI prefers signed shelters (within 48h / 6v range) when already indoors.

### World / movement

- Roadrunner — per-block speed multipliers configured via string-array (`block=multiplier`).
- Pretty Beaches — breaking sand (or other configured blocks) adjacent to water replaces it with water and animates flooding the column at sea level, eliminating ugly partial-flow artifacts. Optional `realisticErosion` config flag also affects dirt, grass, gravel, clay, mycelium, soul sand, and snow — water carves through soft riverbanks, not just sand.
- Server Pauser — cancels `WorldServer.tick()` entirely when no players online (Mixin-based), resuming on first login. Halts time, weather, scheduled block updates, TE ticks, mob spawning, random ticks. Chunk-loaded farms genuinely freeze.
- World border — per-dimension square borders with bounce-back or Pac-Man-style loop modes; configurable per dim via `<dimId>=<radius>:<mode>` strings.
- Lag-tolerant movement — relaxes vanilla's "moved too quickly" rubberband threshold in `NetHandlerPlayServer` via Mixin (`@ModifyConstant`); configurable multiplier (default 10× vanilla).

### Player UX

- Creative-tab favorites with optional gold-star overlay on favorited slots.
- `/sum help` paginated command listing.
- Loyalty rewards — playtime-milestone bonuses (money via `EconomyBridge` or arbitrary console commands with `{player}` substitution). Two tracks: lifetime (`milestones`) fires once per player ever, persisted in NBT; session (`sessionMilestones`) fires once per session and resets on every login.

## Inspirations

SUM has absorbed or been informed by the following upstream mods. License is noted so contributors know whether code may be paraphrased or only behaviorally referenced:

| Upstream mod | License | How SUM uses it |
|---|---|---|
| [EconomyInc](https://www.curseforge.com/minecraft/mc-mods/economy-inc) | upstream license unspecified — treated as ARR | Replaced. SUM's economy started as a runtime-compat layer (`EconomyBridge`) and now ships its own currency, bill items, ATMs, shops, and migration command. SUM-native code was clean-roomed, not copied. |
| [Pretty Beaches](https://modrinth.com/mod/pretty-beaches) by BlayTheNinth | MIT | Inspired the `beaches` feature — sand-near-water flooding behavior. MIT permitted close paraphrase of the algorithm; SUM's `BeachesHandler` follows the same approach (HarvestDropsEvent + scheduled-tick flood) with SUM-style structure. |
| [Server Pauser](https://modrinth.com/mod/serverpauser) by Smileycorp | LGPL-2.1 | Inspired the `pauser` feature — pauses world progression when no players are online. SUM uses the same approach as upstream: a Mixin on `WorldServer.tick()` HEAD that calls `ci.cancel()` when player count is zero. Mixin scaffolding is per-SUM (separate package, separate config); no upstream code copied. SUM is now a coremod-flavoured mod (Mixin via MixinBooter) — see `buildscript.properties` for `usesMixins=true` and `mixinsPackage=mixin`. |
| [Moving Quickly](https://www.curseforge.com/minecraft/mc-mods/movingquickly) / [GottaGoFast](https://github.com/thiakil/GottaGoFast) | upstream sources not pulled (closed CF, ARR-equivalent) | Inspired the `movement` feature — relaxes vanilla's "moved too quickly" thresholds in `NetHandlerPlayServer` so lag doesn't rubberband players. Upstream patches `100.0F`/`300.0F` constants via ASM; SUM uses `@ModifyConstant` Mixins on `processPlayer` and `processVehicleMove`, gated by config and using a configurable multiplier (default 10) instead of fixed replacement. No upstream code referenced. |
| [Custom Signposts](https://www.curseforge.com/minecraft/mc-mods/custom-signposts) by ThePavoReality | unknown — no public source | Inspired the `signpost` block — directional pointer post with up to 7 arms. Upstream has a GUI editor and target-coords mode; SUM v1 ships a chat-driven editor (`/sum signpost <add|remove|edit|clear>`) and angle-only mode. Block + TE + TESR are clean-room from the project description. |
| [Loyalty Rewards](https://modrinth.com/mod/loyalty-rewards) by Mrbysco | MIT | Inspired the `loyalty` feature — playtime-milestone rewards. Upstream's 1.12.2 source isn't in the repo (earliest branch is 1.15) so reference was architectural only. SUM replaces the upstream CraftTweaker dependency with native string-array config, persists per-player fired-milestone state in `PlayerPersisted` NBT, and routes money rewards through `EconomyBridge` so they work on either backend. |
| [Auto Dropper](https://modrinth.com/mod/auto-dropper) by Ruuubi | unknown — no public source | Inspired the `auto_dropper` block — drops continuously unless redstone-powered, items land without scatter. No public source available; SUM-native code is clean-room based on the project page description. Extends `BlockDropper` for vanilla GUI/inventory, overrides `neighborChanged` to disable vanilla's pulse-trigger, and adds an `ITickable` TE. |
| [World Border](https://github.com/Serilum/World-Border) by Serilum / Natamus | All Rights Reserved | Inspired the `border` feature — per-dim configurable borders with bounce or loop modes. License is restrictive so SUM does not reference upstream code; clean-room implementation works from a `PlayerTickEvent`, supports per-dim radius + mode, and throttles bounce/loop chat notifications to once every 3 seconds per player. |
| [Universal Tweaks](https://github.com/ACGaming/UniversalTweaks) by ACGaming | LGPL-3.0 | Reference only — no code absorbed. SUM examined UT's source to confirm UT's `B:"No Redstone Lighting"` toggle already covers the Dark Redstone use case, so SUM does not duplicate it. |

Other mods are evaluated periodically — see `docs/agent_progress/MERGE_MASTER_PLAN.md` for the current absorption queue.

## Developer Information

### IDE

The preferred development environment/IDE for the UIA SUM
is [IntelliJ IDEA](https://www.jetbrains.com/idea/download). It is recommended to use the latest
version of IntelliJ IDEA for the best development experience.

### Making Changes

To make changes to the UIA SUM, you will need to clone the project from the GitHub repository
and open it in your preferred IDE.

To learn more about the version control system, Git, please
see [https://git-scm.com/doc](https://git-scm.com/doc).

If you are using IntelliJ IDEA as your preferred IDE, you may use the built-in Git integration to
clone the project from the GitHub repository. To learn more about using Git integration with
IntelliJ IDEA, please see
[https://www.jetbrains.com/help/idea/using-git-integration.html](https://www.jetbrains.com/help/idea/using-git-integration.html).

### Submitting Changes

After making changes, you can push your modifications to GitHub on a new branch. To protect the
working code, modification of the `main` branch is not permitted except through pull request.

## Credits

### Active Developers

<img src="https://minotar.net/armor/bust/Akselhok/100.png" width="50"/>

**Name:** Alex<br/>
**GitHub Username:** mica-alex<br/>
**Minecraft Username:** Akselhok


<img src="https://minotar.net/armor/bust/AngelWingsPanda/100.png" width="50"/>

**Name:** Brandon<br />
**GitHub Username:** AngelWingsPanda<br />
**Minecraft Username:** AngelWingsPanda



