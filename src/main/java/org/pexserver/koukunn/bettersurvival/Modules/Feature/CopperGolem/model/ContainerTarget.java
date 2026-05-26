package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model;

import org.bukkit.Location;

import java.util.List;

public class ContainerTarget {
    private final Location anchor;
    private final List<Location> footprint;

    public ContainerTarget(Location anchor, List<Location> footprint) {
        this.anchor = anchor;
        this.footprint = footprint;
    }

    public Location anchor() {
        return anchor;
    }

    public List<Location> footprint() {
        return footprint;
    }
}
