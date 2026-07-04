package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.Set;

/**
 * 耐火のブーツ (fireproofboots) — 火炎耐性の派生。
 *
 * ブーツに付与するとマグマブロック等の高温床ダメージを無効化し、
 * さらにすべての炎・溶岩系ダメージをレベルに応じて軽減する。
 * Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減。
 */
public class FireproofBootsEnchant extends CustomEnchant {

    @SuppressWarnings("deprecation")
    private static final Set<EntityDamageEvent.DamageCause> FIRE_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.FIRE,
            EntityDamageEvent.DamageCause.FIRE_TICK,
            EntityDamageEvent.DamageCause.LAVA,
            EntityDamageEvent.DamageCause.HOT_FLOOR);

    public FireproofBootsEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "fireproofboots";
    }

    @Override
    public String displayName() {
        return "耐火のブーツ";
    }

    @Override
    public String description() {
        return "\u00a77マグマブロック等の高温床ダメージを無効化"
                + "\n\u00a77炎・溶岩ダメージを軽減する"
                + "\n\u00a77Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減";
    }

    @Override
    public Material icon() {
        return Material.MAGMA_BLOCK;
    }

    @Override
    public String vanillaParentName() {
        return "火炎耐性";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_BOOTS");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 耐火のブーツ: マグマクリーム・ファイヤーチャージ・ブレイズパウダー・ブレイズロッド・ネザーレンガ・ネザライトスクラップ
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.MAGMA_CREAM, 8),
                    new ItemStack(Material.FIRE_CHARGE, 4),
                    new ItemStack(Material.BLAZE_POWDER, 8));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.MAGMA_CREAM, 16),
                    new ItemStack(Material.BLAZE_ROD, 4),
                    new ItemStack(Material.NETHER_BRICK, 16),
                    new ItemStack(Material.FIRE_CHARGE, 8));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.MAGMA_CREAM, 32),
                    new ItemStack(Material.BLAZE_ROD, 8),
                    new ItemStack(Material.NETHERITE_SCRAP, 2),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        EntityDamageEvent.DamageCause cause = event.getCause();
        if (!FIRE_CAUSES.contains(cause)) {
            return;
        }
        int level = levelOf(player.getInventory().getBoots());
        if (level <= 0) {
            return;
        }
        if (cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
            return;
        }
        double reduction = level * 0.25D;
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }
}
