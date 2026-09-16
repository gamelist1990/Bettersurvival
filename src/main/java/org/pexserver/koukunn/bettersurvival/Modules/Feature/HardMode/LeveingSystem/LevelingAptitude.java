package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Material;

/**
 * Just Leveling の8能力値を Paper 側で表現する。
 */
public enum LevelingAptitude {
    STRENGTH("strength", "Strength", "STR", Material.IRON_SWORD),
    CONSTITUTION("constitution", "Constitution", "CON", Material.GOLDEN_APPLE),
    DEXTERITY("dexterity", "Dexterity", "DEX", Material.BOW),
    DEFENSE("defense", "Defense", "DEF", Material.SHIELD),
    INTELLIGENCE("intelligence", "Intelligence", "INT", Material.ENCHANTING_TABLE),
    BUILDING("building", "Building", "BLD", Material.DIAMOND_PICKAXE),
    MAGIC("magic", "Magic", "MAG", Material.BREWING_STAND),
    LUCK("luck", "Luck", "LCK", Material.EMERALD);

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

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String abbreviation() {
        return abbreviation;
    }

    public Material icon() {
        return icon;
    }

    public String rank(int level) {
        if (level >= 32) return "Ascendant";
        if (level >= 28) return "Master";
        if (level >= 24) return "Expert";
        if (level >= 20) return "Skilled";
        if (level >= 16) return "Adept";
        if (level >= 12) return "Journeyman";
        if (level >= 8) return "Apprentice";
        if (level >= 4) return "Fledgling";
        return "Novice";
    }

    public int passiveTier10(int level) {
        return thresholdCount(level, 5, 8, 11, 14, 17, 20, 23, 26, 29, 32);
    }

    public int passiveTier5(int level) {
        return thresholdCount(level, 8, 14, 20, 26, 32);
    }

    private static int thresholdCount(int level, int... thresholds) {
        int count = 0;
        for (int threshold : thresholds) {
            if (level >= threshold) count++;
        }
        return count;
    }
}
