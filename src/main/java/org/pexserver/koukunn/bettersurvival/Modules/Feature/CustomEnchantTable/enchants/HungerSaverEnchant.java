package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 満腹維持 (hungersaver)。
 *
 * チェストプレートに付与すると空腹の消費を軽減する。
 * Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減。
 * 空腹回復イベント (満腹値の上昇) には影響しない。
 */
public class HungerSaverEnchant extends CustomEnchant {

    public HungerSaverEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "hungersaver";
    }

    @Override
    public String displayName() {
        return "満腹維持";
    }

    @Override
    public String description() {
        return "\u00a77チェストプレート装備中、空腹の消費を軽減する"
                + "\n\u00a77Lv1=15%軽減 / Lv2=25%軽減 / Lv3=35%軽減";
    }

    @Override
    public Material icon() {
        return Material.BREAD;
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_CHESTPLATE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.BREAD, 16),
                    new ItemStack(Material.WHEAT, 32),
                    new ItemStack(Material.APPLE, 4));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.COOKED_BEEF, 16),
                    new ItemStack(Material.SUGAR, 16),
                    new ItemStack(Material.HAY_BLOCK, 4),
                    new ItemStack(Material.GOLDEN_CARROT, 4));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.GOLDEN_CARROT, 16),
                    new ItemStack(Material.CAKE, 2),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFoodChange(FoodLevelChangeEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int current = player.getFoodLevel();
        int next = event.getFoodLevel();
        int delta = current - next;
        // 空腹値が減る場合のみ軽減対象 (回復には介入しない)
        if (delta <= 0) {
            return;
        }
        int level = levelOf(player.getInventory().getChestplate());
        if (level <= 0) {
            return;
        }
        // Lv1=15% / Lv2=25% / Lv3=35% 軽減 (0.15 + 0.10 * (level-1))
        double reduction = 0.15D + 0.10D * (level - 1);
        // 減少量を軽減。四捨五入で最低1は減らす (完全無効化はしない)
        int reducedDelta = (int) Math.max(1L, Math.round(delta * (1.0D - reduction)));
        if (reducedDelta >= delta) {
            return;
        }
        event.setFoodLevel(current - reducedDelta);
    }
}
