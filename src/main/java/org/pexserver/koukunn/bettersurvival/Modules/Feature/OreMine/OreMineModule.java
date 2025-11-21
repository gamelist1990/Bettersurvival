package org.pexserver.koukunn.bettersurvival.Modules.Feature.OreMine;

 
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.*;

/**
 * OreMine: 鉱石（一括破壊）
 */
public class OreMineModule implements Listener {

    private final ToggleModule toggle;

    public OreMineModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();
        Block b = e.getBlock();

        // 対象: 鉱石（名前が _ORE で終わるもの）
        String typeName = b.getType().name();
        if (!typeName.endsWith("_ORE")) return;

        // 条件: プレイヤーがツルハシを持っていて、シフト（スニーク）していること
        if (!p.isSneaking()) return;
        ItemStack main = p.getInventory().getItemInMainHand();
        if (main == null || main.getType() == Material.AIR) return;
        String name = main.getType().name();
        if (!name.endsWith("_PICKAXE")) return;

        String key = "oremine";

        if (!toggle.getGlobal(key)) return;
        if (!toggle.isEnabledFor(p.getUniqueId().toString(), key)) return;

        Set<Block> toBreak = collectOreBlocks(b);
        if (toBreak.isEmpty()) return;

        // Activation sound
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.0f);

        for (Block block : toBreak) {
            p.getWorld().playSound(block.getLocation(), Sound.BLOCK_STONE_BREAK, 0.8f, 1.0f);
            block.breakNaturally();
        }

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
    }

    private Set<Block> collectOreBlocks(Block start) {
        Set<Block> found = new HashSet<>();
        LinkedList<Block> queue = new LinkedList<>();
        queue.add(start);
        found.add(start);

        int max = 256;

        while (!queue.isEmpty() && found.size() < max) {
            Block b = queue.removeFirst();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = b.getRelative(dx, dy, dz);
                        if (!found.contains(nb) && nb.getType().name().endsWith("_ORE")) {
                            found.add(nb);
                            queue.add(nb);
                        }
                    }
                }
            }
        }

        return found;
    }
}
