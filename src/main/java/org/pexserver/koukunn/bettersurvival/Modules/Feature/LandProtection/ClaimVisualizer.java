package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 保護エリアのデバッグ境界線をパーティクルで描画する。
 *
 * 有効化したプレイヤーにのみ表示され、
 * 自分がアクセス可能なエリアは緑、他人のエリアは赤で描画される。
 * 境界線に近づくほど密度の高い縦の壁として見えるため、
 * 保護範囲を視覚的に確認しやすい。
 */
public class ClaimVisualizer {

    /** 境界線を描画する対象プレイヤーからの最大距離（水平） */
    private static final double VIEW_RANGE = 64.0;
    /** パーティクル1点を描画するプレイヤーからの最大距離 */
    private static final double POINT_RANGE = 32.0;
    private static final long PERIOD_TICKS = 10L;

    private static final Particle.DustOptions COLOR_ACCESSIBLE =
            new Particle.DustOptions(Color.fromRGB(0x55FF55), 1.1F);
    private static final Particle.DustOptions COLOR_FOREIGN =
            new Particle.DustOptions(Color.fromRGB(0xFF5555), 1.1F);
    private static final Particle.DustOptions COLOR_INACTIVE =
            new Particle.DustOptions(Color.fromRGB(0xAAAAAA), 1.0F);

    private final LandProtectionModule module;
    private final Set<UUID> enabledPlayers = ConcurrentHashMap.newKeySet();
    private final BukkitTask task;

    public ClaimVisualizer(Loader plugin, LandProtectionModule module) {
        this.module = module;
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, PERIOD_TICKS, PERIOD_TICKS);
    }

    public boolean isEnabled(Player player) {
        return enabledPlayers.contains(player.getUniqueId());
    }

    /** デバッグ表示を切り替える。戻り値は切替後の状態。 */
    public boolean toggle(Player player) {
        UUID uuid = player.getUniqueId();
        if (enabledPlayers.remove(uuid)) {
            return false;
        }
        enabledPlayers.add(uuid);
        return true;
    }

    public void disable(UUID uuid) {
        enabledPlayers.remove(uuid);
    }

    public void shutdown() {
        task.cancel();
        enabledPlayers.clear();
    }

    private void tick() {
        if (enabledPlayers.isEmpty()) {
            return;
        }
        for (UUID uuid : enabledPlayers) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                enabledPlayers.remove(uuid);
                continue;
            }
            drawForPlayer(player);
        }
    }

    private void drawForPlayer(Player player) {
        World world = player.getWorld();
        Location loc = player.getLocation();
        for (ClaimRegion claim : module.getClaimsInWorld(world.getName())) {
            int radius = claim.getRadius();
            double dx = Math.abs(loc.getX() - (claim.getX() + 0.5));
            double dz = Math.abs(loc.getZ() - (claim.getZ() + 0.5));
            if (dx > radius + VIEW_RANGE || dz > radius + VIEW_RANGE) {
                continue;
            }
            Particle.DustOptions color;
            if (!claim.isActive()) {
                color = COLOR_INACTIVE;
            } else if (module.canBypass(player, claim)) {
                color = COLOR_ACCESSIBLE;
            } else {
                color = COLOR_FOREIGN;
            }
            drawBoundary(player, claim, color);
        }
    }

    /**
     * 保護範囲の外周を描画する。
     * プレイヤーの足元高さ±2段の横ライン + コア位置の目印を表示する。
     */
    private void drawBoundary(Player player, ClaimRegion claim, Particle.DustOptions color) {
        World world = player.getWorld();
        int radius = claim.getRadius();
        double minX = claim.getX() - radius;
        double maxX = claim.getX() + radius + 1.0;
        double minZ = claim.getZ() - radius;
        double maxZ = claim.getZ() + radius + 1.0;
        double baseY = Math.floor(player.getLocation().getY());

        double[] heights = {baseY - 1.0, baseY + 0.5, baseY + 2.0};
        double step = 1.0;
        Location point = new Location(world, 0, 0, 0);

        for (double y : heights) {
            // 北辺・南辺
            for (double x = minX; x <= maxX; x += step) {
                spawnPoint(player, point, x, y, minZ, color);
                spawnPoint(player, point, x, y, maxZ, color);
            }
            // 西辺・東辺
            for (double z = minZ; z <= maxZ; z += step) {
                spawnPoint(player, point, minX, y, z, color);
                spawnPoint(player, point, maxX, y, z, color);
            }
        }

        // 四隅の縦ライン
        for (double y = baseY - 4.0; y <= baseY + 6.0; y += 1.0) {
            spawnPoint(player, point, minX, y, minZ, color);
            spawnPoint(player, point, minX, y, maxZ, color);
            spawnPoint(player, point, maxX, y, minZ, color);
            spawnPoint(player, point, maxX, y, maxZ, color);
        }

        // コア位置の目印（縦ビーム）
        double coreX = claim.getX() + 0.5;
        double coreZ = claim.getZ() + 0.5;
        for (double y = claim.getY() + 1.0; y <= claim.getY() + 4.0; y += 0.5) {
            spawnPoint(player, point, coreX, y, coreZ, color);
        }
    }

    private void spawnPoint(Player player, Location reuse, double x, double y, double z, Particle.DustOptions color) {
        Location loc = player.getLocation();
        double dx = loc.getX() - x;
        double dz = loc.getZ() - z;
        if (dx * dx + dz * dz > POINT_RANGE * POINT_RANGE) {
            return;
        }
        reuse.setX(x);
        reuse.setY(y);
        reuse.setZ(z);
        player.spawnParticle(Particle.DUST, reuse, 1, 0, 0, 0, 0, color);
    }
}
