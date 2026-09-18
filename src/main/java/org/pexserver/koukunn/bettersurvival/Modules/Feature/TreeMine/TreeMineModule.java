package org.pexserver.koukunn.bettersurvival.Modules.Feature.TreeMine;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * TreeMine: 木を一括伐採する。
 * Shift + 斧で発動し、OreMine と同様に中心から外側へ段階的に破壊する。
 * 土地保護エリア内のブロックは、バイパス権限がない限り破壊しない。
 */
public class TreeMineModule implements Listener {

    private static final String KEY = "treemine";
    private final Loader plugin;
    private final ToggleModule toggle;

    public TreeMineModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Block b = e.getBlock();

        if (!isTreeWood(b)) return;
        if (!p.isSneaking()) return;

        ItemStack main = p.getInventory().getItemInMainHand();
        if (main == null || main.getType() == Material.AIR) return;
        if (!main.getType().name().endsWith("_AXE")) return;

        if (!toggle.getGlobal(KEY)) return;
        if (!toggle.isEnabledFor(p.getUniqueId().toString(), KEY)) return;

        LandProtectionModule lp = plugin.getLandProtectionModule();
        if (lp != null) {
            ClaimRegion startClaim = lp.getActiveClaimAt(b.getLocation());
            if (startClaim != null && startClaim.getSettings().isBlockBreak() && !lp.canBypass(p, startClaim)) {
                return;
            }
        }

        Set<Block> toBreak = collectTreeBlocks(b);
        if (!isNetherTree(b) && !hasAdjacentLeaves(toBreak)) return;
        if (toBreak.isEmpty()) return;

        final Block center = b;
        List<Block> sorted = new ArrayList<>(toBreak);
        sorted.sort(Comparator.comparingDouble(bl -> {
            double dx = bl.getX() - center.getX();
            double dy = bl.getY() - center.getY();
            double dz = bl.getZ() - center.getZ();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double angle = Math.atan2(dz, dx);
            if (angle < 0) angle += 2.0 * Math.PI;
            return dist * 2.0 * Math.PI + angle;
        }));

        String itemName = ItemNameUtil.localizedPlainText(main, p);
        p.sendActionBar(Component.text("🪓 " + itemName + " で一括伐採: " + sorted.size() + "ブロック"));
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.6f, 1.0f);

        ItemStack tool = main.clone();
        int perTick = Math.max(1, sorted.size() / 20 + 1);
        int[] idx = {0};

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!p.isOnline()) {
                task.cancel();
                return;
            }

            for (int i = 0; i < perTick && idx[0] < sorted.size(); i++, idx[0]++) {
                Block bl = sorted.get(idx[0]);
                if (bl.getType() == Material.AIR) continue;
                if (!isTreeWood(bl)) continue;

                if (lp != null) {
                    ClaimRegion claim = lp.getActiveClaimAt(bl.getLocation());
                    if (claim != null && claim.getSettings().isBlockBreak() && !lp.canBypass(p, claim)) {
                        continue;
                    }
                }

                bl.getWorld().playSound(bl.getLocation(), Sound.BLOCK_WOOD_BREAK, 0.8f, 1.0f);
                bl.breakNaturally(tool);
            }

            if (idx[0] >= sorted.size()) {
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
                task.cancel();
            }
        }, 0L, 1L);
    }

    private Set<Block> collectTreeBlocks(Block start) {
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
     * 指定したログ集合のいずれかに葉が隣接しているかを判定する。
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
