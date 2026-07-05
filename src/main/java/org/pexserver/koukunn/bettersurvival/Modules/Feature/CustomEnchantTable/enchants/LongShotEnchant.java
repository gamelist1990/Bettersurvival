package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 狙撃 (longshot)。
 *
 * 弓またはクロスボウの矢速を上げ、遠距離の敵へのダメージを増やす。
 * エンチャントレベルを矢の PersistentDataContainer に記録し、
 * ダメージイベントで射手との距離を計算してボーナスを適用する。
 *
 * 8m 未満の近距離ではボーナスなし、8m以上で半ボーナス、16m以上で全ボーナス。
 * Lv1: 矢速1.2倍/+15% / Lv2: 矢速1.4倍/+30% / Lv3: 矢速1.6倍/+50%
 */
public class LongShotEnchant extends CustomEnchant {

    public LongShotEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "longshot";
    }

    @Override
    public String displayName() {
        return "狙撃";
    }

    @Override
    public String description() {
        return "§7矢の速度を上げ、遠距離ほどダメージが増加する"
                + "\n§78m未満では効果なし、16m以上で最大ボーナス"
                + "\n§7Lv1: 速度1.2倍/+15% / Lv2: 1.4倍/+30% / Lv3: 1.6倍/+50%";
    }

    @Override
    public Material icon() {
        return Material.SPYGLASS;
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 24), new ItemStack(Material.ARROW, 32));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 40), new ItemStack(Material.ENDER_PEARL, 4));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 56), new ItemStack(Material.ENDER_PEARL, 8));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            return;
        }
        int level = levelOf(bow);
        if (level <= 0) {
            return;
        }
        event.getProjectile().getPersistentDataContainer().set(key(), PersistentDataType.INTEGER, level);
        double multiplier = 1.0 + (level * 0.2);
        event.getProjectile().setVelocity(event.getProjectile().getVelocity().multiply(multiplier));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }
        Integer level = projectile.getPersistentDataContainer().get(key(), PersistentDataType.INTEGER);
        if (level == null || level <= 0) {
            return;
        }
        double distance = shooter.getLocation().distance(event.getEntity().getLocation());
        double bonus = bonusFor(level, distance);
        if (bonus <= 0.0) {
            return;
        }
        event.setDamage(event.getDamage() * (1.0 + bonus));
    }

    private double bonusFor(int level, double distance) {
        if (distance < 8.0) {
            return 0.0;
        }
        double full = switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> 0.15;
            case 2 -> 0.30;
            default -> 0.50;
        };
        return distance >= 16.0 ? full : full * 0.5;
    }
}
