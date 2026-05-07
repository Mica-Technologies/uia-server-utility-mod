# SUM feature roadmap — bank/ATM kit + other server-utility ideas

Status: **planning only — no code written.** This document is a forward-looking catalog of features SUM could add next, organized so each one ships independently. Top priority by user request is a bank/ATM kit; the remainder are sketched to give Alex options when picking what to build after that.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up where we left off:

> I'm continuing the SUM mod's feature roadmap. Working directory is `E:\gitRepos\uia-server-utility-mod`. The mod targets Minecraft 1.12.2 Forge, mod ID `sum`, package `com.micatechnologies.minecraft.sum`. Build with `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build` (set `JAVA_HOME` from `~/.jdks/azul-17.0.18`).
>
> **Read first, in this order:**
>
> 1. `docs/agent_progress/FEATURE_ROADMAP.md` — this doc. Has the full plan, EconomyInc integration approach, every phase, open questions, recommended order.
> 2. `docs/agent_progress/PLAYER_FAVORITES_MENU.md` — recently-shipped feature; look at it for the format conventions, the per-phase commit cadence, and how SUM's `SumClientProxy`/`SumConfig`/`CommandSum`/lang files get extended for a new feature.
> 3. `docs/agent_progress/NPC_OPTIMIZE_PLAN.md` — older but useful for the Roamer-related parts (phase A3 bank teller, MX postal worker, ST storm-shelter sign).
> 4. `CLAUDE.md` — build commands, JDK location, project conventions.
> 5. `MEMORY.md` (in `~/.claude/projects/E--gitRepos-uia-server-utility-mod/memory/`) — Alex's standing preferences (no `git push`, omit Co-Authored-By only if asked, JDK at `~/.jdks/azul-17.0.18`, etc.).
>
> **Top priority is Section A** of the roadmap, starting with phase **A1** (realistic ATM block kit). A2 (bank lobby) and A3 (bank teller NPC) follow naturally; each ships on its own. Section B (mailbox, sleep-vote, trash can, business cards, job board, storm-shelter signage) is a queue of ideas for after Section A — do not start any of them without explicit confirmation.
>
> **Before writing any code, do the EconomyInc spike** described in the "EconomyInc integration approach" section. The recommended path is reflection-only via a single `EconomyBridge` helper class. Concrete artifacts to inspect before locking the reflection signatures:
>
> - JAR location: `C:\Users\ahawk\AppData\Roaming\.minecraft\mods\economy-inc.jar` (also referenced in the Alto modpack manifest at `E:\gitRepos\minecraft-launcher-modpacks\alto\manifest.json`).
> - Mod ID: `economy` (from the mod's `mcmod.info`).
> - Capability interface: `fr.fifou.economy.capability.IMoney` — get the actual method names by decompiling that class. Likely `getMoney()` returning int, `setMoney(int)`, but verify.
> - Capability holder: `fr.fifou.economy.capability.CapabilityLoading` — has the `@CapabilityInject Capability<IMoney>` static field.
> - Main mod class: `fr.fifou.economy.ModEconomy`.
>
> **Before resolving phases A1–A3 with code, run the open questions past Alex.** The two that materially shape the implementation are (1) ATM GUI ownership — wrap EconomyInc's existing GUI or build our own; default is build our own — and (2) soft-link mechanism — reflection-only `EconomyBridge` (default) or compile-time `compileOnly` dep on EconomyInc.
>
> **Watch out for:** Alex maintains a separate Weather 2 Remastered fork at `E:\gitRepos\LDW2` for weather/snow features that pair with this pack — *not* this repo. There may be uncommitted WIP in this repo's working tree from prior sessions (roamer atlas work under `tools/roamer_atlas/`, `ModelRoamer.java`, etc.) — preserve it and don't bundle it into your commits. Always `git restore --staged` anything that wasn't yours before committing.

---

## Status snapshot

| Phase | Goal | Effort | Status | Commit |
|---|---|---|---|---|
| A1 | Realistic ATM block kit (street kiosk, wall-mounted, drive-thru) | M | ☐ not started — top priority | — |
| A2 | Bank-lobby kit (counter blocks, vault door, safe deposit box, velvet rope) | M-L | ☐ not started | — |
| A3 | Bank teller NPC (Roamer subclass with bank-themed greetings) | S | ☐ not started | — |
| MX | Mailbox + postal system | M | ☐ not started | — |
| SV | Sleep voting / time skip | S | ☐ not started | — |
| TR | Trash can / item-disposal block | XS | ☐ not started | — |
| BC | Business cards (item-based player profile exchange) | S | ☐ not started | — |
| JB | Job board (bulletin-board block for player-listed jobs) | L | ☐ not started | — |
| ST | Storm-shelter signage tied to existing Roamer storm cache | XS | ☐ not started | — |

Effort scale: XS (≤50 LOC, <1h), S (~100 LOC, 1–2h), M (~300 LOC, half-day), L (≥500 LOC, full day or more).

---

## First-day pre-flight checklist (for the next session)

Before touching code, verify each of these. Most should be quick `gh`/`Bash`/`Read` confirmations.

- [ ] `git log --oneline -10` shows recent SUM history; HEAD is on `main` and clean (or you've inspected and noted any uncommitted WIP).
- [ ] `JAVA_HOME="C:/Users/<username>/.jdks/azul-17.0.18" ./gradlew build` succeeds on the current HEAD before any of your changes.
- [ ] `unzip -l C:/Users/ahawk/AppData/Roaming/.minecraft/mods/economy-inc.jar | grep capability` confirms the `fr/fifou/economy/capability/IMoney.class` and `CapabilityLoading.class` are present at the expected paths.
- [ ] You've decompiled `IMoney` and `CapabilityLoading` (use the deobf source jar if available — earlier session located the fernflower-cache source at `C:/Users/ahawk/.gradle/caches/retro_futura_gradle/fernflower-cache/*.jar` for vanilla classes; for EconomyInc classes you'll need to decompile the production jar or pull source from the GitHub repo). Confirmed method names and field names for `IMoney`'s balance getter/setter.
- [ ] Confirmed the `@CapabilityInject` location for `Capability<IMoney>` in `CapabilityLoading`. Locked the field name into the `EconomyBridge` reflection.
- [ ] You've tested in dev (`runClient`) that `Loader.isModLoaded("economy")` returns true when EconomyInc is present and false when it's not.

If any of those fail, fix before proceeding — don't build features on top of a shaky integration.

---

## Modpack context (Alto pack as of 2026-05-07)

This shapes which features are worth building. Highlights from the pack manifest:

- **EconomyInc 1.6.2** (mod id `economy`) provides `BlockAtm`, `BlockChanger`, `BlockSeller`, three `BlockVault` variants, `BlockBills`, `ItemCreditcard`, ~14 bill items, `EntityInformater` (NPC), `CommandBalance`, `CommandPlots`, plus a Forge capability `fr.fifou.economy.capability.IMoney` with a default handler. So the *primitives* of money already exist; SUM's bank/ATM work should add aesthetic and ergonomic depth, not reinvent the balance system.
- **CSM (MinecraftCitySuperMod)** provides traffic signals, fire alarms, crosswalks. SUM's existing Roamer NPCs respond to fire/storm alarms. Anything that fits a "city street" aesthetic is on theme.
- **JEI** (Just Enough Items) — already considered in the favorites work; relevant again for any inventory GUI.
- **No mailbox/postal mod, no sleep-vote mod, no chunk-claim mod, no employment mod** are present in the manifest — those gaps are real opportunities for SUM.

---

## EconomyInc integration approach

Three SUM phases (A1, A2 partly, A3 partly) need to read/write a player's balance. Two ways to wire this up:

### Option 1 — Reflection-only soft dependency (recommended)

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

### Phase A1 — Realistic ATM block kit

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

### Phase A2 — Bank-lobby kit

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

### Phase A3 — Bank teller NPC

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

## Open questions

These should be resolved before phase A1 starts. The first two materially shape the implementation.

1. **ATM GUI: wrap EconomyInc's, or build our own?** Wrapping is faster (zero new GUI code) but ties us to EconomyInc's specific UX (notably: their GUI requires the `Creditcard` item to be present, which may feel like dead weight on a SUM-branded ATM). Building our own is more code but lets the *withdraw/deposit/transfer* flow work without holding a credit card. → **Default unless told otherwise: build our own.**
2. **Soft dependency: reflection or compile-time?** Both work; reflection avoids build coupling but is verbose. → **Default unless told otherwise: reflection-only via `EconomyBridge`.**
3. **ATM block textures: hand-drawn, AI-generated, or "good enough" pixel art?** Affects time budget more than design. → **Default: I do "good enough" pixel art for the dev pass; Alex polishes/swaps later if desired.**
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

## Recommendation: where to start

Order, by my read of value-vs-effort and how the pieces interlock:

1. **A1 — Realistic ATM block kit.** Highest user-articulated priority. Self-contained. Forces the `EconomyBridge` infrastructure into existence so A2/A3 can reuse it. ✅ Start here.
2. **A2 — Bank lobby kit.** Once ATMs work, the bank counter + vault door + safe deposit box make a complete "bank room" buildable. Safe deposit box is the most genuinely useful block in this whole roadmap.
3. **A3 — Bank teller NPC.** Polish on top of A2. Quick to build because of existing Roamer infrastructure.
4. **MX — Mailbox.** First Section-B item to consider; it's a genuine gap and fits the "city utility" theme.
5. **SV — Sleep voting.** Tiny win; ship whenever.
6. **TR — Trash can.** Tiny; could be bundled with anything else.
7. **BC — Business cards.** Cute; low priority unless servers ask for it.
8. **JB — Job board.** Biggest item; defer until there's clear demand.
9. **ST — Storm-shelter signage.** Tiny; bundle with the next Roamer touch-up.

Total budget for everything end-to-end: ~5–7 days of focused work. Phases A1+A2+A3 alone are ~2 days.
