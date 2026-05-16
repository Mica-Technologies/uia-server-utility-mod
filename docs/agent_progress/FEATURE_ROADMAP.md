# SUM feature roadmap — bank/ATM kit + other server-utility ideas

Status: **Sections A, B, C, and Section D core (D1–D5) all complete as of 2026-05-08.** Latest HEAD is `6866ebc`. Shipped this session: full Section C (C1–C9) + a slate of bug fixes + full Section B (TR/ST/SV/BC/MX/JB) + Section D core (D1–D5: data model, wand + admin commands, buy/sell, protection events, trust/transfer) + the storm-shelter sign preference enhancement (signed shelters preferred over auto-discovered when reachable inside the same building) + the storm-shelter sign converted to wall-mounted + `/sum help` paginated command + several playtest-driven bug fixes (bill render, role auto-name, phone/card second-click, plot wand corner-A capture). The plots system is fully functional via chat commands; D6 browser GUI, D7 rental flow, and D8 EconomyInc-plot migration are deferred polish that can land later if Alex wants the in-GUI experience.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up where we left off:

> I'm picking up the SUM mod after a feature-completion sprint. **As of HEAD `6866ebc` (2026-05-08): Sections A, B, C, and Section D core (D1–D5) are all shipped. The mod has every headline feature originally planned: bank/ATM kit, full SUM-native economy that can replace EconomyInc, six server-utility blocks/items (TR/ST/SV/BC/MX/JB), and a working land-claim plots system with protection. The only deferred items are D6 plot-browser GUI, D7 rental flow, and D8 EconomyInc-plot migration; those are polish that can land later if Alex asks. Most of the new surface still needs in-game playtesting (see "Testing status" section below).**
>
> Working directory is `E:\gitRepos\uia-server-utility-mod`. 1.12.2 Forge, mod ID `sum`, package `com.micatechnologies.minecraft.sum`. Build with `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build`.
>
> **Read first, in this order:**
>
> 1. `docs/agent_progress/FEATURE_ROADMAP.md` — this doc. Status snapshot, phase tables, testing checklists, and the Section D research at the end (which is now mostly built).
> 2. `docs/ECONOMYINC_MIGRATION.md` — admin-facing doc for the C9 migration command and the EconomyInc-removal procedure.
> 3. `docs/agent_progress/PLAYER_FAVORITES_MENU.md` — older feature, useful only for commit-cadence/wiring conventions if needed.
> 4. `docs/agent_progress/NPC_OPTIMIZE_PLAN.md` — older Roamer-perf doc; useful background for any new Roamer roles or AI work.
> 5. `CLAUDE.md` and the project memory under `~/.claude/projects/E--gitRepos-uia-server-utility-mod/memory/`. **Standing prefs:** never `git push`; include `Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>` on every commit; JDK at `~/.jdks/azul-17.0.18`; no real usernames in committed files (use `<username>` placeholder); LDW2 fork at `E:\gitRepos\LDW2` is a separate repo for weather/snow work — don't touch it.
>
> **Reuse, don't rebuild.** Reusable infrastructure available in the codebase right now:
>
> *Economy & money (`.economy` package):*
> - `EconomyBridge`: facade with two backends. `isAvailable()`, `getBalance(player)`, `adjustBalance(player, delta)`. Routes to EconomyInc when loaded, falls back to SUM's `ISumMoney` capability when absent. **All money ops go through this.**
> - `ISumMoney` / `DefaultSumMoney` / `CapabilitySumMoney`: SUM-native balance capability attached to every player. NBT-persisted, synced via `PacketSyncSumMoney`.
> - `SumMoneyEvents`: AttachCapabilities + login/dimension-change/death-respawn sync.
> - `Bills` (in `.atm`): denomination ↔ Item lookup. Accepts both `economy:*` and `sum:*` bills for deposits. `billItem(denom)` returns the preferred bill (EconomyInc when loaded, SUM otherwise).
> - `ItemSumBill`: 8 bill items, denominations $1..$500.
> - `ItemSumPacket`: 8 packet items (one per denom). Each packet = 64 bills of that denom. Produced/consumed only by the bill changer.
> - `ItemAccountAccess`: single class, two registry instances (`sum:phone`, `sum:debit_card`). NBT-binds to first right-clicker. Owner right-click opens `GuiSumAtm` via `Sum.proxy.openAccountAccessGui` (NOT `player.openGui` — that path is broken from items in 1.12.2). **Pattern to copy** for any future bound-to-player item that needs to open a client GuiScreen.
> - `BlockBillChanger` + `TileEntityBillChanger`: 2-slot input/output with Bundle/Unbundle buttons. Output is `SlotFurnaceOutput` (take-only).
> - `BlockBillsDisplay` + `TileEntityBillsDisplay` + `TESRBillsDisplay`: decorative tray; right-click bills/packet to insert, right-click empty to take. TESR renders 1-5 stacked rotating sheets above the tray based on count.
> - `BillsLootInjector`: subscribed to `LootTableLoadEvent`, adds bills to 13 vanilla chest tables. Heavy weighting toward small denoms; $200/$500 never world-gen.
>
> *Blocks (in `.atm`, `.bank`, `.shop`, `.mailbox`, `.jobs`, `.trash`, `.roamer`):*
> - `BlockAtmBase` → `BlockAtmKiosk` / `BlockAtmWall` / `BlockAtmDriveThru`: ATM block family, all open `GuiSumAtm`. **Don't create new ATM variants without asking — three is plenty.**
> - `BlockBankCounter`: decorative 1×1 counter, no rotation, no GUI.
> - `BlockSafeDepositBox`: wall-mounted, per-(player, position) 3×3 inventory. **NB:** `InventorySafeDeposit` has an `initialized` flag to prevent the constructor's pre-populate loop from wiping the backing list — replicate this for any future inventory wrapper.
> - `BlockVelvetRope`: multipart-blockstate decorative stanchion with auto-connecting rope segments. **Reference for fence-pattern blocks.**
> - `BlockVaultDoor` + `TileEntityVaultDoor`: passcode-locked single-block door, auto-close. SHA-256 hash; cleartext never stored.
> - `BlockSumShop` + `TileEntityShop`: vending-machine player shop. 10-slot (template + 3×3 stock). Stock matches template by item+meta+NBT. Two GUIs (owner / buyer). Admin infinite-stock toggle. **Pattern to copy** for any future TE-backed block with state sync (`getUpdateTag`/`onDataPacket` plus `world.notifyBlockUpdate` on every mutation).
> - `BlockMailbox` + `TileEntityMailbox`: claim-on-first-right-click. 9-slot inbox; non-owners get a `DepositOnlySlot` (canTakeStack=false).
> - `BlockJobBoard` + `JobBoardSavedData` + `GuiJobBoard`: server-wide listings, paginated browse GUI, posting via `/sum job post`.
> - `BlockTrashCan`: ephemeral 9-slot inventory, content destroyed on close.
> - `BlockStormShelterSign` + `TileEntityStormShelterSign`: **wall-mounted** plaque (4 facings). On placement and chunk-load, registers position via `RoamerShelterCache.recordSignedShelter` so storm-AI roamers prefer it.
>
> *Plots (`.plots` package):*
> - `SumPlot` + `PlotStatus`: 3D-bbox claim with id, name, owner, price, status (FOR_SALE/OWNED/RESERVED/EXPIRED), trusted-builder set.
> - `SumPlotsWorldSavedData`: per-dimension storage via `World.getPerWorldStorage()`. Has `getPlotsContaining(BlockPos)`, `getPlotsNear(center, radius)`, `getPlotsForSale()`, `overlapsAny()`.
> - `SumPlotsChunkIndex`: chunkPos → List<plotId>, eagerly maintained on add/remove. Used by the protection handler for O(1) lookup.
> - `ItemPlotWand`: **sneak+right-click = corner A, right-click = corner B**. Selection persists in NBT. (Earlier left-click variant had a 1.12.2 packet-suppression bug; sneak/no-sneak is bulletproof.)
> - `PlotsProtectionHandler`: cancels BlockBreak/Place from non-owner-non-trusted-non-op; cancels mob-driven destruction inside owned plots; filters protected blocks out of explosions. `sum.plots.bypass` perm node (auto-grants to op-2+) lets ops build inside protected plots.
>
> *Networking, GUIs, registry:*
> - `SumNetwork` (`.atm` package): single SimpleNetworkWrapper. Slots 0–5 are taken (ATM tx, money sync, shop owner action, shop buy, bill-changer action, job action); use 6 next.
> - `SumGuiHandler` (`.atm` package): GUI dispatcher with `GUI_ATM=0`, `GUI_SAFE_DEPOSIT=1`, `GUI_SHOP_OWNER=2`, `GUI_SHOP_BUYER=3`, `GUI_BILL_CHANGER=4`, `GUI_TRASH_CAN=5`, `GUI_MAILBOX=6`, `GUI_JOB_BOARD=7`. Use 8 next.
> - `SumTab.initTabElements()`: where new blocks/items are constructed in `Sum.preInit`. Add new entries here.
> - `SumRegistry.registerBlock` / `registerItem`: called from constructors of every Block/Item.
> - `SumProxy.openAccountAccessGui(player)`: client proxy hook for opening `GuiSumAtm` directly via `Minecraft.displayGuiScreen` from items. Use this instead of `player.openGui` for item-triggered GUIs.
>
> *NPCs (`.roamer` package):*
> - `EntityRoamer` + `RoamerRole` enum: GENERIC, BANK_TELLER. Role auto-applies default name if entity is unnamed (so the role is visible AND unlocks greetings, which gate on `hasCustomName()`). Add new roles via the enum.
> - `RoamerShelterCache`: tracks `shelters` (organic-discovery) and `signedShelters` (sign-marked, separate set). `recordSignedShelter()` adds to both.
> - `EntityAIRoamerStormShelter`: when roamer is already inside, calls `findPreferredSignedShelterInside` first to find a sign within 48 horizontal / 6 vertical that's reachable via a path tolerating up to 2 consecutive sky-exposed nodes. No claim check on signed shelters (designed to attract a crowd, not spread them out).
>
> *Commands (`.command` package):*
> - `/balance [player]` (perm 0 self / perm 2 admin): user-facing.
> - `/sum help [page]` (anyone): paginated help, 7 pages.
> - `/sum econ <balance|add|set>` (op): admin balance manipulation.
> - `/sum vault <unlock|setcode|info|disown>`: vault-door interactions, ray-trace targeted.
> - `/sum roamer <greet|role> ...` (op): Roamer management.
> - `/sum favorites ...` (op): pre-existing.
> - `/sum migrate-economy [verify]` (op): EconomyInc → SUM player data migration.
> - `/sum job <post|list|clear-mine>` (anyone): job-board listings.
> - `/sum plots <create|delete|list|info|buy|sell|trust|untrust|transfer>` (mixed; create/delete are op).
> - `/sum reloadconfig` (op).
>
> *Texture generators (Python + PIL, idempotent):*
> - `tools/atm_textures/generate.py` — ATM block textures
> - `tools/bank_textures/generate.py` — bank lobby block textures
> - `tools/bill_textures/generate.py` — 8 SUM bill item textures
> - `tools/account_items_textures/generate.py` — phone + debit card
> - `tools/shop_textures/generate.py` — player-shop block textures
> - `tools/changer_textures/generate.py` — bill-changer block
> - `tools/packet_textures/generate.py` — 8 packet items
> - `tools/bills_display_textures/generate.py` — bills display tray
> - `tools/trash_textures/generate.py` — trash can
> - `tools/storm_shelter_sign_textures/generate.py` — storm sign
> - `tools/contacts_textures/generate.py` — business card
> - `tools/mailbox_textures/generate.py` — mailbox
> - `tools/jobboard_textures/generate.py` — job board
> - `tools/plotwand_textures/generate.py` — plot wand
>
> **Critical decisions already locked (don't re-litigate without explicit ask):**
> - **Section C: full replacement of EconomyInc** (Approach 3). SUM owns the economy; EconomyInc is optional compat.
> - **Vault-cracker minigame: skipped permanently.** PvP burglary doesn't fit the theme.
> - **Credit card UX: both phone (modern) AND classic card** ship as separate items.
> - **Vault door passcode UI: chat-driven** (`/sum vault unlock <code>`); in-GUI text field is a polish improvement that didn't ship.
> - **Safe deposit box: 3×3** single-chest equivalent, per-(player, position).
> - **Player shop: vending-machine aesthetic + slot stock + admin infinite-stock flag + NBT-exact item matching.** TESR for the in-glass floating item was deferred (buyer GUI shows the item).
> - **Plot ownership: admin-only creation** for v1; players can buy + transfer + trust.
> - **Plot pricing: flat dollar.** Per-block formulas are a polish.
> - **Plot Y bounds: full column** by default (y=0..255). Custom 3D claims supported by direct construction but the wand UX is full-column.
> - **Plot wand gesture: sneak+right-click for corner A, right-click for corner B** — left-click was tried and abandoned due to a 1.12.2 client-cancel-suppresses-server-packet bug.
> - **Section D6/D7/D8 deferred.** Plots system is functional via chat; only pursue these if Alex green-lights.
> - **Bills coexistence:** when EconomyInc is loaded, ATM produces EconomyInc bills (compat); deposits accept both. When EconomyInc is absent, SUM owns everything.
> - **Storm-shelter sign behavior: signed shelters preferred for already-indoor roamers** within 48h/6v range, with a path-stays-inside check (tolerates 2 consecutive sky-exposed nodes for doorways/skylights). No claim limit on signed shelters — designed to attract a crowd.
>
> **Known fixed bugs (don't reintroduce):**
> - `7c74d4e` Safe deposit box wiped items on reopen — fixed via `initialized` flag in `InventorySafeDeposit`.
> - `59cf108` Velvet rope crashed on registry — fixed by adding `getStateFromMeta`/`getMetaFromState` overrides since the four directional bools aren't in metadata.
> - `15192f2` All 8 SUM bill model JSONs had literal backslash-n. **Watch out** for any toolchain that double-escapes JSON heredocs.
> - `c9a40e2` Roamer role had no visible effect — `setRole` now auto-names unnamed roamers, which both shows the role above the head and unlocks greetings.
> - `03f715d` Phone/card 2nd right-click did nothing — `player.openGui` silently drops the OPEN_GUI packet when invoked from `Item.onItemRightClick` in 1.12.2. Use `Sum.proxy.openAccountAccessGui` (direct `displayGuiScreen`) instead.
> - `25e1bf8` Plot wand reported "Corner B" on both clicks — `LeftClickBlock` event is unreliable in 1.12.2 (client cancel suppresses server packet). Switched to sneak+right-click for corner A.
> - `6866ebc` Storm-shelter sign converted to wall-mounted; AI heuristic loosened (range 24→48, dropped claim check on signed shelters, path tolerance 0→2 consecutive sky-exposed nodes) so a crowd in an airport lobby actually converges on the bathroom sign.
>
> **Watch out for:** Alex maintains a separate Weather 2 Remastered fork at `E:\gitRepos\LDW2` for weather/snow features — *not* this repo. The repo working tree should be clean at `6866ebc`; if you find unfamiliar uncommitted WIP, preserve it (`git restore --staged` anything not yours before committing).

---

## Status snapshot

### Section A — Bank & ATM kit (✅ COMPLETE)

| Phase | Goal | Status | Commit |
|---|---|---|---|
| A1.1 | EconomyBridge (reflection) + `/sum econ` smoke command | ✅ shipped | `1e25e0a` |
| A1.2 | Kiosk ATM block + balance-only GUI shell | ✅ shipped | `2439414` |
| A1.3 | ATM withdraw + deposit (bills + network packets) | ✅ shipped | `5e0148e` |
| A1.4 | Wall-mounted ATM variant | ✅ shipped | `81eb4f1` |
| A1.5 | Drive-thru ATM variant | ✅ shipped | `604abea` |
| A2.1 | Bank counter block + bank-textures generator | ✅ shipped | `7f8da46` |
| A2.2 | Safe deposit box (per-UUID 3×3 inventory) | ✅ shipped | `a079592` (+ fix `7c74d4e`) |
| A2.4 | Velvet rope decorative stanchion (auto-connecting rope) | ✅ shipped | `66945f3` (+ fix `f3d63d1`, crash fix `59cf108`) |
| A2.3 | Passcode-locked vault door + auto-close TileEntity | ✅ shipped | `c7a3034` |
| A3 | Bank teller Roamer role + bank-themed greetings | ✅ shipped | `0c525fc` |

### Section C — EconomyInc replacement (✅ COMPLETE)

| Phase | Goal | Status | Commit |
|---|---|---|---|
| C1 | SUM-native `ISumMoney` capability + facade `EconomyBridge` | ✅ shipped | `46f7696` |
| C2 | 8 SUM bill items + `Bills` lookup accepts both backends | ✅ shipped | `2f6ebc2` |
| C3 | `/balance` user-facing command | ✅ shipped | `9f35cdf` |
| C4 | Phone item + classic debit card item (NBT-bound to UUID) | ✅ shipped | `a79f100` |
| C5 | Player shop block (BlockSeller equivalent) | ✅ shipped | `efb8e47` |
| C6 | Bill changer + 8 packet items | ✅ shipped | `9cbe648` |
| C7 | Bills in vanilla chest loot tables | ✅ shipped | `9b43be2` |
| C8 | Decorative bills display block + TESR | ✅ shipped | `0df41a4` |
| C9 | `/sum migrate-economy` command + EconomyInc-removal docs | ✅ shipped | `97c1a85` |

### Section B — Other server-utility ideas (✅ COMPLETE)

| Phase | Goal | Effort | Status | Commit |
|---|---|---|---|---|
| TR | Trash can / item-disposal block | XS | ✅ shipped | `f178d62` |
| ST | Storm-shelter signage tied to existing Roamer storm cache | XS | ✅ shipped | `568aa54` |
| SV | Sleep voting / time skip | S | ✅ shipped | `a9f04b8` |
| BC | Business cards (item-based player profile exchange) | S | ✅ shipped | `ff3555b` |
| MX | Mailbox + postal system | M | ✅ shipped | `2c7d263` |
| JB | Job board (bulletin-board block for player-listed jobs) | L | ✅ shipped | `5fca248` |

### Section D — Plots system (D1–D5 shipped; D6–D8 deferred polish)

| Phase | Goal | Status | Commit |
|---|---|---|---|
| D1 | Data model: `SumPlot`, `PlotStatus`, `SumPlotsWorldSavedData` (per-dimension) + `SumPlotsChunkIndex` for fast contains() lookup | ✅ shipped | `c930136` |
| D2 | Plot wand + `/sum plots create/delete/list/info` admin commands | ✅ shipped | `db55bf6` |
| D3 | `/sum plots buy/sell` — currency via `EconomyBridge` | ✅ shipped | `20d6e16` |
| D4 | `PlotsProtectionHandler` events: BlockBreak/Place/LivingDestroyBlock/ExplosionDetonate | ✅ shipped | `fdf6393` |
| D5 | `/sum plots trust/untrust/transfer` permissions | ✅ shipped | `5bd9474` |
| D6 | Plot-browser GUI (`/sum plots gui`) | ☐ deferred | — |
| D7 | Optional rental flow + auto-balance-deduction | ☐ deferred | — |
| D8 | EconomyInc plots migration command | ☐ deferred | — |

**Roamer / storm-shelter enhancements** — landed alongside Section D, not strictly part of it:

| Change | Status | Commit |
|---|---|---|
| Storm-shelter signs preferred over auto-discovered shelters when roamer is already inside | ✅ shipped | `ae24693` |
| Wall-mounted variant of the storm sign + AI heuristic loosened (range 48, no claim check, path tolerance 2) | ✅ shipped | `6866ebc` |
| `/sum help` paginated command (7 pages, anyone can run) | ✅ shipped | `af0b3b6` |
| Plot wand corner-A capture fix (sneak+right-click) | ✅ shipped | `25e1bf8` |

Effort scale: XS (≤50 LOC, <1h), S (~100 LOC, 1–2h), M (~300 LOC, half-day), L (≥500 LOC, full day or more).

---

## Testing status (as of HEAD `6866ebc`)

### Verified working in-game

- [x] **A1 ATMs**: ATM kiosk + wall + drive-thru blocks place, render, open `GuiSumAtm`. EconomyInc balance shows; withdraw produces EconomyInc bills; deposit consumes them.
- [x] **A2.1 bank counter**: places, renders correctly.
- [x] **A2.2 safe deposit box**: persistence works across save/reload after the `7c74d4e` fix; multiple items in different slots stay put. Box now recesses against the wall behind it after the panel-position fix (was floating on the player-facing side).
- [x] **A2.4 velvet rope**: brass color reads as brass; auto-connecting burgundy rope segments work.
- [x] **C1 economy**: `/sum econ add 100` → `/balance` reports $100 and persists, both with EconomyInc loaded AND with EconomyInc absent (SUM-native standalone path verified).
- [x] **C2 bills (post-fix)**: render correctly after the JSON-newline fix in `15192f2`.
- [x] **A3 bank teller role (post-fix)**: `/sum roamer role set nearest bank_teller` correctly auto-names the roamer "Bank Teller" and greetings fire after `c9a40e2`.
- [x] **C4 phone + debit card (post-fix)**: bind on first right-click works; **second right-click opens the ATM GUI** after the proxy-based fix in `03f715d`.
- [x] **C5 player shop**: working in solo testing per Alex's report.
- [x] **C8 bills display**: TESR + insert/take flow working.
- [x] **C9 migrate-economy build**: command compiles + dispatches; full migration flow not yet end-to-end tested.
- [x] **`/sum help` (`af0b3b6`)**: paginated help across 7 pages, all reachable; op-only entries tagged `[op]`.
- [x] **TR trash can**: 9-slot ephemeral inventory; closing destroys contents.
- [x] **MX mailbox**: claim-on-first-right-click; deposit-only for non-owners; owner takes; break drops contents.
- [x] **JB job board**: right-click opens listings GUI after dropping the `!world.isRemote` guard; bbox aligned with the rotated model; long empty-state hint wraps across lines.
- [x] **A2.3 vault door**: claim flow, passcode unlock, auto-close, owner overrides. Open-state mouseover NPE fixed by overriding `collisionRayTrace`; open state now renders a doorway frame so the door is visible.
- [x] **BC business cards**: right-click air to personalize; right-click another player to give a copy; anvil-rename for title.
- [x] **SV sleep voting**: threshold-met → night skips. Weather is intentionally left alone (server-controlled).
- [x] **C6 bill changer**: bundle requires ≥64 bills of one denom; unbundle splits a packet back into 64. Logic was already correct; GUI now shows a `$N` total under each slot so a 64-stack of bills can't be mistaken for a single bill.

### Bugs found in playtest and fixed this session

- [x] **`15192f2`** — bill model JSONs had literal `\n` characters; rewrote with real newlines.
- [x] **`c9a40e2`** — roamer role change had no visible effect; `setRole` now auto-names.
- [x] **`03f715d`** — phone/card second right-click did nothing; bypassed broken `player.openGui` path.
- [x] **`25e1bf8`** — plot wand reported "Corner B" on both clicks; switched to sneak+right-click for corner A.
- [x] **`6866ebc`** — storm-shelter sign too restrictive (most roamers stayed in the lobby); widened range, dropped claim check, loosened path-stays-inside; also converted to wall-mounted.

### Unverified — needs in-game testing

Highest priority (load-bearing features):

- [—] **C9 migrate-economy end-to-end**: deferred to Alex's server, to be run once most of the rest of the unverified list is cleared.

Plots system (D1–D5, all need real testing):

- [ ] **Plot wand (post-fix `25e1bf8`)**: `/give @s sum:plot_wand` → sneak+right-click block A, right-click block B. Tooltip should show both corners, NOT both as "B".
- [ ] **`/sum plots create <name> [price]`** (op): reads the held wand, creates a plot. `price > 0` → FOR_SALE; `price=0` or omitted → RESERVED. Refuses overlapping selections.
- [ ] **`/sum plots list [near]`** + **`/sum plots info <id-prefix>`**: chat output shows plots in this dimension, optionally filtered to ~64 blocks of player.
- [ ] **`/sum plots buy <id-prefix>`**: deducts the price via EconomyBridge, sets owner, status → OWNED. Refuses if not for sale, wrong dim, or insufficient funds.
- [ ] **`/sum plots sell <id-prefix> <price>`**: re-lists at chosen price.
- [ ] **`/sum plots trust|untrust|transfer <id> <player>`**: trust set survives transfer.
- [ ] **Plot protection (D4)**: as a non-owner, try breaking/placing inside an owned plot → cancelled with "you can't build/break here". Op players bypass via `sum.plots.bypass` (auto-grants to op-2+). TNT inside a plot detonates but plot blocks survive. Creepers inside owned plots can't grief.

Storm shelter sign (post-`6866ebc`):

- [ ] **Wall placement**: place the sign on a wall (4 facings). Should mount flush, no longer a floor plaque.
- [ ] **Multi-roamer convergence**: with the looser AI (range 48, no claim check, path tolerance 2), a designated bathroom sign in a large building should attract MULTIPLE roamers from the lobby during a storm — not just one.

Section B (still unverified end-to-end):

- [ ] **C2 SUM bills functional**: ATM produces SUM bills (when EconomyInc absent), deposit accepts SUM bills.
- [ ] **C3 `/balance`** from non-op; admin `/balance <player>` with op.
- [ ] **C7 chest loot**: open dungeon/stronghold/temple chests; some should drop bills (mostly small denoms).

### Pre-flight (always green)

- [x] `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build` succeeds on every commit on `main`.
- [x] `economy-inc.jar` extracted to `%TEMP%\econ-inc` for reference.
- [x] EconomyInc reflection signatures locked (see "Verified EconomyInc signatures" below).

---

## Modpack context (Alto pack as of 2026-05-07)

This shapes which features are worth building. Highlights from the pack manifest:

- **EconomyInc 1.6.2** (mod id `economy`) provides `BlockAtm`, `BlockChanger`, `BlockSeller`, three `BlockVault` variants, `BlockBills`, `ItemCreditcard`, ~14 bill items, `EntityInformater` (NPC), `CommandBalance`, `CommandPlots`, plus a Forge capability `fr.fifou.economy.capability.IMoney` with a default handler. So the *primitives* of money already exist; SUM's bank/ATM work should add aesthetic and ergonomic depth, not reinvent the balance system.
- **CSM (MinecraftCitySuperMod)** provides traffic signals, fire alarms, crosswalks. SUM's existing Roamer NPCs respond to fire/storm alarms. Anything that fits a "city street" aesthetic is on theme.
- **JEI** (Just Enough Items) — already considered in the favorites work; relevant again for any inventory GUI.
- **No mailbox/postal mod, no sleep-vote mod, no chunk-claim mod, no employment mod** are present in the manifest — those gaps are real opportunities for SUM.

---

## EconomyInc integration approach

Three SUM phases (A1, A2 partly, A3 partly) need to read/write a player's balance. Two ways to wire this up. **A1 shipped with Option 1 (reflection-only).** The bridge lives at `com.micatechnologies.minecraft.sum.economy.EconomyBridge`; A2 and A3 should reuse it without modification.

### Verified EconomyInc signatures (locked from javap on `economy-inc.jar` 1.6.2)

The bridge that shipped is in `EconomyBridge.java`; these are the actual signatures it binds to. Where this section originally guessed, the verified value is in **bold**:

- `fr.fifou.economy.capability.IMoney`
  - `double getMoney()` — returns **`double`** (originally guessed `int`; balance is fractional dollars, e.g. $12.50)
  - `void setMoney(double)`
  - `void sync(EntityPlayer)` — must be called after `setMoney` from server-side mutations so the client GUI sees the new value (originally not noted in the guess)
  - Other methods exposed: `getLinked`/`setLinked`, `getName`/`setName`, `getOnlineUUID`/`setOnlineUUID` — the bridge ignores these
- `fr.fifou.economy.capability.CapabilityLoading`
  - Static field name: **`CAPABILITY_MONEY`** of type `Capability<IMoney>` (originally guessed `MONEY_CAPABILITY` — wrong)
  - Static helper: **`public static IMoney getMoneyHandler(Entity entity)`** — wraps `entity.hasCapability(CAPABILITY_MONEY, EnumFacing.DOWN)` + `entity.getCapability(...)` and returns null if absent. The bridge calls this directly instead of binding the `@CapabilityInject` field, which avoids one Capability<?> wildcard reflection step.
- Bill items (used by ATM withdraw/deposit, see `Bills.java`):
  - `economy:item_oneb`, `item_fiveb`, `item_tenb`, `item_twentyb`
  - `economy:item_fiftybe` (note typo: trailing 'e'), `economy:item_hundreedb` (note typo: doubled 'e'), `economy:item_twohundreedb`, `economy:item_fivehundreedb`
  - These are the *physical* money items. The IMoney capability is the *account balance*. ATM withdraw converts balance → bills; deposit converts bills → balance.

### Option 1 — Reflection-only soft dependency (what shipped)

- No build-graph change. EconomyInc stays a runtime-only dependency of the *modpack*, not of SUM.
- All access goes through a single `EconomyBridge` helper class. Recommended skeleton (drop into `com.micatechnologies.minecraft.sum.economy`):

  ```java
  public final class EconomyBridge {

      private static final String MOD_ID = "economy";

      // Resolve once at class load. All fields null/false if EconomyInc absent or API drift.
      private static final boolean MOD_PRESENT;
      private static final Class<?> IMONEY_CLASS;
      private static final Capability<?> MONEY_CAPABILITY;
      private static final Method GET_BALANCE;
      private static final Method SET_BALANCE;

      static {
          boolean present = Loader.isModLoaded(MOD_ID);
          Class<?> imoney = null;
          Capability<?> capability = null;
          Method get = null;
          Method set = null;
          if (present) {
              try {
                  imoney = Class.forName("fr.fifou.economy.capability.IMoney");
                  Class<?> loader = Class.forName("fr.fifou.economy.capability.CapabilityLoading");
                  // The @CapabilityInject field name needs verification by decompiling
                  // CapabilityLoading. Lock the actual name once confirmed.
                  Field capField = loader.getDeclaredField("MONEY_CAPABILITY"); // VERIFY
                  capField.setAccessible(true);
                  capability = (Capability<?>) capField.get(null);
                  // Method names below are best guesses; verify by decompiling IMoney.
                  get = imoney.getMethod("getMoney"); // VERIFY signature
                  set = imoney.getMethod("setMoney", int.class); // VERIFY signature
              } catch (Throwable t) {
                  Sum.LOGGER.warn("[economy] Failed to bind to EconomyInc; bridge will be inert.", t);
                  imoney = null; capability = null; get = null; set = null;
              }
          }
          MOD_PRESENT = present;
          IMONEY_CLASS = imoney;
          MONEY_CAPABILITY = capability;
          GET_BALANCE = get;
          SET_BALANCE = set;
      }

      private EconomyBridge() {}

      public static boolean isAvailable() {
          return MOD_PRESENT && MONEY_CAPABILITY != null
              && GET_BALANCE != null && SET_BALANCE != null;
      }

      /** Returns the player's current balance, or -1 if EconomyInc isn't bound. */
      public static long getBalance(EntityPlayer player) {
          if (!isAvailable() || player == null) return -1L;
          try {
              Object handler = player.getCapability(MONEY_CAPABILITY, null);
              if (handler == null) return -1L;
              Number n = (Number) GET_BALANCE.invoke(handler);
              return n == null ? -1L : n.longValue();
          } catch (Throwable t) {
              Sum.LOGGER.warn("[economy] getBalance failed", t);
              return -1L;
          }
      }

      /** Adjusts balance by `delta`. Returns true on success, false if blocked or unavailable. */
      public static boolean adjustBalance(EntityPlayer player, long delta) {
          if (!isAvailable() || player == null) return false;
          try {
              Object handler = player.getCapability(MONEY_CAPABILITY, null);
              if (handler == null) return false;
              Number current = (Number) GET_BALANCE.invoke(handler);
              long now = current == null ? 0L : current.longValue();
              long next = now + delta;
              if (next < 0L) return false; // refuse to go negative
              SET_BALANCE.invoke(handler, (int) Math.max(0L, Math.min(Integer.MAX_VALUE, next)));
              return true;
          } catch (Throwable t) {
              Sum.LOGGER.warn("[economy] adjustBalance failed", t);
              return false;
          }
      }
  }
  ```

- The `// VERIFY` comments mark the two facts that need to be locked from a decompile of `IMoney` and `CapabilityLoading` before this code is final. The pre-flight checklist above includes these as required steps.
- If EconomyInc isn't installed, every SUM block/NPC that needs the balance fails gracefully (chat message: "An economy mod is required for this to work"). Use `EconomyBridge.isAvailable()` to gate.
- Pros: zero compile-time coupling, SUM keeps loading if EconomyInc is removed from the pack, no jar duplication.
- Cons: reflection is verbose; method signatures may change between EconomyInc versions and we'd have to handle that in the static initializer.

### Option 2 — Compile-time `compileOnly` dev dependency

- Add EconomyInc to `dependencies.gradle` as a `compileOnly` jar (dev-only).
- Use `@Optional.Method(modid = "economy")` and `@Optional.Interface` for any code that touches EconomyInc types directly. Forge strips the bytecode if `economy` isn't loaded at runtime.
- Pros: type-safe, IDE-friendly, easier to track upstream API changes.
- Cons: ties SUM's build to a specific EconomyInc version; a CurseForge maven dep adds friction.

**Recommendation: Option 1 for v1**, since EconomyInc has had quiet years and we don't want our build to break if its CurseForge artifact ever moves. Revisit if reflection becomes a maintenance burden.

### What to call

From the JAR contents:
- `fr.fifou.economy.capability.IMoney` — interface, methods like `getMoney()` and `setMoney(int)` (likely; verify by decompiling).
- `fr.fifou.economy.capability.CapabilityLoading` — likely holds the `@CapabilityInject` static `Capability<IMoney>` instance.
- `fr.fifou.economy.ModEconomy` — main `@Mod` class, mod ID `economy`.

A spike in phase A1 should decompile `IMoney` and `CapabilityLoading` once and lock the reflection signatures into `EconomyBridge`. After that, the rest of the bank work just calls `EconomyBridge.getBalance` / `adjustBalance`.

---

## Section A — Bank & ATM kit (user-prioritized)

This is the headline work. Three phases that build on each other but each can ship on its own.

### Phase A1 — Realistic ATM block kit ✅ SHIPPED 2026-05-07

Shipped over 5 commits (`1e25e0a` → `604abea`). What landed:

- `EconomyBridge` (reflection-only) + `/sum econ <balance|add|set> [player]` smoke command for verifying the bridge in dev.
- Three ATM blocks: `sum:atm_kiosk`, `sum:atm_wall`, `sum:atm_drive_thru`. All inherit `BlockAtmBase`; only bounding boxes and front textures differ.
- `GuiSumAtm` (custom, not a wrap of EconomyInc's GUI) with $1/$5/$10/$20/$50/$100 withdraw buttons + "Deposit All Bills" + balance display. Uses `SumNetwork` + `AtmPacketTransaction` for client→server transactions; server-side handler converts balance ↔ bill items via `Bills` and `EconomyBridge`.
- Three placeholder pixel-art textures (kiosk_front, kiosk_side, kiosk_top + a distinct drive_thru_front), generated by `tools/atm_textures/generate.py`. Polish-quality textures are a future pass.

**What was deferred from the original spec:**

- *True multi-block kiosk and drive-thru* (1×2 / 1×3 footprints): all three variants ship as 1×1 blocks. Implementing door-style upper/lower placement is its own follow-up if servers want the taller silhouette.
- *Transfer tab* in the GUI: only Withdraw and Deposit shipped. Adding a per-player `/sum econ pay <player> <amount>` is the cheaper way to cover transfer for now.
- *Block-position validation in the transaction packet*: the server doesn't currently re-check the player is near an ATM when handling withdraw/deposit packets. Low risk on a friendly server; harden if needed.

**Original spec follows for reference:**

**Goal:** SUM ships three visually distinct ATM block variants that look like real ATMs, integrate with EconomyInc's balance, and feel like first-class city street furniture rather than the placeholder cube of vanilla Forge mods.

**Variants:**

1. **Street kiosk ATM** — freestanding, ~1×2 footprint with a tall metal-and-glass enclosure. Card slot + numeric keypad + small screen on the front. Rotatable to face any direction. Models after "TCF Express" or "Chase Drive-Up" outdoor kiosks.
2. **Indoor wall ATM** — single block, flat-mount against a wall (like the kind you find inside grocery stores or hotel lobbies). Includes a little ATM "cubby" wall texture so it sits convincingly in a building.
3. **Drive-thru ATM** — taller block (1×3 or 2×3), angled keypad + receipt slot at car-window height. The kind you actually pull up to in a vehicle.

Optional addition: a **deposit box** mailbox-style mini-block that goes next to one of the ATMs for after-hours deposits. Visual flavor only in v1; phase A2's safe deposit box is the functional version.

**Right-click behavior:**

- **Decision needed (open question):** does right-click open *EconomyInc's existing ATM GUI* (cleanest, free interop), or *SUM's own thin GUI* (more design control, more work)?
- If wrapping EconomyInc's GUI: reflectively call whatever EconomyInc does when its `BlockAtm` is right-clicked. Risk: their GUI assumes a specific item (the credit card) is present and will refuse if not.
- If building our own: a small `GuiSumAtm` with three tabs — *Withdraw*, *Deposit*, *Transfer*. Each tab shows the current balance and a numeric input. "Withdraw" creates appropriate bill items in the inventory and decrements `IMoney`. "Deposit" consumes bill items and increments. "Transfer" takes a target player name and moves balance. v1 could ship just *Withdraw* and *Deposit* and defer *Transfer*.

**Files (approximate):**

```
src/main/java/.../sum/atm/
├── BlockAtmKiosk.java          # variant #1
├── BlockAtmWall.java           # variant #2
├── BlockAtmDriveThru.java      # variant #3
├── TileEntityAtm.java          # shared TE for tab state, etc. (only if needed)
├── GuiSumAtm.java              # if we build our own GUI
├── ContainerSumAtm.java        # container backing GuiSumAtm
└── EconomyBridge.java          # shared reflection helper (used by A2/A3 too)

src/main/resources/assets/sum/
├── models/block/atm_kiosk.json
├── models/block/atm_wall.json
├── models/block/atm_drive_thru.json
├── textures/blocks/atm_*.png
```

**Risks/watch-outs:**

- Block models with non-standard shapes (a tall ATM kiosk) need a custom block bounding box and a careful `getBoundingBox`/`getCollisionBoundingBox` override; otherwise players can phase through.
- If we wrap EconomyInc's GUI, we depend on its packet system; verify behavior in single-player vs. dedicated server.
- Models look terrible without good textures. Budget time for art or hand off to Alex for the texture pass.

**Effort:** **M** — ~300 LOC, plus 3 block models and ~6 textures. Half-day if textures are placeholder; longer for polished art.

---

### Phase A2 — Bank-lobby kit ✅ SHIPPED 2026-05-07

Shipped over 4 commits + 3 bug fixes. What landed:

- **Bank counter** (`7f8da46`): single full-cube block with marble top + walnut sides. Symmetric on all four faces (no rotation property), so counters line up cleanly without facing logic. Roadmap originally specified left/middle/right variants — collapsed to one block since visual difference was minor.
- **Safe deposit box** (`a079592` + fix `7c74d4e`): wall-mounted thin-shell block (10×16×5 cuboid). Right-click opens a 3×3 inventory unique to (player UUID, block position). Persisted via per-dimension `SafeDepositSavedData extends WorldSavedData`. Reuses vanilla `textures/gui/container/dispenser.png` as the GUI background. **Bug fix:** initial constructor pre-populate loop was wiping the backing list mid-iteration; fixed with an `initialized` flag that gates `markDirty` write-back during construction.
- **Velvet rope** (`66945f3` + brass-color/auto-rope fix `f3d63d1` + crash fix `59cf108`): multipart-blockstate fence-pattern block. Brass post always renders; burgundy rope segments auto-connect to adjacent stanchions in any of the four horizontal directions. **Bugs fixed:** brass color was reading as wood (palette swapped to goldenrod); registry crashed on `getMetaFromState` because the four directional booleans aren't stored in metadata (added default no-op overrides matching vanilla fence pattern).
- **Vault door** (`c7a3034`): single 1×1×1 block, 4 facings, OPEN state. Closed = solid full cube; open = empty model with no collision. Auto-closes 100 ticks (5s) after each unlock. SHA-256 passcode hashing in `TileEntityVaultDoor`; cleartext never stored. Owner = first right-clicker (claim model). `/sum vault unlock|setcode|info|disown` admin/owner commands; ray-trace targeting.

**What was deferred from the original spec:**

- *Three counter variants* (left/middle/right). Single symmetric block ships instead.
- *Multi-block 2×3 vault door*. Single 1×1 block ships instead — the roadmap allowed for "single-tick open animation rather than smooth transition" and this commit takes that allowance.
- *In-GUI passcode entry* for vault doors. Chat-based via `/sum vault unlock <code>` for v1; in-GUI text field is a polish improvement.
- *Auto-connecting rope between stanchions*. Originally planned to be implied visually; ended up implementing for real via multipart blockstates.
- *Cash register decorative block*. Listed as optional in spec; skipped for v1.

**Original spec follows for reference:**

**Goal:** A set of decorative + functional blocks that lets a server admin or roleplaying player build a convincing "bank" interior.

**Blocks:**

1. **Bank counter** — a counter-height block with three variants (left end, middle, right end) so you can build a continuous teller line of any length. Glass divider on top is part of the model.
2. **Vault door** — large 2×3 door, animated open/close, password-locked (NBT field on the tile entity stores hash of an admin-set passcode). Right-click prompts for code; correct code opens, wrong code denies.
3. **Safe deposit box** — wall-mounted block that opens a small (3×3 or 5×3) inventory tied to the *opening player's UUID*. Different players see their own box even if they right-click the same block — like an EnderChest, but located. Useful for in-game character "bank storage."
4. **Velvet rope** — purely decorative, two-block-tall stanchion + rope, connects horizontally. Lets builders make queue lines.
5. *(Optional)* **Cash register** — placed atop the bank counter, for visual flavor. Could double as a per-block balance for shops in a future iteration.

**Notes:**

- The vault door's password mechanic should be a simple SHA-256 of the entered string compared to the stored hash. No heavy crypto needed for game purposes; just don't store cleartext.
- Safe deposit box is genuinely useful gameplay — every server has people asking "where can I store my stuff." This solves it.

**Risks:**

- Vault door is the trickiest. Animated multi-block doors have interesting state-sync issues in multiplayer. Consider a single-tick "open" animation rather than an actual smooth transition.
- Safe deposit box per-UUID inventory needs server-side persistence (saved with the world). A simple file in `world/sum_safe_deposits/<uuid>.dat` per-box-per-player works; or a single `SafeDepositSavedData` keyed by `(blockPos, uuid)`.

**Effort:** **M-L** — ~500 LOC, 5+ block models, custom GUI for safe deposit box. Full day.

---

### Phase A3 — Bank teller NPC ✅ SHIPPED 2026-05-07

Shipped in `0c525fc`. What landed:

- `RoamerRole` enum with GENERIC and BANK_TELLER values. Each role has a default greetings array (BANK_TELLER's: "Welcome to First National.", "Cash or check?", "Have a nice day.", "Need help with your account?", "Please step up to the counter.", "Next in line, please.").
- `EntityRoamer` gained a `role` field. Persists via NBT under "Role" key (string id; unknown ids fall back to GENERIC). `setRole(role)` replaces the greeting list with the role's defaults.
- `/sum roamer role <set|get> <nearest|uuid> [role-id]` admin command. Tab completes role ids on the set path.

**What was deferred:**

- *Idle counter-tap animation* (`swingArm` trigger). Pure flavor, not necessary for v1.
- *Right-click bank teller to open ATM GUI* (turning them into a walking ATM). Future enhancement; the role tag is only metadata in v1.
- *Per-role config in SumConfig*. The default greetings are baked into the enum; runtime customization is via the existing `/sum roamer greet add` command.

**Original spec follows for reference:**

**Goal:** Reuse the existing Roamer NPC infrastructure to add a "bank teller" variant — same engine, different dialogue and idle animations, can stand behind the bank counter.

**Approach:**

- Subclass `EntityRoamer` as `EntityBankTeller` (or just configure an existing roamer with a special "role" config). Tag-based approach is lighter weight: add a `role` enum to Roamer and a `bank_teller` value with appropriate greeting defaults.
- Pre-loaded dialogue list ("Welcome to First National." / "Cash or check?" / "Have a nice day."). Use the existing greeting system.
- Idle animation: stand still, occasionally tap on the counter (use a small `swingArm` trigger).
- Bank teller doesn't need transactional powers in v1 — they're flavor. Phase A4-or-later could add "right-click teller to open ATM GUI", essentially turning them into a walking ATM.

**Files:**

```
src/main/java/.../sum/roamer/
├── RoamerRole.java          # enum: GENERIC, BANK_TELLER, POSTAL_WORKER, etc.
├── EntityRoamer.java        # gain a `role` field (default GENERIC)
└── (no new entity class needed if we use the role enum)
```

**Risks:**

- We've already spent serious time tuning Roamer performance; adding a role tag is cheap, but make sure idle-animation hooks don't bypass the proximity-based AI gate added in `d435020`.
- Dialogue lists per role should be configurable via SumConfig.

**Effort:** **S** — ~100 LOC. Mostly dialogue strings + a small enum.

---

## Section B — Other server-utility ideas (sketches)

These are listed so we have a queue of ideas after the bank kit lands. Each is a sketch, not a fully-specified plan; if Alex picks one to do next, we'd flesh it out into its own dedicated doc at that point.

### Phase MX — Mailbox + postal system

**Pitch:** Every server's "where do I send stuff to my friend who's offline?" gap. SUM provides a placeable mailbox block. Right-click to open: top half is "incoming" (only the owner can take), bottom half is "outgoing" (anyone can drop items in addressed to the owner). Server-side persistence per mailbox-position. Optional: notification on next login ("You have N items in your mailbox at X,Y,Z").

**Roamer angle:** A "postal worker" Roamer role (per phase A3) that walks between known mailbox positions. Pure flavor.

**Effort: M** — ~300 LOC + textures + GUI. Server-side state.

### Phase SV — Sleep voting

**Pitch:** When *some* players sleep, time should skip. Vanilla 1.12.2 requires *all* players to sleep, which is annoying on a busy server. SUM listens to `PlayerSleepInBedEvent`, counts sleeping players, and if the ratio passes a configurable threshold (default 50%), sets the world time to morning and clears thunder.

**Effort: S** — ~80 LOC, server-side only, no GUI.

### Phase TR — Trash can

**Pitch:** A block that destroys items dropped/inserted into it. Optional: keep the last N destroyed items in a "recycle bin" inventory for short undo. Right-click to open a 1×9 inventory; closing destroys what's inside.

**Effort: XS** — ~40 LOC + one model + texture. Half an hour to ship.

### Phase BC — Business cards

**Pitch:** A new item, `business_card`. Right-click *air* with empty slot: spawns a card with your username, current dimension/coords, and an optional NBT "title" the player can set via anvil. Right-click *another player* with a card: gives them your card. Right-click a card in inventory: shows a tooltip-like info card. Effectively a contact-trading system.

**Effort: S** — ~100 LOC, one new item + NBT handling.

### Phase JB — Job board

**Pitch:** A bulletin-board block. Right-click to open a list of player-posted listings: "I need help building a roof, paying $50 — contact PlayerName." Listings expire after configurable time (default 7 days). Players can post via a chat-driven form or via writing on a sign placed adjacent. Browsable + searchable in the GUI.

**Effort: L** — ~500 LOC + persistence + GUI. Full day, biggest item in this section.

### Phase ST — Storm-shelter signage

**Pitch:** Tiny ergonomic improvement to the existing Roamer storm-shelter feature. A sign-block variant that, when placed, registers its position into `RoamerShelterCache` directly so Roamers seek it during storm alarms without having to organically discover it. Decorative + functional.

**Effort: XS** — ~50 LOC. Closes a small loop in the existing roamer code.

---

## Section C — Replacing EconomyInc with SUM-native equivalents (research)

Status: **research only — no code, no commitment.** The question is whether SUM should grow to *replace* EconomyInc rather than depend on it via the reflection bridge that A1 shipped. This section catalogs what EconomyInc actually does, what each piece would cost SUM to replicate, the migration story, and three concrete approaches with a recommendation.

### Why consider this at all

The A1 reflection bridge works, but it locks SUM to a mod that hasn't seen a release since November 2020 and contains at least one literal "Will be fix in another version of the mod. Quite bugged for the moment." string in its `ItemCreditcard` bytecode. Reasons SUM might want to own the economy outright:

- **Stability.** EconomyInc's CurseForge artifact could move or disappear; the reflection bridge degrades gracefully but the bank/ATM features become inert if it's removed. If SUM owns the data model, the features stand on their own.
- **Coherent UX.** A2's safe deposit box, A3's bank teller, MX's mailbox postage, JB's job-board payouts all want to read/write balance. Today every one of them goes through `EconomyBridge`; it works, but every feature inherits EconomyInc's quirks (fractional `double` balances, the credit-card-required-for-card-GUI, the buggy ItemCreditcard).
- **Pack reduction.** The Alto modpack already tracks ~80 mods; if SUM can absorb EconomyInc cleanly, that's one fewer artifact to chase.

Reasons not to do it:

- **Real cost.** Even excluding the plots system and the vault-cracking minigame, replicating just money + bills + credit card + bill-changer + a player-shop block is ~2 weeks of focused work plus an in-game economy-balance pass.
- **EconomyInc already works.** Despite the rough edges, the mod has been in production servers for years. Reimplementing it from scratch trades known bugs for unknown ones.
- **Migration is the hard part.** Existing servers have player balances stored in EconomyInc's `IMoney` capability NBT, plot ownership in `PlotsWorldSavedData`, and bill items scattered through inventories. Any SUM replacement has to either coexist or migrate every piece.

### What EconomyInc actually ships (full inventory)

Decompiled from `economy-inc.jar` 1.6.2. Each row notes whether SUM already replicates it, whether it's worth replicating, and a rough effort estimate.

| EconomyInc piece | What it does | SUM today | Replication cost |
|---|---|---|---|
| `IMoney` capability + `ProviderMoney` + `StorageMoney` + `DefaultMoneyHandler` | Per-player `double` balance, NBT-serialized, attached at entity construction. Standard Forge capability pattern. | reads via `EconomyBridge` | **M** — ~250 LOC for capability interface, default impl, provider, storage, and per-player attach event. Standard pattern, well-trodden. |
| 8 bill items (`item_oneb`..`item_fivehundreedb`) | Physical money, denominations $1..$500. Author misspelled `item_fiftybe` and `item_hundreedb` and they're frozen that way for save-compat. | reads via `Bills` | **S** — ~200 LOC + 8 textures. Trivially repetitive; one base class. SUM names them properly (`sum:bill_1`..`sum:bill_500`). |
| 8 packet items | Bundled bills (e.g. `item_packet_hundreedb` = a stack of $100 bills as one item slot, presumably for inventory compactness). | not used | **S** — ~150 LOC + 8 textures. Pairs with the changer block; if we skip the changer, packets aren't needed. |
| `ItemCreditcard` | NBT-keyed by `OwnerUUID`, requires "wireless technology" component, opens a card GUI on right-click. **Author marks it as "Quite bugged for the moment."** | not used | **S** — ~150 LOC + texture. SUM should ship a clean reimplementation if we go down this path; the EconomyInc one isn't a good model. Skip the wireless-tech component. |
| `BlockAtm` + `GuiItemATM` | Cube ATM block, right-click opens a buttons-only GUI requiring credit card | ✅ **shipped** (A1: kiosk/wall/drive-thru + GuiSumAtm without card requirement) | **DONE** |
| `BlockChanger` + TE + `GuiChanger` | Convert bills ↔ packets (and presumably balance ↔ bills/packets). | not used | **M** — ~300 LOC: BlockContainer + TE + Container + GUI + 1 texture. Pairs with packets. |
| `BlockSeller` + TE + `GuiSeller` + `GuiSellerBuy` | Player-owned vending machine. TE stores owner, ownerName, item, amount, cost, funds_total, admin flag, "created" two-phase setup. | not used | **L** — ~600 LOC: most complex single block in the mod. Two GUIs (set-up + buy), per-block save state, ownership/permissions, withdraw of accumulated funds. |
| `BlockBills` + `ModelBills` + TESR | Decorative block that visually shows physical bills inserted. | not used | **S** — ~250 LOC + TESR + texture. Pure flavor; skip unless we want decorative cash. |
| 3 vault blocks (`BlockVault`, `BlockVault2by2`, `BlockVaultCracked`) + TEs + TESRs + 4 GUIs | Personal storage with passcode, 1×1 and 2×2×2 sizes, "cracked" intermediate state, settings GUIs. | covered by A2 vault door + safe deposit box (different design, similar use case) | **L if literal-equivalent, M if just A2's design** — A2 already plans a passcode vault door + per-UUID safe deposit box. The 2×2×2 multi-block vault is a separate effort. |
| `ItemVaultCracker` + crafting components (`ItemGear`, `ItemGearsMecanism`, `ItemMicroChip`) + `GuiCracking` | Mini-game for breaking into someone else's vault. | not used | **M** — ~300 LOC. **Skip.** Player-vs-player burglary mechanics aren't in SUM's "server utility" theme. |
| `EntityInformater` + `RenderInformater` + `ModelInformater` + `GuiInformaterTrade` + `ContainerInformaterTrade` | NPC trader (oddly extends `EntityMob`). Has a `safeCode` static (!) for some kind of code-protected trade. | A3 covers a Roamer-based bank teller; A3 + future "shopkeeper role" can absorb this | **S** — bank teller is already planned; shopkeeper role is an additive ~100 LOC on top. |
| `CommandBalance` | `/balance` user-facing command | partial: `/sum econ balance` (admin permission level 2) | **XS** — un-restrict the existing command path, or add `/balance` as an alias. ~20 LOC. |
| `CommandPlots` + `CommandPlotsBuy` + `PlotsData` + `PlotsWorldSavedData` + `PlotsChunkData` + `ChunksWorldSavedData` | Admin-defined buyable land plots. Plots have name/owner/bbox/price/bought; a chunk index speeds up containment checks. | not used | **XL** — ~1000+ LOC. Largest single feature in EconomyInc. Treat as its own roadmap section if SUM wants to absorb it. **Defer.** |
| `VillageHandlerShop` + `VillageComponentShop` | Procedural village structures that spawn shops with Informaters inside. | not used | **M** — ~250 LOC + structure NBT. Tightly coupled to the Informater + Plots; only meaningful if we replicate those. |
| `CustomLootTableList` | Injects bill drops into vanilla chest loot tables. | not used | **XS** — ~30 LOC + a single loot-table JSON. Free if we have bills. |
| `MySQL` + online linking (IMoney's `linked`/`onlineUUID`) | Optional server-side cross-world balance sync via MySQL DB, gated by `ConfigFile.connectDB`. | not used | **XL or skip** — niche feature, requires a JDBC driver bundled, raises the bar of "what runs out of the box." **Skip.** |
| `ConfigFile` knobs (preview-in-block, gold-nugget recipe, village shops on/off, etc.) | User-facing toggles. | not used | absorbed naturally as we replicate the dependent features. |

**Total replication budget for the realistic scope** (money + bills + credit card + changer + seller + bank vault + bills-display + balance command + loot inject; *excluding* plots, village shops, vault-cracker, MySQL): roughly **2–3 weeks of focused work** plus an in-game balance/playtest pass.

### The migration story

Replacement is harmless on a fresh world. On a server with existing players and items, three things have to migrate, in this order:

1. **Player balances.** EconomyInc stores `IMoney` per-player as serialized NBT in the world's `playerdata/<uuid>.dat` files. SUM's replacement ships its own `Capability<ISumMoney>` and a one-time on-login migration: at `PlayerLoggedInEvent`, if the player has a non-zero EconomyInc balance and a zero SUM balance, copy across via reflection (the `EconomyBridge` already knows how to read EconomyInc), then mark the migration complete in a NBT flag on the SUM capability. This means EconomyInc must still be installed during the migration window.
2. **Existing bill items in inventories.** Every bill item already in the world has registry name `economy:item_oneb` etc. If we remove EconomyInc, those item slots become "unknown item" placeholders and the player loses them. Three options:
   - **Migration item filter.** On `PlayerLoggedInEvent`, walk inventory; for each EconomyInc bill, replace with the equivalent SUM bill of the same denomination. Run for 1–2 weeks while both mods are loaded, then EconomyInc can be uninstalled cleanly.
   - **Reflective name aliasing.** Register SUM's bills under both `sum:bill_500` *and* `economy:item_fivehundreedb` as a fallback. This is undocumented Forge territory and probably brittle.
   - **Drop and refund.** On EconomyInc removal, sweep all players' inventories, count their bills, credit the equivalent balance to their SUM capability, log the refund. Lossless but server-side-only and a one-shot operation.
3. **Existing in-world blocks.** EconomyInc ATMs, vaults, sellers, etc. placed in the world become "missing block" stubs if EconomyInc is removed. Server admins would need to replace them with SUM equivalents manually, OR SUM ships a one-time `/sum migrate-economy-blocks` command that scans loaded chunks and converts. **High risk** — block conversion can lose state (vault contents, seller stock/funds). On Alto, this is probably tractable since the in-world block count is small, but it's a real operational task.

Plot ownership data is the lowest concern: it's stored in `PlotsWorldSavedData` which is just an NBT file under `data/`. If SUM doesn't replicate plots, the file becomes orphan but doesn't break anything; admins can delete it.

### Three approaches

**Approach 1 — Stay reflection-only.** SUM keeps `EconomyBridge`. Future SUM features (A2 safe deposit, A3 teller, MX mailbox postage, JB job board payments) all go through it. The Alto pack continues to ship EconomyInc. **Effort: zero (status quo).** **Risk: long-term coupling to a stale mod.**

**Approach 2 — Reimplement money + bills only; coexist with EconomyInc for everything else.** SUM ships its own `Capability<ISumMoney>` + bill items. Critical: SUM has to *prefer* EconomyInc's `IMoney` when both are loaded, otherwise players end up with two parallel balances and no way to spend one on the other's blocks. Implementation: `EconomyBridge.isAvailable()` becomes the toggle — if true, `getBalance/adjustBalance` route to EconomyInc; if false, they route to SUM's own capability. SUM's vending blocks (when added) accept either currency type. **Effort: ~M (~500 LOC + bill assets).** **Risk: subtle dual-source bugs; players holding EconomyInc bills can't spend at SUM blocks unless we add the dual-currency logic.**

**Approach 3 — Full replacement on a target date.** SUM ships its own everything (excluding plots and vault-cracker, which we explicitly skip). At the cutover, server admins run a `/sum migrate-economy` command, then uninstall EconomyInc from the pack. SUM owns money, bills, credit card, changer, seller, vault (overlaps with A2), bills-display block, the user-facing `/balance` command, and the loot injector. **Effort: ~2–3 weeks.** **Risk: highest of the three; the in-world block migration is the biggest single risk and the playtest period is long.**

There's also a quieter **Approach 4 — Approach 1 forever, but harden the bridge.** SUM stays reflection-only but ships a `EconomyBridge` "shim mod" — a tiny standalone mod-or-companion-class that mimics enough of EconomyInc's `IMoney` to keep SUM working if EconomyInc disappears. This is a defensive variant of Approach 1 that buys insurance without committing to replacement. Effort: ~S (~150 LOC + capability provider).

### Recommendation

**Start at Approach 4, plan toward Approach 2.** Ship a tiny SUM-native `Capability<ISumMoney>` + provider + storage that *only* activates when EconomyInc is absent. The bridge becomes "use EconomyInc's IMoney if present, else use SUM's own." This:

- Removes the hard-dependency vibe (SUM works alone) without committing to bills, credit cards, changers, sellers, or the rest of EconomyInc's surface.
- Sets up the data model so Approach 2 is a natural extension when (if) we add SUM-native bills.
- Costs ~S effort and ships in 1 day.
- Defers the genuinely expensive parts (seller block, plots, migration) until we have a real reason to do them.

**Don't pursue Approach 3 unless EconomyInc actually breaks.** The economic case for full replacement only holds if EconomyInc is unmaintainable; right now it works for the use cases we care about, and the migration risk on existing servers is real.

### Phase plan (Approach 3 in flight)

Alex green-lit Approach 3 (full replacement) on 2026-05-07. Phases shipping iteratively:

| Phase | Goal | Effort | Status | Commit |
|---|---|---|---|---|
| C1 | SUM-native `ISumMoney` capability + provider/storage + per-player attach event. Inert when EconomyInc present. `EconomyBridge` becomes a facade. | S | ✅ shipped | `46f7696` |
| C2 | SUM bill item set (8 denominations). Lang strings, textures, creative tab. `Bills` lookup accepts both EconomyInc and SUM bills as deposits. | S | ✅ shipped | `2f6ebc2` (+ JSON-render fix `15192f2`) |
| C3 | `/balance` user-facing command (perm 0 self / perm 2 admin). Admin retains `/sum econ`. | XS | ✅ shipped | `9f35cdf` |
| C4 | Phone item + classic debit card item, NBT-bound to OwnerUUID, open `GuiSumAtm` from anywhere. **Both phone and card** per Alex's call. | S | ✅ shipped | `a79f100` |
| C5 | SUM player-shop block (BlockSeller equivalent). Two-phase setup, per-block owner + funds + stock, owner can withdraw funds, non-owners buy. | L | ✅ shipped | `efb8e47` |
| C6 | Bill-changer block (BlockChanger equivalent) + 8 packet items. | M | ✅ shipped | `9cbe648` |
| C7 | Loot injector — SUM bills appear in vanilla chest loot at low rates. | XS | ✅ shipped | `9b43be2` |
| C8 | Bills display block + TESR (BlockBills equivalent). | S | ✅ shipped | `0df41a4` |
| C9 | Migration command `/sum migrate-economy` + EconomyInc-removal documentation (`docs/ECONOMYINC_MIGRATION.md`). | M | ✅ shipped | `97c1a85` |

C1 + C2 + C3 together give SUM a self-sufficient money system — that milestone is now hit. Everything after C3 is feature parity for completeness, on the way to letting Alto remove EconomyInc.

### Open questions specific to Section C

Resolved 2026-05-07 via the AskUserQuestion interface:

1. ✅ **Approach choice.** Approach 3 (full replacement), shipped iteratively. C1 (the SUM money capability) lands first as the foundation; everything else extends it.
2. ✅ **In-world EconomyInc items/blocks during transition.** Coexistence first; migration tooling is C9 once the rest of Section C is in place.
3. ✅ **Credit card UX.** Both phone (modern, recommended) and debit card (classic). C4 ships both as separate items.
4. ✅ **Plots system.** Out of Section C — moved to Section D as its own research/spec.
5. ✅ **Vault-cracker minigame.** Out of scope. PvP burglary doesn't fit SUM's theme. Skip the cracker tool, gear/microchip/mechanism crafting components, GuiCracking, and BlockVaultCracked entirely.

---

## Section D — Plots system (D1–D5 SHIPPED; D6/D7/D8 deferred)

Status: **Core plots system is shipped and live in code as of 2026-05-08.** The D1–D5 commits implement: data model + per-dimension persistence (`c930136`), wand + admin commands (`db55bf6`, with corner-A fix in `25e1bf8`), buy/sell (`20d6e16`), protection events (`fdf6393`), and trust/transfer (`5bd9474`). What follows is the **original research spec** kept for reference; it largely matches what shipped, but design decisions made during implementation are noted inline.

### What EconomyInc's plots system actually is

Admin-defined buyable land regions. **Not** a free-form chunk-claim system like FTBChunks or GriefPrevention — admins explicitly create plots with bounding boxes and prices, then players run a command to buy one.

Decompiled data model from EconomyInc 1.6.2:

```java
// fr.fifou.economy.world.saveddata.PlotsData
public class PlotsData {
    public String name;        // human-readable plot ID, used in /plots buy <name>
    public String owner;       // player username (not UUID — fragile under name changes)
    public int xPosFirst;      // bbox corner 1 X
    public int zPosFirst;      // bbox corner 1 Z
    public int xPosSecond;     // bbox corner 2 X
    public int zPosSecond;     // bbox corner 2 Z
    public int yPos;           // a single Y? not a vertical bbox — surprising
    public double price;       // dollar cost via IMoney
    public boolean bought;     // ownership flag
}
```

Persistence:

- `PlotsWorldSavedData extends WorldSavedData` — holds the master `List<PlotsData>` per dimension, written into `world/data/plots.dat`.
- `ChunksWorldSavedData` + `PlotsChunkData` — secondary chunk-keyed index for fast "what plot does this chunk belong to" lookups during PvP/build-permission checks.

Commands:

- `/plots` — `CommandPlots` likely lists plots, shows info, admin-creates (verify by decompiling at impl time).
- `/plots buy <name>` — `CommandPlotsBuy` deducts price via IMoney and flips `bought=true`, sets `owner` to the buying player.

Interactions with the rest of the mod:

- `VillageHandlerShop` + `VillageComponentShop` — village shops that procedurally generate inside village structures may register as plots automatically. **Need to verify.** If true, SUM's plot system has to play nice with vanilla village generation.
- IMoney — buy operations deduct from the player's balance.

### Limitations of the EconomyInc design (reasons SUM should not just copy it)

- **Username, not UUID, for owner.** A player who changes their Mojang username loses their plot. Modern mods key by UUID and resolve the display name at GUI time.
- **Single Y coordinate.** A plot is `(x1,z1)..(x2,z2)` at one specific Y level — not a true 3D box. Surface-only feature; no "claim the cave under my house."
- **Boolean `bought` flag, no states.** No "for sale", "for rent", "expired", "disputed" states. No rental.
- **No permissions model.** Plot owner controls it absolutely. No "let my friend build here too" without giving them ownership.
- **No protection enforcement (apparently).** The plot data is stored but I don't see a `BlockEvent.BreakEvent` handler in EconomyInc's event classes. Plots may be purely informational, with admins relying on vanilla op permissions for actual protection. **Verify by decompiling EventClassCommon at impl time.**
- **No selection ergonomics.** Admins create plots by typing exact coordinates; no wand/click-corners flow.

### Proposed SUM-native design

A SUM `Plot` is a 3D bbox owned by zero or one players, with a permissions list and a status:

```java
public class SumPlot {
    UUID plotId;                       // stable internal ID
    String displayName;                // shown in /sum plots list
    UUID ownerUuid;                    // null = unowned/admin-listed
    BlockPos cornerA, cornerB;         // 3D bbox; cornerA.y/cornerB.y allow vertical claims
    int dimensionId;
    double price;                      // 0 if not for sale
    Status status;                     // FOR_SALE, OWNED, RESERVED, EXPIRED
    Set<UUID> trustedBuilders;         // players allowed to build despite not owning
    long createdAt;                    // epoch ms
    long lastActivity;                 // for expiry policies (e.g. unused for 90 days → EXPIRED)
}
enum Status { FOR_SALE, OWNED, RESERVED, EXPIRED }
```

Persistence:

- `SumPlotsWorldSavedData extends WorldSavedData` — per-dimension master list, written to `world/data/sum_plots.dat`.
- `SumPlotsChunkIndex` — chunk-keyed lookup table, eagerly built from the master list at world load and kept in sync on plot create/delete. Used by the protection event handler.

Commands (`/sum plots <subcommand>`):

- `/sum plots list [near]` — list plots; `near` filters to ones inside ~64 blocks.
- `/sum plots info <id>` — name, owner, bbox, status, price, trusted list.
- `/sum plots create <name> <price>` — admin only. Uses the player's wand selection (see below) for the bbox.
- `/sum plots delete <id>` — admin only.
- `/sum plots buy <id>` — anyone with sufficient balance.
- `/sum plots sell <id>` — owner sets back to FOR_SALE at a chosen price.
- `/sum plots trust <id> <player>` / `/sum plots untrust <id> <player>` — owner manages builders.
- `/sum plots transfer <id> <player>` — owner transfers ownership outright.

User-facing items:

- **Plot wand (`sum:plot_wand`)** — admin tool. Left-click corner A, right-click corner B, then `/sum plots create` reads the selection. Mirrors the WorldEdit wand convention familiar to most server admins.

Protection enforcement:

- `BlockEvent.BreakEvent` and `BlockEvent.PlaceEvent` listener. If the affected block is inside a plot the actor doesn't own/isn't trusted on, cancel the event and send a chat warning. Op-level players bypass.
- `PlayerInteractEvent` listener for chest/door access — same rule, configurable.
- `LivingDestroyBlockEvent` for mob breakage of plot blocks (creepers, withers) — cancel if plot has the "no mob damage" flag.

GUI:

- `/sum plots gui` opens a browser showing FOR_SALE plots with map-style location preview, sortable by price/distance.

### Implementation phases (sketch)

C1 is shipped (✅), so the currency dependency is already satisfied — Section D could in principle start any time, but Alex has explicitly deferred it pending Section C completion.

| Phase | Goal | Effort | Depends on |
|---|---|---|---|
| D1 | Data model: `SumPlot`, `SumPlotsWorldSavedData`, NBT round-trip. No commands or protection yet. | M | ✅ C1 (satisfied) |
| D2 | `/sum plots create/delete/list/info` admin commands + plot wand item. | M | D1 |
| D3 | `/sum plots buy/sell` + currency deduction via `EconomyBridge`. | S | D1, D2 |
| D4 | Protection: BreakEvent/PlaceEvent/InteractEvent listeners + chunk index. | M | D1 |
| D5 | `/sum plots trust/untrust/transfer` permissions + non-owner trusted-builder support. | S | D4 |
| D6 | Plot browser GUI (`/sum plots gui`). | M | D3 |
| D7 | Optional rental flow + auto-balance-deduction. | M | D5 |
| D8 | (Optional) EconomyInc plots migration command. | S | D1 |

**Total effort for D1–D6** (everything except rental and EconomyInc migration): ~5 days. **D7 + D8** add ~1 day each.

### Open questions for Section D

These can stay open until D-phase work begins.

1. **Per-dimension or world-global plots?** Vanilla `WorldSavedData` is per-dimension by default; cross-dimension plots would need a separate global manager. Recommend per-dimension.
2. **Y-axis claims: full-column, custom-3D, or surface-only?** Custom 3D is the most flexible but the chunk-index becomes 3D too (more complex). Recommend full-column claims by default with an admin override for custom 3D.
3. **Protection scope: blocks only, or also entities (mobs, item frames, paintings)?** Vanilla griefing is mostly blocks; entity protection is a small additional handler. Recommend including it.
4. **Plot creation: admin-only, or can players self-stake claims (with a limit)?** EconomyInc is admin-only, FTBChunks lets players claim. Recommend admin-only for v1 with a config flag for opening it up later.
5. **Plot pricing: flat dollar or per-block (e.g. $1 per block^2)?** Per-block scales fairly with claim size but is harder to think about. Recommend flat dollar by default with an admin-config-controlled per-block-floor formula.
6. **Should D ship before or after the rest of Section C?** Section C is the bank/economy work; Section D is the land-claim work. Doing C first means plots have a working `EconomyBridge.adjustBalance` to deduct from, so the order is C → D. Don't interleave.

---

## Open questions

A1 questions are resolved (see ✅). A2/A3 questions still open at the time of writing.

1. ✅ **ATM GUI: wrap EconomyInc's, or build our own?** Resolved 2026-05-07: built our own (`GuiSumAtm`). No credit-card requirement; cleaner UX.
2. ✅ **Soft dependency: reflection or compile-time?** Resolved 2026-05-07: reflection-only via `EconomyBridge`.
3. ✅ **ATM block textures: hand-drawn, AI-generated, or "good enough" pixel art?** Resolved 2026-05-07: pixel art via `tools/atm_textures/generate.py`. Alex can polish/swap any time without touching the block models.
4. **Vault door passcode UI: chat-driven form or in-GUI text field?** GUI is more polished but more code. → **Default: in-GUI text field.**
5. **Safe deposit box size: 3×3 (single chest equivalent) or 5×3 (large chest equivalent)?** → **Default: 3×3, configurable.**
6. **Bank teller dialogue: hand-written list or pull from a config file?** → **Default: config file, with sensible built-in defaults.**
7. **Should phase A3 (bank teller) ship before phase A2 (bank lobby)?** A teller standing behind a vanilla counter looks worse than a teller standing behind our custom counter. → **Recommendation: ship A2 before A3.**

---

## Risks across the whole roadmap

- **EconomyInc API drift.** The mod hasn't seen a release since Nov 2020, but if Florent ships an update that changes `IMoney` signatures, our reflection-based bridge needs to handle it without crashing the world. Always wrap reflective calls in try/catch and fall back to "economy unavailable" cleanly.
- **Block model count growth.** Each ATM variant + counter pieces + vault door + safe deposit box + trash can + mailbox + business card + job board adds up to ~15–20 new blocks/items. Watch the registry growth; nothing dangerous, but it does mean ~20 new lang-string entries and ~20 new texture files.
- **Save data per block.** Several phases (A2 vault door, A2 safe deposit, A3 mailbox, JB job board) want world-saved state. A shared "SUM SavedData" infrastructure (one `WorldSavedData` per feature, written into the world's `data/` folder) is worth building once and reusing.
- **Multiplayer correctness.** All economy operations must run on the server, not the client. SUM has commands and a Roamer system already that get this right; just make sure new GUIs send packets to the server rather than mutating client-only state.

---

## Testing checklist (per-phase, abbreviated)

Each phase gets its own checklist when it's promoted from sketch → implementation. Top-level smoke tests common to all:

- [ ] Build cleanly: `JAVA_HOME=... ./gradlew build`
- [ ] World loads with the new blocks/items registered; no crash on first place
- [ ] Save/quit/reload cycle preserves block state and any tile-entity NBT
- [ ] On a dedicated server: client connects, sees the blocks, can interact, GUIs sync correctly
- [ ] If EconomyInc is **not** loaded: `EconomyBridge.isAvailable()` returns false and any economy-dependent action shows a graceful chat message instead of crashing

Phase-specific checklists go into the phase's own plan doc once that phase begins.

---

## Session-end summary (2026-05-08)

This session shipped the rest of Sections C, B, and the core of D. Phase log in commit order:

| Commit | What | Section |
|---|---|---|
| `15192f2` | Bill model JSON fix (literal `\n` → real newlines) | C2 fix |
| `a79f100` | C4 phone + debit card items | C |
| `c9a40e2` | Roamer role auto-naming (greetings now visible) | A3 fix |
| `efb8e47` | C5 player shop block (vending machine) | C |
| `03f715d` | Phone/card 2nd-click GUI open fix (proxy path) | C4 fix |
| `9cbe648` | C6 bill changer + 8 packet items | C |
| `9b43be2` | C7 bills loot injector | C |
| `0df41a4` | C8 decorative bills display + TESR | C |
| `97c1a85` | C9 migrate-economy command + removal docs | C |
| `f178d62` | TR trash can | B |
| `568aa54` | ST storm-shelter sign (initial floor plaque) | B |
| `a9f04b8` | SV sleep voting | B |
| `ff3555b` | BC business cards | B |
| `2c7d263` | MX mailbox | B |
| `5fca248` | JB job board + listings | B |
| `c930136` | D1 plot data model | D |
| `db55bf6` | D2 plot wand + admin commands | D |
| `20d6e16` | D3 plot buy/sell | D |
| `fdf6393` | D4 plot protection events | D |
| `5bd9474` | D5 plot trust/transfer | D |
| `ae24693` | Storm-shelter sign preference (signed > organic when indoor) | enhancement |
| `af0b3b6` | `/sum help` paginated, 7 pages | enhancement |
| `25e1bf8` | Plot wand corner-A fix (sneak+right-click) | D2 fix |
| `6866ebc` | Storm sign wall-mounted + AI loosened (range/claim/path tolerance) | enhancement |

## Where things stand

- **All four roadmap sections shipped** for everything that was originally planned. SUM is a feature-complete server-utility + economy mod.
- **D6/D7/D8 deferred**: plot-browser GUI, rental flow, EconomyInc-plot migration. None are blocking; chat-driven plot management works fine for v1.
- **Most surface still needs in-game testing**. Verified-in-playtest items are listed in the "Testing status" section. Untested items are flagged with `[ ]`. Highest-priority untested: C1 standalone path (without EconomyInc loaded), `/sum help` pages, plot system end-to-end, storm-shelter sign multi-roamer convergence post-`6866ebc`.
- **Working directory is clean at `6866ebc`**.

## What's left to do (if Alex picks up later)

Roughly in priority order:

1. **Playtest the remaining unverified items.** Anything with `[ ]` in the Testing status checklist. Bug-fix commits from playtest reports are the highest-leverage work.
2. **D6 plot-browser GUI** (optional): paginated GuiScreen showing FOR_SALE plots with click-to-buy. ~M effort, ~250 LOC. Mirrors `GuiJobBoard`. Skip if chat-driven `/sum plots list` is enough.
3. **D7 rental flow** (optional): time-bounded ownership with auto-balance-deduction. ~M effort. Niche — only if a server actually needs it.
4. **D8 EconomyInc-plots migration** (optional): only relevant if a pack with EconomyInc is being moved to SUM. ~S effort, similar shape to C9.
5. **Polish passes**: real art for any of the placeholder textures; in-GUI passcode entry for vault doors; postal-worker Roamer role for MX; cash-register decorative block; etc.

Section A through D core is done. The mod doesn't need anything else to be useful — what's left is polish.
