package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 保護コアの燃料になるアイテムと、その燃料ユニット換算表。
 * 原木系・板材系・木炭・石炭など「燃料になる木材/炭系」を対象とする。
 */
public final class FuelValues {

    private FuelValues() {
    }

    /**
     * 1個あたりの燃料ユニットを返す。燃料でない場合は 0。
     */
    public static double unitValue(Material material) {
        if (material == null) {
            return 0;
        }
        String name = material.name();
        // 石炭・木炭系
        if (material == Material.COAL || material == Material.CHARCOAL) {
            return 8;
        }
        if (material == Material.COAL_BLOCK) {
            return 72;
        }
        // 原木系（原木・樹皮付き・表皮を剥いだもの・ネザー菌糸柄・竹ブロック）
        if (name.endsWith("_LOG") || name.endsWith("_WOOD")
                || name.endsWith("_STEM") || name.endsWith("_HYPHAE")
                || material == Material.BAMBOO_BLOCK || material == Material.STRIPPED_BAMBOO_BLOCK) {
            return 4;
        }
        // 板材系
        if (name.endsWith("_PLANKS")) {
            return 1;
        }
        return 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return stack != null && unitValue(stack.getType()) > 0;
    }

    public static double valueOf(ItemStack stack) {
        if (stack == null) {
            return 0;
        }
        return unitValue(stack.getType()) * stack.getAmount();
    }
}
