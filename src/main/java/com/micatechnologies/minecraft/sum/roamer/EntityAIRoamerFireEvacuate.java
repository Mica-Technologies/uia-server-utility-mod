package com.micatechnologies.minecraft.sum.roamer;

import com.micatechnologies.minecraft.sum.SumConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.ai.RandomPositionGenerator;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;

/**
 * AI task that makes the roamer evacuate to a safe rally point outside when a CSM fire alarm is
 * active nearby. Evacuation has three phases:
 * <ol>
 *   <li><b>Indoor phase</b> — emergency mode ON, bypassing walkable-block pathfinding
 *       restrictions so the roamer can navigate through any solid-floor area to reach an exit.</li>
 *   <li><b>Outdoor transit phase</b> — once the roamer steps outside, emergency mode is turned
 *       OFF and the roamer continues to the rally point using only walkable blocks.</li>
 *   <li><b>Rally phase</b> — once at the rally point, the task stays active and the roamer
 *       wanders outdoors on walkable blocks. This prevents the normal wander AI from taking
 *       over and walking the roamer back into the building.</li>
 * </ol>
 * Roamers claim unique rally positions so they spread out instead of all standing on one block.
 * <p>
 * This task only activates when CSM is loaded and a fire alarm (not storm) is sounding within
 * hearing range.
 * <p>
 * Priority note: this task and {@link EntityAIRoamerStormShelter} share mutex bit 1 at priority 1.
 * Fire evacuate is registered first, so if both fire and storm alarms are active simultaneously,
 * fire evacuation takes precedence (get out of a burning building even during a storm).
 */
public class EntityAIRoamerFireEvacuate extends EntityAIBase {

    // Note: the AI task system only calls shouldExecute() every 3 ticks, so the effective
    // real-time interval is CHECK_INTERVAL_TICKS * 3. Keep this low for emergencies.
    private static final int CHECK_INTERVAL_TICKS = 5; // ~0.75 seconds effective
    // First-check stagger applied at construction so a freshly-loaded chunk full of roamers
    // doesn't run their initial alarm probe on the same tick. Steady-state cadence is unaffected.
    private static final int INITIAL_STAGGER_TICKS = 60;
    private static final int SEARCH_RADIUS_XZ = 30;
    private static final int SEARCH_RADIUS_Y = 10;
    private static final int MIN_OPEN_SKY_BLOCKS = 60;
    private static final int OPEN_AREA_CHECK_RADIUS = 5;
    private static final int REPATH_INTERVAL_TICKS = 60;
    private static final int CONTINUE_CHECK_INTERVAL = 20;
    private static final int MAX_CANDIDATES = 5;

    // Rally-phase outdoor wander settings
    private static final int OUTDOOR_WANDER_RANGE = 6;
    private static final int OUTDOOR_WANDER_INTERVAL_MIN = 60;  // 3 seconds
    private static final int OUTDOOR_WANDER_INTERVAL_MAX = 200; // 10 seconds

    // Shared set of claimed rally positions — prevents two roamers from picking the same block
    private static final Set<BlockPos> claimedPositions = new HashSet<>();

    private final EntityRoamer roamer;
    private final double speed;
    private final boolean csmLoaded;

    private BlockPos exitTarget;     // the position the roamer is currently pathing to
    private BlockPos rallyPoint;     // the final outdoor rally position (well clear of building)
    private int checkTimer;
    private int repathTimer;
    private int continueCheckTimer;
    private boolean reachedOutdoors;
    private boolean atRallyPoint;
    private int outdoorWanderTimer;

    public EntityAIRoamerFireEvacuate(EntityRoamer roamer, double speed) {
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

        if (!CsmIntegration.isFireAlarmActiveNear(world, entityPos)) {
            return false;
        }

        // Already at a safe rally point — take over to stay in rally phase
        if (isRallyPoint(world, entityPos)) {
            atRallyPoint = true;
            reachedOutdoors = true;
            exitTarget = entityPos;
            return true;
        }

        // Enable emergency mode for the indoor pathfinding phase
        roamer.setEmergencyMode(true);

        // Check the shared exit cache first — a nearby roamer may have already found the way out.
        // Exits are sorted: same-level first (no stairs needed), then other levels by distance.
        List<BlockPos> cachedExits = RoamerExitCache.findExitsByPriority(entityPos,
            world.getTotalWorldTime());
        for (BlockPos cachedExit : cachedExits) {
            if (roamer.getNavigator().getPathToXYZ(
                    cachedExit.getX() + 0.5, cachedExit.getY(), cachedExit.getZ() + 0.5) != null) {
                exitTarget = cachedExit;
                return true;
            }
        }

        // No cached exit worked — run the full search
        exitTarget = findReachableExit(world, entityPos);
        if (exitTarget == null) {
            roamer.setEmergencyMode(false);
        }
        return exitTarget != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (!csmLoaded) {
            return false;
        }

        // Throttle the expensive checks
        if (continueCheckTimer > 0) {
            continueCheckTimer--;
            // During rally phase, always continue; otherwise check path
            return atRallyPoint || !roamer.getNavigator().noPath();
        }
        continueCheckTimer = CONTINUE_CHECK_INTERVAL;

        // Alarm ended — release rally position
        if (!CsmIntegration.isFireAlarmActiveNear(roamer.world, roamer.getPosition())) {
            return false;
        }

        // Check if we've reached the rally point
        if (!atRallyPoint && isRallyPoint(roamer.world, roamer.getPosition())) {
            atRallyPoint = true;
            claimPosition(roamer.getPosition());
            outdoorWanderTimer = OUTDOOR_WANDER_INTERVAL_MIN
                + roamer.getRNG().nextInt(OUTDOOR_WANDER_INTERVAL_MAX - OUTDOOR_WANDER_INTERVAL_MIN);
        }

        // During evacuation phases, stop if path exhausted (will retry on next shouldExecute)
        if (!atRallyPoint) {
            return !roamer.getNavigator().noPath();
        }

        // Rally phase — always continue (keeps wander AI from taking over)
        return true;
    }

    @Override
    public void startExecuting() {
        repathTimer = 0;
        continueCheckTimer = CONTINUE_CHECK_INTERVAL;
        outdoorWanderTimer = 0;

        if (atRallyPoint) {
            // Already at rally point — go straight to rally phase
            roamer.setEmergencyMode(false);
            outdoorWanderTimer = OUTDOOR_WANDER_INTERVAL_MIN
                + roamer.getRNG().nextInt(OUTDOOR_WANDER_INTERVAL_MAX - OUTDOOR_WANDER_INTERVAL_MIN);
        } else {
            reachedOutdoors = false;
            roamer.getNavigator().tryMoveToXYZ(exitTarget.getX() + 0.5, exitTarget.getY(),
                exitTarget.getZ() + 0.5, speed);
        }
    }

    @Override
    public void updateTask() {
        if (atRallyPoint) {
            // Rally phase: wander outdoors on walkable blocks
            outdoorWanderTimer--;
            if (outdoorWanderTimer <= 0) {
                outdoorWanderTimer = OUTDOOR_WANDER_INTERVAL_MIN
                    + roamer.getRNG().nextInt(OUTDOOR_WANDER_INTERVAL_MAX - OUTDOOR_WANDER_INTERVAL_MIN);
                tryOutdoorWander();
            }
            return;
        }

        // Phase transition: once the roamer steps outside, disable emergency mode, record the
        // doorway in the exit cache, and find a proper rally point further from the building
        if (!reachedOutdoors && roamer.world.canSeeSky(roamer.getPosition().up())) {
            reachedOutdoors = true;
            roamer.setEmergencyMode(false);
            RoamerExitCache.recordExit(roamer.getPosition(), roamer.world.getTotalWorldTime());

            // Find a rally point well clear of the building (walkable blocks only now)
            rallyPoint = findNearbyRallyPoint(roamer.world, roamer.getPosition());
            if (rallyPoint != null) {
                claimPosition(rallyPoint);
                exitTarget = rallyPoint;
            }
            if (exitTarget != null) {
                roamer.getNavigator().tryMoveToXYZ(exitTarget.getX() + 0.5, exitTarget.getY(),
                    exitTarget.getZ() + 0.5, speed);
            }
            repathTimer = 0;
            return;
        }

        repathTimer++;
        if (repathTimer >= REPATH_INTERVAL_TICKS) {
            repathTimer = 0;
            if (exitTarget != null) {
                roamer.getNavigator().tryMoveToXYZ(exitTarget.getX() + 0.5, exitTarget.getY(),
                    exitTarget.getZ() + 0.5, speed);
            }
        }
    }

    @Override
    public void resetTask() {
        if (rallyPoint != null) {
            releasePosition(rallyPoint);
        }
        exitTarget = null;
        rallyPoint = null;
        reachedOutdoors = false;
        atRallyPoint = false;
        checkTimer = CHECK_INTERVAL_TICKS;
        roamer.setEmergencyMode(false);
    }

    /**
     * Picks a random nearby outdoor position on a walkable block and walks there slowly,
     * keeping the roamer moving naturally outside without re-entering the building.
     */
    private void tryOutdoorWander() {
        Vec3d target = RandomPositionGenerator.findRandomTarget(roamer, OUTDOOR_WANDER_RANGE, 3);
        if (target == null) {
            return;
        }
        BlockPos targetPos = new BlockPos(target);
        // Only wander to outdoor positions on walkable blocks
        if (roamer.world.canSeeSky(targetPos.up())) {
            Block groundBlock = roamer.world.getBlockState(targetPos.down()).getBlock();
            if (SumConfig.isBlockWalkableByRoamer(groundBlock)) {
                roamer.getNavigator().tryMoveToXYZ(target.x, target.y, target.z, 0.6D);
            }
        }
    }

    // --- Position claiming ---

    private static void claimPosition(BlockPos pos) {
        claimedPositions.add(pos);
    }

    private static void releasePosition(BlockPos pos) {
        claimedPositions.remove(pos);
    }

    /**
     * Clears all claimed rally positions. Should be called on world unload.
     */
    public static void clearClaims() {
        claimedPositions.clear();
    }

    // --- Exit search ---

    private BlockPos findReachableExit(World world, BlockPos entityPos) {
        List<BlockPos> candidates = findExitCandidates(world, entityPos);
        for (BlockPos candidate : candidates) {
            // Skip positions already claimed by another roamer
            if (isPositionClaimed(candidate)) {
                continue;
            }
            if (roamer.getNavigator().getPathToXYZ(
                    candidate.getX() + 0.5, candidate.getY(), candidate.getZ() + 0.5) != null) {
                claimPosition(candidate);
                return candidate;
            }
        }
        return null;
    }

    private static boolean isPositionClaimed(BlockPos candidate) {
        return claimedPositions.contains(candidate);
    }

    /**
     * Searches outward from the roamer's current outdoor position for a nearby rally point on
     * walkable blocks that isn't already claimed. This is called when the roamer first steps
     * outside, so it finds a spot well clear of the building to wait at (not the doorstep).
     * Uses a smaller, cheaper search than the full exit search since we're already outside.
     */
    private BlockPos findNearbyRallyPoint(World world, BlockPos from) {
        int rallySearchRadius = 15;
        BlockPos best = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int r = 3; r <= rallySearchRadius; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }
                    BlockPos candidate = from.add(dx, 0, dz);

                    if (!world.isBlockLoaded(candidate)) {
                        continue;
                    }
                    if (!world.isAirBlock(candidate) || !world.isAirBlock(candidate.up())) {
                        continue;
                    }
                    IBlockState belowState = world.getBlockState(candidate.down());
                    if (!belowState.getMaterial().isSolid()) {
                        continue;
                    }
                    if (!SumConfig.isBlockWalkableByRoamer(belowState.getBlock())) {
                        continue;
                    }
                    if (!isRallyPoint(world, candidate)) {
                        continue;
                    }
                    if (isPositionClaimed(candidate)) {
                        continue;
                    }

                    double distSq = from.distanceSq(candidate);
                    if (distSq < bestDistSq) {
                        bestDistSq = distSq;
                        best = candidate;
                    }
                }
            }
            if (best != null) {
                return best;
            }
        }
        return best;
    }

    /**
     * Checks if a position qualifies as a safe rally point: outdoors with a large open-sky
     * footprint, well clear of the building.
     */
    private boolean isRallyPoint(World world, BlockPos pos) {
        if (!world.canSeeSky(pos.up())) {
            return false;
        }
        return hasEnoughOpenSky(world, pos, OPEN_AREA_CHECK_RADIUS, MIN_OPEN_SKY_BLOCKS);
    }

    private boolean hasEnoughOpenSky(World world, BlockPos center, int radius, int threshold) {
        int count = 0;
        int side = radius * 2 + 1;
        int total = side * side;
        int checked = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (world.canSeeSky(center.add(dx, 1, dz))) {
                    count++;
                    if (count >= threshold) {
                        return true;
                    }
                }
                checked++;
                if (count + (total - checked) < threshold) {
                    return false;
                }
            }
        }
        return count >= threshold;
    }

    private List<BlockPos> findExitCandidates(World world, BlockPos entityPos) {
        List<BlockPos> candidates = new ArrayList<>();

        for (int r = 1; r <= SEARCH_RADIUS_XZ; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.abs(dx) != r && Math.abs(dz) != r) {
                        continue;
                    }

                    for (int dy = -SEARCH_RADIUS_Y; dy <= SEARCH_RADIUS_Y; dy++) {
                        BlockPos candidate = entityPos.add(dx, dy, dz);

                        if (!world.isBlockLoaded(candidate)) {
                            continue;
                        }

                        if (!world.isAirBlock(candidate) || !world.isAirBlock(candidate.up())) {
                            continue;
                        }

                        if (!world.getBlockState(candidate.down()).getMaterial().isSolid()) {
                            continue;
                        }

                        if (!world.canSeeSky(candidate.up())) {
                            continue;
                        }

                        if (!hasEnoughOpenSky(world, candidate, OPEN_AREA_CHECK_RADIUS,
                            MIN_OPEN_SKY_BLOCKS)) {
                            continue;
                        }

                        candidates.add(candidate);
                    }
                }
            }

            if (candidates.size() >= MAX_CANDIDATES) {
                break;
            }
        }

        candidates.sort(Comparator.comparingDouble(entityPos::distanceSq));
        if (candidates.size() > MAX_CANDIDATES) {
            candidates.subList(MAX_CANDIDATES, candidates.size()).clear();
        }
        return candidates;
    }
}
