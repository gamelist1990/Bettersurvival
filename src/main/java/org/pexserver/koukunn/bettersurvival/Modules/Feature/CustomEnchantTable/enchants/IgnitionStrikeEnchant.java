package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 点火の一撃 (ignitionstrike) — 火属性の派生。
 *
 * 剣・斧に付与すると攻撃時に対象を燃やす。
 * Lv1=1秒 / Lv2=3秒 / Lv3=5秒。
 * バニラの火属性IIが4秒なので、Lv3=5秒は同程度の位置づけ。
 * 既に燃焼中の対象は残時間が短い場合のみ延長する。
 */
public class IgnitionStrikeEnchant extends CustomEnchant {

    public IgnitionStrikeEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "ignitionstrike";
    }

    @Override
    public String displayName() {
        return "点火の一撃";
    }

    @Override
    public String description() {
        return "\u00a77攻撃時に対象を燃やす"
                + "\n\u00a77Lv1=1秒 / Lv2=3秒 / Lv3=5秒";
    }

    @Override
    public Material icon() {
        return Material.FLINT_AND_STEEL;
    }

    @Override
    public String vanillaParentName() {
        return "火属性";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 点火の一撃: 火打ち石・ブレイズパウダー・マグマクリーム・ファイヤーチャージ
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.FLINT_AND_STEEL, 1),
                    new ItemStack(Material.BLAZE_POWDER, 8),
                    new ItemStack(Material.COAL, 16));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.BLAZE_POWDER, 16),
                    new ItemStack(Material.MAGMA_CREAM, 8),
                    new ItemStack(Material.FIRE_CHARGE, 4),
                    new ItemStack(Material.GOLD_INGOT, 4));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.BLAZE_ROD, 4),
                    new ItemStack(Material.MAGMA_CREAM, 16),
                    new ItemStack(Material.FIRE_CHARGE, 8),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        Entity target = event.getEntity();
        int level = levelOf(attacker.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        int ticks = switch (level) {
            case 1 -> 20;   // 1秒
            case 2 -> 60;   // 3秒
            default -> 100; // 5秒
        };
        if (target.getFireTicks() < ticks) {
            target.setFireTicks(ticks);
        }
    }
}
