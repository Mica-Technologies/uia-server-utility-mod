package com.micatechnologies.minecraft.sum.huds;

import cc.polyfrost.oneconfig.hud.SingleTextHud;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.IResourcePack;

/**
 * Name of the topmost (highest-priority) enabled resource pack. Modeled on
 * EvergreenHUD's resource-pack element.
 */
public class ResourcePackHud extends SingleTextHud {

    public ResourcePackHud() {
        super("Pack:", true, 5, 5);
    }

    @Override
    protected String getText(boolean example) {
        if (example) {
            return "Default";
        }
        List<IResourcePack> packs = Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntriesAll()
            .stream()
            .filter(e -> Minecraft.getMinecraft().getResourcePackRepository().getRepositoryEntries().contains(e))
            .map(e -> e.getResourcePack())
            .collect(java.util.stream.Collectors.toList());
        if (packs.isEmpty()) {
            return "Default";
        }
        // Last-applied wins in vanilla's stacking order.
        IResourcePack top = packs.get(packs.size() - 1);
        return top.getPackName();
    }
}
