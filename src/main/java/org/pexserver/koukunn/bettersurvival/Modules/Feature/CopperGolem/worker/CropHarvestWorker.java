package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.CopperGolem;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.ContainerTarget;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.CropRouteMode;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class CropHarvestWorker {

    private static final double RANGE_RETURN_MARGIN = 2.0D;
    private static final double HARVEST_REACH_DISTANCE = 1.8D;
    private static final double CENTER_STAY_DISTANCE = 2.0D;
    private static final int HELD_ITEM_CYCLES_AFTER_ACTION = 2;
    private static final int MIN_BONE_MEAL_AGE = 1;
    private static final int HARVEST_NEIGHBOR_RADIUS = 2;
    private static final double MOVE_SPEED_BASE = 1.0D;
    private static final double MOVE_SPEED_PER_POINT = 0.05D;
    private static final double MOVE_SPEED_MAX = 1.8D;
    private final Map<UUID, Integer> heldItemCycles = new HashMap<>();

    @SuppressWarnings("null")
    public int runCropWorker(
            GolemProfile profile,
            CopperGolem golem,
            int harvestCapacity,
            int maxRange,
            boolean allowReplant,
            boolean allowBoneMeal) {
        tickHeldItemAnimation(golem);

        if (profile.cropFilters().isEmpty()) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            return 0;
        }

        int capacity = Math.max(1, harvestCapacity);
        if (capacity <= 0) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            return 0;
        }
        int maxHarvestBlocks = blocksPerCycleFromCapacity(capacity, profile.harvestPoints());
        int harvestSweepRadius = harvestSweepRadiusFromPoints(profile.harvestPoints());
        double moveSpeed = cropMoveSpeed(profile.moveSpeedPoints());
        CropRouteMode routeMode = profile.cropRouteMode();

        int range = Math.max(1, Math.min(profile.range(), maxRange));
        profile.setRange(range);
        Set<Material> cropFilters = Set.copyOf(profile.cropFilters());

        Location searchCenter = resolveSearchCenter(profile.targets(), golem.getLocation());
        if (searchCenter.getWorld() == null || golem.getLocation().getWorld() == null) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            golem.getPathfinder().stopPathfinding();
            return 0;
        }
        if (!Objects.equals(searchCenter.getWorld(), golem.getLocation().getWorld())) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            golem.getPathfinder().stopPathfinding();
            return 0;
        }

        double allowedDistance = range + RANGE_RETURN_MARGIN;
        if (golem.getLocation().distanceSquared(searchCenter) > (allowedDistance * allowedDistance)) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            golem.getPathfinder().moveTo(searchCenter, moveSpeed);
            golem.setGolemState(CopperGolem.State.GETTING_NO_ITEM);
            return 0;
        }

        List<Block> ripeCrops = findRipeCrops(searchCenter, range, cropFilters);
        if (ripeCrops.isEmpty()) {
            if (allowBoneMeal && profile.autoBoneMeal() && !profile.boneMealSources().isEmpty()) {
                int fertilized = runBoneMealWorker(profile, golem, searchCenter, range, maxHarvestBlocks, moveSpeed);
                if (fertilized > 0) {
                    return 0;
                }
            }
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            if (golem.getLocation().distanceSquared(searchCenter) > (CENTER_STAY_DISTANCE * CENTER_STAY_DISTANCE)) {
                golem.getPathfinder().moveTo(searchCenter, Math.max(0.8D, moveSpeed - 0.1D));
                golem.setGolemState(CopperGolem.State.GETTING_NO_ITEM);
            } else {
                golem.getPathfinder().stopPathfinding();
                golem.setGolemState(CopperGolem.State.GETTING_NO_ITEM);
            }
            return 0;
        }

        Block primaryTarget = selectPrimaryHarvestTarget(ripeCrops, searchCenter, range, routeMode);
        if (primaryTarget == null) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            golem.getPathfinder().stopPathfinding();
            return 0;
        }
        Location harvestPoint = primaryTarget.getLocation().add(0.5D, 0.0D, 0.5D);
        if (golem.getLocation().distanceSquared(harvestPoint) > (HARVEST_REACH_DISTANCE * HARVEST_REACH_DISTANCE)) {
            clearHeldItem(golem, CopperGolem.State.GETTING_ITEM);
            golem.getPathfinder().moveTo(harvestPoint, moveSpeed);
            golem.setGolemState(CopperGolem.State.GETTING_ITEM);
            return 0;
        }
        List<Block> harvestTargets = collectHarvestBatchTargets(primaryTarget, ripeCrops, maxHarvestBlocks, harvestSweepRadius, searchCenter, routeMode);

        List<ItemStack> collected = new ArrayList<>();
        Map<Material, Integer> collectedCounts = new LinkedHashMap<>();
        int harvested = 0;
        int harvestedBlocks = 0;
        for (Block block : harvestTargets) {
            if (harvestedBlocks >= maxHarvestBlocks) {
                break;
            }
            if (!cropFilters.contains(block.getType())) {
                continue;
            }
            if (block.getType() == Material.SUGAR_CANE) {
                int harvestedSugarCane = harvestSugarCaneColumn(
                        block,
                        maxHarvestBlocks - harvestedBlocks,
                        profile.autoReplant() && allowReplant,
                        golem,
                        collected,
                        collectedCounts);
                if (harvestedSugarCane <= 0) {
                    continue;
                }
                harvested += harvestedSugarCane;
                harvestedBlocks += harvestedSugarCane;
                continue;
            }
            if (!(block.getBlockData() instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
                continue;
            }

            Material cropType = block.getType();
            Collection<ItemStack> drops = block.getDrops();
            Material display = resolveDisplayMaterial(cropType, drops);
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir()) {
                    continue;
                }
                ItemStack pickup = drop.clone();
                collected.add(pickup);
                collectedCounts.merge(pickup.getType(), pickup.getAmount(), Integer::sum);
                harvested += pickup.getAmount();
            }

            showHarvestAnimation(golem, display, block);
            block.setType(Material.AIR, false);
            if (profile.autoReplant() && allowReplant) {
                tryReplant(block, cropType, collectedCounts);
            }
            harvestedBlocks++;
        }

        if (collected.isEmpty()) {
            clearHeldItem(golem, CopperGolem.State.DROPPING_NO_ITEM);
            golem.setGolemState(CopperGolem.State.DROPPING_NO_ITEM);
            return 0;
        }

        storeToContainers(profile.targets(), golem.getLocation(), collected);
        golem.setGolemState(CopperGolem.State.DROPPING_ITEM);
        heldItemCycles.put(golem.getUniqueId(), HELD_ITEM_CYCLES_AFTER_ACTION);
        return harvested;
    }

    public void clearTracking(UUID golemId) {
        if (golemId == null) {
            return;
        }
        heldItemCycles.remove(golemId);
    }

    private int runBoneMealWorker(
            GolemProfile profile,
            CopperGolem golem,
            Location searchCenter,
            int range,
            int maxActions,
            double moveSpeed) {
        List<Block> growable = findGrowableCrops(searchCenter, range, Set.copyOf(profile.cropFilters()));
        if (growable.isEmpty()) {
            return 0;
        }

        Block first = growable.get(0);
        Location firstPoint = first.getLocation().add(0.5D, 0.0D, 0.5D);
        if (golem.getLocation().distanceSquared(firstPoint) > (HARVEST_REACH_DISTANCE * HARVEST_REACH_DISTANCE)) {
            clearHeldItem(golem, CopperGolem.State.GETTING_ITEM);
            golem.getPathfinder().moveTo(firstPoint, moveSpeed);
            golem.setGolemState(CopperGolem.State.GETTING_ITEM);
            return 0;
        }

        int availableBoneMeal = consumeBoneMeal(profile.boneMealSources(), maxActions);
        if (availableBoneMeal <= 0) {
            clearHeldItem(golem, CopperGolem.State.DROPPING_NO_ITEM);
            return 0;
        }

        int used = 0;
        for (Block block : growable) {
            if (used >= availableBoneMeal || used >= maxActions) {
                break;
            }
            Location point = block.getLocation().add(0.5D, 0.0D, 0.5D);
            if (golem.getLocation().distanceSquared(point) > (HARVEST_REACH_DISTANCE * HARVEST_REACH_DISTANCE)) {
                break;
            }
            if (block.applyBoneMeal(BlockFace.UP)) {
                showBoneMealAnimation(golem, block);
                used++;
            }
        }

        if (used <= 0) {
            clearHeldItem(golem, CopperGolem.State.DROPPING_NO_ITEM);
            return 0;
        }

        heldItemCycles.put(golem.getUniqueId(), HELD_ITEM_CYCLES_AFTER_ACTION);
        golem.setGolemState(CopperGolem.State.DROPPING_ITEM);
        return used;
    }

    private int blocksPerCycleFromCapacity(int capacity, int harvestPoints) {
        int blocks = 1 + (capacity / 25) + Math.max(0, harvestPoints / 2);
        return Math.max(1, Math.min(24, blocks));
    }

    private int harvestSweepRadiusFromPoints(int harvestPoints) {
        return Math.max(0, Math.min(6, harvestPoints / 3));
    }

    private double cropMoveSpeed(int moveSpeedPoints) {
        int points = Math.max(0, moveSpeedPoints);
        return Math.min(MOVE_SPEED_MAX, MOVE_SPEED_BASE + (points * MOVE_SPEED_PER_POINT));
    }

    private Location resolveSearchCenter(List<ContainerTarget> targets, Location fallback) {
        if (targets == null || targets.isEmpty()) {
            return fallback;
        }
        if (fallback.getWorld() == null) {
            return fallback;
        }

        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ContainerTarget target : targets) {
            if (target == null || target.anchor() == null || target.anchor().getWorld() == null) {
                continue;
            }
            if (!Objects.equals(target.anchor().getWorld(), fallback.getWorld())) {
                continue;
            }
            double distance = target.anchor().distanceSquared(fallback);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = target.anchor();
            }
        }

        if (nearest == null) {
            return fallback;
        }
        return nearest.clone().add(0.5D, 0.0D, 0.5D);
    }

    private void storeToContainers(List<ContainerTarget> targets, Location fallback, List<ItemStack> items) {
        if (targets.isEmpty()) {
            for (ItemStack item : items) {
                fallback.getWorld().dropItemNaturally(fallback, item);
            }
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack working = item.clone();
            for (ContainerTarget target : targets) {
                Inventory inventory = resolveTargetInventory(target);
                if (inventory == null) {
                    continue;
                }
                Map<Integer, ItemStack> leftovers = inventory.addItem(working);
                if (leftovers.isEmpty()) {
                    working = null;
                    break;
                }
                working = leftovers.values().iterator().next();
            }
            if (working != null && !working.getType().isAir() && working.getAmount() > 0) {
                remaining.add(working);
            }
        }

        for (ItemStack leftover : remaining) {
            fallback.getWorld().dropItemNaturally(fallback, leftover);
        }
    }

    private Inventory resolveTargetInventory(ContainerTarget target) {
        if (target == null || target.footprint().isEmpty()) {
            return null;
        }

        for (Location location : target.footprint()) {
            BlockState state = location.getBlock().getState();
            if (!(state instanceof Container container)) {
                continue;
            }
            Inventory inventory = container.getInventory();
            if (inventory != null) {
                return inventory;
            }
        }
        return null;
    }

    private List<Block> findRipeCrops(Location center, int range, Set<Material> filters) {
        List<Block> result = new ArrayList<>();
        Block base = center.getBlock();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = base.getRelative(dx, dy, dz);
                    if (!filters.contains(block.getType())) {
                        continue;
                    }
                    if (block.getType() == Material.SUGAR_CANE) {
                        if (isSugarCaneBase(block)) {
                            result.add(block);
                        }
                        continue;
                    }
                    if (!(block.getBlockData() instanceof Ageable ageable)) {
                        continue;
                    }
                    if (ageable.getAge() < ageable.getMaximumAge()) {
                        continue;
                    }
                    result.add(block);
                }
            }
        }
        return result;
    }

    private Block selectPrimaryHarvestTarget(List<Block> ripeCrops, Location center, int range, CropRouteMode routeMode) {
        if (ripeCrops.isEmpty()) {
            return null;
        }
        Map<Long, Block> byKey = new HashMap<>();
        for (Block block : ripeCrops) {
            byKey.put(toBlockKey(block.getX(), block.getY(), block.getZ()), block);
        }

        Block best = null;
        double bestScore = Double.NEGATIVE_INFINITY;
        for (Block candidate : ripeCrops) {
            int denseCount = countRipeNeighbors(candidate, byKey, HARVEST_NEIGHBOR_RADIUS);
            double distanceFromCenter = Math.sqrt(candidate.getLocation().distanceSquared(center));
            double routeScore;
            if (routeMode == CropRouteMode.BALANCED) {
                double preferred = Math.max(1.0D, range * 0.6D);
                routeScore = -Math.abs(distanceFromCenter - preferred) * 0.45D;
            } else {
                routeScore = -distanceFromCenter * 0.85D;
            }
            double score = (denseCount * 4.0D) + routeScore;
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }
        return best;
    }

    private List<Block> collectHarvestBatchTargets(
            Block primaryTarget,
            List<Block> ripeCrops,
            int maxHarvestBlocks,
            int sweepRadius,
            Location center,
            CropRouteMode routeMode) {
        if (primaryTarget == null || ripeCrops.isEmpty() || maxHarvestBlocks <= 0) {
            return List.of();
        }
        List<Block> sorted = new ArrayList<>(ripeCrops);
        double radiusSquared = sweepRadius <= 0 ? 0.0D : (double) sweepRadius * sweepRadius;
        sorted.sort((left, right) -> {
            boolean leftInSweep = left.getLocation().distanceSquared(primaryTarget.getLocation()) <= radiusSquared;
            boolean rightInSweep = right.getLocation().distanceSquared(primaryTarget.getLocation()) <= radiusSquared;
            if (leftInSweep != rightInSweep) {
                return leftInSweep ? -1 : 1;
            }

            double leftPrimary = left.getLocation().distanceSquared(primaryTarget.getLocation());
            double rightPrimary = right.getLocation().distanceSquared(primaryTarget.getLocation());
            int primaryCompare = Double.compare(leftPrimary, rightPrimary);
            if (primaryCompare != 0) {
                return primaryCompare;
            }
            if (routeMode == CropRouteMode.BALANCED) {
                return Double.compare(
                        Math.abs(Math.sqrt(left.getLocation().distanceSquared(center))),
                        Math.abs(Math.sqrt(right.getLocation().distanceSquared(center))));
            }
            return Double.compare(left.getLocation().distanceSquared(center), right.getLocation().distanceSquared(center));
        });

        List<Block> result = new ArrayList<>();
        long primaryKey = toBlockKey(primaryTarget.getX(), primaryTarget.getY(), primaryTarget.getZ());
        for (Block block : sorted) {
            if (result.size() >= maxHarvestBlocks) {
                break;
            }
            if (sweepRadius > 0) {
                double distancePrimary = block.getLocation().distanceSquared(primaryTarget.getLocation());
                if (distancePrimary > radiusSquared && toBlockKey(block.getX(), block.getY(), block.getZ()) != primaryKey) {
                    continue;
                }
            }
            result.add(block);
        }
        if (result.isEmpty()) {
            result.add(primaryTarget);
        }
        return result;
    }

    private int countRipeNeighbors(Block centerBlock, Map<Long, Block> ripeByKey, int radius) {
        int count = 0;
        int baseX = centerBlock.getX();
        int baseY = centerBlock.getY();
        int baseZ = centerBlock.getZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = -1; dy <= 1; dy++) {
                    long key = toBlockKey(baseX + dx, baseY + dy, baseZ + dz);
                    if (ripeByKey.containsKey(key)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private long toBlockKey(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }

    private List<Block> findGrowableCrops(Location center, int range, Set<Material> filters) {
        List<Block> result = new ArrayList<>();
        Block base = center.getBlock();
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                for (int dy = -2; dy <= 2; dy++) {
                    Block block = base.getRelative(dx, dy, dz);
                    if (!filters.contains(block.getType())) {
                        continue;
                    }
                    if (!(block.getBlockData() instanceof Ageable ageable)) {
                        continue;
                    }
                    if (ageable.getAge() >= ageable.getMaximumAge()) {
                        continue;
                    }
                    if (ageable.getAge() <= MIN_BONE_MEAL_AGE) {
                        continue;
                    }
                    result.add(block);
                }
            }
        }
        result.sort(Comparator
                .comparingInt((Block block) -> remainingGrowthSteps(block))
                .thenComparingDouble(block -> block.getLocation().distanceSquared(center)));
        return result;
    }

    private int remainingGrowthSteps(Block block) {
        if (!(block.getBlockData() instanceof Ageable ageable)) {
            return Integer.MAX_VALUE;
        }
        return ageable.getMaximumAge() - ageable.getAge();
    }

    private void tryReplant(Block harvestedBlock, Material cropType, Map<Material, Integer> collectedCounts) {
        Material seedType = seedForCrop(cropType);
        if (seedType == null) {
            return;
        }
        if (cropType == Material.SUGAR_CANE) {
            int canes = collectedCounts.getOrDefault(seedType, 0);
            if (canes <= 0 || !canReplantSugarCane(harvestedBlock)) {
                return;
            }
            harvestedBlock.setType(Material.SUGAR_CANE, false);
            collectedCounts.put(seedType, canes - 1);
            return;
        }
        int seeds = collectedCounts.getOrDefault(seedType, 0);
        if (seeds <= 0) {
            return;
        }
        Block below = harvestedBlock.getRelative(0, -1, 0);
        if (below.getType() != Material.FARMLAND) {
            return;
        }

        harvestedBlock.setType(cropType, false);
        if (harvestedBlock.getBlockData() instanceof Ageable ageable) {
            ageable.setAge(0);
            harvestedBlock.setBlockData(ageable, false);
        }
        collectedCounts.put(seedType, seeds - 1);
    }

    private void showHarvestAnimation(CopperGolem golem, Material displayMaterial, Block block) {
        if (displayMaterial != null && !displayMaterial.isAir()) {
            golem.getEquipment().setItemInMainHand(new ItemStack(displayMaterial), true);
        }
        golem.setGolemState(CopperGolem.State.GETTING_ITEM);
        golem.swingHand(org.bukkit.inventory.EquipmentSlot.HAND);
        Sound breakSound = block.getBlockSoundGroup().getBreakSound();
        block.getWorld().playSound(block.getLocation().add(0.5D, 0.5D, 0.5D), breakSound, 0.8F, 1.0F);
        block.getWorld().spawnParticle(org.bukkit.Particle.BLOCK, block.getLocation().add(0.5D, 0.5D, 0.5D),
                14, 0.25D, 0.25D, 0.25D, block.getBlockData());
    }

    private void showBoneMealAnimation(CopperGolem golem, Block block) {
        golem.getEquipment().setItemInMainHand(new ItemStack(Material.BONE_MEAL), true);
        golem.setGolemState(CopperGolem.State.GETTING_ITEM);
        golem.swingHand(org.bukkit.inventory.EquipmentSlot.HAND);
        block.getWorld().playSound(block.getLocation().add(0.5D, 0.5D, 0.5D), Sound.ITEM_BONE_MEAL_USE, 0.7F, 1.0F);
        block.getWorld().spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, block.getLocation().add(0.5D, 0.8D, 0.5D),
                8, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    private void clearHeldItem(CopperGolem golem, CopperGolem.State nextState) {
        heldItemCycles.remove(golem.getUniqueId());
        golem.getEquipment().setItemInMainHand(new ItemStack(Material.AIR), true);
        if (nextState != null) {
            golem.setGolemState(nextState);
        }
    }

    private void tickHeldItemAnimation(CopperGolem golem) {
        Integer cycles = heldItemCycles.get(golem.getUniqueId());
        if (cycles == null) {
            return;
        }
        if (cycles <= 1) {
            clearHeldItem(golem, CopperGolem.State.GETTING_NO_ITEM);
            return;
        }
        heldItemCycles.put(golem.getUniqueId(), cycles - 1);
    }

    private Material seedForCrop(Material cropType) {
        return switch (cropType) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case NETHER_WART -> Material.NETHER_WART;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            default -> null;
        };
    }

    private int harvestSugarCaneColumn(
            Block baseBlock,
            int maxBlocks,
            boolean allowReplant,
            CopperGolem golem,
            List<ItemStack> collected,
            Map<Material, Integer> collectedCounts) {
        if (baseBlock == null || baseBlock.getType() != Material.SUGAR_CANE || maxBlocks <= 0) {
            return 0;
        }

        List<Block> column = new ArrayList<>();
        Block current = baseBlock;
        while (current.getType() == Material.SUGAR_CANE && column.size() < maxBlocks) {
            column.add(current);
            current = current.getRelative(0, 1, 0);
        }

        if (column.isEmpty()) {
            return 0;
        }

        for (int index = column.size() - 1; index >= 0; index--) {
            Block block = column.get(index);
            Collection<ItemStack> drops = block.getDrops();
            Material display = Material.SUGAR_CANE;
            for (ItemStack drop : drops) {
                if (drop == null || drop.getType().isAir()) {
                    continue;
                }
                ItemStack pickup = drop.clone();
                collected.add(pickup);
                Material material = pickup.getType();
                int currentAmount = collectedCounts.getOrDefault(material, 0);
                collectedCounts.put(material, currentAmount + pickup.getAmount());
            }
            showHarvestAnimation(golem, display, block);
            block.setType(Material.AIR, false);
        }

        if (allowReplant && current.getType() != Material.SUGAR_CANE) {
            tryReplant(baseBlock, Material.SUGAR_CANE, collectedCounts);
        }

        return column.size();
    }

    private boolean canReplantSugarCane(Block block) {
        if (block == null || block.getType() != Material.AIR) {
            return false;
        }
        Block supportBlock = block.getRelative(0, -1, 0);
        return isSugarCaneSupportBlock(supportBlock.getType()) && hasAdjacentWater(supportBlock);
    }

    private boolean isSugarCaneBase(Block block) {
        if (block == null || block.getType() != Material.SUGAR_CANE) {
            return false;
        }
        Block supportBlock = block.getRelative(0, -1, 0);
        return isSugarCaneSupportBlock(supportBlock.getType()) && hasAdjacentWater(supportBlock);
    }

    private boolean isSugarCaneSupportBlock(Material material) {
        return switch (material) {
            case SAND, RED_SAND, DIRT, GRASS_BLOCK, MYCELIUM, PODZOL, COARSE_DIRT, ROOTED_DIRT, MOSS_BLOCK, MUD -> true;
            default -> false;
        };
    }

    private boolean hasAdjacentWater(Block block) {
        return block.getRelative(BlockFace.NORTH).getType() == Material.WATER
                || block.getRelative(BlockFace.SOUTH).getType() == Material.WATER
                || block.getRelative(BlockFace.EAST).getType() == Material.WATER
                || block.getRelative(BlockFace.WEST).getType() == Material.WATER;
    }

    private Material resolveDisplayMaterial(Material cropType, Collection<ItemStack> drops) {
        Material seedType = seedForCrop(cropType);
        Material fallback = cropType;
        for (ItemStack drop : drops) {
            if (drop == null || drop.getType().isAir()) {
                continue;
            }
            if (seedType != null && drop.getType() != seedType) {
                return drop.getType();
            }
            fallback = drop.getType();
        }
        return fallback;
    }

    private int consumeBoneMeal(List<ContainerTarget> sources, int requested) {
        if (requested <= 0 || sources == null || sources.isEmpty()) {
            return 0;
        }
        int remaining = requested;
        for (ContainerTarget source : sources) {
            if (remaining <= 0) {
                break;
            }
            Inventory inventory = resolveTargetInventory(source);
            if (inventory == null) {
                continue;
            }
            for (int slot = 0; slot < inventory.getSize(); slot++) {
                if (remaining <= 0) {
                    break;
                }
                ItemStack item = inventory.getItem(slot);
                if (item == null || item.getType() != Material.BONE_MEAL || item.getAmount() <= 0) {
                    continue;
                }
                int take = Math.min(item.getAmount(), remaining);
                item.setAmount(item.getAmount() - take);
                if (item.getAmount() <= 0) {
                    inventory.setItem(slot, null);
                } else {
                    inventory.setItem(slot, item);
                }
                remaining -= take;
            }
        }
        return requested - remaining;
    }
}
