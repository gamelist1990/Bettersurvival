package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Material;

/**
 * Just Leveling の8能力値を Paper 側で表現する。
 */
public enum LevelingAptitude {
    STRENGTH("strength", "筋力", "STR", Material.IRON_SWORD),
    CONSTITUTION("constitution", "体力", "CON", Material.GOLDEN_APPLE),
    DEXTERITY("dexterity", "敏捷", "DEX", Material.BOW),
    DEFENSE("defense", "防御", "DEF", Material.SHIELD),
    INTELLIGENCE("intelligence", "知力", "INT", Material.ENCHANTING_TABLE),
    BUILDING("building", "建築", "BLD", Material.DIAMOND_PICKAXE),
    MAGIC("magic", "魔力", "MAG", Material.BREWING_STAND),
    LUCK("luck", "幸運", "LCK", Material.EMERALD);

    public static final int MAX_LEVEL = 32;

    private final String key;
    private final String displayName;
    private final String abbreviation;
    private final Material icon;

    LevelingAptitude(String key, String displayName, String abbreviation, Material icon) {
        this.key = key;
        this.displayName = displayName;
        this.abbreviation = abbreviation;
        this.icon = icon;
    }

    public String key() { return key; }
    public String displayName() { return displayName; }
    public String abbreviation() { return abbreviation; }
    public Material icon() { return icon; }

    public String rank(int level) {
        if (level >= 32) return "超越者";
        if (level >= 28) return "達人";
        if (level >= 24) return "熟練者";
        if (level >= 20) return "上級者";
        if (level >= 16) return "中級者";
        if (level >= 12) return "一人前";
        if (level >= 8) return "見習い";
        if (level >= 4) return "駆け出し";
        return "初心者";
    }

    public int passiveTier10(int level) {
        return thresholdCount(level, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32);
    }

    public int passiveTier5(int level) {
        return thresholdCount(level, 8, 14, 20, 26, 32);
    }

    private static int thresholdCount(int level, int... thresholds) {
        int count = 0;
        for (int threshold : thresholds) if (level >= threshold) count++;
        return count;
    }
}
