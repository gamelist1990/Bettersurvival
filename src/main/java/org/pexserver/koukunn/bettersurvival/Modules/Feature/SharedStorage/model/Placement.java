package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.Location;

public record Placement(String id, String role, Location anchor) {
    public boolean isMain() {
        return "main".equals(role);
    }
}
