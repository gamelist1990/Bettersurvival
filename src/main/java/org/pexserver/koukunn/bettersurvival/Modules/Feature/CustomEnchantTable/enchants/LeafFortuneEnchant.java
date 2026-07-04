package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * リーフ・フォーチュン (leaffortune) — 幸運の派生。
 *
 * ハサミに付与すると葉ブロック破壊時の追加ドロップを増やす。
 * 1レベルごとに各ドロップアイテムが2倍になる確率が増加する。
 * Lv1=25% / Lv2=50% / Lv3=100%。
 */
public class LeafFortuneEnchant extends CustomEnchant {

    public LeafFortuneEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "leaffortune";
    }

    @Override
    public String displayName() {
        return "リーフ・フォーチュン";
    }

    @Override
    public String description() {
        return "\u00a77葉ブロック破壊時のドロップ品が増える"
                + "\n\u00a77Lv1=各ドロップ25%で2倍 / Lv2=50% / Lv3=100%";
    }

    @Override
    public Material icon() {
        return Material.OAK_LEAVES;
    }

    @Override
    public String vanillaParentName() {
        return "幸運";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.SHEARS;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // リーフフォーチュン: 苗木・リンゴ・クモの巣・ハニカム・金のリンゴ
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.OAK_SAPLING, 16),
                    new ItemStack(Material.APPLE, 8),
                    new ItemStack(Material.COBWEB, 4));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.JUNGLE_SAPLING, 16),
                    new ItemStack(Material.APPLE, 16),
                    new ItemStack(Material.HONEYCOMB, 8),
                    new ItemStack(Material.EMERALD, 2));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.DARK_OAK_SAPLING, 16),
                    new ItemStack(Material.GOLDEN_APPLE, 2),
                    new ItemStack(Material.HONEYCOMB, 16),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockDrop(BlockDropItemEvent event) {
        Block block = event.getBlock();
        if (!block.getType().name().endsWith("_LEAVES")) {
            return;
        }
        Player player = event.getPlayer();
        int level = levelOf(player.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        double chance = level * 0.25D;
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        event.getItems().forEach(item -> {
            if (rng.nextDouble() < chance) {
                ItemStack copy = item.getItemStack().clone();
                copy.setAmount(copy.getAmount());
                item.getItemStack().setAmount(item.getItemStack().getAmount() * 2);
            }
        });
    }
}
