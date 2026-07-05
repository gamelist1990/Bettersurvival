package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 奈落救出 (voidrescue)。
 *
 * 奈落ダメージを受けると緊急救出を発動し、ファントムに乗って上空へ脱出する。
 * 鎧またはトーテム・オブ・アンダイングに付与可能。
 * 救出中は奈落ダメージを完全にキャンセルし、安全な高さ (Y=64) に達したら降ろす。
 */
public class VoidRescueEnchant extends CustomEnchant {

    private static final int SAFE_Y = 64;
    private static final int MAX_RESCUE_TICKS = 300;

    private final Set<UUID> rescuing = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> rescueTasks = new ConcurrentHashMap<>();
    private final Map<UUID, Phantom> rescuePhantoms = new ConcurrentHashMap<>();
    private final Map<UUID, Location> lastSafeLocations = new ConcurrentHashMap<>();
    private final Map<UUID, Location> rescueTargets = new ConcurrentHashMap<>();

    public VoidRescueEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "voidrescue";
    }

    @Override
    public String displayName() {
        return "奈落救出";
    }

    @Override
    public String description() {
        return "§7奈落ダメージを受けるとファントムが"
                + "\n§7上空まで連れ出してくれる緊急脱出";
    }

    @Override
    public Material icon() {
        return Material.PHANTOM_MEMBRANE;
    }

    @Override
    public String vanillaParentName() {
        return "ダメージ軽減";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || type == Material.TOTEM_OF_UNDYING;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 24),
                    new ItemStack(Material.PHANTOM_MEMBRANE, 4),
                    new ItemStack(Material.ENDER_PEARL, 4));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 40),
                    new ItemStack(Material.PHANTOM_MEMBRANE, 8),
                    new ItemStack(Material.ENDER_PEARL, 8));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 56),
                    new ItemStack(Material.PHANTOM_MEMBRANE, 16),
                    new ItemStack(Material.ENDER_PEARL, 16));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        UUID uid = player.getUniqueId();
        if (rescuing.contains(uid)) {
            event.setCancelled(true);
            return;
        }
        int level = getMaxEquippedLevel(player);
        if (level <= 0) {
            return;
        }
        event.setCancelled(true);
        startRescue(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uid = player.getUniqueId();
        if (rescuing.contains(uid)) {
            return;
        }
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        Location to = event.getTo();
        if (to == null || to.getWorld() == null) {
            return;
        }
        if (to.getY() <= to.getWorld().getMinHeight() + 4) {
            return;
        }
        if (isSafeStandLocation(to)) {
            lastSafeLocations.put(uid, center(to));
        }
    }

    private int getMaxEquippedLevel(Player player) {
        int max = 0;
        for (ItemStack item : player.getInventory().getArmorContents()) {
            int l = levelOf(item);
            if (l > max) {
                max = l;
            }
        }
        int offhand = levelOf(player.getInventory().getItemInOffHand());
        if (offhand > max) {
            max = offhand;
        }
        int mainhand = levelOf(player.getInventory().getItemInMainHand());
        if (mainhand > max) {
            max = mainhand;
        }
        return max;
    }

    private void startRescue(Player player) {
        UUID uid = player.getUniqueId();
        rescuing.add(uid);

        Location spawnLoc = player.getLocation().clone();
        World world = spawnLoc.getWorld();
        Location targetLoc = lastSafeLocations.get(uid);
        if (targetLoc == null || targetLoc.getWorld() == null || !targetLoc.getWorld().equals(world) || !isSafeStandLocation(targetLoc)) {
            targetLoc = findNearestSafeLanding(world, spawnLoc, player.getLocation().getYaw());
        }
        if (targetLoc == null || !isSafeStandLocation(targetLoc)) {
            targetLoc = center(world.getSpawnLocation());
        }
        rescueTargets.put(uid, targetLoc.clone());

        world.playSound(spawnLoc, Sound.ENTITY_PHANTOM_FLAP, 1.5f, 0.7f);
        player.sendActionBar(ComponentUtils.legacy("§d✦ 奈落救出 §7- ファントムが上空へ連れて行く!"));

        Phantom phantom = (Phantom) world.spawnEntity(spawnLoc, org.bukkit.entity.EntityType.PHANTOM);
        phantom.setSilent(true);
        phantom.setAI(false);
        phantom.setGravity(false);
        phantom.setInvulnerable(true);
        phantom.addPassenger(player);
        rescuePhantoms.put(uid, phantom);
        player.playEffect(org.bukkit.EntityEffect.PROTECTED_FROM_DEATH);
        world.spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, spawnLoc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.2);

        final int[] ticks = {0};
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!player.isOnline() || ticks[0] >= MAX_RESCUE_TICKS) {
                finishRescue(uid, player);
                return;
            }
            if (!phantom.isValid() || !phantom.getPassengers().contains(player)) {
                finishRescue(uid, player);
                return;
            }
            ticks[0]++;
            Location current = phantom.getLocation();
            if (current.getY() >= SAFE_Y) {
                finishRescue(uid, player);
                return;
            }
            phantom.teleport(current.add(0, 0.8, 0));
        }, 1L, 1L);
        rescueTasks.put(uid, task);
    }

    private void finishRescue(UUID uid, Player player) {
        BukkitTask task = rescueTasks.remove(uid);
        if (task != null) {
            task.cancel();
        }
        Phantom phantom = rescuePhantoms.remove(uid);
        Location rescueLoc = rescueTargets.remove(uid);
        if (phantom != null && phantom.isValid()) {
            phantom.eject();
            phantom.remove();
        }
        rescuing.remove(uid);
        if (player.isOnline()) {
            World world = player.getWorld();
            Location safeLoc = rescueLoc != null && rescueLoc.getWorld() != null && rescueLoc.getWorld().equals(world) && isSafeStandLocation(rescueLoc)
                    ? rescueLoc
                    : findNearestSafeLanding(world, player.getLocation(), player.getLocation().getYaw());
            if (safeLoc == null || !isSafeStandLocation(safeLoc)) {
                safeLoc = center(world.getSpawnLocation());
            }
            player.teleport(safeLoc, PlayerTeleportEvent.TeleportCause.PLUGIN);
            world.playSound(safeLoc, Sound.ENTITY_PHANTOM_DEATH, 1.0f, 1.2f);
            player.sendActionBar(ComponentUtils.legacy("§a✦ 奈落救出完了 §7- 安全な場所に降りました"));
        }
    }

    private Location findNearestSafeLanding(World world, Location base, float yaw) {
        int baseX = base.getBlockX();
        int baseZ = base.getBlockZ();
        for (int radius = 0; radius <= 12; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) != radius && Math.abs(dz) != radius) {
                        continue;
                    }
                    Location found = findSafeLanding(world, baseX + dx, baseZ + dz, base.getY(), yaw);
                    if (found != null) {
                        return found;
                    }
                }
            }
        }
        return null;
    }

    private Location findSafeLanding(World world, int x, int z, double baseY, float yaw) {
        int startY = Math.min((int) baseY, world.getMaxHeight() - 3);
        for (int y = startY; y > world.getMinHeight() + 1; y--) {
            Location candidate = new Location(world, x + 0.5, y + 1, z + 0.5, yaw, 0);
            if (isSafeStandLocation(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    private boolean isSafeStandLocation(Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        World world = location.getWorld();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        if (y <= world.getMinHeight() + 1 || y >= world.getMaxHeight() - 2) {
            return false;
        }
        Block floor = world.getBlockAt(x, y - 1, z);
        Block feet = world.getBlockAt(x, y, z);
        Block head = world.getBlockAt(x, y + 1, z);
        return floor.getType().isSolid()
                && !floor.isLiquid()
                && feet.isPassable()
                && head.isPassable()
                && !feet.isLiquid()
                && !head.isLiquid();
    }

    private Location center(Location location) {
        return new Location(
                location.getWorld(),
                location.getBlockX() + 0.5,
                location.getBlockY(),
                location.getBlockZ() + 0.5,
                location.getYaw(),
                location.getPitch());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        finishRescue(event.getPlayer().getUniqueId(), event.getPlayer());
    }

    @Override
    public void shutdown() {
        for (UUID uid : Set.copyOf(rescuing)) {
            Player player = plugin.getServer().getPlayer(uid);
            if (player != null) {
                finishRescue(uid, player);
            } else {
                rescueTasks.remove(uid);
                Phantom ph = rescuePhantoms.remove(uid);
                if (ph != null && ph.isValid()) {
                    ph.eject();
                    ph.remove();
                }
                rescueTargets.remove(uid);
                rescuing.remove(uid);
            }
        }
    }
}
