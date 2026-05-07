# Player favorites menu (creative-mode favorites tab)

Status: **MVP code complete on 2026-05-07; in-game testing pending.** Commits `9c70b8c` through `0a25c14` ship phases #1–#5. The full build is green (`./gradlew build`); no in-world verification yet. Phases #6–#8 are deferred until Alex has tried the MVP in the Alto pack.

This document captures the feasibility analysis, the design, and the phased implementation plan for adding a "Favorites" tab to the creative inventory GUI. The Alto pack runs ~13 pages of creative tabs, so a personal favorites tab — especially for tools/weapons — short-circuits hunting through pagination every time the player wants the same handful of stacks.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up where we left off:

> I'm continuing work on the SUM mod's creative-mode favorites tab.
>
> Read `docs/agent_progress/PLAYER_FAVORITES_MENU.md` first — it has the full plan, feasibility notes, phases, what's been done vs. what's left, open design questions, and the testing checklist.
>
> Phases #1–#5 (the MVP) shipped in commits `9c70b8c`, `b8fa3ff`, `e40d378`, `dd2d1ba`, `0a25c14`. Code compiles cleanly and the full `./gradlew build` is green, but **none of it has been verified in-game**. Before doing more work: ask Alex how playtest went and whether they hit any of the watch-outs in the Risks section (tooltip-vs-overlay layering, JEI search-field focus eating B presses, search-tab false-negatives on toggle, reflection failure on `setCurrentCreativeTab` in obfuscated build).
>
> All design questions were resolved on 2026-05-07 (see "Resolved design decisions"); don't re-litigate them unless Alex brings them up. Locked-in: toggle keybind = `B`, jump-to-favorites keybind = `Z` (originally `Ctrl+B`, but Alex's setup binds Ctrl+B to a narrator toggle — see "Open follow-ups"), NBT excluded from identity in v1, storage is per-installation at `<minecraft>/config/sum/favorites.json`, soft warn at 200 favorites with no hard cap.
>
> Phases #6–#8 (reorder, /sum favorites command, polish) are deferred. Don't start them without explicit confirmation. The feature is **client-only** (creative inventory is a client-side construct), **creative-only by virtue of GuiContainerCreative not opening in survival**, and **persisted to a JSON file in the Minecraft config directory** so favorites survive across worlds and reinstalls of the mod pack.

---

## Status snapshot

| Phase | Goal | Status | Commit |
|---|---|---|---|
| #0 | Feasibility spike (this doc) | ✅ done | — |
| #1 | `FavoritesStore` — in-memory list + JSON persistence | ✅ done, unverified in-game | `9c70b8c` |
| #2 | `CreativeTabFavorites` — custom tab populated from the store | ✅ done, unverified in-game | `b8fa3ff` |
| #3 | Toggle keybind (`B` default) — add/remove favorite while hovering an item | ✅ done, unverified in-game | `e40d378` |
| #4 | Star overlay icon on favorited slots inside any creative tab | ✅ done, unverified in-game | `dd2d1ba` |
| #5 | "Jump to favorites" keybind (`Ctrl+B` default) — switch tab without paginating | ✅ done, unverified in-game | `0a25c14` |
| #6 | Reorder support (Shift+Scroll on a favorited slot in the favorites tab) | ✅ done, unverified in-game | (this commit) |
| #7 | `/sum favorites` admin/debug subcommand (list, clear, export, importfile, file) | ✅ done, unverified in-game | (this commit) |
| #8 | Polish: config knob, language strings, JEI/NEI compat plan | ✅ done, unverified in-game | (this commit) |

Phases #1–#5 are the **MVP** and have all landed; the full `./gradlew build` is green. Phases #6–#8 are nice-to-have — pause for in-game testing first, then revisit.

---

## Feasibility summary

Short answer: **yes, this is feasible without mixins or core mods**, using only public Forge 1.12.2 hooks. The main building blocks are all standard:

| Need | Mechanism |
|---|---|
| New tab in creative inventory | Subclass `net.minecraft.creativetab.CreativeTabs`, override `displayAllRelevantItems(NonNullList<ItemStack>)`. Construction registers the tab automatically. |
| Show only in creative | Automatic — `GuiContainerCreative` only opens for `GameType.CREATIVE` (and spectators viewing creatives, which is a non-issue here). The survival player never sees creative tabs at all. |
| Detect "the item the mouse is hovering" inside the creative GUI | `GuiContainerCreative.getSlotUnderMouse()` is public; can be read from a `GuiScreenEvent.KeyboardInputEvent.Pre` handler scoped to `GuiContainerCreative`. |
| Toggle keybind that only fires inside the creative GUI | `KeyBinding` registered via `ClientRegistry.registerKeyBinding`, then dispatched from the same `GuiScreenEvent.KeyboardInputEvent.Pre` based on `Keyboard.getEventKey()`. (Top-level `InputEvent.KeyInputEvent` doesn't fire while a GUI is open in 1.12.2 — must use the GUI variant.) |
| Render a star overlay on favorited slots | `GuiScreenEvent.DrawScreenEvent.Post` filtered to `GuiContainerCreative`, iterate `inventorySlots.inventorySlots`, draw a 16×16 sprite over each favorited slot. |
| Persist favorites across launches | Plain JSON file in `<minecraft>/config/sum/favorites.json`. Loaded at `FMLPostInitializationEvent` (after registries are populated), saved on toggle. |
| Reorder, scroll past 45 items | `CreativeTabs.hasScrollBar()` defaults to true and the standard tab page already scrolls vertically; no custom GUI needed for v1. |

### What does **not** work and why

- **Mixing into `GuiContainerCreative` to insert a "favorite this" button**. SUM does not currently use mixins (`build.gradle` ships with mixins disabled — see `usesMixins` in `gradle.properties`). Enabling mixins for one feature is a heavy lift and a breaking change for the build setup. **Avoid.**
- **Putting the favorites tab at a fixed position (always first / always last)**. `CreativeTabs.tabIndex` is assigned in construction order; with 13+ tabs from many mods, we cannot guarantee a position. Best we can do is construct ours early in `preInit` so it lands among the first non-vanilla tabs. **Mitigation: ship a "jump to favorites" keybind in phase #5 so position is irrelevant.**
- **Synchronizing favorites across machines / between client and server**. Favorites are a personal client-side preference for the local player's creative menu. Servers don't store creative inventory — they react to vanilla creative-give packets. **No netcode needed; favorites are local-only.**
- **Showing favorites in survival**. Survival mode opens `GuiInventory`, not `GuiContainerCreative`. There is no "creative tab system" in survival to plug into. The only way to give survival players a favorites HUD is a from-scratch GUI screen, which is out of scope for v1 and was excluded in the original ask anyway.

### Identity model: what counts as "the same item"

This is the one semantic question that matters at design time. Two stacks should be considered the same favorite when:

- Same `Item` (registry name match), AND
- Same metadata / damage value (1.12.2 uses `meta` for subtypes — wool colors, wood types, etc.). For tools, `damage = durability`, but creative tabs always show full-durability stacks, so this is fine to compare.

NBT (custom names, enchantments, modifier tags) is **excluded** from the equality key for v1. The rationale:

- Creative tabs almost always emit "vanilla" stacks. Custom-NBT items are a niche.
- Including NBT in the equality key forces a deep `NBTTagCompound.equals` on every render frame for the overlay phase — measurable cost when the slot grid is dense.
- A user who wants their custom-named diamond sword as a favorite can still copy the NBT into the favorites JSON manually, or we can ship phase #6 with NBT-aware "advanced mode" toggled by a config flag.

The favorite key type is therefore a small POJO: `FavoriteKey { ResourceLocation registryName; int meta; }`. `equals`/`hashCode` over those two fields. Storage in the JSON file is `"<registry_name>#<meta>"`, e.g. `"minecraft:wool#14"` for red wool.

---

## Architecture overview

```
src/main/java/com/micatechnologies/minecraft/sum/favorites/
├── FavoriteKey.java               # Item+meta equality wrapper (immutable, hashable)
├── FavoritesStore.java            # In-memory list + JSON load/save; thread-confined to client thread
├── CreativeTabFavorites.java      # CreativeTabs subclass; displayAllRelevantItems() reads the store
├── FavoritesClientHandler.java    # @SubscribeEvent: keybinds, hover toggle, overlay rendering
├── FavoritesKeybinds.java         # KeyBinding instances and registration
└── FavoritesIcon.java             # 16x16 star sprite resource handle
```

All client-side. Wired in via `SumClientProxy.preInit` (keybinds, tab construction) and `SumClientProxy.init` / `postInit` (event bus registration, store load — store load is gated to **post-init** because it has to resolve registry names against the populated `Item` registry).

### Where this slots into existing SUM code

- **`Sum.java`** — no changes. The favorites subsystem doesn't need server-side hooks for v1.
- **`SumClientProxy.java`** — register `FavoritesClientHandler` on the event bus, register keybinds, kick off `FavoritesStore.load()` in `postInit`.
- **`SumCommonProxy.java`** — no changes. Server-side proxy doesn't see this feature.
- **`SumTab.java`** — leave the existing SUM creative tab alone. The favorites tab is a *separate* `CreativeTabs` instance, not a replacement. Construct it from `SumClientProxy.preInit` so it doesn't appear at all on dedicated servers.
- **`SumConfig.java`** — add a small `favorites` category with the toggle keybind name (default), star-overlay enable flag, and NBT-aware-mode flag (off by default). Use the same pattern as `roamer` / `roadrunner` categories.
- **`CommandSum.java`** — phase #7 adds `/sum favorites <list|clear|export|import> ...`. Server-side command, but it operates on the *client* favorites file by virtue of running in single-player. On a dedicated server it should print "favorites are client-side; use the keybind in your creative menu" rather than crash.
- **`SumRegistry.java`** — no changes. No new blocks/items.
- **`assets/sum/textures/gui/favorite_star.png`** — new 16×16 (or 32×32 for crisp HD packs) sprite.

---

## Phase detail

### Phase #1 — `FavoritesStore` and JSON persistence

**Goal:** an in-memory `List<FavoriteKey>` that loads from disk on first use and saves on every mutation. Pure data layer — no GUI touched yet.

**Files added:**

- `favorites/FavoriteKey.java` — `final class` with two fields (`ResourceLocation registryName`, `int meta`), `equals`/`hashCode`, `parse(String)` and `toString()` ("`namespace:path#meta`"), and a `resolveStack()` helper that returns an `ItemStack` (or `ItemStack.EMPTY` if the item has been uninstalled since the favorite was saved).
- `favorites/FavoritesStore.java` — singleton-ish static class. Holds an `ArrayList<FavoriteKey>` (ordered; insertion order is the display order). Methods: `add`, `remove`, `toggle`, `contains`, `snapshot`, `load`, `save`, `getStorageFile`. File location: `<minecraft>/config/sum/favorites.json`.

**JSON shape:**

```json
{
  "version": 1,
  "items": [
    "minecraft:diamond_sword#0",
    "minecraft:wool#14",
    "tconstruct:rapier#0"
  ]
}
```

`version` lets us migrate later (e.g. when phase #6 adds NBT-aware entries). Unknown items at load time are silently dropped *after* a warn log so a removed mod doesn't poison the file.

**Concurrency:** all access is from the client thread. `FavoritesStore` does not need synchronization. `save()` writes to a `.tmp` file then atomic-renames to avoid leaving a half-written file if the JVM dies mid-save.

**Done when:**
- Calling `FavoritesStore.add(key); FavoritesStore.save();` produces a readable JSON file at the expected path.
- Restarting Minecraft and calling `FavoritesStore.snapshot()` returns the previously-added entries in insertion order.
- A unit test exercises round-tripping (out of scope if `enableJUnit` isn't on for this project — currently it isn't, so verify by hand).

---

### Phase #2 — `CreativeTabFavorites` populated from the store

**Goal:** a new tab appears in the creative inventory, showing whatever `FavoritesStore.snapshot()` returns. Empty tab on first install.

**Files added:**

- `favorites/CreativeTabFavorites.java` — extends `CreativeTabs`. Constructor takes the label `"sum_favorites"` (used as `getTabLabel()` for the language key `itemGroup.sum_favorites`). Overrides:
  - `createIcon()` → returns a `new ItemStack(Items.NETHER_STAR)` for v1 (cheap, recognizable). Phase #8 swaps to a custom star icon.
  - `displayAllRelevantItems(NonNullList<ItemStack> out)` → iterate `FavoritesStore.snapshot()`, call `key.resolveStack()` on each, add non-empty stacks to `out`.
  - `hasSearchBar()` → false (favorites are short by definition; saves the cost of the search-tab GUI).

**Wiring:** `FavoritesClientHandler.init()` (called from `SumClientProxy.preInit`) constructs the tab. Construction has the side effect of registering the tab into `CreativeTabs.CREATIVE_TAB_ARRAY`.

**Tab placement:** with 13+ tabs already in the pack, we cannot guarantee position. Construct as early as possible in `preInit` to land near the start of the modded section. Phase #5 mitigates the "where did it go" problem with a hotkey.

**Done when:**
- New tab shows up in the creative menu with a Nether Star icon.
- Manually editing `favorites.json` to contain `["minecraft:diamond_sword#0"]` and reopening the menu shows a diamond sword in the favorites tab.
- The tab is empty and harmless when the JSON file doesn't exist or is empty.

---

### Phase #3 — toggle keybind

**Goal:** while the creative inventory is open and the mouse is hovering an item slot, pressing the toggle keybind (default `B`) adds the hovered item to favorites if not present, or removes it if already present. Empty slots and the favorites tab itself are special-cased.

**Files added:**

- `favorites/FavoritesKeybinds.java` — registers two `KeyBinding`s in `preInit`: `key.sum.favorites.toggle` (default `B`, category `key.sum.category`) and the phase #5 jump key.
- `favorites/FavoritesClientHandler.java` — `@SubscribeEvent` on `GuiScreenEvent.KeyboardInputEvent.Pre`:
  1. Bail out unless `event.getGui() instanceof GuiContainerCreative`.
  2. Bail out unless `Keyboard.getEventKeyState()` is true (key down, not key up).
  3. Bail out unless `Keyboard.getEventKey() == toggleKeyBinding.getKeyCode()`.
  4. Get `slotUnderMouse` from the GUI. Bail out if null or empty.
  5. Build a `FavoriteKey` from the slot's `ItemStack`. Call `FavoritesStore.toggle(key)`. Save.
  6. Play `BLOCK_NOTE_PLING` at low volume as audible confirmation, color the stack in the overlay (phase #4) on next frame.
  7. `event.setCanceled(true)` so the keypress doesn't leak into the search bar if it happens to be focused. (This is why we use the `*Pre` event variant.)

**Edge case — the favorites tab itself:** if the user toggles a slot inside the favorites tab, treat that as a "remove" (regardless of `contains()`). This matches the user expectation: pressing `B` on something in the favorites tab should remove it. Implement by checking `((GuiContainerCreative) gui).getSelectedTabIndex() == FavoritesTab.tabIndex` in step 5.

**Edge case — the player inventory tab:** the inventory tab shows the player's actual inventory and the bottom row of stacks. Toggling a favorite from there is fine — the favorite is keyed by item type, not by slot location. Same code path works.

**Edge case — search tab results:** the search tab is a synthetic tab; slot stacks behave like any other tab. No special handling.

**Done when:**
- Open creative menu, hover any item, press `B` → that item appears in the favorites tab.
- Press `B` again on the same item → it disappears from favorites.
- Editing keybind in vanilla controls menu rebinds it correctly.
- Pressing `B` on an empty slot or while not hovering anything is a no-op.

---

### Phase #4 — star overlay on favorited slots

**Goal:** every slot that holds a stack matching a current favorite gets a small gold star overlay in its upper-right corner, regardless of which tab the user is viewing. Visual confirmation of "this is favorited."

**Files modified:** `FavoritesClientHandler.java`. **Files added:** `assets/sum/textures/gui/favorite_star.png` (16×16, alpha-cut star).

**Implementation:**
- `@SubscribeEvent` on `GuiScreenEvent.DrawScreenEvent.Post` filtered to `GuiContainerCreative`.
- Build a `Set<FavoriteKey>` from the store *once per frame* (cache invalidation: bump a version counter on every store mutation; rebuild the set only when the counter advances). Avoids hashing 45 slots × 60 fps × N favorites of allocator pressure.
- For each slot in `gui.inventorySlots.inventorySlots`, if the slot's stack is non-empty and the key is in the set, draw the star at `(slot.xPos + 8, slot.yPos)` (upper-right). Use `Minecraft.getMinecraft().getTextureManager().bindTexture(STAR)` then `Gui.drawModalRectWithCustomSizedTexture` to handle non-256 atlas size cleanly. `GlStateManager.disableLighting()` and `enableBlend()` before, restore after.
- Honor a config flag `favorites.showStarOverlay` (default true) — see phase #8.

**Performance note:** the `Set` cache means per-frame cost is `O(visible_slots)` `HashSet.contains` calls, not `O(slots × favorites)`. Star draw is one quad per favorited slot. Should be invisible in profiles even with hundreds of favorites and the search tab showing 90 slots.

**Done when:**
- Favoriting an item makes a star appear in its slot in every tab that displays it.
- Removing a favorite makes the star disappear next frame.
- Disabling the config flag hides all stars but leaves favorites functional.

---

### Phase #5 — "jump to favorites" keybind

**Goal:** while the creative inventory is open, pressing `Ctrl+B` (default) switches directly to the favorites tab without paginating through the tab arrows. Removes the "where did my tab go" problem entirely.

**Files modified:** `FavoritesKeybinds.java` (register the binding), `FavoritesClientHandler.java` (handle it in the same `KeyboardInputEvent.Pre` branch as phase #3).

**Implementation:** when the binding fires, call `((GuiContainerCreative) gui).setCurrentCreativeTab(CreativeTabFavorites.INSTANCE)` (it's `setCurrentCreativeTab` in 1.12.2 — verify in mappings; may be private and require `setSelectedTab` reflection or the public alternative). Worst case it's `((GuiContainerCreative) gui).selectTab(...)` — confirm at implementation time.

**Done when:** in the creative menu, hitting `Ctrl+B` snaps to the favorites tab from any other tab, instantly.

---

### Phase #6 — reorder support (deferred to MVP+1)

**Goal:** let the user reorder the favorites list so the most-used items show up at the top.

**Approach (lightweight):** in the favorites tab only, listening to `GuiScreenEvent.MouseInputEvent.Pre`, detect shift+scroll-wheel on a hovered slot and use it to move the corresponding favorite up/down in the list. Save after each move. No drag-and-drop; vanilla click-drag interactions in `GuiContainerCreative` are bound to stack pickup behavior.

**Alternative (heavier, deferred):** subcommand `/sum favorites move <fromIndex> <toIndex>`. Less ergonomic in-game but trivial to implement. Probably ship both: scroll for ergonomics, command for completeness.

**Risks:** scroll-wheel inside `GuiContainerCreative` already drives the search-tab scroll bar. Need to verify our `MouseInputEvent.Pre` handler runs *before* vanilla and only cancels when the conditions match (shift held, on the favorites tab, hovering a non-empty slot).

---

### Phase #7 — `/sum favorites` subcommand

**Goal:** admin/debug visibility into the favorites list and import/export for power users moving between installations.

**Subcommands:**
- `/sum favorites list` — print the current list with indices.
- `/sum favorites clear` — empties the list (with a confirmation prompt: requires `/sum favorites clear yes` on second invocation).
- `/sum favorites export` — copies the JSON to clipboard (client-side only — server-side prints the JSON to chat instead).
- `/sum favorites import <json>` — replaces the list from a JSON string. Same rules.

Mostly a thin wrapper around `FavoritesStore` methods. Slot it into `CommandSum.execute` next to the existing `roamer` / `addroamerblock` cases.

**Server-side note:** on a dedicated server the favorites file lives on the *client*. The server-side command should print "favorites are stored on your client; use the keybind from your creative inventory" and refuse to operate. Detect via `sender.getEntityWorld().isRemote` semantics applied at the right layer (commands run on the integrated server in single-player, so `isRemote` is false even there — the right test is `FMLCommonHandler.instance().getSide().isClient()` for the dedicated-server filter).

---

### Phase #8 — polish

- **Custom tab icon.** Replace the Nether Star with a SUM-branded star sprite. Either reuse an existing `Item` and rely on its model, or register a new tab-icon-only item. The latter is cleaner but adds a registry entry; for the first cut, the Nether Star is fine and zero-overhead.
- **Language strings.** Add to `assets/sum/lang/en_us.lang`:
  ```
  itemGroup.sum_favorites=SUM: Favorites
  key.sum.favorites.toggle=Toggle Favorite
  key.sum.favorites.jump=Jump to Favorites
  key.sum.category=Server Utility Mod
  ```
- **Config knobs.** Add `favorites` category to `SumConfig.java`:
  - `enableStarOverlay` (default true) — see phase #4.
  - `enableNbtAware` (default false) — gate for phase #6+ NBT-aware identity.
  - `confirmClearOnCommand` (default true) — whether `/sum favorites clear` requires the `yes` confirmation.
- **JEI/NEI compat smoke test.** JEI hooks into the creative GUI for its own search overlay. Verify that:
  - Our keybind doesn't fire when the JEI search bar has keyboard focus (cancel-handling in `KeyboardInputEvent.Pre` handles this if we check `gui.getListener()` or equivalent — verify experimentally).
  - JEI's "show recipes" hotkey on a favorited slot still works.
  - Our star overlay draws *over* JEI's slot highlights, or under them — verify visually and adjust draw order if needed.

---

## Resolved design decisions

These were the open questions at draft time. All resolved with Alex on 2026-05-07.

1. **Toggle keybind default = `B`.** Unbound by default in vanilla 1.12.2 inventory context. Doesn't conflict with `Q` (drop), `E` (close), `1–9` (hotbar), `LMB`/`RMB`, `MMB` (clone).
2. **Jump-to-favorites keybind default = `Z`.** Originally `Ctrl+B` to mirror the toggle, but Alex's setup binds Ctrl+B to a narrator/accessibility toggle (vanilla 1.12.2 doesn't natively define this — likely OS-level or a third-party mod). Switched to `Z`, single key, no modifier — sidesteps the Forge 1.12.2 modifier-key clunkiness too.
3. **NBT excluded from identity in v1.** Identity key is `(registry name, meta)`. Custom-NBT items (named/enchanted tools) all collapse to the same favorite. A future phase can add an opt-in NBT-aware mode behind a config flag.
4. **Tab placement: best-effort early construction.** Built in `SumClientProxy.preInit` so it lands among the first non-vanilla tabs. The `Ctrl+B` jump key sidesteps "where did my tab go" entirely.
5. **Storage = per-installation.** Single JSON at `<minecraft>/config/sum/favorites.json`. Same favorites across every world. Per-world or per-character variants are not on the roadmap.
6. **Favorites count = soft warn at 200, no hard cap.** Log a warning past 200 entries; allow more. Creative tab pages scroll, so large lists still render fine.
7. **`/sum favorites import` mechanism: deferred.** Phase #7 will pick the import format (clipboard vs path-on-disk) at implementation time. Not on the MVP path.

---

## Playtest follow-ups (resolved 2026-05-07)

- **`Ctrl+B` jump conflicted with a narrator-toggle binding** in Alex's setup (not from vanilla 1.12.2 — likely OS-level or a mod). Jump key changed to plain `Z`, no modifier. Resolved.
- **First "B does nothing" report was a testing artifact** — Alex was pressing B/Z while in the world (no GUI open). `KeyboardInputEvent.Pre` only fires when a GUI is open, so there was nothing for our handler to receive. Once tested inside `GuiContainerCreative` (i.e. after pressing E to open the inventory), both `B` (toggle) and `Z` (jump-to-favorites) work as designed. The mid-stream switch to tick-based `Keyboard.isKeyDown()` polling has been reverted in favor of the original `KeyboardInputEvent.Pre` handler — efficient, reactive, no per-tick overhead. Toggle is unchanged at `B`.

## Risks and watch-outs

- **Tab count creep.** The pack already has 13 pages of tabs (~84+ tabs). Adding one more is fine, but if Forge's tab-page math has any rounding bugs at boundary counts, this is where they'd surface. Mitigation: test in-game with the full pack before declaring victory.
- **Keybind conflict reports.** `B` is unbound vanilla, but a popular mod in the Alto pack might claim it. If conflict reports come in, the user can rebind — but a config-driven default would be a small ergonomic win. Phase #8 already covers this.
- **JSON file corruption.** Atomic-rename in `save()` plus a try/catch in `load()` that falls back to an empty list (with a `.bak` of the corrupt file) covers the common cases. The fallback should *not* silently overwrite the corrupt file; preserving the bad copy lets the user recover.
- **Removed-mod entries.** When a mod is uninstalled, its registry names disappear and the favorites JSON entries that point to them resolve to `ItemStack.EMPTY`. The favorites tab silently skips these. The user only loses *display* of the removed item — re-adding the mod restores them. Don't auto-prune the JSON; the user might be temporarily disabling a mod.
- **Performance of the overlay during heavy frames.** With JEI installed, `DrawScreenEvent.Post` fires 60+ times per second on a tab page that may show 90+ slots after JEI extends it. Per-frame allocations must be zero in the overlay handler. Cache the favorite-key set, reuse `MutableTextureBinding` if possible, draw in batched form.
- **Save thrash.** Toggling many favorites quickly does many disk writes. For the volume expected (a handful of toggles per session), this is fine. If it becomes a problem, debounce the save to a 1-second timer fired off the client tick event.
- **Search tab interaction.** The search tab's input field captures keypresses. If our `KeyboardInputEvent.Pre` doesn't check for the search field having focus, pressing `B` while typing a search query would toggle a favorite. Mitigation: check `gui.searchField != null && gui.searchField.isFocused()` (search field is a private member — we'd need to either reflectively read it or accept a small risk and rely on the `Pre` event letting us bail by inspecting the event's target). Verify on first implementation.

---

## Testing checklist

Run with the full Alto modpack loaded so tab count and JEI are realistic.

### Smoke
- [ ] Build cleanly: `JAVA_HOME=... ./gradlew build`
- [ ] Start a creative single-player world. Open inventory. Favorites tab is present with the Nether Star icon and is empty.
- [ ] Hover an oak log. Press `B`. Switch to favorites tab. Oak log is there.
- [ ] Press `B` on the oak log in the favorites tab. It disappears.
- [ ] Add 5 different items. Quit world. Reload world. Open favorites tab. All 5 are still there in insertion order.
- [ ] Edit `<minecraft>/config/sum/favorites.json` directly to add a hand-written entry. Restart MC. The entry appears.
- [ ] Delete the file. Restart MC. Favorites tab is empty, no crash.

### Identity
- [ ] Favorite white wool. Red wool is *not* shown as favorited. (Meta is part of the key.)
- [ ] Favorite a tinkers tool. It appears with full durability. Works the same for vanilla diamond sword.
- [ ] Favorite a stack with custom NBT (e.g. enchanted book). The star appears on *all* enchanted books, not just that one. (Confirms NBT is excluded from the key — this is by design for v1.)

### Overlay
- [ ] Star renders on the slot in the favorites tab.
- [ ] Star renders on the slot in the source tab (e.g. "Building blocks") for the same item.
- [ ] Star renders on the slot in JEI's overlay if JEI is showing the item.
- [ ] Star renders correctly at HD resource pack scale (32×32 sprite scaled).
- [ ] Disabling `favorites.showStarOverlay` in config hides the stars.

### Keybinds
- [ ] Toggle key (`B`) only fires inside the creative inventory. Pressing `B` outside the GUI does nothing.
- [ ] Pressing `B` while focused on the JEI/vanilla search field types `b` into the field rather than toggling. (Critical UX bug if we get this wrong.)
- [ ] Jump key (`Ctrl+B`) snaps to favorites tab from any other tab.
- [ ] Rebinding both keys via vanilla controls menu and restarting respects the new bindings.

### Survival
- [ ] Switch to survival (`/gamemode survival`). Open inventory. The vanilla 2×2 crafting inventory shows; no creative tabs at all. Confirms favorites are creative-only by virtue of the GUI itself.
- [ ] Pressing `B` in survival inventory does nothing.

### Multi-world / cross-launch
- [ ] Add favorites in world A. Quit. Open world B. Favorites tab is the same.
- [ ] Quit MC. Restart launcher. Favorites tab is the same.

### Pack realism
- [ ] In the full Alto pack with 13+ pages of tabs, the favorites tab is reachable both by paginating arrows and by `Ctrl+B`.
- [ ] No crashes or render glitches when switching between favorites and the search tab.
- [ ] Adding ~50 favorites does not noticeably slow the GUI or drop FPS below baseline.

---

## Out of scope for v1 (and likely v2)

- **Survival favorites HUD.** Would require a completely custom GUI, plus a way to give items in survival (which the favorites system can't do — that's against survival rules). If desired, that's a different feature: a "quick-select hotbar swap" that swaps your hotbar with a saved set, no item creation involved. Distinct enough to be its own plan doc.
- **Multiple favorites lists / categories.** "Tools," "decorative blocks," "redstone parts" as separate sublists. Useful, but the creative tab system has no native sub-tab UI; building one is a custom-GUI project.
- **Server-side enforcement / sharing.** Favorites travel with the player's Minecraft installation, not with the player on a server. Server-side sync would need netcode, a server-side store, and a permission model; probably never worth it for a creative-only convenience feature.
- **Favorites for items only obtainable via `/give` with NBT.** Already covered by the "NBT excluded in v1" decision.
- **Backups / version history of the favorites file.** A single `.bak` on corrupted-load is enough. Anything more is a config file, not user data.

---

## Quick implementation sequence (for the next session)

If Alex green-lights MVP without changes:

1. Phase #1 — `FavoriteKey` + `FavoritesStore` with JSON I/O. ~150 LOC. One commit.
2. Phase #2 — `CreativeTabFavorites` + `SumClientProxy` wiring. ~80 LOC. One commit.
3. Phase #3 — toggle keybind via `KeyboardInputEvent.Pre`. ~60 LOC. One commit.
4. Phase #4 — star overlay via `DrawScreenEvent.Post`. ~80 LOC + 1 PNG. One commit.
5. Phase #5 — jump keybind. ~20 LOC. One commit.
6. **Pause for in-game testing with the full pack.** Resolve any conflicts surfaced.
7. Phases #6–#8 as desired.

Total MVP: roughly 400 LOC across 5 commits, plus one texture asset. Realistic effort: 4–6 hours of focused work, more if JEI compatibility has surprises.
