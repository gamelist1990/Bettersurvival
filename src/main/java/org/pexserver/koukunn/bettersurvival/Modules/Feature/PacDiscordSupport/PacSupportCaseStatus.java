package org.pexserver.koukunn.bettersurvival.Modules.Feature.PacDiscordSupport;

enum PacSupportCaseStatus {
    STARTED("開始"),
    RESOLVED("解決"),
    CLOSED("終了");

    private final String displayName;

    PacSupportCaseStatus(String displayName) {
        this.displayName = displayName;
    }

    String displayName() {
        return displayName;
    }

    static PacSupportCaseStatus fromValue(String value) {
        if (value == null || value.isBlank()) return STARTED;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return STARTED;
        }
    }
}
