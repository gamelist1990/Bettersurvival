package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.EntityBlockFormEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityInteractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Protect のワールド変化トラッカー。
 *
 * 高頻度イベントではDBへ直接アクセスせず ProtectModule の非同期キューへ投入する。
 * 液体は短期の原因キャッシュを伝播させ、流したプレイヤーを可能な範囲で維持する。
 */
public final class ProtectWorldListener implements Listener {
    private static final long LIQUID_ACTOR_TTL_MS = TimeUnit.MINUTES.toMillis(30);
    private static final long GROWTH_LOOKBACK_MS = TimeUnit.DAYS.toMillis(30);

    private final Loader plugin;
    private final ProtectModule module;
    private final Map<BlockKey, ActorRef> liquidActors = new ConcurrentHashMap<>();
    private final Set<BlockKey> pendingPhysics = ConcurrentHashMap.newKeySet();
    private final AtomicInteger liquidFlowOps = new AtomicInteger();

    public ProtectWorldListener(Loader plugin, ProtectModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block target = event.getBlockClicked().getRelative(event.getBlockFace());
        Material placed = switch (event.getBucket()) {
            case WATER_BUCKET -> Material.WATER;
            case LAVA_BUCKET -> Material.LAVA;
            case POWDER_SNOW_BUCKET -> Material.POWDER_SNOW;
            default -> null;
        };
        if (placed == null) return;

        Player player = event.getPlayer();
        ActorRef actor = ActorRef.of(player);
        liquidActors.put(BlockKey.of(target), actor.withExpiry(System.currentTimeMillis() + LIQUID_ACTOR_TTL_MS));

        module.recordPlayer(
                player,
                target.getLocation(),
                ProtectAction.LIQUID_PLACE,
                target.getBlockData().getAsString(),
                Bukkit.createBlockData(placed).getAsString(),
                null, null, null,
                event.getBucket().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block source = event.getBlockClicked();
        module.recordPlayer(
                event.getPlayer(),
                source.getLocation(),
                ProtectAction.LIQUID_REMOVE,
                source.getBlockData().getAsString(),
                Bukkit.createBlockData(Material.AIR).getAsString(),
                null, null, null,
                event.getBucket().name());
        liquidActors.remove(BlockKey.of(source));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (!module.isRecordingEnabled()) return;
        cleanupLiquidActorsOccasionally();

        Block from = event.getBlock();
        Block to = event.getToBlock();
        Material sourceType = from.getType();

        if (sourceType == Material.WATER || sourceType == Material.LAVA) {
            ActorRef actor = resolveLiquidActor(from);
            if (actor != null) {
                liquidActors.put(BlockKey.of(to), actor.withExpiry(System.currentTimeMillis() + LIQUID_ACTOR_TTL_MS));
            }

            Location targetLocation = to.getLocation().clone();
            String before = to.getBlockData().getAsString();
            Bukkit.getScheduler().runTask(plugin, () -> {
                Block afterBlock = targetLocation.getBlock();
                String after = afterBlock.getBlockData().getAsString();
                if (before.equals(after)) return;
                recordActor(
                        actor,
                        targetLocation,
                        ProtectAction.LIQUID_FLOW,
                        before,
                        after,
                        sourceType.name());
            });
            return;
        }

        if (sourceType == Material.DRAGON_EGG) {
            String operationId = ProtectModule.newOperationId("dragon-egg");
            List<PendingMutation> mutations = new ArrayList<>(2);
            mutations.add(PendingMutation.capture(from));
            mutations.add(PendingMutation.capture(to));
            Bukkit.getScheduler().runTask(plugin, () -> {
                for (PendingMutation mutation : mutations) {
                    Block afterBlock = mutation.location().getBlock();
                    String after = afterBlock.getBlockData().getAsString();
                    if (mutation.before().equals(after)) continue;
                    module.recordSystemGrouped(
                            "#dragon_egg",
                            mutation.location(),
                            ProtectAction.BLOCK_MOVE,
                            mutation.before(),
                            after,
                            null,
                            mutation.beforeSnapshot(),
                            ProtectBlockSnapshot.capture(afterBlock.getState()),
                            "dragon egg teleport",
                            operationId);
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (!module.isRecordingEnabled()) return;

        ActorRef actor = event.getPlayer() != null
                ? ActorRef.of(event.getPlayer())
                : actorFor(event.getIgnitingEntity(), "#fire");

        Block block = event.getBlock();
        recordActor(
                actor,
                block.getLocation(),
                ProtectAction.FIRE_IGNITE,
                block.getBlockData().getAsString(),
                Bukkit.createBlockData(Material.FIRE).getAsString(),
                event.getCause().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (!module.isRecordingEnabled()) return;
        Block block = event.getBlock();
        module.recordSystem(
                "#fire",
                block.getLocation(),
                ProtectAction.FIRE_BURN,
                block.getBlockData().getAsString(),
                Bukkit.createBlockData(Material.AIR).getAsString(),
                null, ProtectBlockSnapshot.capture(block.getState()), null,
                "burn");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block block = event.getBlock();
        ProtectAction action = block.getType() == Material.FIRE || block.getType() == Material.SOUL_FIRE
                ? ProtectAction.FIRE_FADE
                : ProtectAction.NATURAL_FORM;
        module.recordSystem(
                action == ProtectAction.FIRE_FADE ? "#fire" : "#natural",
                block.getLocation(),
                action,
                block.getBlockData().getAsString(),
                event.getNewState().getBlockData().getAsString(),
                null, null, null,
                "fade");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block source = event.getSource();
        Block target = event.getBlock();
        String sourceName = source.getType().name();
        ProtectAction action;
        String actor;
        if (source.getType() == Material.FIRE || source.getType() == Material.SOUL_FIRE) {
            action = ProtectAction.FIRE_IGNITE;
            actor = "#fire";
        } else if (sourceName.startsWith("SCULK")) {
            action = ProtectAction.SCULK_SPREAD;
            actor = "#sculk";
        } else {
            action = ProtectAction.GROWTH;
            actor = "#growth";
        }

        module.recordSystem(
                actor,
                target.getLocation(),
                action,
                target.getBlockData().getAsString(),
                event.getNewState().getBlockData().getAsString(),
                null, null, null,
                "spread from " + sourceName);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGrow(BlockGrowEvent event) {
        if (!module.isRecordingEnabled()) return;
        Block block = event.getBlock();
        module.recordSystem(
                "#growth",
                block.getLocation(),
                ProtectAction.GROWTH,
                block.getBlockData().getAsString(),
                event.getNewState().getBlockData().getAsString(),
                null, null, null,
                block.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!module.isRecordingEnabled()) return;

        List<BlockMutation> mutations = new ArrayList<>(event.getBlocks().size());
        for (BlockState newState : event.getBlocks()) {
            Block block = newState.getBlock();
            mutations.add(new BlockMutation(
                    block.getLocation().clone(),
                    block.getBlockData().getAsString(),
                    newState.getBlockData().getAsString(),
                    newState.getType().name()));
        }

        Player player = event.getPlayer();
        if (player != null) {
            for (BlockMutation mutation : mutations) {
                module.recordPlayer(player, mutation.location(), ProtectAction.GROWTH,
                        mutation.before(), mutation.after(), null, null, null,
                        "structure " + event.getSpecies().name());
            }
            return;
        }

        Location origin = event.getLocation();
        long since = System.currentTimeMillis() - GROWTH_LOOKBACK_MS;
        module.getDatabase().queryNearby(
                        origin.getWorld().getUID().toString(),
                        origin.getBlockX(), origin.getBlockY(), origin.getBlockZ(),
                        0, since, null,
                        EnumSet.of(ProtectAction.BLOCK_PLACE),
                        1, 0)
                .whenComplete((records, throwable) -> Bukkit.getScheduler().runTask(plugin, () -> {
                    String uuid = null;
                    String name = "#growth";
                    if (throwable == null && records != null && !records.isEmpty()) {
                        ProtectRecord source = records.get(0);
                        uuid = source.actorUuid();
                        name = source.actorName();
                    }
                    for (BlockMutation mutation : mutations) {
                        module.recordActor(uuid, name, mutation.location(), ProtectAction.GROWTH,
                                mutation.before(), mutation.after(), null, null, null,
                                "structure " + event.getSpecies().name());
                    }
                }));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (!module.isRecordingEnabled()) return;
        Block block = event.getBlock();
        module.recordSystem(
                "#leaf_decay",
                block.getLocation(),
                ProtectAction.LEAF_DECAY,
                block.getBlockData().getAsString(),
                Bukkit.createBlockData(Material.AIR).getAsString(),
                null, null, null,
                block.getType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!module.isRecordingEnabled() || event instanceof EntityBlockFormEvent) return;

        Block block = event.getBlock();
        module.recordSystem(
                "#natural",
                block.getLocation(),
                ProtectAction.NATURAL_FORM,
                block.getBlockData().getAsString(),
                event.getNewState().getBlockData().getAsString(),
                null, null, null,
                "form");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityBlockForm(EntityBlockFormEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block block = event.getBlock();
        module.recordSystem(
                "#" + event.getEntity().getType().name().toLowerCase(Locale.ROOT),
                block.getLocation(),
                ProtectAction.NATURAL_FORM,
                block.getBlockData().getAsString(),
                event.getNewState().getBlockData().getAsString(),
                null, null, null,
                "entity form");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block block = event.getBlock();
        ProtectAction action = block.getType() == Material.FARMLAND && event.getTo() == Material.DIRT
                ? ProtectAction.FARMLAND_TRAMPLE
                : ProtectAction.ENTITY_CHANGE;

        ActorRef actor = actorFor(event.getEntity(),
                "#" + event.getEntityType().name().toLowerCase(Locale.ROOT));
        recordActor(
                actor,
                block.getLocation(),
                action,
                block.getBlockData().getAsString(),
                event.getBlockData().getAsString(),
                event.getEntityType().name());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityTrample(EntityInteractEvent event) {
        if (!module.isRecordingEnabled()) return;
        Block block = event.getBlock();
        if (block.getType() != Material.TURTLE_EGG) return;

        Location location = block.getLocation().clone();
        String before = block.getBlockData().getAsString();
        ActorRef actor = actorFor(
                event.getEntity(),
                "#" + event.getEntityType().name().toLowerCase(Locale.ROOT));
        Bukkit.getScheduler().runTask(plugin, () -> {
            String after = location.getBlock().getBlockData().getAsString();
            if (before.equals(after)) return;
            recordActor(actor, location, ProtectAction.FARMLAND_TRAMPLE,
                    before, after, "turtle egg trample");
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!module.isRecordingEnabled()) return;

        ActorRef actor = actorForExplosion(event.getEntity());
        for (Block block : event.blockList()) {
            recordActor(
                    actor,
                    block.getLocation(),
                    ProtectAction.EXPLOSION,
                    block.getBlockData().getAsString(),
                    Bukkit.createBlockData(Material.AIR).getAsString(),
                    ProtectBlockSnapshot.capture(block.getState()),
                    null,
                    event.getEntityType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!module.isRecordingEnabled()) return;

        for (Block block : event.blockList()) {
            module.recordSystem(
                    "#explosion",
                    block.getLocation(),
                    ProtectAction.EXPLOSION,
                    block.getBlockData().getAsString(),
                    Bukkit.createBlockData(Material.AIR).getAsString(),
                    null, ProtectBlockSnapshot.capture(block.getState()), null,
                    "source=" + event.getBlock().getType().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (!module.isRecordingEnabled()) return;
        trackPiston(event.getBlock(), event.getBlocks(), event.getDirection(), "extend");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (!module.isRecordingEnabled()) return;
        trackPiston(event.getBlock(), event.getBlocks(), event.getDirection(), "retract");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPortalCreate(PortalCreateEvent event) {
        if (!module.isRecordingEnabled()) return;

        String actor = event.getEntity() instanceof Player player
                ? player.getName()
                : "#portal";
        String uuid = event.getEntity() instanceof Player player
                ? player.getUniqueId().toString()
                : null;

        for (BlockState newState : event.getBlocks()) {
            Block block = newState.getBlock();
            module.recordActor(
                    uuid, actor, block.getLocation(), ProtectAction.PORTAL_CREATE,
                    block.getBlockData().getAsString(),
                    newState.getBlockData().getAsString(),
                    null, null, null,
                    event.getReason().name());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        if (!module.isRecordingEnabled()) return;

        Block block = event.getBlock();
        if (!shouldTrackPhysics(block.getType())) return;

        BlockKey key = BlockKey.of(block);
        if (!pendingPhysics.add(key)) return;

        Location location = block.getLocation().clone();
        String before = block.getBlockData().getAsString();
        String changedType = event.getChangedType().name();
        Bukkit.getScheduler().runTask(plugin, () -> {
            pendingPhysics.remove(key);
            Block afterBlock = location.getBlock();
            String after = afterBlock.getBlockData().getAsString();
            if (before.equals(after)) return;
            module.recordSystem(
                    "#physics", location, ProtectAction.BLOCK_PHYSICS,
                    before, after, null, null, null,
                    changedType);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpecialInteract(PlayerInteractEvent event) {
        if (!module.isRecordingEnabled() || event.getHand() != EquipmentSlot.HAND) return;
        Block block = event.getClickedBlock();
        if (block == null) return;

        Material type = block.getType();
        ItemStack hand = event.getItem();

        if (event.getAction() == Action.PHYSICAL && type == Material.TURTLE_EGG) {
            Player player = event.getPlayer();
            Location location = block.getLocation().clone();
            String before = block.getBlockData().getAsString();
            Bukkit.getScheduler().runTask(plugin, () -> {
                String after = location.getBlock().getBlockData().getAsString();
                if (before.equals(after)) return;
                module.recordPlayer(player, location, ProtectAction.FARMLAND_TRAMPLE,
                        before, after, null, null, null, "turtle egg trample");
            });
            return;
        }

        if ((type == Material.SUSPICIOUS_SAND || type == Material.SUSPICIOUS_GRAVEL)
                && hand != null && hand.getType() == Material.BRUSH) {
            module.recordPlayer(
                    event.getPlayer(), block.getLocation(), ProtectAction.BRUSH,
                    block.getBlockData().getAsString(), block.getBlockData().getAsString(),
                    null, ProtectModule.serializeItem(hand), null,
                    type.name());
            return;
        }

        if (isFlowerPot(type)) {
            Player player = event.getPlayer();
            Location location = block.getLocation().clone();
            String before = block.getBlockData().getAsString();
            byte[] handBytes = ProtectModule.serializeItem(hand);
            String typeName = type.name();
            Bukkit.getScheduler().runTask(plugin, () -> {
                String after = location.getBlock().getBlockData().getAsString();
                if (before.equals(after)) return;
                module.recordPlayer(
                        player, location,
                        ProtectAction.POT_CHANGE,
                        before, after, null,
                        handBytes, null,
                        typeName);
            });
        }
    }

    private void trackPiston(
            Block piston,
            List<Block> blocks,
            BlockFace direction,
            String detail) {
        if (piston == null) return;

        String operationId = ProtectModule.newOperationId("piston-" + detail);
        Map<BlockKey, PendingMutation> mutations = new LinkedHashMap<>();

        capturePistonCandidate(mutations, piston);
        capturePistonCandidate(mutations, piston.getRelative(direction));
        capturePistonCandidate(mutations, piston.getRelative(direction.getOppositeFace()));

        if (blocks != null) {
            for (Block source : blocks) {
                capturePistonCandidate(mutations, source);
                // 両側を取ることでextend/retractの方向解釈に依存せず、
                // 翌tickに実際に変化した座標だけを記録できる。
                capturePistonCandidate(mutations, source.getRelative(direction));
                capturePistonCandidate(mutations, source.getRelative(direction.getOppositeFace()));
            }
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PendingMutation mutation : mutations.values()) {
                Block afterBlock = mutation.location().getBlock();
                String after = afterBlock.getBlockData().getAsString();
                if (mutation.before().equals(after)) continue;
                module.recordSystemGrouped(
                        "#piston",
                        mutation.location(),
                        ProtectAction.BLOCK_MOVE,
                        mutation.before(),
                        after,
                        null,
                        mutation.beforeSnapshot(),
                        ProtectBlockSnapshot.capture(afterBlock.getState()),
                        detail,
                        operationId);
            }
        });
    }

    private void capturePistonCandidate(
            Map<BlockKey, PendingMutation> mutations,
            Block block) {
        mutations.putIfAbsent(BlockKey.of(block), PendingMutation.capture(block));
    }

    private void cleanupLiquidActorsOccasionally() {
        if ((liquidFlowOps.incrementAndGet() & 1023) != 0) return;
        long now = System.currentTimeMillis();
        liquidActors.entrySet().removeIf(entry -> entry.getValue().expiresAt() < now);
    }

    private ActorRef resolveLiquidActor(Block source) {
        long now = System.currentTimeMillis();
        ActorRef actor = liquidActors.get(BlockKey.of(source));
        if (actor == null) return null;
        if (actor.expiresAt() < now) {
            liquidActors.remove(BlockKey.of(source), actor);
            return null;
        }
        return actor;
    }

    private void recordActor(
            ActorRef actor,
            Location location,
            ProtectAction action,
            String before,
            String after,
            String detail) {
        recordActor(actor, location, action, before, after, null, null, detail);
    }

    private void recordActor(
            ActorRef actor,
            Location location,
            ProtectAction action,
            String before,
            String after,
            byte[] beforeSnapshot,
            byte[] afterSnapshot,
            String detail) {
        if (actor == null) {
            module.recordSystem("#liquid", location, action, before, after,
                    null, beforeSnapshot, afterSnapshot, detail);
        } else {
            module.recordActor(actor.uuid(), actor.name(), location, action, before, after,
                    null, beforeSnapshot, afterSnapshot, detail);
        }
    }

    private ActorRef actorForExplosion(Entity entity) {
        if (entity instanceof TNTPrimed tnt && tnt.getSource() != null) {
            return actorFor(tnt.getSource(), "#tnt");
        }
        return actorFor(entity, entity == null
                ? "#explosion"
                : "#" + entity.getType().name().toLowerCase(Locale.ROOT));
    }

    private ActorRef actorFor(Entity entity, String fallback) {
        if (entity instanceof Player player) {
            return ActorRef.of(player);
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return ActorRef.of(player);
        }
        return new ActorRef(null, fallback, Long.MAX_VALUE);
    }

    private boolean shouldTrackPhysics(Material type) {
        if (type == null || type.isAir()) return false;
        if (type.hasGravity()) return true;

        String name = type.name();
        return name.contains("TORCH")
                || name.contains("SIGN")
                || name.contains("BANNER")
                || name.contains("BUTTON")
                || name.contains("PRESSURE_PLATE")
                || name.contains("RAIL")
                || name.contains("VINE")
                || name.contains("LADDER")
                || name.contains("HANGING")
                || name.contains("CANDLE")
                || name.contains("FLOWER")
                || name.contains("SAPLING")
                || name.contains("CORAL")
                || name.contains("TRIPWIRE");
    }

    private boolean isFlowerPot(Material type) {
        return type == Material.FLOWER_POT || type.name().startsWith("POTTED_");
    }

    private record BlockKey(UUID world, int x, int y, int z) {
        static BlockKey of(Block block) {
            return new BlockKey(
                    block.getWorld().getUID(),
                    block.getX(), block.getY(), block.getZ());
        }
    }

    private record ActorRef(String uuid, String name, long expiresAt) {
        static ActorRef of(Player player) {
            return new ActorRef(
                    player.getUniqueId().toString(),
                    player.getName(),
                    Long.MAX_VALUE);
        }

        ActorRef withExpiry(long expiresAt) {
            return new ActorRef(uuid, name, expiresAt);
        }
    }

    private record PendingMutation(
            Location location,
            String before,
            byte[] beforeSnapshot) {
        static PendingMutation capture(Block block) {
            return new PendingMutation(
                    block.getLocation().clone(),
                    block.getBlockData().getAsString(),
                    ProtectBlockSnapshot.capture(block.getState()));
        }
    }

    private record BlockMutation(
            Location location,
            String before,
            String after,
            String detail) {
    }
}
