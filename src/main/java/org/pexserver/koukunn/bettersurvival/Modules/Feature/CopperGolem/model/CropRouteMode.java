package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model;

import java.util.Locale;

public enum CropRouteMode {
    NEAR_ORIGIN("原点優先"),
    BALANCED("バランス");

    private final String displayName;

    CropRouteMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public CropRouteMode next() {
        CropRouteMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static CropRouteMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NEAR_ORIGIN;
        }
        try {
            return CropRouteMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return NEAR_ORIGIN;
        }
    }
}
