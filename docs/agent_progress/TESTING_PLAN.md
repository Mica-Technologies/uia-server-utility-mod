# SUM testing plan + feature reference

This is the canonical doc for SUM as of 2026-07-14 (the 2026-06-07 session
notes, formerly a root-level `TESTING_AND_NEXT_STEPS.md`, have been merged
in — see §4.11 and §8). It captures:

1. **Where everything lives** — block / item / command / HUD / network-slot
   inventory so a future Claude session or dev can find any feature without
   grep-spelunking
2. **Locked design decisions** — choices that have been made and shouldn't be
   re-litigated without an explicit ask
3. **Known bug fixes** — things that broke once, were fixed, and must not
   regress
4. **Per-feature playtest checklists** — verified items marked done with the
   commit that proved them out; unverified items with detailed step-by-step
   instructions
5. **Next-steps backlog** (§8) — features planned but not yet implemented

Sections 1–3 are reference; sections 4+ are the actual playtest workflow.
Tick boxes as you go. Add notes inline for anything that misbehaves so the
failure is captured before memory fades.

---

## 0. Pre-flight

Run each new playtest session against the latest `main`:

- [ ] Working tree clean: `git status`
- [ ] Build green: `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build`
- [ ] Launch dev client: `JAVA_HOME="..." ./gradlew runClient` (Windows/x86_64)
      OR `./gradlew runClient17` (Apple Silicon Mac via lwjgl3ify)
- [ ] On first dev launch, OneConfig stage0 loader prints
      `Updated OneConfig Loader!` / `Updated OneConfig Jar!` (cached on
      subsequent runs)
- [ ] F3 confirms `sum` in the loaded mods list

JDK path varies by OS — the `JAVA_HOME` examples assume Azul Zulu 17 installed
to the IntelliJ-managed `.jdks/azul-17.0.18` directory. Real path lives in
project memory; never commit it.

---

## 1. Reference: where everything lives

### 1.1 Working directory

`E:\gitRepos\uia-server-utility-mod` (Windows). 1.12.2 Forge, mod ID `sum`,
package `com.micatechnologies.minecraft.sum`. Version comes from git tags
(`YYYY.MM.DD` for releases).

Separate-repo gotcha: weather/snow features go in `E:\gitRepos\LDW2` (LDW2 /
Weather 2 Remastered fork) — NOT this repo.

### 1.2 Sub-packages and what's in each

| Package | What |
|---|---|
| `Sum.java` / `SumConstants.java` / `SumConfig.java` / `SumRegistry.java` / `SumTab.java` / `SumProxy.java` / `Sum{Client,Common}Proxy.java` | Mod root; lifecycle, registration, config, creative tab, proxies |
| `.afk` | AFK tracking (`AfkTracker`) — activity detection, sleep-vote exclusion, all-AFK world pause |
| `.atm` | ATM kit (kiosk/wall/drive-thru), `SumNetwork`, `SumGuiHandler`, atm GUI + packet |
| `.bank` | Bank counter, safe deposit box, vault door, velvet rope |
| `.beaches` | Pretty-Beaches absorption (`BeachesHandler`) |
| `.border` | World-border (`BorderHandler`, `BorderEntry`) |
| `.command` | `CommandSum`, `CommandBalance`, `CommandPay` |
| `.economy` | `EconomyBridge` facade, SUM-native money capability, bill items + packet items, bill changer, bills display, phone + debit card item (`ItemAccountAccess`), bills loot injector, EconomyInc migration command logic |
| `.favorites` | Creative-tab favorites system (`FavoriteKey`, `FavoritesStore`, `CreativeTabFavorites`, `FavoritesClientHandler`, `FavoritesKeybinds`) |
| `.huds` | ~30 OneConfig HUDs + `TextPlaceholders` helper for `CustomTextHud` |
| `.huds.presets` | `HudPresets`, `HudStyle`, `HudLayout` (8 styles × 17 layouts) |
| `.huds.snapshot` | Server→client player-status bridge for SUM-data HUDs (slot 14) |
| `.jobs` | Job board block + listings + GUI + escrow/accept/complete loop (`PacketOpenJobBoard` snapshot push) |
| `.loyalty` | Loyalty rewards (milestones + handler) |
| `.mailbox` | Mailbox block + claim/deposit/inbox GUI |
| `.mixin` | `SumCoreMod` (IFMLLoadingPlugin), `MixinWorldServer`, `MixinNetHandlerPlayServer` |
| `.pauser` | (Empty per Mixin rewrite; logic lives in `MixinWorldServer`) |
| `.phone` | Phone item + GUI shell (incl. the "Pay" app, personal phones only), desk phone TE, phone cloud (notes/contacts/messages saved-data), area-code allocation |
| `.plots` | Plot data + saved-data + chunk index + wand + protection events + `/sum plots gui` browser |
| `.pocket` | Pocket inventory (3-slot capability + GUI), `PocketHud` (favorites preview), `SumOneConfig` root config |
| `.roamer` | Roamer NPC entity, AI tasks, shelter cache, role enum, atlas + persona variants, render |
| `.roadrunner` | Speed-boost-on-block handler |
| `.serverconfig` | Server-config mirror — snapshot, packet (slot 17), bridge (login push), client mirror, GUI viewer |
| `.shop` | Player vending-machine shop block + owner/buyer GUIs + sync packets |
| `.signpost` | (Empty — feature moved to CSM) |
| `.trash` | Trash can block + ephemeral GUI |

### 1.3 Blocks (all `sum:` prefix)

| Registry name | Class | What |
|---|---|---|
| `atm_kiosk` / `atm_wall` / `atm_drive_thru` | `BlockAtm*` | Three ATM variants. All open `GuiSumAtm`. |
| `bank_counter` | `BlockBankCounter` | Decorative full-cube counter |
| `safe_deposit_box` | `BlockSafeDepositBox` | Wall-mounted 3×3 per-(player, position) inventory |
| `velvet_rope` | `BlockVelvetRope` | Multipart-blockstate decorative stanchion with auto-connecting rope segments |
| `vault_door` | `BlockVaultDoor` + TE | Single-block passcode-locked door, auto-close after 5s |
| `shop` | `BlockSumShop` + TE | Player vending machine (template + 3×3 stock, owner + buyer GUIs) |
| `mailbox` | `BlockMailbox` + TE | Claim-on-first-rightclick; deposit-only for non-owners |
| `job_board` | `BlockJobBoard` | Server-wide job listings; opens `GuiJobBoard` |
| `trash_can` | `BlockTrashCan` | Ephemeral 9-slot inventory destroyed on close |
| `storm_shelter_sign` | `BlockStormShelterSign` + TE | Wall-mounted plaque; registers position with `RoamerShelterCache` |
| `bill_changer` | `BlockBillChanger` + TE | Bundle 64 bills → packet; unbundle packet → 64 bills |
| `bills_display` | `BlockBillsDisplay` + TE + TESR | Decorative bills tray with rotating-sheet render |
| `auto_dropper` | `BlockAutoDropper` + TE | Drops items on cadence unless redstone-powered |
| `desk_phone` | `BlockDeskPhone` + TE | Shared phone with chunk-derived number |

### 1.4 Items (all `sum:` prefix)

| Registry name | What |
|---|---|
| `bill_1` / `bill_5` / `bill_10` / `bill_20` / `bill_50` / `bill_100` / `bill_200` / `bill_500` | 8 SUM bill denominations |
| `packet_1` … `packet_500` | 8 packet items (one per denom), each = 64 bills of that denom |
| `phone` | `ItemAccountAccess` instance — NBT-binds to first right-clicker; right-click again opens phone GUI |
| `debit_card` | `ItemAccountAccess` instance — same bind pattern; right-click opens `GuiSumAtm` directly |
| `roamer_spawn_egg` | `ItemRoamerSpawnEgg` |
| `business_card` | Personalized contact card (right-click air to set; right-click player to give) |
| `plot_wand` | `ItemPlotWand` — sneak+right-click = corner A, right-click = corner B |

### 1.5 Commands

`/sum <subcommand>` is the umbrella; some subcommands need op-2.

| Command | Perm | What |
|---|---|---|
| `/sum help [page]` | anyone | Paginated help, 7 pages |
| `/sum reloadconfig` | op | Reloads `sum.cfg` |
| `/sum addroamerblock <block>` / `/sum rmroamerblock <block>` | op | Add/remove from roamer-walkable list |
| `/sum roamer greet <add\|list\|clear> <target> [message]` | op | Roamer greetings |
| `/sum roamer role <set\|get> <target> [role-id]` | op | Roamer role (generic, bank_teller) |
| `/sum favorites <list\|clear\|export\|importfile\|file>` | op | Creative-favorites debug |
| `/sum econ <balance\|add\|set> [player] [amount]` | op | Admin balance manipulation |
| `/sum vault <unlock\|setcode\|info\|disown> [code]` | mixed | Vault-door interactions (ray-trace targeted) |
| `/sum migrate-economy [verify]` | op | EconomyInc → SUM player-data migration |
| `/sum job <post\|list\|clear-mine> ...` | anyone | Job board (server-wide) |
| `/sum plots <create\|delete\|list\|info\|buy\|sell\|trust\|untrust\|transfer\|gui> ...` | mixed | Plots (create/delete are op) |
| `/sum sealevel` | anyone | Reports current dim's sea Y (debug aid for beaches testing) |
| `/sum beaches debug` | op | Toggle chat-spam on each beaches match (debug aid) |
| `/balance [player]` | anyone self / op for others | User-facing balance |
| `/pay <player> <amount>` | anyone | Player-to-player transfer; optional fee via `pay.feePercent`; tab-completes online players |

### 1.6 SumNetwork packet slots (`SumNetwork.init()`)

| Slot | Packet | Direction |
|---|---|---|
| 0 | `AtmPacketTransaction` | C→S |
| 1 | `PacketSyncSumMoney` | S→C |
| 2 | `PacketShopOwnerAction` | C→S |
| 3 | `PacketShopBuy` | C→S |
| 4 | `PacketBillChangerAction` | C→S |
| 5 | `PacketJobAction` | C→S |
| 6 | RESERVED (was Signpost — moved to CSM) | — |
| 7 | RESERVED (was PhonePacketNotesSync — replaced by `PhoneCloudAction.UPDATE_NOTES`) | — |
| 8 | `PhoneCloudFetchRequest` | C→S |
| 9 | `PhoneCloudSync` | S→C |
| 10 | `PhoneCloudAction` | C→S |
| 11 | `PacketAddFavorite` | S→C |
| 12 | `PacketSyncPocket` | S→C |
| 13 | `PacketOpenPocketGui` | C→S |
| 14 | `PacketSyncPlayerStatus` | S→C |
| 15 | `PacketOpenPlotBrowser` | S→C |
| 16 | `PacketPlotAction` | C→S |
| 17 | `PacketSyncServerConfig` | S→C |
| 18 | `PacketOpenJobBoard` | S→C |

Next free slot: **19**. Update `SumNetwork.java` AND this table when claiming.

### 1.7 SumGuiHandler IDs

| ID | What |
|---|---|
| 0 | `GUI_ATM` |
| 1 | `GUI_SAFE_DEPOSIT` |
| 2 | `GUI_SHOP_OWNER` |
| 3 | `GUI_SHOP_BUYER` |
| 4 | `GUI_BILL_CHANGER` |
| 5 | `GUI_TRASH_CAN` |
| 6 | `GUI_MAILBOX` |
| 7 | RETIRED (was `GUI_JOB_BOARD` — job board now opens via `PacketOpenJobBoard`, slot 18, so the GUI gets a server-built snapshot instead of reading client-side `WorldSavedData`) |
| 8 | RESERVED (was Signpost — moved to CSM) |
| 9 | `GUI_DESK_PHONE` |
| 10 | `GUI_POCKET` |

Next free ID: **11**. Note that the plot browser does NOT use a GuiHandler ID
— it's opened directly via packet→`displayGuiScreen`, since the data lives
server-side and is pushed via `PacketOpenPlotBrowser`.

### 1.8 HUDs (declared in `SumOneConfig`, ~30 total)

| Category | Subcategory | HUDs |
|---|---|---|
| HUDs | Information | Coords, Facing Direction, Clock, Biome, Day Counter, Real-Life Date, Server IP, Resource Pack, Game Mode, Block Above, Height Limit, Looking-At Block, Light Level |
| HUDs | Player | Pitch, Yaw, Speed, Saturation, Armor Durability, Health, Hunger, Experience, Active Effects, Tool Durability |
| HUDs | Performance | FPS, Memory, Ping, TPS, Frame Time |
| HUDs | SUM | Wallet, Phone Number, Pocket (text), Nearest Roamer, Border Distance, Bank Balance, Plot Info, Jobs Available, Loyalty |
| HUDs | Counters | CPS, Click Counter, Blocks Placed, Session Playtime |
| HUDs | Custom Text | Custom Text 1 through 10 (10 slots, support `%placeholder%` tokens) |
| Favorites | HUD | Favorites HUD (top 3 favorites preview + `Z:` keybind label) |

Custom-text placeholders: `%player%`, `%x%`, `%y%`, `%z%`, `%dim%`, `%biome%`,
`%server%`, `%time%` (in-game 24h), `%realtime%` (wall-clock), `%date%`,
`%fps%`, `%direction%` (N/E/S/W). Unknown tokens left intact so typos are
visible.

### 1.9 OneConfig presets

| Type | Count | Default |
|---|---|---|
| Style presets | 8 | Default, Minimal Brackets, Clean Professional, Stylish Glass, Compact Light, Retro Terminal, Realistic Game HUD, High Contrast |
| Layout presets | 17 | Off, Vanilla+, Survival Essentials, Speedrunner, PvP Focus, Builder, Explorer, Performance Watcher, Time Tracker, Minimal Top, Server Op, Urban Builder, City Explorer, Architect, Cityscape Photographer, Explorer 2 (SUM) **← modpack default**, Combat Pro |

Apply via OneConfig → SUM → Presets → Style/Layout dropdown → Apply button.
`HudPresets.applyStyle` auto-reflows the current layout afterward so a style
swap re-spaces rows without a second click.

### 1.10 SumConfig categories (server-side, lives in `run/config/sum.cfg`)

| Category | Knobs |
|---|---|
| `roamer` | `walkableBlocks` (string list) |
| `roadrunner` | `speedBlocks` (string list, `block=multiplier`) |
| `favorites` | `enableStarOverlay` (migrated to OneConfig; Forge config still loaded as fallback) |
| `border` | `enabled`, `borders` (string list, `dimId=radius:mode` where mode is bounce/loop) |
| `autodropper` | `enabled`, `tickInterval` |
| `loyalty` | `enabled`, `milestones`, `sessionMilestones` (string list, `minutes=type:value`) |
| `movement` | `toleranceEnabled`, `toleranceMultiplier` |
| `pauser` | `enabled` |
| `beaches` | `enabled`, `affectedBlocks`, `animatedFlooding`, `infiniteBucketWater`, `realisticErosion` |
| `sleep_vote` | `enabled`, `thresholdPercent`, `actionBarProgress` (live action-bar progress line; false = legacy chat-on-change) |
| `afk` | `enabled`, `thresholdSeconds` (default 300), `excludeFromSleepVote`, `announce`, `pauseWorldWhenAllAfk` |
| `pay` | `enabled`, `feePercent` (default 0) |

After edits: `/sum reloadconfig` rereads the file. The Server Config Viewer
GUI is per-login — re-login to see changes there.

---

## 2. Locked design decisions

These were settled with Alex; don't re-litigate without an explicit ask.
Each line is one decision + brief why.

### Economy / bank kit (Sections A + C)

- **Full replacement of EconomyInc** (Approach 3). SUM owns the economy;
  EconomyInc is optional compat via `EconomyBridge`.
- **Vault-cracker minigame: skipped permanently.** PvP burglary doesn't fit
  the theme.
- **Credit card UX: both phone and classic card** ship as separate items
  (`sum:phone`, `sum:debit_card`), both `ItemAccountAccess` instances.
- **Vault door passcode UI: chat-driven** (`/sum vault unlock <code>`);
  in-GUI text field deferred.
- **Safe deposit box: 3×3** single-chest equivalent, per-(player, position).
- **Player shop: vending-machine aesthetic** + slot stock + admin
  infinite-stock flag + NBT-exact item matching. TESR for the floating
  in-glass item deferred.
- **Bills coexistence:** when EconomyInc is loaded, ATM produces EconomyInc
  bills (compat); deposits accept both. When EconomyInc is absent, SUM owns
  everything.

### Plots (Section D)

- **Admin-only creation** for v1; players can buy + transfer + trust.
- **Pricing: flat dollar.** Per-block formulas deferred.
- **Y bounds: full column** by default (y=0..255). Custom 3D claims
  supported by direct construction.
- **Wand gesture: sneak+right-click for corner A, right-click for corner B.**
  Left-click was tried + abandoned due to 1.12.2
  client-cancel-suppresses-server-packet bug.
- **D6 GUI shipped** (`/sum plots gui`); **D7 (rental flow) and D8
  (EconomyInc-plot migration) dropped** — D7 had no real need, D8 had
  nothing to migrate.

### HUDs (Section E)

- **OneConfig-backed, not bespoke.** All HUD persistence, drag-to-reposition
  UX, per-HUD knobs go through OneConfig. Don't reinvent storage / editor.
- **HUD layout presets are a fixed catalog** (17 layouts × 8 styles in
  `HudPresets`). User-saved layouts NOT implemented — discussed as future
  expansion but punted.
- **Pocket-inventory-into-vanilla-GuiInventory was scrapped.** Mixin-based
  slot injection was unreliable. Replacement: auto-switch to favorites tab
  on creative open + `PocketHud` repurposed as favorites preview HUD.
  Pocket inventory itself (3-slot cap + P-key GUI) kept.
- **Bank balance "tally" semantics:** `BankBalanceHud` sums bill values
  across all safe-deposit boxes the player owns in their current dim.
  Non-bill items don't contribute.
- **Snapshot-driven SUM HUDs lag up to 2s** (push cadence). Acceptable for
  plot/balance/jobs/loyalty — they change slowly. Don't try to make them
  realtime.
- **Custom text uses 10 fixed slots + placeholder substitution**, not
  EvergreenHUD's full dynamic HudList framework. Placeholders give per-slot
  power that obviates dynamic count.
- **Server-config mirror: Button + custom GuiScreen**, not `@Info` fields.
  OneConfig's `@Info.text()` is a compile-time literal so it can't display
  dynamic values. Snapshot is one-shot per login.

### Roamer / storm-shelter

- **Signed shelters preferred for already-indoor roamers** within 48h/6v
  range. No claim limit on signed shelters (designed to attract a crowd).
- **Path tolerance: 2 consecutive sky-exposed nodes** for doorways/skylights.

### Merged-from-upstream mods (Section M)

- **Replacement, not compat.** Pull each upstream jar after the SUM phase
  ships + playtests cleanly. (EconomyInc was the exception — pre-existing
  player data.)
- **No `@Optional` bridges** in new code.
- **`sum:` namespace only** for new registry entries.
- **Loyalty Rewards: native config, not CraftTweaker.**
- **Server Pauser: Mixin on `WorldServer.tick()`**, gated by config flag.
  (Was gamerule-toggle initially; reworked to Mixin to match upstream
  pause-everything semantics.)
- **Moving Quickly: `@ModifyConstant` Mixin** on
  `NetHandlerPlayServer.processPlayer` (100.0F + 300.0F) and
  `processVehicleMove` (100.0D), gated by config flag, default multiplier 10×.
- **SUM is a coremod via MixinBooter** (`mixin.SumCoreMod` IFMLLoadingPlugin).
  Reversal of the original "no coremod" rule, locked 2026-05-09.

### Scope

- **Teleport/utility commands** (`/home`, `/tpa`, `/back`, `/warp`, `/spawn`)
  are **out of scope by decision** — handled by **ForgeEssentials** in the
  Alto pack; intentionally not duplicated in SUM.

### Build / packaging

- **OneConfig wrapper embedded into the published jar** (Task 14). End users
  installing SUM alone get OneConfig auto-bootstrapped — no separate
  OneConfig-Bootstrap install needed. `TweakClass:
  cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker` + `TweakOrder:
  0` in SUM's manifest, classes unpacked from the wrapper via the `embed`
  Gradle configuration.
- **CSM resolved via GitHub Releases Ivy repo** in `repositories.gradle`,
  pinned by `gradle.properties#csmVersion`. Replaces the brittle
  sibling-checkout / CI `gh release download` setup.
- **JDK: Azul Zulu 17.** Path is project-local; never commit real
  usernames — use `<username>` placeholder in any committed file.

---

## 3. Known fixed bugs (don't reintroduce)

Each entry: commit + symptom + fix mechanic. If a future change in the same
area, double-check it doesn't undo one of these.

| Commit | What broke | Fix |
|---|---|---|
| `7c74d4e` | Safe deposit box wiped items on reopen | `initialized` flag in `InventorySafeDeposit` to gate the constructor's pre-populate write-back |
| `59cf108` | Velvet rope crashed on registry | Added `getStateFromMeta`/`getMetaFromState` overrides since the 4 directional bools aren't in metadata |
| `15192f2` | All 8 SUM bill model JSONs had literal `\n` characters | Rewrote with real newlines. Watch out for any toolchain that double-escapes JSON heredocs |
| `c9a40e2` | Roamer role had no visible effect | `setRole` now auto-names unnamed roamers — both shows the role above the head AND unlocks greetings (which gate on `hasCustomName()`) |
| `03f715d` | Phone / debit card 2nd right-click did nothing | `player.openGui` silently drops the OPEN_GUI packet when called from `Item.onItemRightClick` in 1.12.2. Use `Sum.proxy.openAccountAccessGui` (direct `displayGuiScreen`) instead |
| `25e1bf8` | Plot wand reported "Corner B" on both clicks | `LeftClickBlock` event is unreliable in 1.12.2 (client cancel suppresses server packet). Switched to sneak+right-click for corner A |
| `6866ebc` | Storm-shelter sign converted to wall-mounted; AI heuristic loosened | Range 24→48, dropped claim check on signed shelters, path tolerance 0→2 consecutive sky-exposed nodes |
| `ebea593` | OneConfig save StackOverflow #1 — missing `initialize()` call | `SumOneConfig` constructor must call `initialize()` (Config base does NOT auto-init) |
| `ebea593` | OneConfig save StackOverflow #2 — non-transient `INSTANCE` self-reference | `public static transient SumOneConfig INSTANCE` (OneConfig's Gson exclusion only filters TRANSIENT, not STATIC) |
| `ebea593` | OneConfig save StackOverflow #3 — static helper consts in HUDs (`DateTimeFormatter` etc.) | `SumOneConfig.addGsonOptions` overridden to restore Gson's default `STATIC \| TRANSIENT` exclusion |
| `f1dc77c` | Layout-apply bottom-flush bug | `HudPresets.applyLayout` was passing `screenHeight=1077` instead of `1080` to `Position.setPosition` |
| `f1dc77c` | HUD title double colons (`"HP::"`) | OneConfig's `SingleTextHud.getCompleteText` auto-appends `": "`; stripped trailing colons from all 30 HUD titles |
| `f1dc77c` | Mixin config not loading in dev | MixinBooter only auto-discovers configs via jar MANIFEST attributes. Dev launches had no manifest. Fix: `SumCoreMod` IFMLLoadingPlugin + `coreModClass` in `buildscript.properties` |
| `f1dc77c` | Favorites HUD icons overflowed at small style scales (Realistic Game HUD 0.4×) | `renderItemAndEffectIntoGUI` always paints 16×16; wrapped with `GlStateManager.scale` to size to the slot frame |
| `a594a2e` | Job board showed no listings on a dedicated server | GUI read `JobBoardSavedData` client-side, which is empty off the integrated server. Replaced the `openGui` path (GUI id 7, now retired) with `PacketOpenJobBoard` (slot 18) pushing a server-built snapshot |

### Brittle areas to be careful in

- **`SumNetwork.init()`** registers packets in fixed-discriminator slots —
  changing slot numbers breaks live client/server connections. Only ever
  ADD at the next free slot (currently **18**), never reuse a hole.
- **`SumOneConfig` field names** are JSON keys in the user's saved config —
  renaming = breaking user-saved positions / values. Add new fields with
  new names; never rename in place.
- **`HudPresets.applyLayout`** must call `position.setSize(0, 0)` BEFORE
  `setPosition(x, y, 1920f, 1080f)`, otherwise OneConfig's anchor math
  computes against the last-rendered width and HUDs drift on re-apply.
- **Reference frame for layout positions is `1920×1080`** — pass `1080f`
  (not the cursor value!) as `screenHeight` arg to `Position.setPosition`.
- **`oneconfig-1.12.2-forge` is `compileOnly` only.** Putting it on the
  dev runtime classpath crashes launch — its manifest declares a TweakClass
  not present in the jar (the real one is downloaded by the wrapper).
- **In Forge 1.12.2, don't guard `player.openGui` with `!world.isRemote`
  for non-Container GUIs** — both sides need to fire (memory:
  `feedback_forge_screen_only_gui`).
- **Screen-only GUIs must not read server-side `WorldSavedData` from the
  client** — it's empty on a dedicated server (the job-board bug, `a594a2e`).
  Any GUI whose data lives in saved-data needs a server-pushed snapshot
  (packet → `displayGuiScreen`, like the plot browser and job board). Worth a
  quick audit if any other screen looks empty on the dedicated server.

---

## 4. Per-feature playtest checklists

Items already verified are marked with the commit that proved them out — you
only need to re-test them if a recent change might have regressed the area
(see "Regression checks" near the end).

A 🧪 marker means the underlying logic is now locked by an automated unit test
(see § 4.0). 🧪 covers the pure logic only — it does NOT replace the in-game
behavior check (rendering, commands, world events), so those boxes stay open
until a playtest confirms the full path.

### 4.0 Automated unit-test coverage (`src/test`, 318 tests)

Run with `JAVA_HOME="..." ./gradlew test`. JUnit 5 is enabled
(`enableJUnit=true`); the suite is pure-logic only — no Minecraft runtime — so
it runs in seconds and gates regressions in CI. All 318 green as of 2026-07-18
(expanded from 83 in the 2026-07-18 unit-test push).

**Testing approach.** Three access patterns are used throughout: (1) pure public
static methods tested directly; (2) MC-dependent methods where the pure math is
extracted into a helper (e.g. `PhoneFormat`, `HudFormat`, `MoneyTransfer
.computeAmounts`, `BorderHandler.clampToInset`, `TileEntityShop.breakIntoBills`)
and the live method delegates to it; (3) private static parsers/data reached via
reflection. `NBTTagCompound`, `BlockPos`, netty `ByteBuf`, Forge `ByteBufUtils`,
`WorldSavedData`/`TileEntity` (with a null world → `markDirty` no-op), and
`TextComponent*` all work off-runtime; **OneConfig** types do not (compileOnly),
so HUD classes and `HudPresets` can't be loaded in tests — hence the standalone
`HudFormat`.

Original core (still green):
- [x] **Plot bbox math** (`SumPlotTest`, 13) · **plot store/overlap**
      (`SumPlotsWorldSavedDataTest`, 12) · **chunk index**
      (`SumPlotsChunkIndexTest`, 9) · **plot status** (`PlotStatusTest`, 5)
- [x] **Config parsers** (`SumConfigParsersTest`, 15) — border `dimId=radius:mode`,
      loyalty `minutes=type:value`, **roadrunner `block=multiplier`** (new)
- [x] **Bill loot invariants** (`BillsLootInjectorTest`, 6) · **bill tables**
      (`BillsTest`, 7 — +withdraw-subset + registry-alignment)
- [x] **Custom-text placeholders** (`TextPlaceholdersTest`, 11) · **desk-phone
      exchange** (`DeskPhoneExchangeTest`, 4) · **border model**
      (`BorderEntryTest`, 2) · **sleep-vote math** (`SleepVoteMathTest`, 7 —
      +`ticksUntilMorning`)

2026-07-18 expansion (~235 new tests):
- [x] **economy/atm** — `DefaultSumMoney` (clamp/overdraft/NBT),
      `PersonalItemsSavedData` (bitmask NBT), `MoneyTransfer` (round/fee/clamp),
      `TileEntityBillChanger.describe`, three packet round-trips
- [x] **phone** — cloud `Message`/`MessageThread` (FIFO evict)/`Contact`/
      `PhoneCloudData`/`PhoneCloudSavedData` (number format + allocation)/
      `PhoneCloudAction` (byte round-trip); `PhoneTextField` state machine;
      `PhoneFormat` (clock/calc/brighten/firstLine)
- [x] **jobs** — `JobListing` (lifecycle + NBT + ByteBuf), `JobStatus`,
      `JobBoardSavedData` (filter/sort/take/sweep), `JobListing.formatRemaining`
- [x] **roamer** — `RoamerRole`, `RoamerExitCache` + `RoamerShelterCache`
      (dedup/range/priority geometry)
- [x] **favorites/pocket/bank** — `FavoriteKey`, `FavoritesStore` (version
      counter), `PocketInventory.Slot`, `TileEntityVaultDoor` (SHA-256 passcode)
- [x] **serverconfig/huds** — `ServerConfigSnapshot` (versioned wire),
      `ServerConfigBridge` formatters, `PlayerStatusSnapshot`,
      `PacketSyncPlayerStatus`, `HudLayout` builder, `HudFormat` (13 formatters
      incl. `direction`)
- [x] **loyalty/border/shop/contacts** — `LoyaltyMilestone`/`LoyaltyHandler`,
      `BorderHandler` geometry, `TileEntityShop` (describe/clamps/breakIntoBills),
      `ItemBusinessCard.isPersonalizedTag`

> ✅ **Fixed during the expansion:** `DirectionHud`'s octant math
> `Math.round((yaw+22.5)/45)` double-shifted the bucket offset — due south
> rendered "SW", north "NE" (every facing read 45° clockwise). Changed to
> truncation `(int)((yaw+22.5)/45)`, extracted to `HudFormat.direction`, and
> covered by compass tests. See §6 bug log.

### 4.1 Section A — Bank / ATM kit (mostly verified)

| Item | Status | Verification |
|---|---|---|
| ATM kiosk / wall / drive-thru — place, render, open GuiSumAtm | ✅ | Pre-2026-05-08 playtest |
| ATM withdraw produces bills; deposit consumes them | ✅ | Pre-2026-05-08 |
| Bank counter — place, render | ✅ | Pre-2026-05-08 |
| Safe deposit box — per-player inventory, persists across reload | ✅ | `7c74d4e` fix verified |
| Velvet rope — color + auto-connecting segments | ✅ | `f3d63d1` / `59cf108` fixes verified |
| Vault door — claim, passcode unlock, auto-close, owner override | ✅ | Pre-2026-05-08 |
| Bank teller Roamer role | ✅ | `c9a40e2` fix verified |

No re-test needed unless touching the relevant files.

### 4.2 Section B — Server utility blocks (mostly verified)

| Item | Status | Verification |
|---|---|---|
| TR Trash can — ephemeral 9-slot, destroys on close | ✅ | Pre-2026-05-08 |
| ST Storm-shelter sign — wall placement, registers in cache | ✅ | `6866ebc` fix verified |
| ST Multi-roamer convergence on signed shelter | ☐ **untested** | See section 4.7 below |
| SV Sleep voting — threshold-met → night skips | ✅ | Pre-2026-05-08 |
| BC Business cards — personalize / give / view | ✅ | Pre-2026-05-08 |
| MX Mailbox — claim on first right-click; deposit-only for non-owners | ✅ | Pre-2026-05-08 |
| JB Job board — listings, post/list/clear-mine, GUI Remove button | ✅ (superseded) | Pre-2026-05-08 — the `a594a2e` escrow rework replaced the GUI open path and buttons; re-test via §4.11.4 |

### 4.3 Section C — SUM economy (mostly verified)

| Item | Status | Verification |
|---|---|---|
| C1 `/sum econ add 100` → `/balance` reports $100, persists | ✅ | Pre-2026-05-08, with AND without EconomyInc |
| C2 Bills render correctly | ✅ | `15192f2` fix verified |
| C3 `/balance` non-op self-query + op `/balance <player>` | ✅ | Pre-2026-05-08 |
| C4 Phone + debit card bind + 2nd-click opens GUI | ✅ | `03f715d` fix verified |
| C5 Player shop — owner setup, buyer purchase | ✅ | Pre-2026-05-08 |
| C6 Bill changer — bundle 64 → packet, unbundle | ✅ | Pre-2026-05-08 |
| C7 Bills in vanilla chest loot drops | 🧪 invariant locked / ☐ in-game | Weighting + $200/$500-excluded proven by `BillsLootInjectorTest`; world-gen path still untested — see 4.5 |
| C8 Bills display — TESR + insert/take | ✅ | Pre-2026-05-08 |
| C9 `/sum migrate-economy` end-to-end | ⏳ deferred | Run on Alex's server when convenient |

### 4.4 Section D — Plots (D1–D6, **all need in-game testing**)

This is the biggest untested chunk. Setup once:

- [ ] Op yourself: `/op <yourname>` (dev integrated server auto-ops, but
      check `op` permission via `/sum plots create` succeeding before
      assuming)
- [ ] Get a wand: `/give @s sum:plot_wand`
- [ ] Give yourself dollars: `/sum econ add <yourname> 1000`

#### D2 — Wand corner-A/B selection (post-`25e1bf8` fix)

- [ ] Sneak + right-click block at known position A → chat "Corner A set
      at (X, Y, Z)" (or similar) — the wand stores corner A in NBT
- [ ] Right-click (no sneak) block at known position B → chat "Corner B
      set at (X, Y, Z)"
- [ ] Hover the wand in inventory: tooltip shows BOTH corners with the
      correct coords. Critically — NOT both showing "Corner B" (that was
      the bug fixed in `25e1bf8`)
- [ ] Sneak + right-click a different block → corner A updates; corner B
      preserved
- [ ] Drop wand into a chest, take back out → selection still in NBT

#### D2 — `/sum plots create` (op)

- [ ] With both corners set: `/sum plots create test1 100` → success
      message; plot status = FOR_SALE (price > 0)
- [ ] `/sum plots create test2` (no price) → status RESERVED (price = 0)
- [ ] `/sum plots create test_overlap 50` with selection overlapping an
      existing plot → refused with overlap error
      🧪 overlap detection (incl. reversed corners, per-dim, touching faces)
      covered by `SumPlotsWorldSavedDataTest`; only the command wiring is left
- [ ] `/sum plots create` with no wand selection → refused with usage hint

#### D2 — `/sum plots delete` (op)

- [ ] `/sum plots delete test2` (or use id-prefix) → chat confirms deletion
- [ ] `/sum plots delete <bogus-prefix>` → chat "no plot matches"

#### D2 — `/sum plots list` / `info`

- [ ] `/sum plots list` → all plots in current dim, with id-prefix,
      name, owner, status, price
- [ ] `/sum plots list near` → only plots within ~64 blocks of player
- [ ] `/sum plots info <id-prefix>` → name, owner, corners, volume,
      price, trusted-builder count
      🧪 `volume()` math (inclusive on all axes, no int overflow) covered by
      `SumPlotTest`
- [ ] Switch to nether (`/execute in minecraft:the_nether run tp ~ ~ ~`
      doesn't exist in 1.12.2 — just use a nether portal) → `/sum plots
      list` shows empty (per-dim filter)

#### D3 — `/sum plots buy / sell`

- [ ] `/sum plots buy test1` (a FOR_SALE plot you can afford) → $100
      deducted, you become owner, status flips to OWNED. Verify via
      `/balance` and `/sum plots info test1`.
- [ ] Re-attempt `/sum plots buy test1` → refused ("isn't for sale")
- [ ] Travel to a different dim, attempt `/sum plots buy <plot-in-overworld>`
      → refused ("travel there to buy it")
- [ ] `/sum econ set <yourname> 0`, then `/sum plots buy <something>` →
      refused ("insufficient funds")
- [ ] `/sum plots sell test1 50` → re-lists at $50, status flips back to
      FOR_SALE
- [ ] As non-owner: `/sum plots sell <plot-not-yours> 50` → refused
      ("not your plot" or similar)

#### D4 — Plot protection (the load-bearing feature for plots)

Setup: have a plot you own AND a plot owned by someone else. Easiest in
single-player: buy two plots, then `/deop <yourname>` so you're treated as
non-op for the second. (Trust list is empty on both.)

- [ ] Inside YOUR plot (after deop, still owner): break + place blocks
      freely
- [ ] Inside the OTHER plot (after deop, not trusted): break attempt
      cancelled with chat "you can't build/break here" or similar; block
      stays
- [ ] Re-op (`/op <yourname>`): break attempt now succeeds (op bypasses via
      `sum.plots.bypass`, auto-granted to op-2+)
- [ ] Place TNT inside someone else's plot, light it. Blocks INSIDE the
      plot survive; blocks OUTSIDE can be destroyed
- [ ] Spawn a creeper inside someone else's plot, let it detonate — no
      plot damage
- [ ] Try right-clicking a chest inside someone else's plot (if your config
      protects interaction): should be cancelled OR allowed depending on
      what `PlotsProtectionHandler` covers. Document actual behavior in the
      notes column.

#### D5 — Trust / untrust / transfer

- [ ] As owner: `/sum plots trust test1 <other-player>` → confirmed; trust
      count in `/sum plots info` increments
- [ ] Other player (or you on the integrated server with another fake-deop'd
      identity) places/breaks inside the plot → allowed
- [ ] `/sum plots untrust test1 <other-player>` → confirmed; protection
      re-engages
- [ ] `/sum plots transfer test1 <other-player>` → ownership flips; trust
      list carries over (verify by re-trusting then transferring)

#### D6 — Plot browser GUI (NEW in `cf241e5`)

Setup: have ≥3 plots FOR_SALE in your current dim at different prices.
At least one should be unaffordable (set price > your balance).

- [ ] `/sum plots gui` → opens `GuiPlotBrowser` paginated 4-per-page
- [ ] Title bar shows "Plots For Sale"; top-right shows your current
      wallet in `$X.XX` format (matches `/balance`)
- [ ] Each row shows: plot name (truncated if long); price (GREEN if
      affordable, RED if not); bbox-center coords (`@X, Y, Z`); volume
      (e.g. `vol 65k`); "was <previous-owner>" or "unowned"; `Xm away`
      distance from player
- [ ] Affordable rows: Buy button enabled. Unaffordable: Buy button
      grayed out
- [ ] Click Buy on an affordable row → chat says "Bought <name> for
      $X.XX"; GUI refreshes in place (that row gone, page count updated,
      wallet updates next frame). YOU DID NOT NEED TO REOPEN THE GUI.
- [ ] Open GUI, in a separate chat run `/sum econ set <yourname> 0`
      (use F3+T or a second window), then click Buy on a row whose
      price you can no longer afford → chat "Insufficient funds. Need
      $X, have $Y." GUI stays open with that row still visible
- [ ] Open GUI, run `/sum plots delete <one-of-the-displayed-plots>` in
      chat, click Buy on it → "That plot no longer exists." Snapshot
      refresh removes the row
- [ ] Prev / Next pagination works; page counter "Page 2 / 3" updates;
      Prev grayed out on page 1, Next grayed out on last page
- [ ] Close button closes the GUI; ESC also closes
- [ ] In a dim with zero FOR_SALE plots: GUI opens with "No plots for
      sale in this dimension." + hint text below
- [ ] Switch dimensions, run `/sum plots gui` → GUI now shows the OTHER
      dim's FOR_SALE plots (filter is per-current-dim, refreshed each
      time the command runs)

### 4.5 Section C7 — Bill loot drops

`BillsLootInjector` adds bills to 13 vanilla chest tables. Heavy weighting
toward small denoms; **$200 and $500 should never world-gen**.

- [ ] Use `/locate Temple`, `/locate Stronghold`, `/locate Mansion`,
      `/locate Mineshaft` to find chest-bearing structures; or set
      `level-seed` to one you know has dungeons
- [ ] Open ≥10 chests across different structure types
- [ ] At least one chest should contain a SUM bill (`sum:bill_1` /
      `sum:bill_5` / `sum:bill_10` / `sum:bill_20` / `sum:bill_50` /
      `sum:bill_100`)
- [x] 🧪 Small denoms ($1, $5, $10) vastly more common than $50 / $100 —
      weight table is strictly descending by denomination
      (`BillsLootInjectorTest.smallerDenominationsHaveStrictlyHigherWeight`).
      Still worth an eyeball in-game, but the rule can't silently regress now.
- [x] 🧪 **NEVER** see `sum:bill_200` or `sum:bill_500` in a generated chest
      (those are admin-only) — `$200`/`$500` are absent from the injection
      table (`BillsLootInjectorTest.neverInjectsTwoHundredOrFiveHundredBills`).

If EconomyInc is loaded: ATM produces EconomyInc bills, but chest loot still
drops SUM bills (`BillsLootInjector` doesn't gate on EconomyInc presence).

### 4.6 Section E — HUDs (mostly verified; new bits need testing)

Verified during the 2026-05-17/18 HUD sprint:

| Item | Status |
|---|---|
| OneConfig loads; SUM mod card appears in editor | ✅ |
| 25 originally-ported HUDs render | ✅ |
| Drag-to-reposition works | ✅ |
| Style presets apply visually | ✅ |
| Layout presets apply with responsive row spacing | ✅ |
| Edge alignment (right flush at 1920, left flush at 0, bottom 1077, top 1) | ✅ |
| Auto-reflow on style preset apply | ✅ |
| 7 vanilla-gap HUDs (Health/Hunger/XP/Effects/Durability/Light/LookingAt) | ✅ |
| 5 client-side SUM HUDs (Wallet/Phone/PocketText/NearestRoamer/BorderDistance) | ✅ |
| Snapshot bridge → 4 server-data HUDs populate within 2s of spawn | ✅ |
| Explorer 2 (SUM) layout — modpack default | ✅ |
| Favorites tab auto-jump on creative open | ✅ |
| Favorites HUD scales correctly at 0.4× (Realistic Game HUD style) | ✅ |
| HUD title double-colon fix | ✅ |
| Bottom-edge regression fix | ✅ |
| Dev-mode mixin loading via `SumCoreMod` | ✅ |

#### New — Task 15: Custom-text placeholders + slot bump (untested)

Setup: R-Shift → OneConfig → SUM mod card → HUDs → Custom Text →
Custom Text 1.

- [ ] Enable the HUD; set Text to `%player% @ %x%, %y%, %z%` → renders with
      your display name and floored coords; updates as you move
- [ ] Set Text to `%biome% / %dim% / %time%` → biome name, dim, in-game
      HH:MM
- [ ] Set Text to `%server% / FPS:%fps% / %realtime%` → "Singleplayer" (or
      server IP), current FPS, wall-clock time
- [ ] Set Text to `facing %direction%` → shows N/E/S/W; rotate the camera
      and confirm all four facings work
- [x] 🧪 Set Text to `%bogus% %x%` → output `%bogus% 100` (unknown token
      left intact so typos are visible) —
      `TextPlaceholdersTest.unknownTokenIsLeftIntact`
- [ ] Set Text to `%date%` → YYYY-MM-DD
      🧪 example-value path covered by `TextPlaceholdersTest`; live wall-clock
      formatting still needs an eyeball
- [x] 🧪 Set Text to empty string → HUD draws nothing (effectively hidden) —
      `TextPlaceholdersTest.emptyStringStaysEmpty`
- [ ] In OneConfig editor preview (drag the HUD): preview should show
      example values (Steve / 100 / 64 / -250 / Plains / Singleplayer /
      12:00 / 14:30 / 2026-05-20 / 120 / N) not live values
      🧪 the example-value substitution itself is covered by
      `TextPlaceholdersTest`; only the editor-render wiring is left

Slot bump 5 → 10:

- [ ] OneConfig "Custom Text" subcategory shows 10 slots (Custom Text 1
      through Custom Text 10)
- [ ] Enable a slot ≥6 with a unique string + position, save, restart MC.
      Position + text persist across launches.
- [ ] OneConfig `sum.json` (in `run/config/sum/sum.json`) should now have
      `customText6` … `customText10` fields. Older configs without these
      fields should load cleanly (defaults applied).

#### New — Task 16: Server-config OneConfig mirror viewer (untested)

Setup: OneConfig → SUM → Server Config → Mirror → "Open Server Config
Viewer" button.

- [ ] Before logging into any world: viewer shows "No snapshot received
      yet. Log in to a server to populate." in warning color
- [ ] Open a world (or connect to integrated server). Viewer header shows
      "Snapshot at HH:MM:SS — values reflect server state at your last
      login. Re-login after /sum reloadconfig to refresh."
- [ ] All sections render in order: Sleep Vote, Beaches, Server Pauser,
      Movement Tolerance, Loyalty, Auto Dropper, World Border, Roamer NPCs,
      RoadRunner
- [ ] Each on/off knob matches the value in `run/config/sum.cfg`
- [ ] Numeric values (movement multiplier, auto-dropper interval, sleep
      vote %) match
- [ ] List sections render as bullet rows (Affected blocks, Milestones,
      Border entries, Walkable blocks, Speed blocks)
- [ ] Empty lists show "(none)"
- [ ] Scroll wheel scrolls content; content clips at the inner panel
      (not the whole screen)
- [ ] Edit `run/config/sum.cfg` (e.g. flip `pauserEnabled` to false), run
      `/sum reloadconfig`. Open viewer → still shows OLD value (snapshot
      from login). Disconnect + reconnect → viewer now shows updated value.
      This is EXPECTED behavior.
- [ ] Close button closes; ESC also closes
- [ ] Viewer doesn't pause the game (move keys work behind it — verify by
      pressing W and watching the F3 X coord change)

### 4.7 Storm-shelter sign — multi-roamer convergence (post-`6866ebc`)

Wall placement + basic seek are verified. Multi-roamer convergence still
needs in-game observation.

- [ ] Build a large enclosed structure (e.g. an "airport lobby" 20×20 +
      smaller "bathroom" room separated by a wall + door)
- [ ] Place a storm-shelter sign inside the bathroom (wall-mounted, 4
      facings should work)
- [ ] Spawn 5+ roamers in the lobby with `/give @s sum:roamer_spawn_egg`
- [ ] Trigger storm alarm (requires CSM — or just wait for natural
      thunderstorm)
- [ ] Expected: most or all roamers path INTO the bathroom (because
      signed shelters have no claim limit). NOT just one — the looser AI
      (range 48, no claim check, path tolerance 2 sky-exposed nodes for
      doorways) should attract a crowd.

### 4.8 Section M — Mod absorption (mostly verified)

| # | Mod | Code status | Test status |
|---|---|---|---|
| M1 | Pretty Beaches | ✅ shipped (6 fixes) | ✅ all 10 items verified 2026-05-09 |
| M2 | Dark Redstone | n/a — UT config flip | ⏳ pending modpack-side (see below) |
| M3 | Server Pauser | ✅ shipped (Mixin) | ⏳ pending dedicated-server test (see below) |
| M4 | Loyalty Rewards | ✅ shipped (lifetime + session tracks) | ✅ all 9 items verified 2026-05-09 |
| M5 | Auto Dropper | ✅ shipped | ✅ all 9 items verified 2026-05-09 |
| M6 | World Border | ✅ shipped | ✅ all 6 items verified 2026-05-09 |
| M7 | Custom Signposts | 🔁 stripped from SUM, moved to CSM | n/a |
| M8 | Moving Quickly | ✅ shipped (Mixin) | ⏳ solo sanity only; real validation needs production lag |

#### M3 — Server Pauser (dedicated server only)

Not testable in single-player — the integrated server never runs with 0
players. Use `./gradlew runServer`:

- [ ] Launch dev server: `JAVA_HOME="..." ./gradlew runServer`
- [ ] Connect with the dev client
- [ ] `/time set 0`, then disconnect
- [ ] Wait 60+ seconds, reconnect
- [ ] `/time query daytime` → still ~0 (NOT ~1200 ticks advanced)
- [ ] Weather: `/weather rain 100000`, disconnect 60s, reconnect → still
      raining at near same weather time
- [ ] TileEntities: place furnace mid-smelt, disconnect 60s, reconnect →
      smelt progress unchanged
- [ ] Set `B:enabled=false` in `run/config/sum.cfg`, `/sum reloadconfig`,
      repeat tests → vanilla behavior (time + weather advance)

#### M8 — Moving Quickly (production lag required for real validation)

Solo sanity tests only — real validation needs lossy network conditions.

- [ ] Default config (multiplier 10.0): play normally with elytra,
      sprinting, vehicles. No "moved too quickly" log spam in client log
- [ ] `B:toleranceEnabled=false`, `/sum reloadconfig`, restart client → no
      visible behavior change on stable connection
- [ ] `D:toleranceMultiplier=100.0`, `/sum reloadconfig` → verify the
      value reads correctly via the Server Config Viewer (section 4.6
      Task 16 above)
- [ ] On Alto production: note any "moved too quickly" messages — that's
      the signal this is needed

#### M2 — Dark Redstone (modpack-side, no SUM code)

In the Alto modpack repo, NOT this repo. Tracked here for completeness:

- [ ] Flip `B:"No Redstone Lighting"=false` to `=true` in Alto's
      `Universal-Tweaks---Tweaks.cfg` (line 1541)
- [ ] Restart dev client. Place redstone wire + power it → no block light
- [ ] Place redstone torch → no block light
- [ ] Pull `dark-redstone.jar` from Alto's `manifest.json`
- [ ] Confirm wires / torches still don't glow without the original mod

### 4.9 Task 14 — OneConfig wrapper jar bundling (NEW, untested)

Dev launches use `extraTweakClasses` injection in `addon.gradle` so they
work regardless of the SUM jar manifest. The manifest path ONLY kicks in
for production-style installs.

#### Production-style install test

- [ ] Copy `build/libs/uia-server-utility-mod-*.dirty.jar` (the reobf jar,
      NOT `-dev.jar` or `-sources.jar`) to a separate MultiMC / vanilla
      Forge instance that does NOT have OneConfig-Bootstrap installed
- [ ] Launch that instance
- [ ] Expected: OneConfig stage0 loader fires, downloads OneConfig binary
      to `OneConfig/Loader/`, FML proceeds normally. SUM in mod list.
- [ ] Extract `META-INF/MANIFEST.MF` from the built jar:
      `unzip -p build/libs/uia-server-utility-mod-*.dirty.jar META-INF/MANIFEST.MF`
      should list `TweakClass: cc.polyfrost.oneconfig.loader.stage0.LaunchWrapperTweaker`,
      `TweakOrder: 0`, `ForceLoadAsMod: true`, `FMLCorePlugin`,
      `MixinConfigs: mixins.sum.json`
- [ ] Verify embedded wrapper classes:
      `unzip -l build/libs/uia-server-utility-mod-*.dirty.jar | grep polyfrost`
      should list `LaunchWrapperTweaker.class`, `OneConfigWrapperBase.class`
      (+ inner classes), `SSLStore.class`, `ssl/polyfrost.der`

#### Dev launch regression check

- [ ] `./gradlew runClient` — no `ClassNotFoundException` for any tweaker
- [ ] OneConfig editor (R-Shift) opens; "Server Utility Mod" mod card
      appears and is editable

### 4.10 Cross-cutting (queued from older sessions, never explicitly verified)

These shipped in earlier commits but were never end-to-end tested. Low
priority but worth catching when in the area:

- [ ] ATM GUI on dedicated server (commit `a432ced`)
- [ ] Roamer AI fire/storm response with no player nearby (commit
      `1368f79`) — AI proximity gate should NOT suppress emergency
      response
- [ ] Personal-item auto-rebind + favorites sync (commit `14fe681`)
- [ ] Pocket capability (commit `49fe0d6`)
- [ ] Area codes on player phone numbers (commit `383e049`)
- [ ] Desk-phone TE + chunk-derived numbers (commit `2fb3682`)
      🧪 the chunk→exchange hash (deterministic, [0,1000), negative-coord safe,
      well-spread) is covered by `DeskPhoneExchangeTest`; the TE allocation +
      display path is the in-game part still to confirm

### 4.11 — 2026-06-07 session features (AFK, `/pay`, phone Pay app, job escrow)

Shipped on branch `dev/ogh`:

| Commit | Feature |
|--------|---------|
| `97ee3e4` | AFK tracking + `/pay` player-to-player transfers |
| `a0e0833` | Phone "Pay" app |
| `a594a2e` | Job-board escrow + accept/complete loop (+ dedicated-server listings fix, see §3) |

All three compile (`gradlew compileJava`) and the JUnit suite passes
(`gradlew test`). None have been exercised in a running client/server yet —
that's the checklists below. Run with two players where noted
(`gradlew runServer` + a client, or two clients). Set `JAVA_HOME` to the
Java 17 Azul install first.

#### 4.11.1 AFK tracking → sleep vote + pauser

**Config:** `afk` category — `enabled` (true), `thresholdSeconds` (300),
`excludeFromSleepVote` (true), `announce` (true), `pauseWorldWhenAllAfk`
(false).

- [ ] Stand still (no movement/look/chat) for `thresholdSeconds` → "is now
      AFK" broadcast (if `announce`). Move/look/chat → "no longer AFK".
- [ ] Drop `thresholdSeconds` to ~15 to test quickly.
- [ ] **Main win:** with 2 players, one in bed and the other AFK → night
      skips (AFK player excluded from the head count). Toggle
      `excludeFromSleepVote` off → vanilla behavior returns (AFK player
      blocks skip).
- [ ] Sleeping must never count as AFK (getting in bed is activity).
- [ ] Set `pauseWorldWhenAllAfk=true`: with all online players AFK, world
      freezes (time/mobs/growth stop); any player moving unfreezes it.
      Confirm it does **not** freeze when at least one player is active.
- **Known limitation:** activity = position, rotation, or chat. A player who
  is genuinely interacting but perfectly still and silent (e.g. staring at a
  furnace) would eventually flag AFK. Acceptable; revisit if it bites.

#### 4.11.2 `/pay <player> <amount>`

**Config:** `pay` category — `enabled` (true), `feePercent` (0).

- [ ] `/pay <name> 25` → debits sender, credits recipient, both get chat
      lines. Tab-completes online players.
- [ ] Guards: pay yourself, pay more than you have, non-numeric amount,
      amount ≤ 0, offline/unknown target.
- [ ] Set `feePercent` > 0 → recipient receives `amount − fee`; sender pays
      full amount; the difference is a sink. Confirm rounding to cents.
- [ ] Works on both economy backends (EconomyInc present vs. SUM-native
      fallback).

#### 4.11.3 Phone "Pay" app

- [ ] Personal phone shows the **Pay** tile (7th app); desk phone does
      **not**.
- [ ] Pay flow: open Pay → pick a contact → enter amount → Pay. Balance line
      shows your wallet.
- [ ] Amount field accepts only digits + one decimal point; Enter submits.
- [ ] Same guards as `/pay` (insufficient funds, offline recipient, ≤ 0) —
      surfaced as chat lines from the server; the entry screen shows a local
      "Enter a valid amount" for parse failures.
- [ ] Back/Home navigation from both Pay sub-screens.
- **Known limitation:** no client-side success toast — confirmation arrives
  via chat (consistent with the messages app). No per-send cooldown (the
  `/pay` command isn't rate-limited either).

#### 4.11.4 Job-board escrow + accept/complete loop

This is the largest change and also **fixes a latent multiplayer bug** (the
board previously read `JobBoardSavedData` client-side, which is empty on a
dedicated server, so listings never showed — see §3).

Setup: poster + worker, economy backend loaded.

- [ ] `/sum job post 50 break some stone` → debits $50 (escrow). Posting
      with no economy backend, or insufficient funds, is rejected.
- [ ] Right-click a job board → listings appear (test specifically on a
      **dedicated server**, not just singleplayer, since that's the path
      that was broken).
- [ ] Worker view: OPEN listing shows **Accept** → becomes CLAIMED (worker
      name shown); poster is notified in chat.
- [ ] Worker: **Done** → SUBMITTED; **Drop** → back to OPEN.
- [ ] Poster view on SUBMITTED: **Pay** → worker credited $50, listing
      removed, both notified; **Reject** → back to CLAIMED for rework.
- [ ] Poster view on OPEN/CLAIMED: **Cancel** → $50 refunded, listing
      removed (worker notified if mid-job).
- [ ] GUI live-refreshes after each action (server re-pushes the snapshot —
      no close/reopen).
- [ ] `/sum job clear-mine` → removes the poster's listings and refunds
      total escrow.
- [ ] Expiry: post a job, let it expire (or shorten `JOB_EXPIRY_MILLIS` for
      a test build), reopen a board as the poster → escrow auto-reclaimed
      with a chat line.
- [ ] `/sum job list` shows `[CLAIMED by X]` / `[REVIEW by X]` status
      suffixes.
- [ ] "Jobs available" HUD counts **OPEN** listings only.
- **Known limitations:**
  - **Approval requires the worker to be online** (payout needs a live
    capability). Poster is told to wait if the worker is offline; escrow
    stays held.
  - Admin force-cancel of an offline poster's job can't refund (no
    offline-credit path) — but this is currently unreachable from the GUI
    (only the poster sees Cancel), so no money is lost in normal play.

### 4.12 — Sleep-vote progress action bar (2026-07-14, untested)

Backlog item #4 (§8), now implemented. While anyone is in bed in the
overworld, every overworld player gets a live action-bar line
"3/5 sleeping (need 3 to skip)", re-pushed each second; the count math is
🧪 locked by `SleepVoteMathTest`. The chat-on-change announcement is now a
fallback behind `sleep_vote.actionBarProgress=false`.

**Config:** `sleep_vote` — `enabled` (true), `thresholdPercent` (50),
`actionBarProgress` (true).

Two players recommended (`gradlew runServer` + client, or two clients).

- [ ] At night, player A gets in bed → both players see the action-bar line
      (aqua) with correct counts; it stays visible (no flicker) while A
      stays in bed
- [ ] Player A leaves bed → action bar fades out on its own within a few
      seconds (no stale line)
- [ ] With 2 players and threshold 50%: second player gets in bed → night
      skips immediately (no progress line lingers after the skip); "Night
      skipped ... Good morning!" still arrives in chat
- [ ] AFK denominator: with 2 players, force B AFK (drop
      `afk.thresholdSeconds` to ~15 and idle) → A getting in bed shows
      "1/1 sleeping (need 1 to skip)" and the night skips
- [ ] Players in other dimensions do NOT get the action bar (it's
      overworld-only, matching the vote)
- [ ] `sleep_vote.actionBarProgress=false` + `/sum reloadconfig` → old
      behavior: chat line on sleeper-count change only, no action bar
- [ ] `sleep_vote.enabled=false` → no action bar, no chat, vanilla sleep

---

## 5. Regression checks (relevant after the 2026-05-20 session)

The 2026-05-20 housekeeping pass touched a few cross-cutting things; verify
these still work since the changes could have shaken them loose:

### After Task 14 (jar bundling)

- [ ] All dev launches still work — `runClient`, `runClient17`, `runServer`
- [ ] OneConfig editor opens
- [ ] All HUDs render

### After D6 (plot browser, slot 15 + 16, new package)

- [ ] ATM withdraw / deposit (slot 0) still works
- [ ] `/balance` (slot 1) still works
- [ ] Player shop buy (slot 3) still works
- [ ] All other slots 4–14 packets still flow without errors

### After Task 15 (custom-text slot bump 5→10 + placeholders)

- [ ] OneConfig `sum.json` from a session BEFORE the bump loads cleanly
      with the 5 new fields auto-defaulted (no NPE, no StackOverflow)
- [ ] All 10 slots persist their text across launches
- [ ] Existing positioning of the original 5 slots is preserved across
      the bump
- [ ] Apply each HUD layout preset → all 30+ HUDs end up in expected
      positions, including the new Custom Text slots (which should
      default to top-left if the layout doesn't position them — layout
      presets pre-date the slot bump so they only cover slots 1–5
      explicitly)

### After Task 16 (server-config bridge, slot 17, new package)

- [ ] Login to a world / connect to server → no crash or chat error
- [ ] PlayerStatusSnapshot HUDs (slot 14, Bank Balance / Plot Info /
      Jobs / Loyalty) still populate within 2s of spawn (verifies the
      tracker subscription wasn't disturbed)
- [ ] Other login-event handlers (loyalty tracker, money sync) still fire

### After the 2026-06-07 session (slot 18, GUI id 7 retired, AFK hooks)

- [ ] All packets on slots 0–17 still flow without errors (slot 18 is
      additive; no holes reused)
- [ ] Other `SumGuiHandler` GUIs (ATM, safe deposit, shop, bill changer,
      trash, mailbox, desk phone, pocket) still open — id 7 was retired,
      not renumbered
- [ ] Sleep vote with AFK feature **disabled** (`afk.enabled=false`) →
      behaves exactly as before the AFK exclusion existed
- [ ] Server pauser (`pauser` config) unaffected by the separate
      `pauseWorldWhenAllAfk` path when the latter is off

---

## 6. Bug log (fill in as you find issues)

| # | Section | Symptom | Repro steps | Severity | Commit-fix |
|---|---|---|---|---|---|
| 1 | E / DirectionHud | Compass read 45° clockwise — facing due south showed "SW", north "NE", etc. | Face due south (F3 yaw ≈ 0) with the Direction HUD enabled | Medium (cosmetic, but wrong on every facing) | ✅ **Fixed** 2026-07-18 — `Math.round((yaw+22.5)/45)` → `(int)((yaw+22.5)/45)`, extracted to `HudFormat.direction`, covered by `HudFormatTest` compass tests. |

---

## 7. After playtest — wrap-up

When everything in section 4 is green (or has a tracked fix-commit):

- [ ] Pull the 7 absorbed mod jars from Alto's `manifest.json`:
      `pretty-beaches.jar`, `serverpauser.jar`, `loyalty-rewards.jar`,
      `auto-dropper.jar`, `world-border.jar`, `moving-quickly.jar`,
      `dark-redstone.jar` (custom-sign-posts was already stripped earlier
      when M7 moved to CSM)
- [ ] Bump `packVersion` in Alto's `manifest.json`
- [ ] Update this doc's "Status" line at top with the date
- [ ] Optional: archive this doc + start a fresh one if the next round of
      work is materially different in scope

---

## 8. Next steps — planned but not yet implemented

From the feature review at the start of the 2026-06-07 session. Numbering
matches that discussion (the missing numbers shipped in that session or were
dropped).

### #2 — Death graves / item recovery

A `BlockGrave` tile entity holding the dead player's inventory + XP,
owner-priority pickup, optional decay. Reuses the mailbox/safe-deposit TE+GUI
patterns. Pair with a phone "Find my grave" coordinate ping (the coords +
snapshot-sync plumbing already exists). Highest single frustration-reducer
for a survival server.

### #4 — Sleep-vote progress feedback — ✅ SHIPPED 2026-07-14

An action-bar line ("3/5 sleeping (need 3 to skip)") shown live while anyone
is in bed, AFK-adjusted denominator, `sleep_vote.actionBarProgress` toggle
with chat-on-change fallback. Playtest checklist: §4.12.

### #5 — `/baltop` leaderboard + notification toasts

- `/baltop`: top balances. Small command; pairs with `/balance` and `/pay`.
- Toast HUD: a transient overlay for events that currently only hit chat
  (money received, new message, job accepted/approved, loyalty milestone).
  Reuses the OneConfig HUD framework + `PlayerStatusSnapshot` sync. Would
  make `/pay`, the phone Pay app, and the jobs loop feel much more
  responsive.

### #7 — Plot money sinks (economy depth)

The economy has many faucets (loyalty, loot bills, job rewards) and few
sinks. Plots are the natural sink:

- **Rent / upkeep** — periodic charge on owned plots (loyalty-style tick +
  `EconomyBridge`); unpaid → reverts to `FOR_SALE`.
- **Plot-home teleport** — `/plot home` to an owned plot's spawn.
- **Street-address system** — assign each plot an address, unifying plots +
  mailbox delivery + a phone Maps/GPS app.

### #8 — Roamer economy ("make the city alive")

Give Roamers economic purpose, reusing `TileEntityShop` + `EntityRoamer`:

- **Customer roamers** periodically buy from nearby player shops, depositing
  to the owner — passive income that rewards building shops on trafficked
  roads.
- **Shopkeeper roamers** bound to a shop block → right-click the NPC opens
  `GuiShopBuyer`.
- **Quest-giver roamers** that post to the job board — ties roamers + the
  new jobs loop together.

Most distinctive direction; depends on whether the next priority is economy
depth (#7) or city flavor (#8).
