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
 * 跳躍 (jumpboost)。
 *
 * ブーツに付与するとレベルに応じたジャンプ力上昇効果を付与する。
 * Lv1=跳躍I / Lv2=跳躍II / Lv3=跳躍III。
 * バニラの跳躍ポーションと同等の効果で、落下ダメージにも通常通り関係する。
 */
public class JumpBoostBootsEnchant extends CustomEnchant {

    private static final int REFRESH_TICKS = 40;

    private final BukkitTask task;

    public JumpBoostBootsEnchant(Loader plugin) {
        super(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    @Override
    public String id() {
        return "jumpboost";
    }

    @Override
    public String displayName() {
        return "跳躍";
    }

    @Override
    public String description() {
        return "\u00a77ブーツ装備中、ジャンプ力を上げる"
                + "\n\u00a77Lv1=跳躍I / Lv2=跳躍II / Lv3=跳躍III";
    }

    @Override
    public Material icon() {
        return Material.RABBIT_FOOT;
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
        // 跳躍: ウサギの足・スライムボール・火薬・金・蜜ブロック
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.RABBIT_FOOT, 2),
                    new ItemStack(Material.SLIME_BALL, 8),
                    new ItemStack(Material.SUGAR, 16));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.RABBIT_FOOT, 4),
                    new ItemStack(Material.GUNPOWDER, 16),
                    new ItemStack(Material.HONEY_BLOCK, 4),
                    new ItemStack(Material.GOLD_INGOT, 8));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.RABBIT_FOOT, 8),
                    new ItemStack(Material.SLIME_BLOCK, 4),
                    new ItemStack(Material.PHANTOM_MEMBRANE, 4),
                    new ItemStack(Material.EMERALD, 4));
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
                        PotionEffectType.JUMP_BOOST, REFRESH_TICKS + 20, level - 1, false, false, true));
            }
        }
    }
}
