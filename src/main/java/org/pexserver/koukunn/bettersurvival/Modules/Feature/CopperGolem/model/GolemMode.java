package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model;

import java.util.Locale;

public enum GolemMode {
    IDLE("Idle"),
    CROP("作物採取"),
    COMBAT("戦闘");

    private final String displayName;

    GolemMode(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static GolemMode from(String raw) {
        if (raw == null || raw.isBlank()) {
            return IDLE;
        }
        try {
            return GolemMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return IDLE;
        }
    }
}
