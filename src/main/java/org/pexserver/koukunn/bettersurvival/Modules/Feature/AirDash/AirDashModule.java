package org.pexserver.koukunn.bettersurvival.Modules.Feature.AirDash;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * エアダッシュ (Apex Legends のアッシュ風)。
 *
 * 空中でもう一度ジャンプキー(スペース)を押すと、視線方向へ短いダッシュが出る。
 * 空中にいる間だけ短時間 allowFlight を立て、飛行トグルをダッシュ入力として扱う。
 *
 * クールダウンは ActionBar にゲージ表示される。
 */
public class AirDashModule implements Listener {

    public static final String FEATURE_KEY = "airdash";

    private static final long COOLDOWN_MS = 5_000L;
    private static final double DASH_HORIZONTAL_POWER = 1.4D;
    private static final double DASH_VERTICAL_POWER = 0.3D;
    /** 空中判定と allowFlight の付け外しを行う間隔 */
    private static final long ARM_PERIOD_TICKS = 2L;
    /** ActionBar のクールダウン表示更新間隔 */
    private static final long ACTIONBAR_PERIOD_TICKS = 4L;
    /** トグル設定(ファイル読み込み)のキャッシュ更新間隔 */
    private static final long ENABLED_CACHE_PERIOD_TICKS = 40L;
    private static final double LANDING_DISARM_DISTANCE = 0.12D;
    private static final double PLAYER_EDGE_OFFSET = 0.3D;
    private static final long ARM_MAX_AGE_MS = 2_000L;

    private final ToggleModule toggle;

    /** このモジュールが allowFlight を立てたプレイヤー */
    private final Set<UUID> armed = new HashSet<>();
    /** AirDash待機開始時刻 */
    private final Map<UUID, Long> armedAt = new HashMap<>();
    /** 待機状態中の落下開始Y */
    private final Map<UUID, Double> armedFallBaseY = new HashMap<>();
    /** AirDash発動後、発動地点基準で落下距離を計算するためのY */
    private final Map<UUID, Double> dashFallBaseY = new HashMap<>();
    /** クールダウン終了時刻 (epoch ms) */
    private final Map<UUID, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Boolean> enabledCache = new ConcurrentHashMap<>();

    private final BukkitTask armTask;
    private final BukkitTask actionBarTask;
    private final BukkitTask enabledCacheTask;

    public AirDashModule(Loader plugin, ToggleModule toggle) {
        this.toggle = toggle;
        refreshEnabledCache();
        armTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickArm, ARM_PERIOD_TICKS, ARM_PERIOD_TICKS);
        actionBarTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickActionBar, ACTIONBAR_PERIOD_TICKS, ACTIONBAR_PERIOD_TICKS);
        enabledCacheTask = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshEnabledCache, ENABLED_CACHE_PERIOD_TICKS, ENABLED_CACHE_PERIOD_TICKS);
    }

    /** トグル設定はファイル読みなので、定期的にまとめてキャッシュする */
    private void refreshEnabledCache() {
        boolean global = toggle.getGlobal(FEATURE_KEY);
        enabledCache.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean enabled = global && toggle.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY);
            enabledCache.put(player.getUniqueId(), enabled);
        }
    }

    private boolean isEnabledFor(Player player) {
        return enabledCache.getOrDefault(player.getUniqueId(), false);
    }

    // ================= 空中での武装 (allowFlight 管理) =================

    private void tickArm(){
        for (Player player : Bukkit.getOnlinePlayers()) {
            UUID uuid = player.getUniqueId();
            boolean isArmed = armed.contains(uuid);
            if (isArmed && player.isFlying()) {
                player.setFlying(false);
            }
            if (!canDashNow(player)) {
                if (isArmed) {
                    disarm(player, true);
                }
                continue;
            }
            Long armedStart = armedAt.get(uuid);
            if (isArmed && armedStart != null && System.currentTimeMillis() - armedStart > ARM_MAX_AGE_MS) {
                disarm(player, true);
                continue;
            }
            if (!isArmed) {
                // 空中で入力受付の瞬間だけ allowFlight を付与
                if (!hasBlockBelow(player, LANDING_DISARM_DISTANCE)) {
                    player.setFlyingFallDamage(net.kyori.adventure.util.TriState.TRUE);
                    player.setAllowFlight(true);
                    armed.add(uuid);
                    armedAt.put(uuid, System.currentTimeMillis());
                }
            }
        }
    }

    /** 今この瞬間にダッシュ入力を受け付けてよい状態か */
    private boolean canDashNow(Player player) {
        if (!isEnabledFor(player)) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }
        if (player.isFlying() || player.isGliding() || player.isInsideVehicle()) {
            return false;
        }
        if (player.isInWater() || player.isInLava() || player.isClimbing()) {
            return false;
        }
        if (hasBlockBelow(player, LANDING_DISARM_DISTANCE)) {
            return false;
        }
        return remainingCooldownMs(player.getUniqueId()) <= 0L;
    }

    private long remainingCooldownMs(UUID uuid) {
        Long until = cooldownUntil.get(uuid);
        return until == null ? 0L : until - System.currentTimeMillis();
    }

    private void disarm(Player player, boolean removeAllowFlight) {
        UUID uuid = player.getUniqueId();
        if (armed.remove(uuid)) {
            player.setFlying(false);
            if (removeAllowFlight) {
                player.setAllowFlight(false);
            }
        }
        armedAt.remove(uuid);
        armedFallBaseY.remove(uuid);
    }

    // ================= ダッシュ発動 =================

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!armed.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);

        // ✅ ここが最重要：即flight無効化
        player.setAllowFlight(false);
        player.setFlying(false);

        disarm(player, false);

        if (!canDashNowIgnoringGround(player)) {
            return;
        }
        performDash(player);
    }

    /** トグルイベント時点では空中前提なので地面判定を除いて再チェック */
    private boolean canDashNowIgnoringGround(Player player) {
        if (!isEnabledFor(player)) {
            return false;
        }
        GameMode mode = player.getGameMode();
        if (mode != GameMode.SURVIVAL && mode != GameMode.ADVENTURE) {
            return false;
        }
        if (player.isGliding() || player.isInsideVehicle() || player.isInWater() || player.isInLava()) {
            return false;
        }
        return remainingCooldownMs(player.getUniqueId()) <= 0L;
    }

    private void performDash(Player player) {
        Vector direction = player.getEyeLocation().getDirection();
        Vector horizontal = new Vector(direction.getX(), 0.0D, direction.getZ());
        if (horizontal.lengthSquared() < 1.0E-4D) {
            // ほぼ真上/真下を向いている場合は体の向きへ
            float yaw = player.getLocation().getYaw();
            double rad = Math.toRadians(yaw);
            horizontal = new Vector(-Math.sin(rad), 0.0D, Math.cos(rad));
        }
        Vector velocity = horizontal.normalize().multiply(DASH_HORIZONTAL_POWER);
        velocity.setY(DASH_VERTICAL_POWER);
        player.setVelocity(velocity);
        // fallDistanceはVanillaに任せる

        cooldownUntil.put(player.getUniqueId(), System.currentTimeMillis() + COOLDOWN_MS);
        Location loc = player.getLocation().add(0.0D, 0.9D, 0.0D);
        player.getWorld().spawnParticle(Particle.CLOUD, loc, 14, 0.25D, 0.25D, 0.25D, 0.05D);
        player.getWorld().spawnParticle(Particle.CRIT, loc, 8, 0.2D, 0.2D, 0.2D, 0.1D);
        player.getWorld().playSound(loc, Sound.ITEM_TRIDENT_RIPTIDE_1, 0.8F, 1.6F);
        player.getWorld().playSound(loc, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.5F, 1.4F);
    }

    // ================= ActionBar クールダウン表示 =================

    private void tickActionBar() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Long> entry : new HashMap<>(cooldownUntil).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) {
                cooldownUntil.remove(entry.getKey());
                continue;
            }
            long remaining = entry.getValue() - now;
            if (remaining <= 0L) {
                cooldownUntil.remove(entry.getKey());
                continue;
            }
            if (!isEnabledFor(player)) {
                continue;
            }
            double ratio = 1.0D - (double) remaining / (double) COOLDOWN_MS;
            player.sendActionBar(ComponentUtils.legacy(cooldownBar(ratio)));
        }
    }

    private static String cooldownBar(double ratio) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * 10.0D);
        return (ratio >= 0.999D ? "§a" : ratio >= 0.5D ? "§e" : "§c")
                + "▰".repeat(filled) + "§8" + "▱".repeat(10 - filled);
    }

    // ================= 後始末 =================

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // ✅ 着地したらだけ解除（Vanillaに任せる）
        if (armed.contains(uuid) && hasBlockBelow(player, LANDING_DISARM_DISTANCE)) {
            disarm(player, true);
        }
    }



    private boolean hasBlockBelow(Player player, double distance) {
        Location loc = player.getLocation();
        return isSolidBelow(loc, 0.0D, 0.0D, distance)
                || isSolidBelow(loc, PLAYER_EDGE_OFFSET, PLAYER_EDGE_OFFSET, distance)
                || isSolidBelow(loc, PLAYER_EDGE_OFFSET, -PLAYER_EDGE_OFFSET, distance)
                || isSolidBelow(loc, -PLAYER_EDGE_OFFSET, PLAYER_EDGE_OFFSET, distance)
                || isSolidBelow(loc, -PLAYER_EDGE_OFFSET, -PLAYER_EDGE_OFFSET, distance);
    }

    private boolean isSolidBelow(Location loc, double offsetX, double offsetZ, double distance) {
        return loc.clone().add(offsetX, -distance, offsetZ).getBlock().getType().isSolid();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        disarm(player, true);
        cooldownUntil.remove(player.getUniqueId());
        dashFallBaseY.remove(player.getUniqueId());
        enabledCache.remove(player.getUniqueId());
    }

    @EventHandler
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        // クリエイティブ等へ切り替わる場合は allowFlight を奪わないよう即時解除
        disarm(event.getPlayer(), true);
    }

    public void shutdown() {
        armTask.cancel();
        actionBarTask.cancel();
        enabledCacheTask.cancel();
        for (UUID uuid : new HashSet<>(armed)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) {
                disarm(player, true);
            }
        }
        armed.clear();
        armedAt.clear();
        armedFallBaseY.clear();
        dashFallBaseY.clear();
        cooldownUntil.clear();
    }
}
