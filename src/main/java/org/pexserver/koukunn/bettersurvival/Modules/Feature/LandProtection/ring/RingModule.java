package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ClaimRegion;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.LandProtectionModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.ui.RingMenu;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 土地保護内の闘技場「リング」モジュール。
 *
 * オーナー又は副リーダーが保護領域内に円形のリングを設置でき、
 * リング内では PVP が有効になる。境界に近づくとパーティクルの壁が
 * 見えるため境界線が分かる。KeepInventory・即時復活・復活地点
 * （リング中心から50ブロック以内）を設定できる。
 *
 * Duel モードのリングでは Duel を開始するまで攻撃できず、
 * 右クリックで出る UI から Duel を開始すると 3・2・1 → FIGHT の
 * 演出とともに試合が始まる。対戦ボタンを押すと自動マッチングの
 * 待機列に入り、対応リングへ TP して試合、終了後に元の場所へ戻る。
 */
public class RingModule implements Listener {

    private static final long PARTICLE_PERIOD_TICKS = 10L;
    private static final long TICK_PERIOD_TICKS = 20L;
    /** 境界パーティクルが見え始める境界からの距離 */
    private static final double BOUNDARY_VIEW_DISTANCE = 8.0;
    private static final long DUEL_REQUEST_EXPIRE_MILLIS = 30_000L;

    private static final Particle.DustOptions BOUNDARY_COLOR =
            new Particle.DustOptions(Color.fromRGB(0xFFAA00), 1.2F);
    private static final Particle.DustOptions BOUNDARY_COLOR_FIGHT =
            new Particle.DustOptions(Color.fromRGB(0xFF4444), 1.3F);

    private final Loader plugin;
    private final LandProtectionModule landModule;
    private final RingStore store;
    private final RingMenu menu;
    private final Random random = new Random();

    /** claimKey -> リング */
    private final Map<String, RingRegion> rings = new LinkedHashMap<>();
    /** 対戦ボタンの位置キー (world:x:y:z) */
    private final Set<String> duelButtons = new LinkedHashSet<>();

    /** claimKey -> 進行中の Duel */
    private final Map<String, DuelSession> sessionByRing = new ConcurrentHashMap<>();
    /** 参加者 -> 進行中の Duel */
    private final Map<UUID, DuelSession> sessionByPlayer = new ConcurrentHashMap<>();
    /** マッチング待機列 (uuid -> 参加時の位置) */
    private final Map<UUID, Location> matchQueue = new LinkedHashMap<>();
    /** 対戦ボタン登録モード中のプレイヤー */
    private final Set<UUID> registeringButton = ConcurrentHashMap.newKeySet();
    /** Duel リクエスト (受信者 -> リクエスト) */
    private final Map<UUID, DuelRequest> duelRequests = new ConcurrentHashMap<>();
    /** 死亡後の復活地点 */
    private final Map<UUID, Location> pendingRespawn = new ConcurrentHashMap<>();
    /** リング内で設置されたブロック (位置キー -> 設置者)。設置者本人のみ破壊できる */
    private final Map<String, UUID> placedBlocks = new ConcurrentHashMap<>();
    /** マッチング不成立通知のクールダウン */
    private long lastQueueNotice;
    /** 外部モジュール（トーナメント等）が占有中のリング (claimKey) */
    private final Set<String> lockedRings = ConcurrentHashMap.newKeySet();

    private final BukkitTask particleTask;
    private final BukkitTask tickTask;

    private record DuelRequest(UUID requester, String claimKey, long expireAt) {
    }

    public RingModule(Loader plugin, LandProtectionModule landModule) {
        this.plugin = plugin;
        this.landModule = landModule;
        this.store = new RingStore(plugin.getConfigManager());
        rings.putAll(store.loadRings());
        duelButtons.addAll(store.loadButtons());
        this.menu = new RingMenu(plugin, this);
        this.particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickParticles,
                PARTICLE_PERIOD_TICKS, PARTICLE_PERIOD_TICKS);
        this.tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickSecond,
                TICK_PERIOD_TICKS, TICK_PERIOD_TICKS);
    }

    public RingMenu getMenu() {
        return menu;
    }

    public LandProtectionModule getLandModule() {
        return landModule;
    }

    public void shutdown() {
        particleTask.cancel();
        tickTask.cancel();
        for (DuelSession session : new ArrayList<>(sessionByRing.values())) {
            abortDuel(session, "§eサーバー停止のため Duel を中断しました");
        }
        matchQueue.clear();
        saveAll();
    }

    public void saveAll() {
        store.saveAll(rings.values(), duelButtons);
    }

    // ================= 参照 API =================

    public RingRegion getRing(String claimKey) {
        return rings.get(claimKey);
    }

    /** リングが有効か（親の保護領域が存在し保護が有効）。 */
    public boolean isRingActive(RingRegion ring) {
        ClaimRegion claim = landModule.getClaimByKey(ring.getClaimKey());
        return claim != null && claim.isActive();
    }

    /** 指定座標を含む有効なリングを返す（なければ null）。 */
    public RingRegion getActiveRingAt(Location location) {
        if (location == null || location.getWorld() == null) {
            return null;
        }
        for (RingRegion ring : rings.values()) {
            if (ring.contains(location) && isRingActive(ring)) {
                return ring;
            }
        }
        return null;
    }

    public DuelSession getSession(UUID uuid) {
        return sessionByPlayer.get(uuid);
    }

    public DuelSession getSessionInRing(RingRegion ring) {
        return sessionByRing.get(ring.getClaimKey());
    }

    public boolean isQueued(UUID uuid) {
        return matchQueue.containsKey(uuid);
    }

    public int getQueueSize() {
        return matchQueue.size();
    }

    public int getButtonCount() {
        return duelButtons.size();
    }

    /** 登録済みリングの一覧（読み取り専用）。 */
    public java.util.Collection<RingRegion> getRings() {
        return java.util.Collections.unmodifiableCollection(rings.values());
    }

    // ================= 外部モジュール連携 (トーナメント等) =================

    /** リングを占有ロックする。ロック中は通常の Duel・自動マッチングに使われない。 */
    public void lockRing(String claimKey) {
        lockedRings.add(claimKey);
    }

    public void unlockRing(String claimKey) {
        lockedRings.remove(claimKey);
    }

    public boolean isRingLocked(String claimKey) {
        return lockedRings.contains(claimKey);
    }

    /**
     * 外部モジュールが指定した 2 人で Duel を開始する（トーナメントの試合消化用）。
     * 両者を現在地からリングへ移動し、終了後に元の場所へ戻す。
     * 結果はコールバックで通知される。開始できなければ false。
     */
    public boolean startArrangedDuel(RingRegion ring, Player a, Player b,
            DuelSession.ResultCallback callback) {
        if (sessionByRing.containsKey(ring.getClaimKey())
                || sessionByPlayer.containsKey(a.getUniqueId())
                || sessionByPlayer.containsKey(b.getUniqueId())
                || !isRingActive(ring) || ring.getWorld() == null) {
            return false;
        }
        matchQueue.remove(a.getUniqueId());
        matchQueue.remove(b.getUniqueId());
        DuelSession session = new DuelSession(ring, a.getUniqueId(), b.getUniqueId(), true);
        session.setResultCallback(callback);
        session.getReturnLocations().put(a.getUniqueId(), a.getLocation().clone());
        session.getReturnLocations().put(b.getUniqueId(), b.getLocation().clone());
        beginDuel(session);
        return true;
    }

    private DuelRequest getRequestFor(UUID uuid) {
        DuelRequest request = duelRequests.get(uuid);
        if (request != null && request.expireAt() < System.currentTimeMillis()) {
            duelRequests.remove(uuid);
            return null;
        }
        return request;
    }

    /** 指定リングで自分宛の Duel リクエストを出しているプレイヤー（なければ null）。 */
    public UUID getPendingRequester(UUID target, RingRegion ring) {
        DuelRequest request = getRequestFor(target);
        if (request == null || !request.claimKey().equals(ring.getClaimKey())) {
            return null;
        }
        return request.requester();
    }

    /**
     * 土地保護の PVP 制限・フレンドリーファイアを無効化してよいか。
     * 攻撃側と被弾側の両方が同じ有効なリング内にいる場合に許可する。
     * （Duel モードの未開始チェックは本モジュールのハンドラで行う）
     */
    public boolean allowsPvp(Player attacker, Player victim) {
        RingRegion ring = getActiveRingAt(victim.getLocation());
        return ring != null && ring.contains(attacker.getLocation());
    }

    // ================= リング作成 / 削除 =================

    /** リングを作成する。失敗時はエラーメッセージ、成功時は null。 */
    public String createRing(Player player, ClaimRegion claim) {
        if (rings.containsKey(claim.key())) {
            return "この保護領域には既にリングが設置されています";
        }
        Location loc = player.getLocation();
        RingRegion ring = new RingRegion(claim.key(), claim.getWorldName(),
                loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());
        String error = validateInsideClaim(claim, ring, ring.getRadius());
        if (error != null) {
            return error;
        }
        rings.put(claim.key(), ring);
        saveAll();
        return null;
    }

    public void deleteRing(RingRegion ring) {
        DuelSession session = sessionByRing.get(ring.getClaimKey());
        if (session != null) {
            abortDuel(session, "§eリングが削除されたため Duel を中断しました");
        }
        rings.remove(ring.getClaimKey());
        saveAll();
    }

    /** リング（円）が保護領域（正方形）に収まっているか検証する。 */
    public String validateInsideClaim(ClaimRegion claim, RingRegion ring, int radius) {
        int margin = claim.getRadius() - radius;
        if (margin < 0
                || Math.abs(ring.getCenterX() - claim.getX()) > margin
                || Math.abs(ring.getCenterZ() - claim.getZ()) > margin) {
            return "リングが保護範囲からはみ出しています (保護半径 " + claim.getRadius()
                    + " / リング半径 " + radius + ")";
        }
        return null;
    }

    // ================= 対戦ボタン =================

    public void beginButtonRegistration(Player player) {
        registeringButton.add(player.getUniqueId());
    }

    public void clearButtons() {
        duelButtons.clear();
        saveAll();
    }

    private static String blockKey(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    // ================= マッチング =================

    public void joinQueue(Player player) {
        if (sessionByPlayer.containsKey(player.getUniqueId())) {
            player.sendMessage("§cDuel 中はマッチングに参加できません");
            return;
        }
        if (matchQueue.remove(player.getUniqueId()) != null) {
            player.sendMessage("§e対戦待ちを取り消しました");
            return;
        }
        matchQueue.put(player.getUniqueId(), player.getLocation().clone());
        player.sendMessage("§a対戦待ちに登録しました §7(現在 " + matchQueue.size() + " 人待機中)");
        player.sendMessage("§7マッチングが成立すると自動的にリングへ移動します。もう一度押すと取り消せます");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8F, 1.4F);
    }

    private void tickMatchmaking() {
        // オフラインの待機者を除去
        matchQueue.keySet().removeIf(uuid -> Bukkit.getPlayer(uuid) == null);
        if (matchQueue.size() < 2) {
            return;
        }
        RingRegion ring = findAvailableMatchRing();
        if (ring == null) {
            long now = System.currentTimeMillis();
            if (now - lastQueueNotice > 15_000L) {
                lastQueueNotice = now;
                for (UUID uuid : matchQueue.keySet()) {
                    Player waiting = Bukkit.getPlayer(uuid);
                    if (waiting != null) {
                        waiting.sendMessage("§e現在空いている対戦リングがありません。空き待ちです…");
                    }
                }
            }
            return;
        }
        Iterator<Map.Entry<UUID, Location>> it = matchQueue.entrySet().iterator();
        Map.Entry<UUID, Location> first = it.next();
        it.remove();
        Map.Entry<UUID, Location> second = it.next();
        it.remove();
        Player a = Bukkit.getPlayer(first.getKey());
        Player b = Bukkit.getPlayer(second.getKey());
        if (a == null || b == null) {
            return;
        }
        DuelSession session = new DuelSession(ring, a.getUniqueId(), b.getUniqueId(), true);
        session.getReturnLocations().put(a.getUniqueId(), first.getValue());
        session.getReturnLocations().put(b.getUniqueId(), second.getValue());
        a.sendMessage("§a§lマッチング成立！ §f対戦相手: §e" + b.getName());
        b.sendMessage("§a§lマッチング成立！ §f対戦相手: §e" + a.getName());
        beginDuel(session);
    }

    private RingRegion findAvailableMatchRing() {
        for (RingRegion ring : rings.values()) {
            if (ring.isAutoMatch() && isRingActive(ring)
                    && !sessionByRing.containsKey(ring.getClaimKey())
                    && !lockedRings.contains(ring.getClaimKey())
                    && ring.getWorld() != null) {
                return ring;
            }
        }
        return null;
    }

    // ================= Duel リクエスト =================

    /** リング内の相手へ Duel を申し込む。 */
    public void requestDuel(Player requester, Player target, RingRegion ring) {
        if (lockedRings.contains(ring.getClaimKey())) {
            requester.sendMessage("§cこのリングは現在トーナメント開催中のため使用できません");
            return;
        }
        if (sessionByRing.containsKey(ring.getClaimKey())) {
            requester.sendMessage("§cこのリングでは既に Duel が進行中です");
            return;
        }
        if (sessionByPlayer.containsKey(target.getUniqueId())) {
            requester.sendMessage("§c相手は既に Duel 中です");
            return;
        }
        // 相手が既に自分へ申し込んでいたら即開始
        DuelRequest incoming = getRequestFor(requester.getUniqueId());
        if (incoming != null && incoming.requester().equals(target.getUniqueId())) {
            acceptDuel(requester, ring);
            return;
        }
        duelRequests.put(target.getUniqueId(), new DuelRequest(requester.getUniqueId(),
                ring.getClaimKey(), System.currentTimeMillis() + DUEL_REQUEST_EXPIRE_MILLIS));
        requester.sendMessage("§e" + target.getName() + " §fに Duel を申し込みました。相手の承諾を待っています…");
        target.sendMessage("§6§l[Duel] §e" + requester.getName() + " §fから Duel を申し込まれました！");
        target.sendMessage("§7リング内で素手で右クリック → §a承諾して開始 §7で試合が始まります (30秒で失効)");
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, 1.8F);
    }

    /** 受け取った Duel リクエストを承諾して開始する。 */
    public void acceptDuel(Player accepter, RingRegion ring) {
        DuelRequest request = getRequestFor(accepter.getUniqueId());
        if (request == null || !request.claimKey().equals(ring.getClaimKey())) {
            accepter.sendMessage("§c有効な Duel リクエストがありません");
            return;
        }
        duelRequests.remove(accepter.getUniqueId());
        if (lockedRings.contains(ring.getClaimKey())) {
            accepter.sendMessage("§cこのリングは現在トーナメント開催中のため使用できません");
            return;
        }
        Player requester = Bukkit.getPlayer(request.requester());
        if (requester == null) {
            accepter.sendMessage("§c相手がオフラインになりました");
            return;
        }
        if (sessionByRing.containsKey(ring.getClaimKey())) {
            accepter.sendMessage("§cこのリングでは既に Duel が進行中です");
            return;
        }
        if (!ring.contains(requester.getLocation()) || !ring.contains(accepter.getLocation())) {
            accepter.sendMessage("§c両者がリング内にいる必要があります");
            return;
        }
        beginDuel(new DuelSession(ring, requester.getUniqueId(), accepter.getUniqueId(), false));
    }

    // ================= Duel 進行 =================

    private void beginDuel(DuelSession session) {
        RingRegion ring = session.getRing();
        Player a = Bukkit.getPlayer(session.getPlayerA());
        Player b = Bukkit.getPlayer(session.getPlayerB());
        if (a == null || b == null) {
            return;
        }
        sessionByRing.put(ring.getClaimKey(), session);
        sessionByPlayer.put(a.getUniqueId(), session);
        sessionByPlayer.put(b.getUniqueId(), session);

        positionPlayers(session, a, b);
        resetForFight(a);
        resetForFight(b);
        showCountdown(session, session.getCountdown());
    }

    /** 開始位置モードに応じて両者を配置する。 */
    private void positionPlayers(DuelSession session, Player a, Player b) {
        RingRegion ring = session.getRing();
        Location locA;
        Location locB;
        switch (ring.getStartMode()) {
            case CUSTOM -> {
                if (ring.getPos1() != null && ring.getPos2() != null) {
                    locA = ring.getPos1().clone();
                    locB = ring.getPos2().clone();
                } else {
                    Location[] pair = randomStartPositions(ring);
                    locA = pair[0];
                    locB = pair[1];
                }
            }
            case RANDOM -> {
                Location[] pair = randomStartPositions(ring);
                locA = pair[0];
                locB = pair[1];
            }
            case FREE -> {
                if (session.isMatchmade()) {
                    // マッチング経由はリング外から来るためランダム配置にフォールバック
                    Location[] pair = randomStartPositions(ring);
                    locA = pair[0];
                    locB = pair[1];
                } else {
                    // 移動なし
                    locA = null;
                    locB = null;
                }
            }
            default -> {
                locA = null;
                locB = null;
            }
        }
        if (locA != null) {
            a.teleport(locA);
        }
        if (locB != null) {
            b.teleport(locB);
        }
        faceEachOther(a, b);
    }

    /** リング内で最低 10 ブロック離れた対角の 2 地点を返す。 */
    private Location[] randomStartPositions(RingRegion ring) {
        World world = ring.getWorld();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = Math.max(RingRegion.DUEL_MIN_DISTANCE / 2.0 + 0.5,
                Math.min(ring.getRadius() - 1.5, ring.getRadius() * 0.7));
        double cx = ring.getCenterX() + 0.5;
        double cz = ring.getCenterZ() + 0.5;
        Location a = groundLocation(world, cx + Math.cos(angle) * distance,
                ring.getCenterY(), cz + Math.sin(angle) * distance);
        Location b = groundLocation(world, cx - Math.cos(angle) * distance,
                ring.getCenterY(), cz - Math.sin(angle) * distance);
        return new Location[]{a, b};
    }

    /** 指定 XZ で足場になる高さを探す。 */
    private Location groundLocation(World world, double x, int baseY, double z) {
        int bx = (int) Math.floor(x);
        int bz = (int) Math.floor(z);
        for (int y = baseY + 3; y >= baseY - RingRegion.VERTICAL_BELOW; y--) {
            Block foot = world.getBlockAt(bx, y, bz);
            Block below = world.getBlockAt(bx, y - 1, bz);
            if (foot.isPassable() && !below.isPassable()) {
                return new Location(world, x, y, z);
            }
        }
        return new Location(world, x, baseY, z);
    }

    private void faceEachOther(Player a, Player b) {
        lookAt(a, b.getLocation());
        lookAt(b, a.getLocation());
    }

    private void lookAt(Player player, Location target) {
        Location loc = player.getLocation();
        Vector dir = target.toVector().subtract(loc.toVector());
        if (dir.lengthSquared() < 0.01) {
            return;
        }
        loc.setDirection(dir.setY(0).normalize());
        player.teleport(loc);
    }

    /** 試合開始時のリセット（体力全回復・ポーション効果クリア・満腹度MAX）。 */
    private void resetForFight(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.MAX_HEALTH);
        player.setHealth(maxHealth == null ? 20.0 : maxHealth.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20.0F);
        player.setExhaustion(0.0F);
        player.setFireTicks(0);
        player.setFallDistance(0.0F);
        for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
            player.removePotionEffect(effect.getType());
        }
    }

    private void tickSecond() {
        tickMatchmaking();
        // カウントダウン進行
        for (DuelSession session : new ArrayList<>(sessionByRing.values())) {
            if (session.getState() != DuelSession.State.COUNTDOWN) {
                continue;
            }
            Player a = Bukkit.getPlayer(session.getPlayerA());
            Player b = Bukkit.getPlayer(session.getPlayerB());
            if (a == null || b == null) {
                abortDuel(session, "§e相手がオフラインになったため Duel を中断しました");
                continue;
            }
            int remaining = session.tickCountdown();
            if (remaining > 0) {
                showCountdown(session, remaining);
            } else {
                startFight(session, a, b);
            }
        }
        // 期限切れリクエストの掃除
        long now = System.currentTimeMillis();
        duelRequests.values().removeIf(request -> request.expireAt() < now);
    }

    private void showCountdown(DuelSession session, int number) {
        String color = switch (number) {
            case 3 -> "§e";
            case 2 -> "§6";
            default -> "§c";
        };
        float pitch = switch (number) {
            case 3 -> 1.0F;
            case 2 -> 1.25F;
            default -> 1.5F;
        };
        Title title = Title.title(
                ComponentUtils.legacy(color + "§l" + number),
                ComponentUtils.legacy("§7まもなく試合開始…"),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(900), Duration.ofMillis(100)));
        for (Player player : duelAudience(session)) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0F, pitch);
        }
        Location center = session.getRing().getCenterLocation();
        if (center != null) {
            center.getWorld().spawnParticle(Particle.FLAME, center.clone().add(0, 1.5, 0),
                    20, 1.2, 0.8, 1.2, 0.01);
        }
    }

    private void startFight(DuelSession session, Player a, Player b) {
        session.setState(DuelSession.State.FIGHTING);
        Title title = Title.title(
                ComponentUtils.legacy("§c§l⚔ FIGHT ⚔"),
                ComponentUtils.legacy("§e" + a.getName() + " §7vs §e" + b.getName()),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1200), Duration.ofMillis(400)));
        for (Player player : duelAudience(session)) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.5F, 1.6F);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.3F);
        }
        for (Player fighter : new Player[]{a, b}) {
            fighter.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,
                    fighter.getLocation().add(0, 1, 0), 30, 0.5, 0.8, 0.5, 0.2);
        }
    }

    /** 演出を届ける対象（参加者 + リング内の観戦者）。 */
    private List<Player> duelAudience(DuelSession session) {
        List<Player> out = new ArrayList<>();
        RingRegion ring = session.getRing();
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (session.isParticipant(online.getUniqueId()) || ring.contains(online.getLocation())) {
                out.add(online);
            }
        }
        return out;
    }

    /** 勝敗が付いて Duel を終了する。 */
    private void endDuel(DuelSession session, UUID winnerId, UUID loserId) {
        if (session.getState() == DuelSession.State.ENDED) {
            return;
        }
        session.setState(DuelSession.State.ENDED);
        cleanupSession(session);

        Player winner = Bukkit.getPlayer(winnerId);
        Player loser = Bukkit.getPlayer(loserId);
        String winnerName = winner != null ? winner.getName() : "?";
        String loserName = loser != null ? loser.getName() : "?";

        Title title = Title.title(
                ComponentUtils.legacy("§6§l" + winnerName + " の勝利！"),
                ComponentUtils.legacy("§7" + winnerName + " §fvs §7" + loserName),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2500), Duration.ofMillis(500)));
        for (Player player : duelAudience(session)) {
            player.showTitle(title);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0F, 1.0F);
        }

        if (winner != null) {
            resetForFight(winner);
            launchVictoryFireworks(winner);
        }
        // マッチング経由なら元の場所へ戻す（勝者は花火の後）
        if (session.isMatchmade()) {
            Location winnerReturn = session.getReturnLocations().get(winnerId);
            if (winner != null && winnerReturn != null) {
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (winner.isOnline()) {
                        winner.teleport(winnerReturn);
                        winner.sendMessage("§a元の場所へ戻りました");
                    }
                }, 70L);
            }
        }
        if (session.getResultCallback() != null) {
            session.getResultCallback().onEnd(winnerId, loserId);
        }
    }

    /** 勝敗を付けずに Duel を中断する。 */
    private void abortDuel(DuelSession session, String message) {
        if (session.getState() == DuelSession.State.ENDED) {
            return;
        }
        session.setState(DuelSession.State.ENDED);
        cleanupSession(session);
        for (UUID uuid : new UUID[]{session.getPlayerA(), session.getPlayerB()}) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null) {
                continue;
            }
            player.sendMessage(message);
            Location back = session.getReturnLocations().get(uuid);
            if (session.isMatchmade() && back != null) {
                player.teleport(back);
            }
        }
        if (session.getResultCallback() != null) {
            session.getResultCallback().onAbort();
        }
    }

    private void cleanupSession(DuelSession session) {
        sessionByRing.remove(session.getRing().getClaimKey());
        sessionByPlayer.remove(session.getPlayerA());
        sessionByPlayer.remove(session.getPlayerB());
    }

    /** 勝者に約3秒間花火を打ち上げる。 */
    private void launchVictoryFireworks(Player winner) {
        for (int i = 0; i < 4; i++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!winner.isOnline()) {
                    return;
                }
                Location loc = winner.getLocation().add(
                        random.nextDouble() * 3 - 1.5, 0.5, random.nextDouble() * 3 - 1.5);
                Firework firework = winner.getWorld().spawn(loc, Firework.class);
                FireworkMeta meta = firework.getFireworkMeta();
                meta.setPower(1);
                meta.addEffect(FireworkEffect.builder()
                        .with(randomFireworkType())
                        .withColor(randomColor(), randomColor())
                        .withFade(randomColor())
                        .flicker(true)
                        .trail(true)
                        .build());
                firework.setFireworkMeta(meta);
            }, i * 15L);
        }
    }

    private FireworkEffect.Type randomFireworkType() {
        FireworkEffect.Type[] types = {FireworkEffect.Type.BALL_LARGE,
                FireworkEffect.Type.STAR, FireworkEffect.Type.BURST};
        return types[random.nextInt(types.length)];
    }

    private Color randomColor() {
        return Color.fromRGB(random.nextInt(0xFFFFFF + 1));
    }

    // ================= イベント: 攻撃 =================

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        DuelSession victimSession = sessionByPlayer.get(victim.getUniqueId());

        // カウントダウン中の参加者は無敵
        if (victimSession != null && victimSession.getState() == DuelSession.State.COUNTDOWN) {
            event.setCancelled(true);
            return;
        }
        if (attacker == null) {
            return;
        }
        RingRegion ring = getActiveRingAt(victim.getLocation());
        if (ring == null || !ring.isDuelMode()) {
            return;
        }
        // Duel モードのリング内: 進行中の試合の対戦者同士のみ攻撃できる
        DuelSession session = sessionByPlayer.get(attacker.getUniqueId());
        boolean fighting = session != null
                && session.getState() == DuelSession.State.FIGHTING
                && session.isParticipant(victim.getUniqueId())
                && session.getRing() == ring;
        if (!fighting) {
            event.setCancelled(true);
            attacker.sendActionBar(ComponentUtils.legacy(
                    "§cこのリングは Duel モードです。素手で右クリックして Duel を開始してください"));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onAnyDamage(EntityDamageEvent event) {
        // カウントダウン中は落下などの環境ダメージも無効
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        DuelSession session = sessionByPlayer.get(player.getUniqueId());
        if (session != null && session.getState() == DuelSession.State.COUNTDOWN) {
            event.setCancelled(true);
        }
    }

    private Player resolvePlayer(org.bukkit.entity.Entity entity) {
        if (entity instanceof Player player) {
            return player;
        }
        if (entity instanceof Projectile projectile && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }

    // ================= イベント: リング内のブロック保護 =================

    /**
     * リング内で設置されたブロックを記録する。
     * MONITOR で拾うため、土地保護などに拒否された設置は記録されない。
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(org.bukkit.event.block.BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (getActiveRingAt(block.getLocation().add(0.5, 0.5, 0.5)) == null) {
            return;
        }
        placedBlocks.put(blockKey(block), event.getPlayer().getUniqueId());
    }

    /**
     * リング内（PVP環境）では、自分が設置したブロック以外の
     * 建築済みブロックは破壊できない。土地の管理者（オーナー/副リーダー）は例外。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(org.bukkit.event.block.BlockBreakEvent event) {
        Block block = event.getBlock();
        RingRegion ring = getActiveRingAt(block.getLocation().add(0.5, 0.5, 0.5));
        if (ring == null) {
            return;
        }
        Player player = event.getPlayer();
        String key = blockKey(block);
        UUID placer = placedBlocks.get(key);
        if (player.getUniqueId().equals(placer)) {
            placedBlocks.remove(key);
            return;
        }
        // 管理者はリングの建築を自由に編集できる
        ClaimRegion claim = landModule.getClaimByKey(ring.getClaimKey());
        if (claim != null && landModule.canManage(player, claim)) {
            placedBlocks.remove(key);
            return;
        }
        event.setCancelled(true);
        player.sendActionBar(ComponentUtils.legacy(
                "§cリング内では自分が設置したブロック以外は破壊できません"));
    }

    // ================= イベント: 右クリック（ボタン / Duel UI） =================

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        Block block = event.getClickedBlock();

        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && block != null
                && block.getType().name().endsWith("_BUTTON")) {
            // ボタン登録モード
            if (registeringButton.remove(player.getUniqueId())) {
                event.setCancelled(true);
                String key = blockKey(block);
                if (duelButtons.add(key)) {
                    saveAll();
                    player.sendMessage("§a対戦ボタンとして登録しました §7(" + key + ")");
                } else {
                    duelButtons.remove(key);
                    saveAll();
                    player.sendMessage("§e対戦ボタンの登録を解除しました");
                }
                return;
            }
            // 対戦ボタン → マッチング待機
            if (duelButtons.contains(blockKey(block))) {
                joinQueue(player);
                return;
            }
        }

        // Duel モードのリング内で素手右クリック → Duel UI
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (!player.getInventory().getItemInMainHand().getType().isAir()) {
            return;
        }
        // 保護コアやチェスト・ドアなど操作可能ブロックの右クリックとは競合させない
        if (block != null && (block.getType() == LandProtectionModule.CORE_MATERIAL
                || block.getType().isInteractable())) {
            return;
        }
        RingRegion ring = getActiveRingAt(player.getLocation());
        if (ring == null || !ring.isDuelMode()) {
            return;
        }
        if (sessionByPlayer.containsKey(player.getUniqueId())) {
            return;
        }
        menu.openDuel(player, ring);
    }

    // ================= イベント: 死亡 / 復活 =================

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        RingRegion ring = getActiveRingAt(player.getLocation());
        DuelSession session = sessionByPlayer.get(player.getUniqueId());
        if (ring == null && session != null) {
            ring = session.getRing();
        }
        if (ring == null) {
            return;
        }

        if (ring.isKeepInventory()) {
            event.setKeepInventory(true);
            event.setKeepLevel(true);
            event.getDrops().clear();
            event.setDroppedExp(0);
        }

        // 復活地点の決定: マッチング敗者は元の場所 / それ以外はリングの復活地点
        Location respawn = null;
        if (session != null && session.isMatchmade()) {
            respawn = session.getReturnLocations().get(player.getUniqueId());
        }
        if (respawn == null && ring.getRespawnPoint() != null
                && ring.isValidRespawnPoint(ring.getRespawnPoint())) {
            respawn = ring.getRespawnPoint().clone();
        }
        if (respawn == null) {
            respawn = ring.getCenterLocation();
        }
        if (respawn != null) {
            pendingRespawn.put(player.getUniqueId(), respawn);
        }

        if (ring.isInstantRespawn()) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline() && player.isDead()) {
                    player.spigot().respawn();
                }
            }, 2L);
        }

        // Duel の勝敗判定
        if (session != null && session.getState() == DuelSession.State.FIGHTING) {
            UUID winner = session.opponentOf(player.getUniqueId());
            if (winner != null) {
                endDuel(session, winner, player.getUniqueId());
            }
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Location respawn = pendingRespawn.remove(event.getPlayer().getUniqueId());
        if (respawn != null && respawn.getWorld() != null) {
            event.setRespawnLocation(respawn);
        }
    }

    // ================= イベント: 移動 / 退出 =================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        DuelSession session = sessionByPlayer.get(event.getPlayer().getUniqueId());
        if (session == null) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (session.getState() == DuelSession.State.COUNTDOWN) {
            // カウントダウン中は視点変更のみ許可
            if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
                Location fixed = from.clone();
                fixed.setYaw(to.getYaw());
                fixed.setPitch(to.getPitch());
                event.setTo(fixed);
            }
            return;
        }
        if (session.getState() == DuelSession.State.FIGHTING
                && session.getRing().contains(from) && !session.getRing().contains(to)) {
            // 試合中はリングの外へ出られない
            event.setTo(from);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        matchQueue.remove(uuid);
        registeringButton.remove(uuid);
        duelRequests.remove(uuid);
        DuelSession session = sessionByPlayer.get(uuid);
        if (session != null && session.getState() != DuelSession.State.ENDED) {
            UUID winner = session.opponentOf(uuid);
            if (winner != null && session.getState() == DuelSession.State.FIGHTING) {
                endDuel(session, winner, uuid);
            } else {
                abortDuel(session, "§e相手がオフラインになったため Duel を中断しました");
            }
        }
    }

    // ================= 境界パーティクル =================

    private void tickParticles() {
        if (rings.isEmpty()) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            String worldName = player.getWorld().getName();
            Location loc = player.getLocation();
            for (RingRegion ring : rings.values()) {
                if (!ring.getWorldName().equals(worldName) || !isRingActive(ring)) {
                    continue;
                }
                double y = loc.getY();
                if (y < ring.getCenterY() - RingRegion.VERTICAL_BELOW - 4
                        || y > ring.getCenterY() + RingRegion.VERTICAL_ABOVE + 4) {
                    continue;
                }
                double distance = ring.horizontalDistance(loc);
                // 境界の狭間（内外どちらからでも）に近づいたときだけ描画する
                if (Math.abs(distance - ring.getRadius()) > BOUNDARY_VIEW_DISTANCE) {
                    continue;
                }
                drawBoundaryNear(player, ring);
            }
        }
    }

    /** プレイヤーの近くの境界の弧をパーティクルで描画する。 */
    private void drawBoundaryNear(Player player, RingRegion ring) {
        Location loc = player.getLocation();
        double cx = ring.getCenterX() + 0.5;
        double cz = ring.getCenterZ() + 0.5;
        double playerAngle = Math.atan2(loc.getZ() - cz, loc.getX() - cx);
        int radius = ring.getRadius();
        // プレイヤー正面の弧 ±（半径に応じた視野）だけを描く
        double arc = Math.min(Math.PI, 14.0 / radius);
        double step = Math.max(0.03, 0.7 / radius);
        double baseY = Math.floor(loc.getY());
        double[] heights = {baseY, baseY + 1.0, baseY + 2.0};
        Particle.DustOptions color = sessionByRing.containsKey(ring.getClaimKey())
                ? BOUNDARY_COLOR_FIGHT : BOUNDARY_COLOR;

        Location point = new Location(player.getWorld(), 0, 0, 0);
        for (double a = playerAngle - arc; a <= playerAngle + arc; a += step) {
            double x = cx + Math.cos(a) * radius;
            double z = cz + Math.sin(a) * radius;
            for (double h : heights) {
                point.setX(x);
                point.setY(h + (a * 31 % 1) * 0.4);
                point.setZ(z);
                player.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0, color);
            }
        }
    }
}
