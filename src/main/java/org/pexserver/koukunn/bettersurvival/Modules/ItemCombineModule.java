package org.pexserver.koukunn.bettersurvival.Modules;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemMergeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * ドロップされた Item 同士の組み合わせ判定を共通化するモジュール。
 *
 * <p>以前は Item × レシピごとに個別 BukkitTask を生成していたため、
 * ドロップ数とレシピ数に比例して scheduler task が増えていた。
 * 現在は 1 本の共有 ticker で追跡 Item をまとめて処理する。</p>
 */
public class ItemCombineModule implements Listener {

    private static final long[] DEFAULT_RETRY_TICKS = {0L, 5L, 10L, 20L};
    private static final long DEFAULT_AIR_RETRY_INTERVAL_TICKS = 5L;
    private static final long DEFAULT_AIR_TRACK_DURATION_TICKS = 60L;

    private final Loader plugin;
    private final Map<String, CombineRegistration> registrations = new LinkedHashMap<>();
    /** Item UUID -> 追跡開始 tick。メインスレッドでのみ操作する。 */
    private final Map<UUID, Long> trackedSeeds = new LinkedHashMap<>();
    private final BukkitTask trackingTask;
    private long schedulerTick;

    public ItemCombineModule(Loader plugin) {
        this.plugin = plugin;
        this.trackingTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickTrackedSeeds, 1L, 1L);
    }

    public CombineBuilder recipe(String key) {
        return new CombineBuilder(key);
    }

    public void unregister(String key) {
        registrations.remove(key);
    }

    public void shutdown() {
        trackingTask.cancel();
        trackedSeeds.clear();
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        scheduleChecks(event.getItemDrop());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemSpawn(ItemSpawnEvent event) {
        scheduleChecks(event.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onItemMerge(ItemMergeEvent event) {
        scheduleChecks(event.getEntity());
        scheduleChecks(event.getTarget());
    }

    private void scheduleChecks(Item seed) {
        UUID seedId = seed.getUniqueId();
        if (trackedSeeds.putIfAbsent(seedId, schedulerTick) != null) {
            return;
        }
        // retryTicks=0 / airborne の初回判定を遅延させない。
        runDueChecks(seedId, 0L);
    }

    private void tickTrackedSeeds() {
        schedulerTick++;
        if (trackedSeeds.isEmpty()) {
            return;
        }

        Iterator<Map.Entry<UUID, Long>> iterator = trackedSeeds.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Long> entry = iterator.next();
            UUID seedId = entry.getKey();
            if (!(Bukkit.getEntity(seedId) instanceof Item seed) || !seed.isValid()) {
                iterator.remove();
                continue;
            }

            long elapsed = schedulerTick - entry.getValue();
            if (!runDueChecks(seedId, elapsed)) {
                iterator.remove();
            }
        }
    }

    /**
     * この elapsed tick で必要なレシピだけを実行する。
     * @return 今後もこの Item を追跡する必要がある場合 true
     */
    private boolean runDueChecks(UUID seedId, long elapsed) {
        boolean keepTracking = false;
        for (CombineRegistration registration : registrations.values()) {
            if (registration.allowAirCombine) {
                if (elapsed <= registration.airTrackDurationTicks) {
                    keepTracking = true;
                    if (elapsed == 0L || elapsed % registration.airRetryIntervalTicks == 0L) {
                        runRegistration(seedId, registration);
                    }
                }
                continue;
            }

            for (long retryTick : registration.retryTicks) {
                if (retryTick >= elapsed) {
                    keepTracking = true;
                }
                if (retryTick == elapsed) {
                    runRegistration(seedId, registration);
                    break;
                }
            }
        }
        return keepTracking;
    }

    private void runRegistration(UUID seedId, CombineRegistration registration) {
        if (!(Bukkit.getEntity(seedId) instanceof Item seed) || !seed.isValid()) {
            return;
        }
        Location seedLocation = seed.getLocation();
        World world = seedLocation.getWorld();
        if (world == null) {
            return;
        }

        List<Item> nearbyItems = collectNearbyItems(world, seedLocation, registration.maxRadius, registration.verticalRadius);
        for (Item first : nearbyItems) {
            if (!first.isValid() || !registration.firstMatcher.test(first.getItemStack())) {
                continue;
            }
            for (Item second : nearbyItems) {
                if (first.getUniqueId().equals(second.getUniqueId())) {
                    continue;
                }
                if (!second.isValid() || !registration.secondMatcher.test(second.getItemStack())) {
                    continue;
                }
                if (!isPairWithinRange(first, second, registration)) {
                    continue;
                }
                registration.handler.accept(new CombineMatch(
                        first,
                        second,
                        getCenter(first, second),
                        registration.allowAirCombine && isAirborne(first, second)));
                return;
            }
        }
    }

    private List<Item> collectNearbyItems(World world, Location center, double horizontalRadius, double verticalRadius) {
        List<Item> items = new ArrayList<>();
        for (Entity entity : world.getNearbyEntities(center, horizontalRadius, verticalRadius, horizontalRadius)) {
            if (entity instanceof Item item && item.isValid()) {
                items.add(item);
            }
        }
        return items;
    }

    private boolean isPairWithinRange(Item first, Item second, CombineRegistration registration) {
        Location firstLocation = first.getLocation();
        Location secondLocation = second.getLocation();
        if (!firstLocation.getWorld().equals(secondLocation.getWorld())) {
            return false;
        }

        double horizontalDeltaX = firstLocation.getX() - secondLocation.getX();
        double horizontalDeltaZ = firstLocation.getZ() - secondLocation.getZ();
        double horizontalDistanceSquared = horizontalDeltaX * horizontalDeltaX + horizontalDeltaZ * horizontalDeltaZ;
        double allowedHorizontalRadius = registration.groundRadius;
        if (registration.allowAirCombine && isAirborne(first, second)) {
            allowedHorizontalRadius = registration.airRadius;
        }
        return horizontalDistanceSquared <= allowedHorizontalRadius * allowedHorizontalRadius
                && Math.abs(firstLocation.getY() - secondLocation.getY()) <= registration.verticalRadius;
    }

    private boolean isAirborne(Item first, Item second) {
        return !first.isOnGround() || !second.isOnGround();
    }

    private Location getCenter(Item first, Item second) {
        Location firstLocation = first.getLocation();
        Location secondLocation = second.getLocation();
        return new Location(firstLocation.getWorld(),
                (firstLocation.getX() + secondLocation.getX()) / 2.0D,
                (firstLocation.getY() + secondLocation.getY()) / 2.0D,
                (firstLocation.getZ() + secondLocation.getZ()) / 2.0D);
    }

    private void consumeItem(Item item, int amount) {
        ItemStack stack = item.getItemStack();
        int remain = stack.getAmount() - amount;
        if (remain <= 0) {
            item.remove();
            return;
        }
        stack.setAmount(remain);
        item.setItemStack(stack);
    }

    public final class CombineBuilder {
        private final String key;
        private Predicate<ItemStack> firstMatcher = stack -> false;
        private Predicate<ItemStack> secondMatcher = stack -> false;
        private double groundRadius = 0.5D;
        private double airRadius = 1.5D;
        private double verticalRadius = 2.0D;
        private boolean allowAirCombine = true;
        private long[] retryTicks = DEFAULT_RETRY_TICKS.clone();
        private long airRetryIntervalTicks = DEFAULT_AIR_RETRY_INTERVAL_TICKS;
        private long airTrackDurationTicks = DEFAULT_AIR_TRACK_DURATION_TICKS;

        private CombineBuilder(String key) {
            this.key = key;
        }

        public CombineBuilder first(Predicate<ItemStack> matcher) {
            this.firstMatcher = matcher;
            return this;
        }

        public CombineBuilder second(Predicate<ItemStack> matcher) {
            this.secondMatcher = matcher;
            return this;
        }

        public CombineBuilder groundRadius(double radius) {
            this.groundRadius = radius;
            return this;
        }

        public CombineBuilder airRadius(double radius) {
            this.airRadius = radius;
            return this;
        }

        public CombineBuilder verticalRadius(double radius) {
            this.verticalRadius = radius;
            return this;
        }

        public CombineBuilder allowAirCombine(boolean allow) {
            this.allowAirCombine = allow;
            return this;
        }

        public CombineBuilder airRetryIntervalTicks(long ticks) {
            this.airRetryIntervalTicks = Math.max(1L, ticks);
            return this;
        }

        public CombineBuilder airTrackDurationTicks(long ticks) {
            this.airTrackDurationTicks = Math.max(this.airRetryIntervalTicks, ticks);
            return this;
        }

        public CombineBuilder retryTicks(long... retryTicks) {
            this.retryTicks = retryTicks == null || retryTicks.length == 0
                    ? DEFAULT_RETRY_TICKS.clone()
                    : retryTicks.clone();
            return this;
        }

        public void then(Consumer<CombineMatch> handler) {
            registrations.put(key, new CombineRegistration(
                    key,
                    firstMatcher,
                    secondMatcher,
                    groundRadius,
                    airRadius,
                    verticalRadius,
                    allowAirCombine,
                    retryTicks,
                    airRetryIntervalTicks,
                    airTrackDurationTicks,
                    handler));
        }
    }

    private static final class CombineRegistration {
        private final String key;
        private final Predicate<ItemStack> firstMatcher;
        private final Predicate<ItemStack> secondMatcher;
        private final double groundRadius;
        private final double airRadius;
        private final double verticalRadius;
        private final boolean allowAirCombine;
        private final long[] retryTicks;
        private final long airRetryIntervalTicks;
        private final long airTrackDurationTicks;
        private final double maxRadius;
        private final Consumer<CombineMatch> handler;

        private CombineRegistration(
                String key,
                Predicate<ItemStack> firstMatcher,
                Predicate<ItemStack> secondMatcher,
                double groundRadius,
                double airRadius,
                double verticalRadius,
                boolean allowAirCombine,
                long[] retryTicks,
                long airRetryIntervalTicks,
                long airTrackDurationTicks,
                Consumer<CombineMatch> handler) {
            this.key = key;
            this.firstMatcher = firstMatcher;
            this.secondMatcher = secondMatcher;
            this.groundRadius = groundRadius;
            this.airRadius = airRadius;
            this.verticalRadius = verticalRadius;
            this.allowAirCombine = allowAirCombine;
            this.retryTicks = retryTicks;
            this.airRetryIntervalTicks = airRetryIntervalTicks;
            this.airTrackDurationTicks = airTrackDurationTicks;
            this.maxRadius = Math.max(groundRadius, allowAirCombine ? airRadius : groundRadius);
            this.handler = handler;
        }
    }

    public final class CombineMatch {
        private final Item first;
        private final Item second;
        private final Location center;
        private final boolean airborne;

        private CombineMatch(Item first, Item second, Location center, boolean airborne) {
            this.first = first;
            this.second = second;
            this.center = center;
            this.airborne = airborne;
        }

        public Item first() {
            return first;
        }

        public Item second() {
            return second;
        }

        public Location center() {
            return center.clone();
        }

        public boolean airborne() {
            return airborne;
        }

        public void consumeFirst(int amount) {
            consumeItem(first, amount);
        }

        public void consumeSecond(int amount) {
            consumeItem(second, amount);
        }

        public void consumeMatchedItems(int firstAmount, int secondAmount) {
            consumeFirst(firstAmount);
            consumeSecond(secondAmount);
        }
    }
}
