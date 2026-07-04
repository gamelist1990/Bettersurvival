package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.Collection;
import java.util.List;

/**
 * 自動回収 (autocollect)。
 *
 * 掘ったブロックのドロップ品を直接インベントリへ入れる。
 * 満杯で入りきらない分だけ従来どおり足元にドロップする。
 * 範囲採掘の合成イベントにも作用するため、範囲破壊分もまとめて回収される。
 */
public class AutoCollectEnchant extends CustomEnchant {

    public AutoCollectEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "autocollect";
    }

    @Override
    public String displayName() {
        return "自動回収";
    }

    @Override
    public String description() {
        return "§7掘ったブロックのドロップ品を"
                + "\n§7そのままインベントリに入れる"
                + "\n§7入りきらない分は足元にドロップ";
    }

    @Override
    public Material icon() {
        return Material.ENDER_EYE;
    }

    @Override
    public String vanillaParentName() {
        return "シルクタッチ";
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return isMiningTool(type);
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.ENDER_PEARL, 4));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        // 他機能が既にドロップを差し替えている場合 (チャンクローダー等) は触らない
        if (!event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (levelOf(tool) <= 0) {
            return;
        }
        Collection<ItemStack> drops = event.getBlock().getDrops(tool, player);
        if (drops.isEmpty()) {
            return;
        }
        event.setDropItems(false);
        boolean overflow = false;
        for (ItemStack drop : drops) {
            for (ItemStack leftover : player.getInventory().addItem(drop).values()) {
                event.getBlock().getWorld().dropItemNaturally(
                        event.getBlock().getLocation().add(0.5D, 0.3D, 0.5D), leftover);
                overflow = true;
            }
        }
        if (!overflow) {
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.25F, 1.6F);
        }
    }
}
