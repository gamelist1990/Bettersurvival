package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.inventory.ItemStack;

import java.util.List;

public record FilteredSubTarget(ResolvedInventory inventory, List<ItemStack> filters, int priority) {
}
