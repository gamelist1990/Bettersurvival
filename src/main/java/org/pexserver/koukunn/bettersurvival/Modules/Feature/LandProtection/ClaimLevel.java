package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Material;

/**
 * 保護レベルの定義テーブル。
 * レベルごとに 保護半径 / 維持コスト(燃料消費) / 次レベルへの強化費用(鉱石) を持つ。
 */
public final class ClaimLevel {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 5;

    /** レベル -> 水平方向の保護半径（ブロック、コアを中心とした正方形・高さは全域） */
    private static final int[] RADIUS = {0, 8, 12, 16, 24, 32};

    /** レベル -> 1時間あたりの燃料消費量（燃料ユニット/時） */
    private static final double[] UPKEEP_PER_HOUR = {0, 16, 32, 48, 64, 96};

    /** レベル(現在) -> 次レベルへの強化に必要な鉱石 */
    private static final Material[] UPGRADE_MATERIAL = {
            null,
            Material.IRON_INGOT,      // Lv1 -> Lv2
            Material.GOLD_INGOT,      // Lv2 -> Lv3
            Material.DIAMOND,         // Lv3 -> Lv4
            Material.NETHERITE_INGOT, // Lv4 -> Lv5
            null,
    };
    private static final int[] UPGRADE_AMOUNT = {0, 32, 24, 16, 2, 0};

    private ClaimLevel() {
    }

    public static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public static int radius(int level) {
        return RADIUS[clamp(level)];
    }

    public static double upkeepPerHour(int level) {
        return UPKEEP_PER_HOUR[clamp(level)];
    }

    public static boolean canUpgrade(int level) {
        return clamp(level) < MAX_LEVEL;
    }

    public static Material upgradeMaterial(int level) {
        return UPGRADE_MATERIAL[clamp(level)];
    }

    public static int upgradeAmount(int level) {
        return UPGRADE_AMOUNT[clamp(level)];
    }
}
