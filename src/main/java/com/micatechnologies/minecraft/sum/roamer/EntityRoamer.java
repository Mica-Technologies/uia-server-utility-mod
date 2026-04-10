package com.micatechnologies.minecraft.sum.roamer;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemNameTag;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagString;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumHand;
import net.minecraft.world.World;
import net.minecraftforge.common.util.Constants;

/**
 * A passive NPC entity that wanders around on blocks specified in the SUM config.
 * The roamer walks at normal speed and stops occasionally for random pauses.
 * It cannot be hurt and does not despawn.
 */
public class EntityRoamer extends EntityCreature {

    private static final String NBT_GREET_ENABLED = "GreetEnabled";
    private static final String NBT_GREET_RADIUS = "GreetRadius";
    private static final String NBT_GREET_COOLDOWN = "GreetCooldown";
    private static final String NBT_GREETINGS = "Greetings";

    private static final double DEFAULT_GREET_RADIUS = 2.0;
    private static final int DEFAULT_GREET_COOLDOWN = 1200; // 60 seconds
    private static final String[] DEFAULT_GREETINGS = {"Hello!", "Hey there!", "Welcome!"};

    private boolean greetEnabled = true;
    private double greetRadius = DEFAULT_GREET_RADIUS;
    private int greetCooldown = DEFAULT_GREET_COOLDOWN;
    private List<String> greetings = new ArrayList<>();

    public EntityRoamer(World world) {
        super(world);
        this.setSize(0.6F, 1.8F); // Same hitbox as a player
        resetGreetingsToDefault();
    }

    @Override
    protected void initEntityAI() {
        this.tasks.addTask(0, new EntityAISwimming(this));
        this.tasks.addTask(1, new EntityAIRoamerWander(this, 1.0D, 10));
        this.tasks.addTask(2, new EntityAIRoamerGreet(this));
        this.tasks.addTask(3, new EntityAIWatchClosest(this, EntityPlayer.class, 8.0F));
        this.tasks.addTask(4, new EntityAILookIdle(this));
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

    // --- Naming support ---

    @Override
    public boolean processInteract(EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (stack.getItem() instanceof ItemNameTag && stack.hasDisplayName()) {
            if (!world.isRemote) {
                this.setCustomNameTag(stack.getDisplayName());
                this.setAlwaysRenderNameTag(true);
                if (!player.capabilities.isCreativeMode) {
                    stack.shrink(1);
                }
            }
            return true;
        }
        return super.processInteract(player, hand);
    }

    // --- NBT persistence ---

    @Override
    public void writeEntityToNBT(NBTTagCompound compound) {
        super.writeEntityToNBT(compound);
        compound.setBoolean(NBT_GREET_ENABLED, greetEnabled);
        compound.setDouble(NBT_GREET_RADIUS, greetRadius);
        compound.setInteger(NBT_GREET_COOLDOWN, greetCooldown);

        NBTTagList greetingList = new NBTTagList();
        for (String greeting : greetings) {
            greetingList.appendTag(new NBTTagString(greeting));
        }
        compound.setTag(NBT_GREETINGS, greetingList);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound compound) {
        super.readEntityFromNBT(compound);
        if (compound.hasKey(NBT_GREET_ENABLED)) {
            greetEnabled = compound.getBoolean(NBT_GREET_ENABLED);
        }
        if (compound.hasKey(NBT_GREET_RADIUS)) {
            greetRadius = compound.getDouble(NBT_GREET_RADIUS);
        }
        if (compound.hasKey(NBT_GREET_COOLDOWN)) {
            greetCooldown = compound.getInteger(NBT_GREET_COOLDOWN);
        }
        if (compound.hasKey(NBT_GREETINGS, Constants.NBT.TAG_LIST)) {
            NBTTagList greetingList = compound.getTagList(NBT_GREETINGS, Constants.NBT.TAG_STRING);
            greetings.clear();
            for (int i = 0; i < greetingList.tagCount(); i++) {
                greetings.add(greetingList.getStringTagAt(i));
            }
        }

        // Restore name tag visibility after load
        if (this.hasCustomName()) {
            this.setAlwaysRenderNameTag(true);
        }
    }

    // --- Property accessors ---

    public boolean isGreetEnabled() {
        return greetEnabled;
    }

    public void setGreetEnabled(boolean enabled) {
        this.greetEnabled = enabled;
    }

    public double getGreetRadius() {
        return greetRadius;
    }

    public void setGreetRadius(double radius) {
        this.greetRadius = radius;
    }

    public int getGreetCooldown() {
        return greetCooldown;
    }

    public void setGreetCooldown(int cooldown) {
        this.greetCooldown = cooldown;
    }

    public List<String> getGreetings() {
        return greetings;
    }

    public void addGreeting(String message) {
        greetings.add(message);
    }

    public void clearGreetings() {
        greetings.clear();
    }

    public void resetGreetingsToDefault() {
        greetings.clear();
        for (String msg : DEFAULT_GREETINGS) {
            greetings.add(msg);
        }
    }
}
