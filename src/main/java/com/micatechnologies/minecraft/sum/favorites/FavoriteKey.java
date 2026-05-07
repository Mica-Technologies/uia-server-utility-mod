package com.micatechnologies.minecraft.sum.favorites;

import javax.annotation.Nullable;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

public final class FavoriteKey {

    private final ResourceLocation registryName;
    private final int meta;

    public FavoriteKey(ResourceLocation registryName, int meta) {
        if (registryName == null) {
            throw new IllegalArgumentException("registryName must not be null");
        }
        this.registryName = registryName;
        this.meta = meta;
    }

    @Nullable
    public static FavoriteKey of(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        Item item = stack.getItem();
        ResourceLocation name = item.getRegistryName();
        if (name == null) {
            return null;
        }
        return new FavoriteKey(name, stack.getMetadata());
    }

    @Nullable
    public static FavoriteKey parse(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int hash = s.indexOf('#');
        String namePart = hash < 0 ? s : s.substring(0, hash);
        int metaPart = 0;
        if (hash >= 0) {
            try {
                metaPart = Integer.parseInt(s.substring(hash + 1));
            } catch (NumberFormatException e) {
                return null;
            }
        }
        ResourceLocation rl;
        try {
            rl = new ResourceLocation(namePart);
        } catch (Exception e) {
            return null;
        }
        return new FavoriteKey(rl, metaPart);
    }

    public ResourceLocation getRegistryName() {
        return registryName;
    }

    public int getMeta() {
        return meta;
    }

    public ItemStack resolveStack() {
        Item item = ForgeRegistries.ITEMS.getValue(registryName);
        if (item == null) {
            return ItemStack.EMPTY;
        }
        return new ItemStack(item, 1, meta);
    }

    @Override
    public String toString() {
        return registryName.toString() + "#" + meta;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof FavoriteKey)) {
            return false;
        }
        FavoriteKey that = (FavoriteKey) o;
        return meta == that.meta && registryName.equals(that.registryName);
    }

    @Override
    public int hashCode() {
        return registryName.hashCode() * 31 + meta;
    }
}
