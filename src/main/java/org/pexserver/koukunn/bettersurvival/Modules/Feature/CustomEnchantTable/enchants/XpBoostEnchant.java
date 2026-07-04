package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 経験値ブースト (xpboost) — 幸運の派生。
 *
 * ブロック採掘で得られる経験値がレベルごとに +50% 増える
 * (Lv3 で 2.5倍)。もともと経験値の出ないブロックには影響しない。
 */
public class XpBoostEnchant extends CustomEnchant {

    private static final double BONUS_PER_LEVEL = 0.5D;

    public XpBoostEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "xpboost";
    }

    @Override
    public String displayName() {
        return "経験値ブースト";
    }

    @Override
    public String description() {
        return "§7採掘で得られる経験値が増える"
                + "\n§7(1レベルごとに +50%)"
                + "\n§7経験値の出ないブロックには無効";
    }

    @Override
    public Material icon() {
        return Material.EXPERIENCE_BOTTLE;
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
        return isMiningTool(type);
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.EMERALD, 4));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.EMERALD, 8));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.EMERALD, 16));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        int exp = event.getExpToDrop();
        if (exp <= 0) {
            return;
        }
        int level = levelOf(event.getPlayer().getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        event.setExpToDrop(exp + (int) Math.ceil(exp * BONUS_PER_LEVEL * level));
    }
}
