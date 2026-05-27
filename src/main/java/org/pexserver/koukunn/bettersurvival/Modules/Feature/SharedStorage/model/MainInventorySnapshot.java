package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class MainInventorySnapshot {
    private final String networkId;
    private final List<ItemStack> contents;

    public MainInventorySnapshot(String networkId, List<ItemStack> contents) {
        this.networkId = networkId;
        this.contents = new ArrayList<>(contents);
    }

    public String networkId() {
        return networkId;
    }

    public List<ItemStack> contents() {
        return new ArrayList<>(contents);
    }
}
