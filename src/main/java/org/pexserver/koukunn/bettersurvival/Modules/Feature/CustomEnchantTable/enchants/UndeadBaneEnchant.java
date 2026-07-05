package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Husk;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Stray;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.Zoglin;
import org.bukkit.entity.Zombie;
import org.bukkit.entity.ZombieVillager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 不死特効 (undeadbane) — アンデッド特効の派生。
 *
 * 剣・斧に付与するとアンデッド系Mobへのダメージを増加させる。
 * Lv1=+15% / Lv2=+30% / Lv3=+45%。
 * バニラのアンデッド特効IV相当の追加ダメージ倍率を超えないよう控えめに設定。
 */
public class UndeadBaneEnchant extends CustomEnchant {

    public UndeadBaneEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "undeadbane";
    }

    @Override
    public String displayName() {
        return "不死特効";
    }

    @Override
    public String description() {
        return "\u00a77アンデッドMobへのダメージを増加させる"
                + "\n\u00a77Lv1=+15% / Lv2=+30% / Lv3=+45%"
                + "\n\u00a77対象: ゾンビ / スケルトン / ファントム 等";
    }

    @Override
    public Material icon() {
        return Material.ROTTEN_FLESH;
    }

    @Override
    public String vanillaParentName() {
        return "アンデッド特効";
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
        // 不死特効: 腐肉・骨・ソウルサンド・ウィザースケルトンの頭骨
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.ROTTEN_FLESH, 24),
                    new ItemStack(Material.BONE, 16),
                    new ItemStack(Material.IRON_INGOT, 4));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.ROTTEN_FLESH, 48),
                    new ItemStack(Material.SOUL_SAND, 8),
                    new ItemStack(Material.BONE_BLOCK, 4),
                    new ItemStack(Material.GOLD_INGOT, 4));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.SOUL_SOIL, 8),
                    new ItemStack(Material.WITHER_SKELETON_SKULL, 1),
                    new ItemStack(Material.BONE_BLOCK, 8),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    private boolean isUndead(Entity entity) {
        return entity instanceof Zombie
                || entity instanceof ZombieVillager
                || entity instanceof Husk
                || entity instanceof Drowned
                || entity instanceof PigZombie
                || entity instanceof Skeleton
                || entity instanceof WitherSkeleton
                || entity instanceof Stray
                || entity instanceof Phantom
                || entity instanceof Zoglin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!isUndead(event.getEntity())) {
            return;
        }
        int level = levelOf(attacker.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        double bonus = 1.0D + (level * 0.15D);
        event.setDamage(event.getDamage() * bonus);
    }
}
