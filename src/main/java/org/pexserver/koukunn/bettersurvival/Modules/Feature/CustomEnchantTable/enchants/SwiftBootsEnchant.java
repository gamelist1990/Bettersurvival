package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 俊足のブーツ (swiftboots)。
 *
 * ブーツに付与するとレベルに応じた移動速度上昇効果を付与する。
 * Lv1=スピードI / Lv2=スピードII。
 * ぶっ壊れを避けるため最大Lv2にとどめる (バニラのスピードポーションI相当まで)。
 */
public class SwiftBootsEnchant extends CustomEnchant {

    private static final int REFRESH_TICKS = 40;

    private final BukkitTask task;

    public SwiftBootsEnchant(Loader plugin) {
        super(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    @Override
    public String id() {
        return "swiftboots";
    }

    @Override
    public String displayName() {
        return "俊足のブーツ";
    }

    @Override
    public String description() {
        return "\u00a77ブーツ装備中、移動速度を上げる"
                + "\n\u00a77Lv1=スピードI / Lv2=スピードII";
    }

    @Override
    public Material icon() {
        return Material.SUGAR;
    }

    @Override
    public int maxLevel() {
        return 2;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_BOOTS");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 俊足のブーツ: 砂糖・レッドストーン・革・馬鎧など
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.SUGAR, 16),
                    new ItemStack(Material.RABBIT_HIDE, 8),
                    new ItemStack(Material.REDSTONE, 16));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.SUGAR, 32),
                    new ItemStack(Material.RABBIT_FOOT, 2),
                    new ItemStack(Material.REDSTONE_BLOCK, 4),
                    new ItemStack(Material.GOLD_INGOT, 4));
        };
    }

    @Override
    public void shutdown() {
        task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            int level = levelOf(player.getInventory().getBoots());
            if (level > 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.SPEED, REFRESH_TICKS + 20, level - 1, false, false, true));
            }
        }
    }
}
