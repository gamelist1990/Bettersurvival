package org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
 
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.logging.Level;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.HashMap;
import java.util.Map;

/**
 * AutoPlant: オフハンドに植えたいアイテムを持ちながら既に耕している土の近くに行くと
 * 自動で田植え(種の植え付け)と100%育った作物の自動収穫を行う機能
 */
public class AutoPlantModule implements Listener {

    private final ToggleModule toggle;
    private final int radius = 2;
    // no cooldown — run every scheduled interval
    private final Plugin plugin;
    @SuppressWarnings("unused")
    private org.bukkit.scheduler.BukkitTask task;

    private static final Map<Material, Material> seedToCrop = new HashMap<>();

    static {
        // Auto-generate seed->crop mapping by scanning Materials for ageable block types.
        for (Material mat : Material.values()) {
            String name = mat.name();
            // pick candidate seed items: "*_SEEDS" or specific items like CARROT/POTATO
                if (!name.endsWith("_SEEDS") && !name.endsWith("_SEED")
                    && !name.equals("CARROT") && !name.equals("POTATO"))
                continue;

            Material crop = findCropForSeed(mat);
            if (crop != null) {
                seedToCrop.put(mat, crop);
            }
        }

        // Manual overrides for tricky cases (ensure reliable mapping)
        try { seedToCrop.put(Material.WHEAT_SEEDS, Material.WHEAT); } catch (Throwable ignored) {}
        try { seedToCrop.put(Material.BEETROOT_SEEDS, Material.BEETROOTS); } catch (Throwable ignored) {}
    }

    private static Material findCropForSeed(Material seed) {
        String base = seed.name();
        base = base.replaceFirst("_SEEDS?$", "");

        // 1) exact match
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            try {
                if (!(m.createBlockData() instanceof Ageable)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            if (m.name().equals(base)) return m;
        }

        // 2) try common suffixes
        String[] suffixes = {"S", "_CROP", "S_CROPS", "_STEM", "_PLANT", "_BUSH"};
        for (String sfx : suffixes) {
            try {
                Material m = Material.valueOf(base + sfx);
                if (m.isBlock()) {
                    try {
                        if (m.createBlockData() instanceof Ageable) return m;
                    } catch (Throwable ignored) {}
                }
            } catch (IllegalArgumentException ignored) {}
        }

        // 3) fallback: find first ageable block that contains base substring
        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            try {
                if (!(m.createBlockData() instanceof Ageable)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            if (m.name().contains(base)) return m;
        }

        return null;
    }

    public AutoPlantModule(ToggleModule toggle) {
        this.toggle = toggle;
        // Schedule periodic check for players
        this.plugin = Loader.getPlugin(Loader.class);
        if (this.plugin != null) this.plugin.getLogger().info("AutoPlant scheduled");
        long intervalTicks = 20; // 1 second
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                runIntervalTask();
            } catch (Throwable t) {
                if (plugin != null) plugin.getLogger().log(Level.WARNING, "AutoPlant task error", t);
            }
        }, 0L, intervalTicks);
    }
    private void runIntervalTask() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            runForPlayer(player);
        }
    }

    private void runForPlayer(Player player) {
        // Toggle are respected
        if (!toggle.getGlobal("autoplant")) return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), "autoplant")) return;

        ItemStack off = player.getInventory().getItemInOffHand();
        if (off == null) return;
        Material mat = off.getType();
        if (!seedToCrop.containsKey(mat)) return;

        // no cooldown — every interval this is checked


        // search nearby farmland blocks (use player's feet as center and allow 2-block vertical tolerance)
        Block center = player.getLocation().getBlock();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                // farmland vertical tolerance: check from -2 to +2 relative to this block
                Block below = null;
                boolean found = false;
                for (int yOff = -2; yOff <= 2; yOff++) {
                    Block cand = b.getRelative(0, yOff, 0);
                    if (cand == null) continue;
                    if (cand.getType() == Material.FARMLAND) {
                        below = cand;
                        found = true;
                        break;
                    }
                }
                if (!found) continue;

                // crop block above farmland
                Block above = below.getRelative(BlockFace.UP);

                // If nothing planted -> plant
                if (above.getType() == Material.AIR) {
                    Material crop = seedToCrop.get(mat);
                    // Plant the crop (age 0) and ensure Ageable stage
                    try {
                        above.setType(crop);
                        if (above.getBlockData() instanceof Ageable) {
                            Ageable a = (Ageable) above.getBlockData();
                            a.setAge(0);
                            above.setBlockData(a, true);
                        }
                       
                    } catch (Throwable t) {
                        if (plugin != null) plugin.getLogger().warning("AutoPlant: failed to plant " + t.getMessage());
                    }
                    consumeOffHand(player);
                    continue;
                }

                // If crop and is fully grown -> harvest and replant
                try {
                    if (above.getBlockData() instanceof Ageable) {
                        Ageable age = (Ageable) above.getBlockData();
                        if (age.getAge() >= age.getMaximumAge()) {
                            // harvest -> this drops naturally
                            above.breakNaturally();
                            // try to replant if player still has seed
                            ItemStack newOff = player.getInventory().getItemInOffHand();
                            if (newOff != null && seedToCrop.containsKey(newOff.getType())) {
                                Material crop = seedToCrop.get(newOff.getType());
                                try {
                                    above.setType(crop);
                                    if (above.getBlockData() instanceof Ageable) {
                                        Ageable a2 = (Ageable) above.getBlockData();
                                        a2.setAge(0);
                                        above.setBlockData(a2, true);
                                    }
                                } catch (Throwable t) {
                                    if (plugin != null) plugin.getLogger().warning("AutoPlant: failed to replant " + t.getMessage());
                                }
                                consumeOffHand(player);
                            }
                        }
                    }
                } catch (Throwable ignored) {}
            }
        }
    }

    private boolean consumeOffHand(Player player) {
        ItemStack s = player.getInventory().getItemInOffHand();
        if (s == null) return false;
        int a = s.getAmount();
        if (a <= 0) return false;
        s.setAmount(a - 1);
        if (s.getAmount() <= 0) player.getInventory().setItemInOffHand(null);
        else player.getInventory().setItemInOffHand(s);
        return true;
    }
}
