package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
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
    private static final int SEARCH_RADIUS_XZ = 25;
    private static final int SEARCH_RADIUS_Y_DOWN = 20;
    private static final int SEARCH_RADIUS_Y_UP = 5;
    private static final int HAZARD_CHECK_DISTANCE = 3;
    private static final int REPATH_INTERVAL_TICKS = 60;
    private static final int CONTINUE_CHECK_INTERVAL = 20;
    private static final int MAX_CANDIDATES = 8;
    private static final int GOOD_ENOUGH_SCORE = 100;

    // Shelter-in-place indoor wander settings
    private static final int INDOOR_WANDER_RANGE = 5;
    private static final int INDOOR_WANDER_INTERVAL_MIN = 60;  // 3 seconds
    private static final int INDOOR_WANDER_INTERVAL_MAX = 200; // 10 seconds

    // Minimum distance between claimed shelter positions — just prevent same-block overlap
    // so roamers can pack shoulder-to-shoulder in a small shelter room
    private static final double MIN_CLAIM_DISTANCE_SQ = 1.0; // 1 block

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

    public EntityAIRoamerStormShelter(EntityRoamer roamer, double speed) {
        this.roamer = roamer;
        this.speed = speed;
        this.csmLoaded = Loader.isModLoaded("csm");
        this.checkTimer = roamer.getRNG().nextInt(CHECK_INTERVAL_TICKS);
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
            return true;
        }

        roamer.setEmergencyMode(true);
        shelterTarget = findReachableShelter(world, entityPos);
        if (shelterTarget == null) {
            roamer.setEmergencyMode(false);
        } else {
            claimPosition(shelterTarget);
        }
        return shelterTarget != null;
    }

    @Override
    public boolean shouldContinueExecuting() {
        if (!csmLoaded) {
            return false;
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
        } else {
            roamer.getNavigator().tryMoveToXYZ(shelterTarget.getX() + 0.5, shelterTarget.getY(),
                shelterTarget.getZ() + 0.5, speed);
        }
    }

    @Override
    public void updateTask() {
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

    private BlockPos findReachableShelter(World world, BlockPos entityPos) {
        List<ScoredPos> candidates = findShelterCandidates(world, entityPos);
        for (ScoredPos sp : candidates) {
            if (roamer.getNavigator().getPathToXYZ(
                    sp.pos.getX() + 0.5, sp.pos.getY(), sp.pos.getZ() + 0.5) != null) {
                return sp.pos;
            }
        }
        return null;
    }

    private List<ScoredPos> findShelterCandidates(World world, BlockPos entityPos) {
        List<ScoredPos> candidates = new ArrayList<>();
        int worstKeptScore = Integer.MIN_VALUE;

        for (int dy = -SEARCH_RADIUS_Y_DOWN; dy <= SEARCH_RADIUS_Y_UP; dy++) {
            for (int r = 0; r <= SEARCH_RADIUS_XZ; r++) {
                int startDx = (r == 0) ? 0 : -r;
                int endDx = r;

                for (int dx = startDx; dx <= endDx; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) {
                            continue;
                        }

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

                        if (world.canSeeSky(candidate.up())) {
                            continue;
                        }

                        // Skip positions already claimed by another roamer
                        if (isPositionClaimed(candidate)) {
                            continue;
                        }

                        int yScore = (entityPos.getY() - candidate.getY()) * 10;
                        int distScore = -(Math.abs(dx) + Math.abs(dz));
                        int score = yScore + distScore;

                        if (candidates.size() >= MAX_CANDIDATES && score <= worstKeptScore) {
                            continue;
                        }

                        // Expensive check — only reached for promising unclaimed candidates
                        if (isNearHazard(world, candidate)) {
                            continue;
                        }

                        candidates.add(new ScoredPos(candidate, score));

                        if (score >= GOOD_ENOUGH_SCORE) {
                            candidates.sort(Comparator.comparingInt(s -> -s.score));
                            return candidates;
                        }

                        if (candidates.size() > MAX_CANDIDATES) {
                            candidates.sort(Comparator.comparingInt(s -> -s.score));
                            candidates.subList(MAX_CANDIDATES, candidates.size()).clear();
                            worstKeptScore = candidates.get(candidates.size() - 1).score;
                        }
                    }
                }
            }
        }

        candidates.sort(Comparator.comparingInt(s -> -s.score));
        return candidates;
    }

    // --- Position claiming ---

    private static void claimPosition(BlockPos pos) {
        claimedPositions.add(pos);
    }

    private static void releasePosition(BlockPos pos) {
        claimedPositions.remove(pos);
    }

    private static boolean isPositionClaimed(BlockPos candidate) {
        for (BlockPos claimed : claimedPositions) {
            if (claimed.distanceSq(candidate) < MIN_CLAIM_DISTANCE_SQ) {
                return true;
            }
        }
        return false;
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
