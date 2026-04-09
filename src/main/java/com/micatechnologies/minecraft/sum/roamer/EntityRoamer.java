package com.micatechnologies.minecraft.sum.roamer;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;

/**
 * A passive NPC entity that wanders around on blocks specified in the SUM config.
 * The roamer walks at normal speed and stops occasionally for random pauses.
 * It cannot be hurt and does not despawn.
 */
public class EntityRoamer extends EntityCreature {

    public EntityRoamer(World world) {
        super(world);
        this.setSize(0.6F, 1.8F); // Same hitbox as a player
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIRoamerWander(this, 1.0D, 10));
        this.tasks.addTask(2, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(3, new EntityAILookIdle(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        this.getEntityAttribute(SharedMonsterAttributes.MAX_HEALTH).setBaseValue(20.0D);
        this.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).setBaseValue(0.23D);
    }

    @Override
    protected PathNavigate createNavigator(World world) {
        return new RoamerWalkableBlocksNavigator(this, world);
    }

    @Override
    public boolean attackEntityFrom(DamageSource source, float amount) {
        // Roamers are invulnerable
        return false;
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean isEntityInvulnerable(DamageSource source) {
        return true;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean canBeRidden(net.minecraft.entity.Entity entityIn) {
        return false;
    }
}
