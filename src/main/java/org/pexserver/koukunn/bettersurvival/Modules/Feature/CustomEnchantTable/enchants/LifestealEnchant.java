package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 吸血 (lifesteal)。
 *
 * 剣・斧に付与すると敵に与えたダメージの一部を体力として吸収する。
 * Lv1=5% / Lv2=10% / Lv3=15%。
 * プレイヤー間PvPでは効果を半減 (バランス保護)。
 * 実際に減ったHPぶんのみ吸収し、オーバーヒール・アーマー貫通はしない。
 */
public class LifestealEnchant extends CustomEnchant {

    public LifestealEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "lifesteal";
    }

    @Override
    public String displayName() {
        return "吸血";
    }

    @Override
    public String description() {
        return "\u00a77与えたダメージの一部を体力として吸収する"
                + "\n\u00a77Lv1=5% / Lv2=10% / Lv3=15%"
                + "\n\u00a77対プレイヤーでは効果半減";
    }

    @Override
    public Material icon() {
        return Material.GHAST_TEAR;
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
        // 吸血: ガストの涙・レッドストーン・肉・血玉 (レッドマッシュルーム)
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.GHAST_TEAR, 2),
                    new ItemStack(Material.ROTTEN_FLESH, 16),
                    new ItemStack(Material.REDSTONE, 16));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.GHAST_TEAR, 4),
                    new ItemStack(Material.REDSTONE_BLOCK, 4),
                    new ItemStack(Material.RED_MUSHROOM, 16),
                    new ItemStack(Material.GOLDEN_APPLE, 1));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.GHAST_TEAR, 8),
                    new ItemStack(Material.NETHERITE_SCRAP, 1),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        int level = levelOf(attacker.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        // 実ダメージ (final damage) をベースに吸収 (アーマー等で吸収された分は除外済み)
        double dealt = event.getFinalDamage();
        if (dealt <= 0.0D) {
            return;
        }
        double ratio = level * 0.05D;
        if (event.getEntity() instanceof Player) {
            ratio *= 0.5D; // PvPでは半減
        }
        double heal = dealt * ratio;
        if (heal <= 0.0D) {
            return;
        }
        AttributeInstance maxHp = attacker.getAttribute(Attribute.MAX_HEALTH);
        double cap = maxHp == null ? 20.0D : maxHp.getValue();
        double newHp = Math.min(cap, attacker.getHealth() + heal);
        attacker.setHealth(newHp);
    }
}
