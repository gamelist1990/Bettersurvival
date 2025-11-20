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

        // 対象: 木材 (LOGS タグ)
        if (!Tag.LOGS.isTagged(b.getType())) return;

        // 条件: プレイヤーが斧を持っていて、シフト（スニーク）していること
        if (!p.isSneaking()) return;
        org.bukkit.inventory.ItemStack main = p.getInventory().getItemInMainHand();
        if (main == null || main.getType() == org.bukkit.Material.AIR) return;
        org.bukkit.Material mt = main.getType();
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

        if (!hasAdjacentLeaves(toBreak)) {
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
                        if (!found.contains(nb) && Tag.LOGS.isTagged(nb.getType())) {
                            found.add(nb);
                            queue.add(nb);
                        }
                    }
                }
            }
        }

        return found;
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
