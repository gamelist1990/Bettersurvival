package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.Location;
import org.bukkit.inventory.Inventory;

import java.util.List;

public record ResolvedInventory(Location anchor, Inventory inventory, List<Location> footprint) {
}
