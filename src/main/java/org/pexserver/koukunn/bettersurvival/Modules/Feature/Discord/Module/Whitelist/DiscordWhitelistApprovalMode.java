package org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Whitelist;

public enum DiscordWhitelistApprovalMode {
    DEFAULT,
    REACTION;

    public static DiscordWhitelistApprovalMode fromName(String value) {
        if (value == null || value.isBlank()) {
            return DEFAULT;
        }
        for (DiscordWhitelistApprovalMode mode : values()) {
            if (mode.name().equalsIgnoreCase(value.trim())) {
                return mode;
            }
        }
        return DEFAULT;
    }
}
