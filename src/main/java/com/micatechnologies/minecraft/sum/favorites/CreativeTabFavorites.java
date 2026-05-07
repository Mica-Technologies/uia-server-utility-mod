package com.micatechnologies.minecraft.sum.favorites;

import javax.annotation.Nonnull;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.util.NonNullList;

public class CreativeTabFavorites extends CreativeTabs {

    public static final String LABEL = "sum_favorites";
    public static CreativeTabFavorites INSTANCE;

    public CreativeTabFavorites() {
        super(LABEL);
    }

    @Override
    @Nonnull
    public ItemStack createIcon() {
        return new ItemStack(Items.NETHER_STAR);
    }

    @Override
    public boolean hasSearchBar() {
        return false;
    }

    @Override
    public void displayAllRelevantItems(@Nonnull NonNullList<ItemStack> items) {
        for (FavoriteKey key : FavoritesStore.snapshot()) {
            ItemStack stack = key.resolveStack();
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
    }
}
