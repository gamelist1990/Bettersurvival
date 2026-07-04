package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 土砂掘削 (sedimentbreak)。
 *
 * シャベルで砂・赤い砂・砂利を掘った時、掘った場所を中心に同種の土砂ブロックを範囲破壊する。
 * 整地・砂集め・砂利集めを楽にするためのシャベル専用エンチャント。
 */
public class SedimentBreakEnchant extends CustomEnchant {

    private static final int MAX_BLOCKS = 80;
    private static final int START_STEP_TICKS = 2;
    private static final int CRACK_DURATION_TICKS = 10;
    private static final double VIEW_RANGE = 32.0D;
    private static final AtomicInteger CRACK_SOURCE_IDS = new AtomicInteger(-78_000_000);

    private final Set<Block> processing = new HashSet<>();

    public SedimentBreakEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "sedimentbreak";
    }

    @Override
    public String displayName() {
        return "土砂掘削";
    }

    @Override
    public String description() {
        return "§7シャベルで砂・砂利を掘ると"
                + "\n§7掘った場所を中心に土砂を範囲破壊する"
                + "\n§7砂集め・砂利集め・整地作業が楽になる";
    }

    @Override
    public Material icon() {
        return Material.GOLDEN_SHOVEL;
    }

    @Override
    public String vanillaParentName() {
        return "効率強化";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_SHOVEL");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.SAND, 32));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.GRAVEL, 32));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.DIAMOND, 3));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (processing.contains(origin)) {
            return;
        }
        if (!isSediment(origin.getType())) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = levelOf(tool);
        if (level <= 0 || !supports(tool.getType())) {
            return;
        }
        List<Block> targets = collectTargets(player, origin, tool, level);
        if (targets.isEmpty()) {
            return;
        }
        startJob(player, origin, tool.clone(), targets);
    }

    private List<Block> collectTargets(Player player, Block origin, ItemStack tool, int level) {
        int radius = level >= 2 ? 2 : 1;
        int verticalRadius = level >= 3 ? 1 : 0;
        LandProtectionModule landProtection = plugin.getLandProtectionModule();
        boolean checkClaims = landProtection != null && landProtection.isFeatureEnabled();
        List<Block> targets = new ArrayList<>();
        for (Vector offset : offsets(radius, verticalRadius)) {
            Block block = origin.getRelative(offset.getBlockX(), offset.getBlockY(), offset.getBlockZ());
            if (block.equals(origin) || !isSediment(block.getType()) || !block.isPreferredTool(tool)) {
                continue;
            }
            if (checkClaims) {
                ClaimRegion claim = landProtection.getActiveClaimAt(block.getLocation());
                if (claim != null && !landProtection.canBypass(player, claim)) {
                    continue;
                }
            }
            targets.add(block);
            if (targets.size() >= MAX_BLOCKS) {
                return targets;
            }
        }
        return targets;
    }

    private List<Vector> offsets(int radius, int verticalRadius) {
        List<Vector> offsets = new ArrayList<>();
        for (int y = -verticalRadius; y <= verticalRadius; y++) {
            for (int ring = 0; ring <= radius; ring++) {
                if (ring == 0) {
                    offsets.add(new Vector(0, y, 0));
                    continue;
                }
                for (int x = -ring; x <= ring; x++) {
                    offsets.add(new Vector(x, y, -ring));
                    offsets.add(new Vector(x, y, ring));
                }
                for (int z = -ring + 1; z <= ring - 1; z++) {
                    offsets.add(new Vector(-ring, y, z));
                    offsets.add(new Vector(ring, y, z));
                }
            }
        }
        return offsets;
    }

    private void startJob(Player player, Block origin, ItemStack toolSnapshot, List<Block> targets) {
        Location originCenter = origin.getLocation().add(0.5D, 0.5D, 0.5D);
        List<CrackEntry> entries = new ArrayList<>();
        int order = 0;
        for (Block block : targets) {
            entries.add(new CrackEntry(block, block.getType(), originCenter, order * START_STEP_TICKS, CRACK_DURATION_TICKS, CRACK_SOURCE_IDS.getAndIncrement()));
            processing.add(block);
            order++;
        }

        UUID playerId = player.getUniqueId();
        final int[] tick = {0};
        final BukkitTask[] taskRef = new BukkitTask[1];
        taskRef[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player owner = Bukkit.getPlayer(playerId);
            if (owner == null || !owner.isOnline() || owner.getInventory().getItemInMainHand().getType() != toolSnapshot.getType()) {
                finishJob(entries, taskRef[0]);
                return;
            }
            boolean anyRemaining = false;
            for (CrackEntry entry : entries) {
                if (entry.finished) {
                    continue;
                }
                if (entry.block.getType() != entry.originalType) {
                    sendCrackClear(entry);
                    entry.finished = true;
                    processing.remove(entry.block);
                    continue;
                }
                int local = tick[0] - entry.startTick;
                if (local < 0) {
                    anyRemaining = true;
                    continue;
                }
                if (local < entry.durationTicks) {
                    anyRemaining = true;
                    float progress = (float) local / (float) entry.durationTicks;
                    sendCrack(entry, progress);
                    spawnDustTrail(entry, progress);
                    continue;
                }
                entry.finished = true;
                sendCrackClear(entry);
                try {
                    breakViaEvent(owner, entry.block, toolSnapshot);
                } finally {
                    processing.remove(entry.block);
                }
            }
            tick[0]++;
            if (!anyRemaining) {
                finishJob(entries, taskRef[0]);
            }
        }, 1L, 1L);
    }

    private void finishJob(List<CrackEntry> entries, BukkitTask task) {
        for (CrackEntry entry : entries) {
            if (!entry.finished) {
                sendCrackClear(entry);
                processing.remove(entry.block);
                entry.finished = true;
            }
        }
        if (task != null) {
            task.cancel();
        }
    }

    private void spawnDustTrail(CrackEntry entry, float progress) {
        World world = entry.block.getWorld();
        Location target = entry.block.getLocation().add(0.5D, 0.5D, 0.5D);
        Vector path = target.toVector().subtract(entry.originCenter.toVector());
        Location point = entry.originCenter.clone().add(path.multiply(progress));
        world.spawnParticle(org.bukkit.Particle.BLOCK, point, 3, 0.08D, 0.08D, 0.08D, 0.0D, entry.block.getBlockData());
    }

    private void sendCrack(CrackEntry entry, float progress) {
        Location loc = entry.block.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Player viewer : world.getNearbyPlayers(loc, VIEW_RANGE)) {
            viewer.sendBlockDamage(loc, Math.min(0.99F, progress), entry.crackSourceId);
        }
    }

    private void sendCrackClear(CrackEntry entry) {
        Location loc = entry.block.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Player viewer : world.getNearbyPlayers(loc, VIEW_RANGE)) {
            viewer.sendBlockDamage(loc, 0.0F, entry.crackSourceId);
        }
    }

    private boolean breakViaEvent(Player player, Block block, ItemStack tool) {
        BlockBreakEvent synthetic = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(synthetic);
        if (synthetic.isCancelled()) {
            return false;
        }
        if (!synthetic.isDropItems()) {
            World world = block.getWorld();
            world.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(), SoundCategory.BLOCKS, 0.8F, 1.0F);
            world.spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5D, 0.5D, 0.5D), 12, 0.25D, 0.25D, 0.25D, block.getBlockData());
            block.setType(Material.AIR);
            return true;
        }
        return block.breakNaturally(tool, true);
    }

    private boolean isSediment(Material type) {
        return type == Material.SAND || type == Material.RED_SAND || type == Material.GRAVEL;
    }

    private static final class CrackEntry {
        final Block block;
        final Material originalType;
        final Location originCenter;
        final int startTick;
        final int durationTicks;
        final int crackSourceId;
        boolean finished;

        CrackEntry(Block block, Material originalType, Location originCenter, int startTick, int durationTicks, int crackSourceId) {
            this.block = block;
            this.originalType = originalType;
            this.originCenter = originCenter;
            this.startTick = startTick;
            this.durationTicks = durationTicks;
            this.crackSourceId = crackSourceId;
        }
    }
}
