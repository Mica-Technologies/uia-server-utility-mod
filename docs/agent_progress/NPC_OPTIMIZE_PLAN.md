# Roamer NPC performance optimization

Status: thirteen commits land on `main`, all green on `gradlew build`. Alex tested mid-stream after the first three commits and confirmed roamers were faster and behaviorally identical; the remaining ten commits (the cooperative search, search-cost cache/constants, ModelPlayer overlay disable, and the texture-atlas + persona expansion) are unverified in-game.

---

## Resume prompt

Paste this verbatim to a future Claude Code session to pick up where we left off:

> I'm continuing the roamer NPC performance optimization pass for the SUM mod.
>
> Read `docs/agent_progress/NPC_OPTIMIZE_PLAN.md` first — it has the full plan, what's been done, the original review findings, and the testing checklist.
>
> Every item in the original review is now landed on `main` through the texture-atlas + persona expansion (FPS #2). Alex validated the first three commits in-game; the remaining ten compile and pass the test suite but are unverified in-game.
>
> Before doing more work: ask Alex what they observed in testing the latest commits, and whether they want to (a) tune any constants based on what they saw (see "Tunable constants summary" at the bottom), (b) author additional roamer personas (edit `tools/roamer_atlas/generate.py` and bump `RenderRoamer.VARIANT_COUNT`), or (c) call this performance pass complete and move on to a different feature.

---

## Status snapshot

| Phase | Goal | Status | Commit |
|---|---|---|---|
| #1 | Block-keyed walkable lookup (kill pathfinder hot-path allocations) | ✅ done, tested | `ad108ca` |
| #2 | Apply Block-keyed lookup to AI tasks | ✅ done, tested (folded into #1 commit) | `ad108ca` |
| #5 | O(1) HashSet claim-check | ✅ done, tested | `b65fa2f` |
| #3 | AI gate (skip ticks when no player within 64 blocks) | ✅ done, tested | `d435020` |
| #4-H | Wider initial alarm-check stagger | ✅ done, unverified | `256679d` |
| #4-C | Persist exit cache for world lifetime | ✅ done, unverified | `4dd79f1` |
| #4-F | MutableBlockPos in cube walks | ✅ done, unverified | `718ae04` |
| #4-E | canSeeSky cache during exit search | ✅ done, unverified | `757947e` |
| #4-G | Tighten emergency-search constants | ✅ done, unverified | `1000c61` |
| #4-D | Storm shelter cache | ✅ done, unverified | `9426f56` |
| #4-A+B fire | Cooperative cross-tick fire search | ✅ done, unverified | `7797af2` |
| #4-A+B storm | Cooperative cross-tick storm search | ✅ done, unverified | `4d6791d` |
| FPS #1 | Disable ModelPlayer overlay layers | ✅ done, unverified | `80ebbd3` |
| FPS #2 | Texture atlas + 16 persona variants | ✅ done, unverified | (this commit) |

---

## Phase detail (in commit order)

### `ad108ca` — Switch roamer walkable-block lookup to Block reference

- **Files**: `SumConfig.java`, `RoamerWalkableBlocksPathNodeProcessor.java`, `EntityAIRoamerWander.java`, `EntityAIRoamerFireEvacuate.java`, `ItemRoamerSpawnEgg.java`
- **What**: Added `SumConfig.isBlockWalkableByRoamer(Block)` overload backed by a lazily-resolved `Set<Block>`. Pathfinder hot path (`RoamerWalkableBlocksPathNodeProcessor.getPathNodeType`, called hundreds-thousands of times per path search) now uses identity lookup with a reusable `MutableBlockPos` instead of allocating a fresh `BlockPos` and `String` per call.
- **Resolution timing**: lazy because the Forge block registry isn't populated until after `preInit`; the cache invalidates on `reloadConfig`/`add`/`remove`.
- **Why this is the biggest win**: pathfinder is by far the hottest loop. String allocation per node × hundreds of nodes × multiple roamers was the dominant TPS cost.

### `b65fa2f` — Replace O(N) claim-distance loop with HashSet contains

- **Files**: `EntityAIRoamerFireEvacuate.java`, `EntityAIRoamerStormShelter.java`
- **What**: `isPositionClaimed` walked every claimed `BlockPos` checking `distanceSq < MIN_CLAIM_DISTANCE_SQ = 1.0`. Since `BlockPos` coords are integers, that condition is exact equality, so `claimedPositions.contains(candidate)` is equivalent and O(1). Constant removed.

### `d435020` — Skip roamer AI ticks when no player is within 64 blocks

- **Files**: `EntityRoamer.java`
- **What**: Override `updateAITasks()`. Re-checks `world.getClosestPlayer(...)` once per second (timer staggered per-instance). When no player is in range, clears any in-progress path and returns early — full AI dispatch (pathfinding, task evaluation, look/move helpers) is skipped. AI resumes the moment a player re-enters range, including emergency response.
- **Tuning knob**: `IDLE_RANGE = 64` blocks. The entity tracker is 80 blocks (so the gap is 16 blocks of "tracked but AI-paused" — fine in practice; players don't engage with NPCs at that range).

### `256679d` — Stagger first emergency check across roamers

- **Files**: `EntityAIRoamerFireEvacuate.java`, `EntityAIRoamerStormShelter.java`
- **What**: Constructor-time `checkTimer` uses `nextInt(INITIAL_STAGGER_TICKS=60)` instead of `nextInt(CHECK_INTERVAL_TICKS=5)`. A chunkful of roamers loaded simultaneously now does first alarm probes spread over 3 seconds rather than 5 ticks. Steady-state cadence between checks is unchanged.

### `4dd79f1` — Persist roamer exit cache for the lifetime of the world

- **Files**: `RoamerExitCache.java`, `EntityAIRoamerFireEvacuate.java`
- **What**: Removed the 5-minute expiry. Building geometry doesn't decay, so an exit found in alarm wave 1 should still work in wave 2. A cached entry that fails pathfinding (e.g. demolished doorway) gracefully falls through to a fresh search. Lookup radius bumped from 50 to 100 blocks for larger compounds. `recordExit`/`findExitsByPriority` no longer take a `worldTime` arg.

### `718ae04` — Reuse MutableBlockPos in fire and storm cube walks

- **Files**: `EntityAIRoamerFireEvacuate.java`, `EntityAIRoamerStormShelter.java`
- **What**: The candidate-finder loops were allocating fresh `BlockPos` (plus `.up()`/`.down()` allocations) for every cell visited — ~57k allocations per fire search and ~50k per storm search, multiplied by N roamers. Three reused `MutableBlockPos` instances per call; only allocate `toImmutable()` for kept candidates. `BlockPos.equals/hashCode` are coord-based, so `HashSet.contains(mutable)` is correct against stored immutables.

### `757947e` — Cache canSeeSky lookups during exit-candidate evaluation

- **Files**: `EntityAIRoamerFireEvacuate.java`
- **What**: `hasEnoughOpenSky` does up to (radius*2+1)² sky-visibility checks per candidate. Adjacent candidates share most of their footprint, so a `HashMap<Long, Boolean>` keyed by `BlockPos.toLong()` short-circuits redundant `canSeeSky` calls. The cache is scoped to one `findExitCandidates` call — for fire only (storm doesn't use `hasEnoughOpenSky`).

### `1000c61` — Tighten emergency-search constants

- **Files**: `EntityAIRoamerFireEvacuate.java`, `EntityAIRoamerStormShelter.java`
- **What**: Three knobs adjusted with goal of bounding worst-case search cost without changing where roamers end up:
  - Fire `OPEN_AREA_CHECK_RADIUS` 5 → 3 (and `MIN_OPEN_SKY_BLOCKS` 60 → 25). 121 → 49 canSeeSky calls per candidate.
  - Storm `HAZARD_CHECK_DISTANCE` 3 → 2. 196 → 100 block-state lookups per kept candidate.
  - `MAX_CANDIDATES`: fire 5 → 3, storm 8 → 4.
- **Watch**: if roamers seem to evacuate to oddly-tight or door-adjacent spots, these are the knobs to revisit.

### `9426f56` — Cache confirmed shelter positions across storm waves

- **Files**: new `RoamerShelterCache.java`, `EntityAIRoamerStormShelter.java`
- **What**: Mirror of `RoamerExitCache` for storm. Roamers record their position when `isShelterPosition` succeeds (both at task entry and on the seek→sheltered transition). Subsequent shelter searches try cached positions first, filtered for current claimedness/hazard/reachability.
- **When it helps**: most within a single storm wave the cached positions are claimed by the roamers using them, so the lookup falls through. Big win on the *second* wave when claims have been released — full O(R³) cube scan is skipped entirely.

### `7797af2` — Spread fire-exit search across ticks via a coroutine-style cube walk

- **Files**: `EntityAIRoamerFireEvacuate.java`
- **What**: This is the structural fix for the per-tick spike when many roamers react to one alarm. Replaces the synchronous `findExitCandidates` O(R³) loop with a `CubeSearchState` iterator that processes `CELLS_PER_TICK = 1000` cells per call.
- **Lifecycle**: `shouldExecute` returns true with `searching=true, exitTarget=null` to claim the AI mutex. `updateTask` drives `stepFireExitSearch` each tick. When the cube exhausts or `MAX_CANDIDATES` is collected, paths are tried in distance order and the first reachable one transitions the task to its moving phase. If alarm stops mid-search or nothing's reachable, `searchFailed` flips and `shouldContinueExecuting` ends the task.
- **Worst-case timing**: ~SEARCH_RADIUS_XZ² × (2*SEARCH_RADIUS_Y+1) / CELLS_PER_TICK ≈ 19 ticks (~1 second) for the full cube. Roamer stands still during the search — acceptable for fire response.

### `4d6791d` — Spread storm-shelter search across ticks via the same coroutine pattern

- **Files**: `EntityAIRoamerStormShelter.java`
- **What**: Storm equivalent of the previous commit. `StormCubeSearchState` walks `dy → r → ringPerimeter`. Path attempts run in score order at the end (preserving the existing scoring + `GOOD_ENOUGH_SCORE` early-exit semantics). Cached-shelter lookup remains synchronous and runs *before* the cube fallback — a cache hit avoids the cooperative path entirely.

### `80ebbd3` — Disable ModelPlayer overlay layers on the roamer renderer

- **Files**: `RenderRoamer.java`
- **What**: `ModelPlayer` renders five "wear" parts (jacket, both sleeves, both pant legs) plus the hat overlay on top of the base biped, doubling the cube count per draw call. Roamer textures don't use the overlay UV regions, so all six layers are pure cost. Helper `buildModel()` flips `showModel = false` on each one. Roughly halves per-roamer render work with no visual change.
- **Watch**: if a custom roamer skin *does* use a hat or jacket UV region, those will now be invisible. Easy to flip back per-layer if needed.

### FPS #2 — Texture atlas + persona variants

- **Files**: new `ModelRoamer.java`, rewritten `RenderRoamer.java`, new generated `assets/sum/textures/entity/roamer_atlas.png`, new `tools/roamer_atlas/generate.py`, the six base templates moved from assets to `tools/roamer_atlas/templates/`.
- **What**: All roamers now sample a single shared atlas (64 wide × `VARIANT_COUNT * 64` tall, vertically stacked). At construction the renderer builds one `ModelRoamer` per variant; each variant rebuilds the rendered biped parts after `super(0.0F, false)` with V offsets shifted by `variantIndex * 64` and the model's logical `textureHeight` raised to the full atlas height so UV normalization stays correct. `doRender` swaps `mainModel` based on `entity.getUniqueID().getLeastSignificantBits() % VARIANT_COUNT`, then delegates to super. `getEntityTexture` returns the single atlas.
- **Why it's cheap**: with one texture for all variants, MC 1.12.2's `TextureManager.bindTexture` short-circuits redundant binds via its `lastBoundTexture` field — a chunkful of roamers now causes one real `glBindTexture` per render pass instead of up to 6.
- **Persona expansion**: `VARIANT_COUNT = 16` (up from 6). The first six entries in `tools/roamer_atlas/generate.py`'s `PERSONAS` list reproduce the original templates unchanged; the next ten are generated by exact-color recoloring of shirt/pants/hair regions to fit a "city" theme (office, casual, construction hi-vis, nightlife, tourist, park ranger, academic, elder, tech, artist). UUID hashing for previously-spawned roamers is unchanged for indices 0–5, so existing entities keep their original look.
- **Adding more variants**: edit `PERSONAS` in `generate.py`, re-run the script, and bump `RenderRoamer.VARIANT_COUNT` to match. The atlas height implies the count, so a mismatch will sample the wrong V slice.
- **Watch**: variant-to-variant UV shifts are integer pixel-aligned (multiples of 64) so there's no risk of bleed between slices, but if a future template happens to share the source-color of *another* template's region, the recolor swap won't isolate it cleanly. Keep templates using flat solid fills per region (no gradients/shading on shirts/pants/hair) so the exact-match recolor stays accurate.

---

## What's left

Nothing in the original review. The texture atlas (FPS #2) was the last deferred item and is now done. See the FPS #2 phase entry below.

---

## Original full review (for context)

This is the analysis that drove the plan. Useful if a future change needs to revisit any of the lower-priority items.

### Server TPS hot spots (original ranking)

1. 🔴 `RoamerWalkableBlocksPathNodeProcessor.getPathNodeType` — fixed by `ad108ca`
2. 🔴 Emergency cube-scan candidate search — fixed by `718ae04`, `757947e`, `1000c61`, `7797af2`, `4d6791d`
3. 🟠 `getRegistryName().toString()` allocations — fixed by `ad108ca`
4. 🟡 Vanilla `EntityCreature` ticking overhead — partially fixed by `d435020` (AI gate); no further action planned
5. 🟡 `isPositionClaimed` O(N) loop — fixed by `b65fa2f`
6. 🟢 `RoamerExitCache.recordExit` linear scan — left as-is (small N in practice, fine)

### Client FPS hot spots (original ranking)

1. 🔴 Per-roamer texture switching breaks render batching — partially mitigated by `80ebbd3` (cuts model cost ~50%, makes binds proportionally smaller); atlas deferred
2. 🟠 Heavy `ModelPlayer` model — fixed by `80ebbd3`
3. 🟠 `setAlwaysRenderNameTag(true)` — left as-is (deliberate UX choice)
4. 🟢 Tracker range 80/3 — left as-is (matches vanilla animal tracker)

---

## Testing checklist

When verifying the unverified commits in-game, walk through these scenarios. The build/dev commands are in `CLAUDE.md` (set `JAVA_HOME` and use `./gradlew runClient` / `runServer`).

### Smoke tests
- [ ] Build cleanly: `JAVA_HOME=... ./gradlew build`
- [ ] Spawn one roamer with the spawn egg on a walkable block. It wanders normally.
- [ ] Walk away ≥64 blocks. Roamer stops moving. Walk back. It resumes.
- [ ] Hit it with a name tag. Custom name appears, greetings fire when you approach.

### Fire evacuation (CSM required)
- [ ] Spawn 5 roamers inside a building with a fire alarm.
- [ ] Trigger the alarm. Roamers begin pathing toward the exit within ~1 second.
- [ ] No visible TPS hitch on the tick the alarm starts (this is the core regression to watch for).
- [ ] All roamers reach a rally point outside; none stand on top of each other (claim-position spread works).
- [ ] Stop and re-trigger the alarm. Second wave should be near-instant — exit cache hits.
- [ ] Demolish the doorway between waves. Roamers fall through to a fresh search and find a different exit.

### Storm shelter (CSM required)
- [ ] Spawn 5 roamers outside near a building with a storm alarm.
- [ ] Trigger the alarm. Roamers seek shelter inside, away from windows/doors.
- [ ] No visible TPS hitch on alarm start.
- [ ] Stop and re-trigger. Second wave should be much faster — shelter cache hits.

### Render
- [ ] Visually compare a roamer pre/post `80ebbd3`. The base body should look identical; no hat or jacket overlay.
- [ ] If a custom skin's hat region was being used, this will be the regression — easy revert.

---

## Tunable constants summary

If something needs tuning based on observed behavior, these are the dials:

### `EntityRoamer.java`
- `IDLE_RANGE = 64` — how close a player must be for AI to run
- `IDLE_CHECK_INTERVAL = 20` — how often the proximity check runs (ticks)

### `EntityAIRoamerFireEvacuate.java`
- `CHECK_INTERVAL_TICKS = 5` — between alarm-active probes when not yet evacuating
- `INITIAL_STAGGER_TICKS = 60` — first-check randomization window
- `SEARCH_RADIUS_XZ = 30`, `SEARCH_RADIUS_Y = 10` — cube extents
- `OPEN_AREA_CHECK_RADIUS = 3`, `MIN_OPEN_SKY_BLOCKS = 25` — what counts as "in a clearing"
- `MAX_CANDIDATES = 3` — exit candidates collected before path attempts
- `CELLS_PER_TICK = 1000` — cooperative search budget (lower = smoother spread, slower response)
- `REPATH_INTERVAL_TICKS = 60`, `CONTINUE_CHECK_INTERVAL = 20` — repath/continue cadence

### `EntityAIRoamerStormShelter.java`
- `CHECK_INTERVAL_TICKS = 5`, `INITIAL_STAGGER_TICKS = 60` — same pattern as fire
- `SEARCH_RADIUS_XZ = 25`, `SEARCH_RADIUS_Y_DOWN = 20`, `SEARCH_RADIUS_Y_UP = 5` — cube extents (asymmetric in Y because basements)
- `HAZARD_CHECK_DISTANCE = 2` — how close a window/door/sky-opening counts as a hazard
- `MAX_CANDIDATES = 4`, `GOOD_ENOUGH_SCORE = 100` — early-exit when a clearly-good candidate is found
- `CELLS_PER_TICK = 1000` — cooperative search budget

### `RoamerExitCache.java` / `RoamerShelterCache.java`
- `MAX_DISTANCE_SQ = 100*100` — how far away a cached entry can be and still be considered

### `RenderRoamer.java`
- `buildModel()` — flip `showModel = true` on a specific layer if a custom skin needs it back
