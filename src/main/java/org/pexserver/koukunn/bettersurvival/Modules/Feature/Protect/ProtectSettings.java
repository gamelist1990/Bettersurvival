package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Loader;

/**
 * Protect の軽量設定。保持期間は既定30日。
 */
public final class ProtectSettings {
    private static final String PATH = "protect/settings.json";
    private static final int DEFAULT_RETENTION_DAYS = 30;
    private static final int MIN_RETENTION_DAYS = 1;
    private static final int MAX_RETENTION_DAYS = 3650;

    private final Loader plugin;
    private volatile int retentionDays = DEFAULT_RETENTION_DAYS;

    public ProtectSettings(Loader plugin) {
        this.plugin = plugin;
        load();
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public synchronized void setRetentionDays(int days) {
        retentionDays = Math.max(MIN_RETENTION_DAYS, Math.min(MAX_RETENTION_DAYS, days));
        save();
    }

    private void load() {
        PEXConfig config = plugin.getConfigManager().loadConfig(PATH).orElseGet(PEXConfig::new);
        Object raw = config.get("retentionDays");
        if (raw instanceof Number number) {
            retentionDays = Math.max(MIN_RETENTION_DAYS, Math.min(MAX_RETENTION_DAYS, number.intValue()));
        } else {
            save();
        }
    }

    private void save() {
        PEXConfig config = plugin.getConfigManager().loadConfig(PATH).orElseGet(PEXConfig::new);
        config.put("retentionDays", retentionDays);
        plugin.getConfigManager().saveConfig(PATH, config);
    }
}
