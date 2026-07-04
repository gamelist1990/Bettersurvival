package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 範囲採掘 (areabreak)。
 *
 * ブロックを掘ると周囲のブロックが「一発で消える」のではなく、
 * 中心から波状に破壊アニメーション (ひび割れの進行 = block damage progress) が
 * 走ってから順番に壊れていく。
 *
 * - Lv1: 3x3 / Lv2: 3x3 奥行き2 / Lv3: 7x7 奥行き2
 * - スニーク + 掘るでのみ発動する
 * - 各ブロックは合成 BlockBreakEvent を経由するため、土地保護や
 *   自動回収など他の機能とそのまま連動する
 */
public class AreaBreakEnchant extends CustomEnchant {

    /** 螺旋アニメーション: 1ブロックごとの開始ずれ (tick) */
    private static final int SPIRAL_STEP_TICKS = 2;
    /** 奥行きレイヤーごとの開始ずれ (tick) */
    private static final int LAYER_DELAY_TICKS = 8;
    /** ひび割れアニメーションの最短/最長 (tick) */
    private static final int MIN_CRACK_TICKS = 4;
    private static final int MAX_CRACK_TICKS = 200;
    /** アニメーションを見せる範囲 */
    private static final double VIEW_RANGE = 32.0D;
    /** 1ジョブの最大ブロック数 (安全弁) */
    private static final int MAX_BLOCKS_PER_JOB = 60;
    /** 同時に走らせられる渦ジョブ数の安全弁 */
    private static final int MAX_PARALLEL_JOBS_PER_PLAYER = 8;

    private static final AtomicInteger CRACK_SOURCE_IDS = new AtomicInteger(-77_000_000);

    private final Map<UUID, Set<BukkitTask>> activeJobs = new ConcurrentHashMap<>();
    private final Map<UUID, BurstState> burstStates = new ConcurrentHashMap<>();
    /** 合成イベントの再帰発動を防ぐガード */
    private final Set<Block> processing = new HashSet<>();

    public AreaBreakEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "areabreak";
    }

    @Override
    public String displayName() {
        return "範囲採掘";
    }

    @Override
    public String description() {
        return "§7スニークしながら掘ると周囲のブロックを"
                + "\n§7中心から渦を巻くように破壊する"
                + "\n§7Lv1: 3x3 / Lv2: 3x3 奥行き2 / Lv3: 7x7 奥行き2"
                + "\n§7通常掘りでは発動しない";
    }

    @Override
    public Material icon() {
        return Material.TNT_MINECART;
    }

    @Override
    public String vanillaParentName() {
        return "範囲ダメージ増加";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 24), new ItemStack(Material.IRON_INGOT, 16));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.DIAMOND, 4));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 64), new ItemStack(Material.DIAMOND, 8));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block origin = event.getBlock();
        if (processing.contains(origin)) {
            return; // 自分が発行した合成イベント
        }
        Player player = event.getPlayer();
        if (!player.isSneaking()) {
            return; // シフト+掘りでのみ発動
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = levelOf(tool);
        if (level <= 0) {
            return;
        }
        if (!tryConsumeBurst(player, level)) {
            return;
        }
        Set<BukkitTask> jobs = activeJobs.get(player.getUniqueId());
        if (jobs != null && jobs.size() >= MAX_PARALLEL_JOBS_PER_PLAYER) {
            return;
        }
        List<TargetInfo> targets = collectTargets(player, origin, tool, level);
        if (targets.isEmpty()) {
            return;
        }
        startJob(player, origin, tool.clone(), targets);
    }

    /** レベルと視線方向から破壊対象の平面 (+奥行き) を集める */
    private List<TargetInfo> collectTargets(Player player, Block origin, ItemStack tool, int level) {
        int radius = level >= 3 ? 3 : 1;
        int depth = level >= 2 ? 2 : 1;

        // 平面の向き: 見下ろし/見上げなら水平面、それ以外は視線に正対する垂直面
        float pitch = player.getLocation().getPitch();
        float yaw = player.getLocation().getYaw();
        Vector axisA;
        Vector axisB;
        Vector depthDir;
        if (pitch > 45.0F || pitch < -45.0F) {
            axisA = new Vector(1, 0, 0);
            axisB = new Vector(0, 0, 1);
            depthDir = new Vector(0, pitch > 0 ? -1 : 1, 0);
        } else {
            boolean alongX = isFacingX(yaw);
            axisA = alongX ? new Vector(0, 0, 1) : new Vector(1, 0, 0);
            axisB = new Vector(0, 1, 0);
            Vector look = player.getLocation().getDirection();
            depthDir = alongX
                    ? new Vector(look.getX() > 0 ? 1 : -1, 0, 0)
                    : new Vector(0, 0, look.getZ() > 0 ? 1 : -1);
        }

        LandProtectionModule landProtection = plugin.getLandProtectionModule();
        boolean checkClaims = landProtection != null && landProtection.isFeatureEnabled();

        List<TargetInfo> targets = new ArrayList<>();
        List<Vector> spiralOffsets = spiralOffsets(radius);
        for (int d = 0; d < depth; d++) {
            int layerOrder = 0;
            for (Vector offset : spiralOffsets) {
                int a = offset.getBlockX();
                int b = offset.getBlockZ();
                if (d == 0 && a == 0 && b == 0) {
                    continue; // 中心は通常の破壊イベントが処理済み
                }
                Block block = origin.getRelative(
                        axisA.getBlockX() * a + axisB.getBlockX() * b + depthDir.getBlockX() * d,
                        axisA.getBlockY() * a + axisB.getBlockY() * b + depthDir.getBlockY() * d,
                        axisA.getBlockZ() * a + axisB.getBlockZ() * b + depthDir.getBlockZ() * d);
                if (!isBreakable(block, tool)) {
                    layerOrder++;
                    continue;
                }
                // 土地保護の事前チェック (ひび割れ演出すら出さない)。
                // 破壊時にも合成 BlockBreakEvent で再チェックされる二重ガード
                if (checkClaims) {
                    ClaimRegion claim = landProtection.getActiveClaimAt(block.getLocation());
                    if (claim != null && !landProtection.canBypass(player, claim)) {
                        layerOrder++;
                        continue;
                    }
                }
                int startTick = layerOrder * SPIRAL_STEP_TICKS + d * LAYER_DELAY_TICKS;
                targets.add(new TargetInfo(block, startTick));
                layerOrder++;
                if (targets.size() >= MAX_BLOCKS_PER_JOB) {
                    return targets;
                }
            }
        }
        return targets;
    }

    /**
     * 中央から外側へ広がる正方形スパイラル順の平面座標を作る。
     */
    private static List<Vector> spiralOffsets(int radius) {
        List<Vector> offsets = new ArrayList<>();
        offsets.add(new Vector(0, 0, 0));
        for (int ring = 1; ring <= radius; ring++) {
            for (int a = -ring + 1; a <= ring; a++) {
                offsets.add(new Vector(a, 0, -ring));
            }
            for (int b = -ring + 1; b <= ring; b++) {
                offsets.add(new Vector(ring, 0, b));
            }
            for (int a = ring - 1; a >= -ring; a--) {
                offsets.add(new Vector(a, 0, ring));
            }
            for (int b = ring - 1; b >= -ring; b--) {
                offsets.add(new Vector(-ring, 0, b));
            }
        }
        return offsets;
    }

    /** ヨー角から視線が X 軸方向 (東西) かどうか */
    private static boolean isFacingX(float yaw) {
        float normalized = ((yaw % 360.0F) + 360.0F) % 360.0F;
        return (normalized >= 45.0F && normalized < 135.0F) || (normalized >= 225.0F && normalized < 315.0F);
    }

    private boolean isBreakable(Block block, ItemStack tool) {
        Material type = block.getType();
        if (type.isAir() || block.isLiquid()) {
            return false;
        }
        if (type.getHardness() < 0.0F) {
            return false; // 岩盤など
        }
        if (block.getState() instanceof TileState) {
            return false; // チェスト・かまど等のブロックエンティティは対象外
        }
        return block.isPreferredTool(tool);
    }

    private void startJob(Player player, Block origin, ItemStack toolSnapshot, List<TargetInfo> targets) {
        Location originCenter = origin.getLocation().add(0.5D, 0.5D, 0.5D);
        List<CrackEntry> entries = new ArrayList<>();
        for (TargetInfo target : targets) {
            Block block = target.block();
            int duration = crackDurationTicks(block, toolSnapshot, player);
            entries.add(new CrackEntry(block, block.getType(), originCenter, target.startTick(), duration, CRACK_SOURCE_IDS.getAndIncrement()));
            processing.add(block);
        }

        UUID playerId = player.getUniqueId();
        final int[] tick = {0};
        final int[] brokenCount = {0};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Player owner = Bukkit.getPlayer(playerId);
            if (owner == null || !owner.isOnline() || owner.getInventory().getItemInMainHand().getType() != toolSnapshot.getType()) {
                finishJob(playerId, entries);
                return;
            }
            boolean anyRemaining = false;
            for (CrackEntry entry : entries) {
                if (entry.finished) {
                    continue;
                }
                // 途中でブロックが変わった/消えたらそのエントリは中止
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
                    spawnSpiralParticle(entry, progress, tick[0]);
                    continue;
                }
                // 破壊フェーズ
                entry.finished = true;
                sendCrackClear(entry);
                if (breakViaEvent(owner, entry.block, toolSnapshot)) {
                    brokenCount[0]++;
                }
                processing.remove(entry.block);
            }
            tick[0]++;
            if (!anyRemaining) {
                applyToolDamage(owner, toolSnapshot.getType(), brokenCount[0]);
                finishJob(playerId, entries);
            }
        }, 1L, 1L);
        activeJobs.computeIfAbsent(playerId, ignored -> ConcurrentHashMap.newKeySet()).add(task);
    }

    /**
     * Lvごとに「連続発動できる回数」と「使い切った後のクールダウン」を変える。
     * Lv1: 2回ごとに 4秒 / Lv2: 3回ごとに 1秒 / Lv3: 4回ごとに 1秒
     */
    private boolean tryConsumeBurst(Player player, int level) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        BurstState state = burstStates.computeIfAbsent(playerId, ignored -> new BurstState());
        if (now < state.cooldownUntilMillis) {
            return false;
        }
        int burstLimit = burstLimit(level);
        state.usesInWindow++;
        if (state.usesInWindow >= burstLimit) {
            state.usesInWindow = 0;
            state.cooldownUntilMillis = now + cooldownMillis(level);
        }
        return true;
    }

    private int burstLimit(int level) {
        return switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> 2;
            case 2 -> 3;
            default -> 4;
        };
    }

    private long cooldownMillis(int level) {
        return switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> 3_000L;
            case 2 -> 500L;
            default -> 0L;
        };
    }

    /**
     * ブロックごとに硬さ・道具素材・効率強化・採掘速度上昇/低下などを反映した
     * 破壊プログレス時間を返す。
     */
    private int crackDurationTicks(Block block, ItemStack tool, Player player) {
        float hardness = block.getType().getHardness();
        if (hardness <= 0.0F) {
            return MIN_CRACK_TICKS;
        }
        double speed = toolMiningSpeed(tool == null ? Material.AIR : tool.getType());
        int efficiencyLevel = tool == null ? 0 : tool.getEnchantmentLevel(Enchantment.EFFICIENCY);
        if (efficiencyLevel > 0) {
            speed += efficiencyLevel * efficiencyLevel + 1.0D;
        }
        speed *= hasteMultiplier(player);
        speed *= miningFatigueMultiplier(player);

        if (player != null && player.isInWater() && !hasAquaAffinity(player)) {
            speed /= 5.0D;
        }
        if (player != null && !isStandingOnGround(player)) {
            speed /= 5.0D;
        }

        speed = Math.max(0.01D, speed);
        int ticks = (int) Math.ceil(hardness * 30.0D / speed);
        return Math.max(MIN_CRACK_TICKS, Math.min(MAX_CRACK_TICKS, ticks));
    }

    private double toolMiningSpeed(Material toolType) {
        String name = toolType.name();
        if (name.startsWith("WOODEN_")) {
            return 2.0D;
        }
        if (name.startsWith("STONE_")) {
            return 4.0D;
        }
        if (name.startsWith("IRON_")) {
            return 6.0D;
        }
        if (name.startsWith("DIAMOND_")) {
            return 8.0D;
        }
        if (name.startsWith("NETHERITE_")) {
            return 9.0D;
        }
        if (name.startsWith("GOLDEN_")) {
            return 12.0D;
        }
        return 1.0D;
    }

    private double hasteMultiplier(Player player) {
        if (player == null) {
            return 1.0D;
        }
        PotionEffect haste = player.getPotionEffect(PotionEffectType.HASTE);
        if (haste == null) {
            return 1.0D;
        }
        return 1.0D + 0.2D * (haste.getAmplifier() + 1);
    }

    private double miningFatigueMultiplier(Player player) {
        if (player == null) {
            return 1.0D;
        }
        PotionEffect fatigue = player.getPotionEffect(PotionEffectType.MINING_FATIGUE);
        if (fatigue == null) {
            return 1.0D;
        }
        return switch (fatigue.getAmplifier()) {
            case 0 -> 0.3D;
            case 1 -> 0.09D;
            case 2 -> 0.0027D;
            default -> 0.00081D;
        };
    }

    private boolean hasAquaAffinity(Player player) {
        ItemStack helmet = player.getInventory().getHelmet();
        return helmet != null && helmet.getEnchantmentLevel(Enchantment.AQUA_AFFINITY) > 0;
    }

    private boolean isStandingOnGround(Player player) {
        World world = player.getWorld();
        BoundingBox box = player.getBoundingBox();
        int minX = (int) Math.floor(box.getMinX() + 1.0E-6D);
        int maxX = (int) Math.floor(box.getMaxX() - 1.0E-6D);
        int minZ = (int) Math.floor(box.getMinZ() + 1.0E-6D);
        int maxZ = (int) Math.floor(box.getMaxZ() - 1.0E-6D);
        int y = (int) Math.floor(box.getMinY() - 0.05D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (world.getBlockAt(x, y, z).getType().isSolid()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 中央から対象ブロックへ向かう渦状の粒子を出す */
    private void spawnSpiralParticle(CrackEntry entry, float progress, int tick) {
        World world = entry.block.getWorld();
        Location target = entry.block.getLocation().add(0.5D, 0.5D, 0.5D);
        Vector path = target.toVector().subtract(entry.originCenter.toVector());
        Location center = entry.originCenter.clone().add(path.multiply(progress));
        double angle = tick * 0.75D + progress * Math.PI * 4.0D;
        double radius = 0.18D + 0.18D * progress;
        double x = Math.cos(angle) * radius;
        double z = Math.sin(angle) * radius;
        world.spawnParticle(org.bukkit.Particle.ENCHANT, center.clone().add(x, 0.0D, z), 2,
                0.02D, 0.02D, 0.02D, 0.0D);
    }

    /** 周囲プレイヤーへひび割れの進行を送る */
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

    /** 指定したひび割れソースIDの表示を周囲プレイヤーから消す */
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

    /** 合成 BlockBreakEvent を経由して1ブロック壊す (保護系・自動回収と連動) */
    private boolean breakViaEvent(Player player, Block block, ItemStack tool) {
        BlockBreakEvent synthetic = new BlockBreakEvent(block, player);
        Bukkit.getPluginManager().callEvent(synthetic);
        if (synthetic.isCancelled()) {
            sendCrackClear(block);
            return false;
        }
        if (!synthetic.isDropItems()) {
            // 自動回収などがドロップを処理済み → ブロックだけ消す
            World world = block.getWorld();
            world.playSound(block.getLocation(), block.getBlockData().getSoundGroup().getBreakSound(),
                    SoundCategory.BLOCKS, 0.8F, 1.0F);
            world.spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5D, 0.5D, 0.5D),
                    20, 0.25D, 0.25D, 0.25D, block.getBlockData());
            block.setType(Material.AIR);
        } else {
            block.breakNaturally(tool, true);
        }
        return true;
    }

    private void sendCrackClear(Block block) {
        Location loc = block.getLocation();
        World world = loc.getWorld();
        if (world == null) {
            return;
        }
        for (Player viewer : world.getNearbyPlayers(loc, VIEW_RANGE)) {
            viewer.sendBlockDamage(loc, 0.0F);
        }
    }

    /** 壊したブロック数ぶん耐久を減らす (耐久エンチャント等はサーバー側処理に委譲) */
    private void applyToolDamage(Player player, Material toolType, int brokenBlocks) {
        if (brokenBlocks <= 0 || player.getGameMode() == org.bukkit.GameMode.CREATIVE) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.getType() != toolType) {
            return; // 途中で持ち替えた場合は耐久消費なし
        }
        ItemStack damaged = hand.damage(brokenBlocks, player);
        player.getInventory().setItemInMainHand(damaged);
    }

    private void finishJob(UUID playerId, List<CrackEntry> entries) {
        for (CrackEntry entry : entries) {
            if (!entry.finished) {
                processing.remove(entry.block);
                sendCrackClear(entry);
            }
        }
        Set<BukkitTask> jobs = activeJobs.get(playerId);
        if (jobs != null) {
            BukkitTask current = Bukkit.getScheduler().getPendingTasks().stream()
                    .filter(BukkitTask::isSync)
                    .filter(task -> jobs.contains(task) && !task.isCancelled())
                    .findFirst()
                    .orElse(null);
            if (current != null) {
                jobs.remove(current);
                current.cancel();
            }
            if (jobs.isEmpty()) {
                activeJobs.remove(playerId);
            }
        }
    }

    @Override
    public void shutdown() {
        for (Set<BukkitTask> jobs : activeJobs.values()) {
            for (BukkitTask task : jobs) {
                task.cancel();
            }
        }
        activeJobs.clear();
        burstStates.clear();
        processing.clear();
    }

    private record TargetInfo(Block block, int startTick) {
    }

    private static final class BurstState {
        int usesInWindow;
        long cooldownUntilMillis;
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
