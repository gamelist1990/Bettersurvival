package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model;

import java.util.Locale;

public enum SubFrameFilterMode {
    EXACT("完全一致"),
    MATERIAL("素材一致"),
    ENCHANT_STATE("素材+エンチャ有無");

    private final String label;

    SubFrameFilterMode(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public SubFrameFilterMode next() {
        SubFrameFilterMode[] values = values();
        return values[(ordinal() + 1) % values.length];
    }

    public static SubFrameFilterMode fromStored(String raw) {
        if (raw == null || raw.isEmpty()) {
            return EXACT;
        }
        try {
            return SubFrameFilterMode.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return EXACT;
        }
    }
}
