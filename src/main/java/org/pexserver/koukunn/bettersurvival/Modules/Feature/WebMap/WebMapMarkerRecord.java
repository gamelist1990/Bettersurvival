package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

public record WebMapMarkerRecord(
        String id,
        String kind,
        String name,
        String displayName,
        String color,
        int x,
        int z,
        int chunkX,
        int chunkZ,
        int rotation,
        int radius,
        String ownerType,
        String ownerName,
        java.util.List<String> members
) {
}
