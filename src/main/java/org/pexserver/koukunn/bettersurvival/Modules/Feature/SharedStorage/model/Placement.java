package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import org.bukkit.Location;

/** networkKey は内部用(scope+id)、id はアイテム表示/互換用の生ID。 */
public record Placement(String networkKey, String id, String role, Location anchor) {
    public boolean isMain() {
        return "main".equals(role);
    }
}
