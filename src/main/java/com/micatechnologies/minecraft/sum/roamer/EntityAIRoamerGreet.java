package com.micatechnologies.minecraft.sum.roamer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.text.TextComponentString;

/**
 * AI task that makes named roamers greet nearby players. Designed for minimal tick cost:
 * <ul>
 *   <li>Only runs on named roamers with greetings enabled</li>
 *   <li>AABB player scan every 40 ticks (2 seconds), not every tick</li>
 *   <li>Per-player cooldown prevents greeting the same player repeatedly</li>
 *   <li>Stale cooldown entries cleaned lazily during scans</li>
 * </ul>
 */
public class EntityAIRoamerGreet extends EntityAIBase {

    private static final int SCAN_INTERVAL = 40; // ticks between player scans

    private final EntityRoamer roamer;
    private final Map<UUID, Long> greetCooldowns = new HashMap<>();
    private int scanTimer;

    public EntityAIRoamerGreet(EntityRoamer roamer) {
        this.roamer = roamer;
        this.setMutexBits(0); // no mutex — greeting doesn't block movement
    }

    @Override
    public boolean shouldExecute() {
        // Fast bail-outs before any work
        if (!roamer.hasCustomName() || !roamer.isGreetEnabled()) {
            return false;
        }
        if (roamer.getGreetings().isEmpty()) {
            return false;
        }
        if (scanTimer > 0) {
            scanTimer--;
            return false;
        }
        return true;
    }

    @Override
    public void startExecuting() {
        scanTimer = SCAN_INTERVAL;

        double radius = roamer.getGreetRadius();
        AxisAlignedBB box = roamer.getEntityBoundingBox().grow(radius);
        List<EntityPlayer> nearby = roamer.world.getEntitiesWithinAABB(EntityPlayer.class, box);

        if (nearby.isEmpty()) {
            return;
        }

        long worldTime = roamer.world.getTotalWorldTime();
        int cooldown = roamer.getGreetCooldown();
        List<String> greetings = roamer.getGreetings();

        // Clean stale cooldown entries lazily (only when we actually have players to check)
        if (greetCooldowns.size() > 16) {
            Iterator<Map.Entry<UUID, Long>> it = greetCooldowns.entrySet().iterator();
            while (it.hasNext()) {
                if (worldTime - it.next().getValue() > cooldown) {
                    it.remove();
                }
            }
        }

        for (EntityPlayer player : nearby) {
            UUID playerId = player.getUniqueID();
            Long lastGreet = greetCooldowns.get(playerId);
            if (lastGreet != null && worldTime - lastGreet < cooldown) {
                continue;
            }

            greetCooldowns.put(playerId, worldTime);

            String message = greetings.get(roamer.getRNG().nextInt(greetings.size()));
            String formatted = "<" + roamer.getCustomNameTag() + "> " + message;
            player.sendMessage(new TextComponentString(formatted));
        }
    }

    @Override
    public boolean shouldContinueExecuting() {
        return false; // one-shot per scan cycle
    }
}
