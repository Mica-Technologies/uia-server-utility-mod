package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockGlass;
import net.minecraft.block.BlockPane;
import net.minecraft.block.BlockStainedGlass;
import net.minecraft.block.BlockStainedGlassPane;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * AI task that makes the roamer seek shelter during a CSM storm/tornado alarm. Has two phases:
 * <ol>
 *   <li><b>Seek phase</b> — emergency mode ON, navigates to a safe interior position away from
 *       doors, windows (glass/pane blocks), and sky-exposed openings.</li>
 *   <li><b>Shelter-in-place phase</b> — once sheltered, the task stays active and the roamer
 *       wanders slowly within the interior. This prevents the normal wander AI from taking over
 *       and walking the roamer back outside.</li>
 * </ol>
 * Roamers claim unique shelter positions so they spread out instead of all standing on one block.
 * <p>
 * This task only activates when CSM is loaded and a storm alarm is sounding within hearing range.
 * <p>
 * Priority note: shares priority 1 / mutex bit 1 with {@link EntityAIRoamerFireEvacuate}.
 * Fire evacuate is registered first, so fire takes precedence over storm when both are active.
 */
public class EntityAIRoamerStormShelter extends EntityAIBase {

    // Note: the AI task system only calls shouldExecute() every 3 ticks, so the effective
    // real-time interval is CHECK_INTERVAL_TICKS * 3. Keep this low for emergencies.
    private static final int CHECK_INTERVAL_TICKS = 5; // ~0.75 seconds effective
    // First-check stagger applied at construction so a freshly-loaded chunk full of roamers
    // doesn't run their initial alarm probe on the same tick. Steady-state cadence is unaffected.
    private static final int INITIAL_STAGGER_TICKS = 60;
    private static final int SEARCH_RADIUS_XZ = 25;
    private static final int SEARCH_RADIUS_Y_DOWN = 20;
    private static final int SEARCH_RADIUS_Y_UP = 5;
    // Lowered from 3 to 2: a window or door 3 blocks past a wall is on the other side of the
    // building and not really a hazard for the position being evaluated. Cuts the hazard scan
    // from 7*4*7=196 to 5*4*5=100 block-state lookups per kept candidate.
    private static final int HAZARD_CHECK_DISTANCE = 2;
    private static final int REPATH_INTERVAL_TICKS = 60;
    private static final int CONTINUE_CHECK_INTERVAL = 20;
    // Lowered: scoring + early-exit picks a viable shelter from the first few candidates in
    // practice, and capping further bounds worst-case cost per search.
    private static final int MAX_CANDIDATES = 4;
    private static final int GOOD_ENOUGH_SCORE = 100;
    // Cooperative-search budget: cells processed per tick. Worst-case search visits
    // SEARCH_RADIUS_XZ^2 * (SEARCH_RADIUS_Y_DOWN + SEARCH_RADIUS_Y_UP + 1) cells, spread over
    // ceil(total / CELLS_PER_TICK) ticks instead of one tick spike.
    private static final int CELLS_PER_TICK = 1000;

    // Shelter-in-place indoor wander settings
    private static final int INDOOR_WANDER_RANGE = 5;
    private static final int INDOOR_WANDER_INTERVAL_MIN = 60;  // 3 seconds
    private static final int INDOOR_WANDER_INTERVAL_MAX = 200; // 10 seconds

    // Shared set of claimed shelter positions — prevents all roamers from picking the same spot
    private static final Set<BlockPos> claimedPositions = new HashSet<>();

    private final EntityRoamer roamer;
    private final double speed;
    private final boolean csmLoaded;

    private BlockPos shelterTarget;
    private int checkTimer;
    private int repathTimer;
    private int continueCheckTimer;
    private boolean sheltered; // true once the roamer reaches a safe interior position
    private int indoorWanderTimer;
    // Cooperative-search state. Non-null while the cube scan is running across ticks.
    private StormCubeSearchState search;
    private boolean searching;       // mirror flag — true while the search is still progressing
    private boolean searchFailed;    // set when the cube exhausts with no reachable shelter

    public EntityAIRoamerStormShelter(EntityRoamer roamer, double speed) {
        this.roamer = roamer;
        this.speed = speed;
        this.csmLoaded = Loader.isModLoaded("csm");
        this.checkTimer = roamer.getRNG().nextInt(INITIAL_STAGGER_TICKS);
        this.setMutexBits(1);
    }

    @Override
    public boolean shouldExecute() {
        if (!csmLoaded) {
            return false;
        }

        if (checkTimer > 0) {
            checkTimer--;
            return false;
        }
        checkTimer = CHECK_INTERVAL_TICKS;

        World world = roamer.world;
        BlockPos entityPos = roamer.getPosition();

        if (!CsmIntegration.isStormAlarmActiveNear(world, entityPos)) {
            return false;
        }

        // Even if already sheltered, take over from wander AI to keep the roamer indoors
        if (isShelterPosition(world, entityPos)) {
            sheltered = true;
            shelterTarget = entityPos;
            RoamerShelterCache.recordShelter(entityPos);
            searching = false;
            searchFailed = false;
            return true;
        }

        roamer.setEmergencyMode(true);

        // Try cached shelters first (cheap, single-shot). Across storm waves the building's
        // shelter zone is the same, and once claims have been released the cached positions
        // are usually re-usable directly.
        for (BlockPos cached : RoamerShelterCache.findNearest(entityPos)) {
            if (isPositionClaimed(cached)) {
                continue;
            }
            if (!isShelterPosition(world, cached)) {
                continue;
            }
            if (roamer.getNavigator().getPathToXYZ(
                    cached.getX() + 0.5, cached.getY(), cached.getZ() + 0.5) != null) {
                shelterTarget = cached;
                claimPosition(shelterTarget);
                searching = false;
                searchFailed = false;
                return true;
            }
        }

        // No cached shelter worked — kick off a cooperative cube scan; updateTask drives it.
        shelterTarget = null;
        searching = true;
        searchFailed = false;
        search = new StormCubeSearchState();
        return true;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (!csmLoaded) {
            return false;
        }

        if (searchFailed) {
            return false;
        }
        if (searching) {
            return true; // cooperative search still running — keep the slot
        }

        // Throttle expensive checks
        if (continueCheckTimer > 0) {
            continueCheckTimer--;
            // During seek phase, stop if path exhausted; during shelter phase, always continue
            return sheltered || !roamer.getNavigator().noPath();
        }
        continueCheckTimer = CONTINUE_CHECK_INTERVAL;

        // Storm ended — release shelter
        if (!CsmIntegration.isStormAlarmActiveNear(roamer.world, roamer.getPosition())) {
            return false;
        }

        // Check if we've arrived at shelter
        if (!sheltered && isShelterPosition(roamer.world, roamer.getPosition())) {
            sheltered = true;
            roamer.setEmergencyMode(false);
            RoamerShelterCache.recordShelter(roamer.getPosition());
            indoorWanderTimer = INDOOR_WANDER_INTERVAL_MIN
                + roamer.getRNG().nextInt(INDOOR_WANDER_INTERVAL_MAX - INDOOR_WANDER_INTERVAL_MIN);
        }

        // During seek phase, stop if path exhausted (will retry on next shouldExecute)
        if (!sheltered) {
            return !roamer.getNavigator().noPath();
        }

        // Shelter phase — always continue (keeps wander AI from taking over)
        return true;
    }

    @Override
    public void startExecuting() {
        repathTimer = 0;
        continueCheckTimer = CONTINUE_CHECK_INTERVAL;
        indoorWanderTimer = 0;

        if (sheltered) {
            // Already sheltered — go straight to shelter-in-place phase
            roamer.setEmergencyMode(false);
            indoorWanderTimer = INDOOR_WANDER_INTERVAL_MIN
                + roamer.getRNG().nextInt(INDOOR_WANDER_INTERVAL_MAX - INDOOR_WANDER_INTERVAL_MIN);
        } else if (searching) {
            // No path target yet — cube scan runs across ticks in updateTask.
        } else {
            roamer.getNavigator().tryMoveToXYZ(shelterTarget.getX() + 0.5, shelterTarget.getY(),
                shelterTarget.getZ() + 0.5, speed);
        }
    }

    @Override
    public void updateTask() {
        if (searching) {
            stepStormShelterSearch();
            return;
        }

        if (sheltered) {
            // Shelter-in-place: wander slowly within the interior
            indoorWanderTimer--;
            if (indoorWanderTimer <= 0) {
                indoorWanderTimer = INDOOR_WANDER_INTERVAL_MIN
                    + roamer.getRNG().nextInt(INDOOR_WANDER_INTERVAL_MAX - INDOOR_WANDER_INTERVAL_MIN);
                tryIndoorWander();
            }
            return;
        }

        // Seek phase: repath periodically
        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL_TICKS) {
            repathTimer = 0;
            if (shelterTarget != null) {
                roamer.getNavigator().tryMoveToXYZ(shelterTarget.getX() + 0.5, shelterTarget.getY(),
                    shelterTarget.getZ() + 0.5, speed);
            }
        }
    }

    @Override
    public void resetTask() {
        if (shelterTarget != null) {
            releasePosition(shelterTarget);
        }
        shelterTarget = null;
        sheltered = false;
        searching = false;
        searchFailed = false;
        search = null;
        checkTimer = CHECK_INTERVAL_TICKS;
        roamer.setEmergencyMode(false);
    }

    /**
     * Picks a random nearby indoor position and walks there slowly, keeping the roamer
     * moving naturally inside the building without leaving.
     */
    private void tryIndoorWander() {
        Vec3d target = RandomPositionGenerator.findRandomTarget(roamer, INDOOR_WANDER_RANGE, 3);
        if (target == null) {
            return;
        }
        BlockPos targetPos = new BlockPos(target);
        // Only wander to positions that are indoors and away from hazards
        if (!roamer.world.canSeeSky(targetPos.up()) && !isNearHazard(roamer.world, targetPos)) {
            roamer.getNavigator().tryMoveToXYZ(target.x, target.y, target.z, 0.6D);
        }
    }

    // --- Shelter position evaluation ---

    /**
     * A position is a good storm shelter if it cannot see the sky and is away from doors,
     * windows (glass/pane blocks), and sky-exposed openings.
     */
    private boolean isShelterPosition(World world, BlockPos pos) {
        if (world.canSeeSky(pos.up())) {
            return false;
        }
        return !isNearHazard(world, pos);
    }

    /**
     * Checks whether the given position is within {@link #HAZARD_CHECK_DISTANCE} blocks of a
     * storm hazard: sky-exposed openings, glass blocks/panes (windows), or doors.
     */
    private boolean isNearHazard(World world, BlockPos pos) {
        int r = HAZARD_CHECK_DISTANCE;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    BlockPos check = pos.add(dx, dy, dz);

                    // Check for sky-exposed openings (doorways, holes)
                    if (world.isAirBlock(check) && world.canSeeSky(check)) {
                        return true;
                    }

                    // Check for window blocks (glass, stained glass, panes)
                    IBlockState state = world.getBlockState(check);
                    Block block = state.getBlock();
                    if (block instanceof BlockGlass || block instanceof BlockStainedGlass
                        || block instanceof BlockPane || block instanceof BlockStainedGlassPane) {
                        return true;
                    }

                    // Check for doors
                    if (block instanceof BlockDoor) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // --- Shelter search ---

    /**
     * Drives one tick of the cooperative cube scan. Processes up to {@link #CELLS_PER_TICK}
     * cells per call; when the cube exhausts (or a candidate scoring {@link #GOOD_ENOUGH_SCORE}
     * or higher is found) the collected candidates are tested in score order via
     * {@code getPathToXYZ}, and the first reachable one transitions the task to its moving
     * phase. If none are reachable {@code searchFailed} ends the task on the next tick.
     */
    private void stepStormShelterSearch() {
        World world = roamer.world;
        BlockPos entityPos = roamer.getPosition();

        // Bail if the storm stopped before the search completed.
        if (!CsmIntegration.isStormAlarmActiveNear(world, entityPos)) {
            searching = false;
            searchFailed = true;
            roamer.setEmergencyMode(false);
            return;
        }

        // Stage 1: cube walk, capped by cube exhaustion or a good-enough candidate.
        if (!search.cubeExhausted && !search.foundGoodEnough) {
            int processed = 0;
            while (processed < CELLS_PER_TICK
                && !search.cubeExhausted && !search.foundGoodEnough) {
                if (!search.advance()) {
                    search.cubeExhausted = true;
                    break;
                }

                int cx = entityPos.getX() + search.dx;
                int cy = entityPos.getY() + search.dy;
                int cz = entityPos.getZ() + search.dz;
                search.cur.setPos(cx, cy, cz);
                processed++;

                if (!world.isBlockLoaded(search.cur)) continue;

                search.above.setPos(cx, cy + 1, cz);
                if (!world.isAirBlock(search.cur) || !world.isAirBlock(search.above)) continue;

                search.below.setPos(cx, cy - 1, cz);
                if (!world.getBlockState(search.below).getMaterial().isSolid()) continue;

                if (world.canSeeSky(search.above)) continue;

                if (isPositionClaimed(search.cur)) continue;

                int yScore = (entityPos.getY() - cy) * 10;
                int distScore = -(Math.abs(search.dx) + Math.abs(search.dz));
                int score = yScore + distScore;

                if (search.candidates.size() >= MAX_CANDIDATES
                        && score <= search.worstKeptScore) {
                    continue;
                }

                if (isNearHazard(world, search.cur)) continue;

                search.candidates.add(new ScoredPos(search.cur.toImmutable(), score));

                if (score >= GOOD_ENOUGH_SCORE) {
                    search.foundGoodEnough = true;
                    break;
                }

                if (search.candidates.size() > MAX_CANDIDATES) {
                    search.candidates.sort(Comparator.comparingInt(s -> -s.score));
                    search.candidates.subList(MAX_CANDIDATES, search.candidates.size()).clear();
                    search.worstKeptScore = search.candidates.get(
                        search.candidates.size() - 1).score;
                }
            }
        }

        // Yield until next tick if more cube to scan.
        if (!search.cubeExhausted && !search.foundGoodEnough) {
            return;
        }

        // Stage 2: try paths in score order, take the first reachable.
        search.candidates.sort(Comparator.comparingInt(s -> -s.score));
        for (ScoredPos sp : search.candidates) {
            if (roamer.getNavigator().getPathToXYZ(
                    sp.pos.getX() + 0.5, sp.pos.getY(), sp.pos.getZ() + 0.5) == null) {
                continue;
            }
            shelterTarget = sp.pos;
            claimPosition(shelterTarget);
            searching = false;
            search = null;
            roamer.getNavigator().tryMoveToXYZ(sp.pos.getX() + 0.5, sp.pos.getY(),
                sp.pos.getZ() + 0.5, speed);
            repathTimer = 0;
            return;
        }

        // Nothing reachable — let shouldContinueExecuting end the task next tick.
        searching = false;
        searchFailed = true;
        search = null;
        roamer.setEmergencyMode(false);
    }

    /**
     * Iterator state for a cooperative ringed cube walk. Outer loop is dy (Y plane), middle is
     * the ring radius, inner two are dx/dz on the ring perimeter. Mirrors the nested-loop order
     * of the previous synchronous {@code findShelterCandidates} but with loop variables
     * externalised so the search can yield mid-walk and resume on the next tick.
     */
    private static class StormCubeSearchState {
        int dy = -SEARCH_RADIUS_Y_DOWN;
        int r = 0;
        int dx = 0;
        int dz = 0;
        boolean firstAdvance = true;
        boolean cubeExhausted = false;
        boolean foundGoodEnough = false;
        int worstKeptScore = Integer.MIN_VALUE;
        final List<ScoredPos> candidates = new ArrayList<>();
        final BlockPos.MutableBlockPos cur = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos above = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();

        boolean advance() {
            if (firstAdvance) {
                firstAdvance = false;
                return true; // initial state at (dy=-Y_DOWN, r=0, dx=0, dz=0) is valid
            }
            do {
                dz++;
                if (dz > r) {
                    dz = -r;
                    dx++;
                    if (dx > r) {
                        r++;
                        if (r > SEARCH_RADIUS_XZ) {
                            // Move to next dy plane
                            r = 0;
                            dx = 0;
                            dz = 0;
                            dy++;
                            if (dy > SEARCH_RADIUS_Y_UP) {
                                return false;
                            }
                            return true;
                        }
                        dx = -r;
                        dz = -r;
                    }
                }
                // Skip interior cells of the ring (only walk the perimeter)
            } while (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r);
            return true;
        }
    }

    // --- Position claiming ---

    private static void claimPosition(BlockPos pos) {
        claimedPositions.add(pos);
    }

    private static void releasePosition(BlockPos pos) {
        claimedPositions.remove(pos);
    }

    private static boolean isPositionClaimed(BlockPos candidate) {
        return claimedPositions.contains(candidate);
    }

    /**
     * Clears all claimed shelter positions. Should be called on world unload.
     */
    public static void clearClaims() {
        claimedPositions.clear();
    }

    private static class ScoredPos {
        final BlockPos pos;
        final int score;

        ScoredPos(BlockPos pos, int score) {
            this.pos = pos;
            this.score = score;
        }
    }
}
