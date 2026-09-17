package org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine;

import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.*;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;

public class TreeMineModule implements Listener {

    private final ToggleModule toggle;

    public TreeMineModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();
        Block b = e.getBlock();

        // 対象: 通常の原木とネザーの幹・菌糸
        if (!isTreeWood(b)) return;

        // 条件: プレイヤーが斧を持っていて、シフト（スニーク）していること
        if (!p.isSneaking()) return;
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main == null || main.getType() == Material.AIR) return;
        Material mt = main.getType();
        // 斧の性質で判定: Material 名が "_AXE" で終わるかどうか
        String name = mt.name();
        boolean isAxe = name.endsWith("_AXE");
        if (!isAxe) return;

        String key = "treemine";

        // グローバルに無効であれば終了
        if (!toggle.getGlobal(key)) return;

        // ユーザーが無効なら終了
        if (!toggle.isEnabledFor(p.getUniqueId().toString(), key)) return;

        Set<Block> toBreak = collectTreeBlocks(b);

        if (!isNetherTree(b) && !hasAdjacentLeaves(toBreak)) {
            // 葉と隣接していない場合は木ではないとみなすじゃないと荒らしに悪用される
            return;
        }

        if (toBreak.isEmpty()) return;

        // もともとのイベントをキャンセルし、明示的に壊す（ドロップを行う）
        e.setCancelled(true);

        // 再生: アクティベート音
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.0f);

        for (Block block : toBreak) {
            // 木材破壊音を再生してから壊す
            p.getWorld().playSound(block.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.8f, 1.0f);
            block.breakNaturally();
        }

        // 最後に回収音で完了フィードバック
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    private Set<Block> collectTreeBlocks(Block start) {
        Set<Block> found = new HashSet<>();
        LinkedList<Block> queue = new LinkedList<>();
        queue.add(start);
        found.add(start);

        int max = 256; // 保険で数の上限

        while (!queue.isEmpty() && found.size() < max) {
            Block b = queue.removeFirst();

            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        if (!found.contains(nb) && isTreeWood(nb)) {
                            found.add(nb);
                            queue.add(nb);
                        }
                    }
                }
            }
        }

        return found;
    }

    private boolean isTreeWood(Block block) {
        Material material = block.getType();
        return Tag.LOGS.isTagged(material)
                || Tag.CRIMSON_STEMS.isTagged(material)
                || Tag.WARPED_STEMS.isTagged(material);
    }

    private boolean isNetherTree(Block block) {
        Material material = block.getType();
        return Tag.CRIMSON_STEMS.isTagged(material) || Tag.WARPED_STEMS.isTagged(material);
    }

    /**
     * 指定したログ集合のいずれかに葉（LEAVES）が隣接しているかを判定
     */
    private boolean hasAdjacentLeaves(Set<Block> logs) {
        for (Block log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = log.getRelative(dx, dy, dz);
                        if (Tag.LEAVES.isTagged(neighbor.getType())) return true;
                    }
                }
            }
        }
        return false;
    }
}
