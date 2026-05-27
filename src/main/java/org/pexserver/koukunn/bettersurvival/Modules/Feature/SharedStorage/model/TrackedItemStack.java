package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.inventory.ItemStack;

public final class TrackedItemStack {
    private final ItemStack stack;
    private int mainContribution;

    public TrackedItemStack(ItemStack stack, int mainContribution) {
        this.stack = stack;
        this.mainContribution = mainContribution;
    }

    public ItemStack stack() {
        return stack;
    }

    public int mainContribution() {
        return mainContribution;
    }

    public void addMainContribution(int amount) {
        this.mainContribution += amount;
    }
}
