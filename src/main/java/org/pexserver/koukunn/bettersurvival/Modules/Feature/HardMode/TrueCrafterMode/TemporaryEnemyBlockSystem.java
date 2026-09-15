package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Monster;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** 元データのgimmick.enemy_blockと同じ足場の材質選択と消滅時間を扱う。 */
public final class TemporaryEnemyBlockSystem {
    private final Map<Location, Entry> blocks = new HashMap<>();
    private final BukkitTask task;

    public TemporaryEnemyBlockSystem(Loader plugin) {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void place(Block block) {
        Material material = materialFor(block);
        block.setType(material, true);
        float volume = material == Material.WARPED_HYPHAE ? 1.5F : 1.0F;
        float pitch = material == Material.WARPED_HYPHAE ? 1.0F : 0.8F;
        block.getWorld().playSound(block.getLocation(), placementSound(material), volume, pitch);
        blocks.put(block.getLocation(), new Entry(material));
    }

    public boolean isTemporary(Block block) {
        Entry entry = blocks.get(block.getLocation());
        return entry != null && block.getType() == entry.material;
    }

    public void shutdown() {
        task.cancel();
    }

    private void tick() {
        Iterator<Map.Entry<Location, Entry>> iterator = blocks.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Location, Entry> tracked = iterator.next();
            Location location = tracked.getKey();
            Block block = location.getBlock();
            if (block.getType() != tracked.getValue().material) {
                iterator.remove();
                continue;
            }
            boolean occupied = location.clone().add(0.5, 1.0, 0.5).getNearbyEntities(1.0, 1.0, 1.0)
                    .stream().anyMatch(entity -> entity instanceof Monster
                            || entity instanceof org.bukkit.entity.Wither
                            || entity instanceof org.bukkit.entity.EnderDragon);
            if (occupied) continue;
            tracked.getValue().emptyTicks++;
            if (tracked.getValue().emptyTicks < 60) continue;
            // datapack の setblock ... air destroy と同じく、足場のドロップを発生させる。
            block.breakNaturally();
            iterator.remove();
        }
    }

    private Material materialFor(Block block) {
        String biome = block.getBiome().getKey().getKey();
        return switch (biome) {
            case "desert" -> Material.SMOOTH_SANDSTONE;
            case "crimson_forest" -> Material.CRIMSON_HYPHAE;
            case "warped_forest" -> Material.WARPED_HYPHAE;
            case "nether_wastes" -> Material.NETHERRACK;
            case "soul_sand_valley" -> Material.SOUL_SOIL;
            case "basalt_deltas" -> Material.SMOOTH_BASALT;
            default -> Material.MOSSY_COBBLESTONE;
        };
    }

    private Sound placementSound(Material material) {
        if (material == Material.CRIMSON_HYPHAE || material == Material.SOUL_SOIL) return Sound.BLOCK_STEM_PLACE;
        if (material == Material.NETHERRACK) return Sound.BLOCK_NETHERRACK_PLACE;
        if (material == Material.SMOOTH_BASALT) return Sound.BLOCK_BASALT_PLACE;
        return Sound.BLOCK_STONE_PLACE;
    }

    private static final class Entry {
        private final Material material;
        private int emptyTicks;

        private Entry(Material material) {
            this.material = material;
        }
    }
}
