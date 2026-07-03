package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.List;

/**
 * 保護レベルの定義テーブル。
 * レベル 1〜5 は従来の単一素材コスト、
 * レベル 6〜100 は複数素材・段違いコストで拡張される。
 */
public final class ClaimLevel {

    public static final int MIN_LEVEL = 1;
    public static final int MAX_LEVEL = 100;
    /** 個人所有の保護コアが到達できる最大レベル */
    public static final int PERSONAL_MAX_LEVEL = 10;

    /** レベル -> 水平方向の保護半径（ブロック、コアを中心とした正方形・高さは全域） */
    private static final int[] RADIUS = {0, 8, 12, 16, 24, 32};

    /** レベル -> 1時間あたりの燃料消費量（燃料ユニット/時） */
    private static final double[] UPKEEP_PER_HOUR = {0, 16, 32, 48, 64, 96};

    /** レベル(現在) -> 次レベルへの強化に必要な単一素材（レベル 1〜4 用） */
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

    public record Requirement(Material material, int amount) {
    }

    public static int clamp(int level) {
        return Math.max(MIN_LEVEL, Math.min(MAX_LEVEL, level));
    }

    public static int radius(int level) {
        int l = clamp(level);
        if (l <= 5) {
            return RADIUS[l];
        }
        // Lv5 以降は 2 ブロックずつ拡大（Lv100 で 222）
        return 32 + 2 * (l - 5);
    }

    public static double upkeepPerHour(int level) {
        int l = clamp(level);
        if (l <= 5) {
            return UPKEEP_PER_HOUR[l];
        }
        // Lv5 以降は指数関数的に上昇（段違いの維持コスト）
        return 96.0 * Math.pow(1.05, l - 5);
    }

    public static boolean canUpgrade(int level) {
        return clamp(level) < MAX_LEVEL;
    }

    /**
     * 指定レベルから次のレベルへ上げるのに必要な素材一覧を返す。
     * レベル 1〜4 は従来の単一素材、レベル 5〜99 は複数素材。
     */
    public static List<Requirement> upgradeRequirements(int level) {
        int l = clamp(level);
        if (l >= MAX_LEVEL) {
            return List.of();
        }
        if (l <= 4) {
            return List.of(new Requirement(UPGRADE_MATERIAL[l], UPGRADE_AMOUNT[l]));
        }
        // Lv5 以降: ネザライト・ダイヤ・エメラルドを要求
        int tier = (l - 5) / 10; // 0〜9
        int step = (l - 5) % 10;
        int netherite = 4 + tier * 4 + step;
        int diamond = 32 + tier * 16 + step * 4;
        int emerald = 32 + tier * 16;
        List<Requirement> list = new ArrayList<>(3);
        list.add(new Requirement(Material.NETHERITE_INGOT, netherite));
        list.add(new Requirement(Material.DIAMOND, diamond));
        list.add(new Requirement(Material.EMERALD, emerald));
        return list;
    }

    public static boolean isPersonalMaxLevel(int level) {
        return clamp(level) >= PERSONAL_MAX_LEVEL;
    }
}
