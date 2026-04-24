package org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoPlant;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
 
import org.bukkit.event.Listener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

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
    private final int radius = 3;
    private final Plugin plugin;
    @SuppressWarnings("unused")
    private BukkitTask task;

    private static final Map<Material, Material> seedToCrop = new HashMap<>();

    static {
        for (Material mat : Material.values()) {
            String name = mat.name();
                if (!name.endsWith("_SEEDS") && !name.endsWith("_SEED")
                    && !name.equals("CARROT") && !name.equals("POTATO"))
                continue;

            Material crop = findCropForSeed(mat);
            if (crop != null) {
                seedToCrop.put(mat, crop);
            }
        }

        try { seedToCrop.put(Material.WHEAT_SEEDS, Material.WHEAT); } catch (Throwable ignored) {}
        try { seedToCrop.put(Material.BEETROOT_SEEDS, Material.BEETROOTS); } catch (Throwable ignored) {}
    }

    private static Material findCropForSeed(Material seed) {
        String base = seed.name();
        base = base.replaceFirst("_SEEDS?$", "");

        for (Material m : Material.values()) {
            if (!m.isBlock()) continue;
            try {
                if (!(m.createBlockData() instanceof Ageable)) continue;
            } catch (Throwable ignored) {
                continue;
            }
            if (m.name().equals(base)) return m;
        }

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
        this.plugin = Loader.getPlugin(Loader.class);
        if (this.plugin != null) this.plugin.getLogger().info("AutoPlant scheduled");
        long intervalTicks = 10; // 0.5 second(バランス的にこれがbest)
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
        if (!toggle.getGlobal("autoplant")) return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), "autoplant")) return;

        if (getOffHandSeed(player) == null) return;



        Block center = player.getLocation().getBlock();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                Block plantTarget = null;
                Block cropTarget = null;
                for (int yOff = -2; yOff <= 2; yOff++) {
                    Block cand = b.getRelative(0, yOff, 0);
                    if (cand == null) continue;
                    if (cand.getType() != Material.FARMLAND) continue;

                    Block above = cand.getRelative(BlockFace.UP);
                    if (above.getBlockData() instanceof Ageable) {
                        cropTarget = above;
                        break;
                    }
                    if (plantTarget == null && above.getType().isAir()) plantTarget = above;
                }

                if (cropTarget != null) {
                    try {
                        Ageable age = (Ageable) cropTarget.getBlockData();
                        if (age.getAge() >= age.getMaximumAge()) {
                            cropTarget.breakNaturally();
                            plantCrop(player, cropTarget);
                        }
                    } catch (Throwable ignored) {}
                    continue;
                }

                if (plantTarget != null) {
                    if (!plantCrop(player, plantTarget)) return;
                }
            }
        }
    }

    private boolean plantCrop(Player player, Block target) {
        Material seed = getOffHandSeed(player);
        if (seed == null) return false;
        Material crop = seedToCrop.get(seed);
        try {
            target.setType(crop);
            if (target.getBlockData() instanceof Ageable) {
                Ageable a = (Ageable) target.getBlockData();
                a.setAge(0);
                target.setBlockData(a, true);
            }

        } catch (Throwable t) {
            if (plugin != null) plugin.getLogger().warning("AutoPlant: failed to plant " + t.getMessage());
            return true;
        }
        if (!consumeOffHand(player)) {
            target.setType(Material.AIR);
            return false;
        }
        return true;
    }

    private Material getOffHandSeed(Player player) {
        ItemStack s = player.getInventory().getItemInOffHand();
        if (s == null) return null;
        if (s.getAmount() <= 0) return null;
        Material mat = s.getType();
        if (!seedToCrop.containsKey(mat)) return null;
        return mat;
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
