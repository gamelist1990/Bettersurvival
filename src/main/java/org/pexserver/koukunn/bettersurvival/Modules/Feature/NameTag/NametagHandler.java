package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

import com.github.retrooper.packetevents.PacketEvents;
import com.github.retrooper.packetevents.event.PacketListener;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfo;
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes;
import com.github.retrooper.packetevents.protocol.entity.type.EntityTypes;
import com.github.retrooper.packetevents.protocol.world.Location;
import com.github.retrooper.packetevents.util.Vector3d;
import com.github.retrooper.packetevents.util.Vector3f;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;

import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetPassengers;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.bukkit.Bukkit;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.event.HandlerList;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiPredicate;

/**
 * PacketEventsを使用したネームタグハンドラー
 * プレイヤーの頭上にカスタムネームタグを表示します
 */
public class NametagHandler implements Listener {
    private static final int TEXT_DISPLAY_TEXT_METADATA_INDEX = 23;
    private static final int TEXT_DISPLAY_STYLE_FLAGS_METADATA_INDEX = 27;
    private static final byte TEXT_DISPLAY_FLAG_SEE_THROUGH = 0x02;
    private static final int RESERVED_ENTITY_ID_START = Integer.MAX_VALUE / 2;
    private static final int STARTUP_ORPHAN_PURGE_COUNT = 4096;
    // 改行間隔の調整値（統合版のTEXT_DISPLAYは行間が広すぎるため補正）
    private static final double BEDROCK_ABSOLUTE_LINE_SPACING_EXTRA = -0.21D;
    private static final double BEDROCK_NAMETAG_HEIGHT_OFFSET = -0.35D;

    private final Plugin plugin;
    private final Map<UUID, NametagEntity> playerNametags = new ConcurrentHashMap<>();
    private final AtomicInteger entityIdCounter = new AtomicInteger(RESERVED_ENTITY_ID_START);
    private BukkitTask timer;
    private BukkitTask hardReloadTimer;
    private BukkitTask visibilityTimer;
    private BukkitTask passengerChainTimer;
    private final Map<UUID, Map<UUID, Boolean>> viewerVisibility = new ConcurrentHashMap<>(); // viewer -> (owner ->
                                                                                              // visibility)
    private final Map<UUID, Map<UUID, String>> createdTeams = new ConcurrentHashMap<>(); // viewer -> (owner ->
                                                                                         // teamName)

    // 手動でネームタグを完全に非表示にするセット（人狼などで使用）
    private final Set<UUID> manuallyHidden = ConcurrentHashMap.newKeySet();

    // 視線モードを有効にするビューワーの集合（視線を向けたプレイヤーのネームタグを表示）
    private final Set<UUID> lookModeEnabled = ConcurrentHashMap.newKeySet();

    // 視線モードにより一時的に表示中のターゲット（viewer -> owner）
    private final Map<UUID, UUID> viewerForcedTarget = new ConcurrentHashMap<>();

    private double nametagSpawnHeight = 1.8;
    private int updateInterval = 20;
    @SuppressWarnings("unused")
    private int viewDistance = 64;
    private double lineSpacing = 0.275;
    private boolean seeThrough = false;
    private BiPredicate<Player, Player> seeThroughResolver;
    private BiPredicate<Player, Player> visibilityResolver;

    // Per-player custom names (if not set, use default)
    private final Map<UUID, List<Component>> customStaticLines = new ConcurrentHashMap<>();

    // Singleton instance accessor
    private static NametagHandler instance;

    public static NametagHandler getInstance() {
        return instance;
    }

    // Team-based hiding: per-viewer mappings
    private static final String TEAM_PREFIX = "nh_"; // short prefix, ensure team name length <= 16
    private final Map<UUID, Map<UUID, String>> backedUpTeams = new ConcurrentHashMap<>(); // viewer -> (owner ->
                                                                                          // originalTeamName)
    
    // プレイヤーの前回のチーム状態を記録（チーム変更検知用）
    private final Map<UUID, Map<UUID, String>> lastKnownTeams = new ConcurrentHashMap<>(); // viewer -> (owner -> lastTeamName)

    public NametagHandler(Plugin plugin) {
        this.plugin = plugin;
        instance = this;

        Bukkit.getScheduler().runTask(plugin, this::initialize);
    }

    private void initialize() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        purgeStartupOrphans();
        startTimer();
        startHardReloadTimer();
        startVisibilityTimer();
        startPassengerChainTimer();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                spawnNametag(onlinePlayer);
            }
        }, 10L);
    }

    public void setUpdateInterval(int ticks) {
        this.updateInterval = ticks;
        if (timer != null) {
            timer.cancel();
        }
        startTimer();
    }

    public void setViewDistance(int blocks) {
        this.viewDistance = blocks;
    }

    public void setNametagHeight(double height) {
        this.nametagSpawnHeight = height;
    }

    public void setLineSpacing(double spacing) {
        this.lineSpacing = spacing;
    }

    public void setSeeThrough(boolean seeThrough) {
        if (this.seeThrough == seeThrough) {
            return;
        }
        this.seeThrough = seeThrough;
        reloadNametags();
    }

    public boolean isSeeThrough() {
        return seeThrough;
    }

    public void setSeeThroughResolver(BiPredicate<Player, Player> seeThroughResolver) {
        this.seeThroughResolver = seeThroughResolver;
        reloadNametags();
    }

    public void setVisibilityResolver(BiPredicate<Player, Player> visibilityResolver) {
        this.visibilityResolver = visibilityResolver;
    }

    public void reload() {
        startTimer();
        reloadNametags();
        startHardReloadTimer();
        startVisibilityTimer();
        startPassengerChainTimer();
    }

    private void startTimer() {
        if (timer != null) {
            timer.cancel();
        }
        this.timer = new BukkitRunnable() {
            @Override
            public void run() {
                cleanupOrphans();
                enforceVisibilityMatrix();
                updateNametagTexts();
            }
        }.runTaskTimer(plugin, 0, updateInterval);
    }

    private void startHardReloadTimer() {
        if (hardReloadTimer != null) {
            hardReloadTimer.cancel();
        }
        this.hardReloadTimer = new BukkitRunnable() {
            @Override
            public void run() {
                softRefreshNametags();
            }
        }.runTaskTimer(plugin, 0, 20 * 60);
    }

    private void startVisibilityTimer() {
        if (visibilityTimer != null) {
            visibilityTimer.cancel();
        }
        this.visibilityTimer = new BukkitRunnable() {
            @Override
            public void run() {
                updateLookTargets();
                checkAndHandleTeamChanges(); // チーム変更をチェック（1秒ごと）
                enforceVisibilityMatrix();
            }
        }.runTaskTimer(plugin, 0L, 5L); // 1秒ごと（20tick）に実行
    }

    /**
     * パッセンジャーチェーンを定期的に送信するタイマー（1tick周期）
     */
    private void startPassengerChainTimer() {
        if (passengerChainTimer != null) {
            passengerChainTimer.cancel();
        }
        this.passengerChainTimer = new BukkitRunnable() {
            @Override
            public void run() {
                NametagHandler.this.sendPassengerChainsToViewers();
            }
        }.runTaskTimer(plugin, 5L, 1L); // 5tick後から1tick周期で実行
    }

    /**
     * 表示中の全ビューアにパッセンジャーチェーンを送信
     */
    private void sendPassengerChainsToViewers() {
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            UUID ownerId = entry.getKey();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline())
                continue;

            NametagEntity entity = entry.getValue();
            int parentId = owner.getEntityId();

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(owner))
                    continue;

                // 表示中のビューアのみが対象
                if (!getLastVisibility(viewer, owner))
                    continue;

                // パッセンジャーチェーンを送信
                int pid = parentId;
                for (int lineEntityId : entity.getEntityIds()) {
                    WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(pid,
                            new int[] { lineEntityId });
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                    pid = lineEntityId;
                }
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player joinedPlayer = event.getPlayer();
            if (!joinedPlayer.isOnline()) return;
            spawnNametag(joinedPlayer);
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(joinedPlayer))
                    continue;
                if (shouldHideForViewer(onlinePlayer, joinedPlayer)) {
                    NametagEntity e = playerNametags.get(onlinePlayer.getUniqueId());
                    if (e != null) {
                        sendDestroyToViewer(joinedPlayer, e.getEntityIds());
                    }
                    setLastVisibility(joinedPlayer, onlinePlayer, false);
                    continue;
                }
                showNametagToPlayer(onlinePlayer, joinedPlayer);
                setLastVisibility(joinedPlayer, onlinePlayer, true);
            }

            // Join直後はscoreboardが差し替わることがあるため、両方向の本名非表示を再適用する。
            for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
                if (onlinePlayer.equals(joinedPlayer)) {
                    continue;
                }

                try {
                    updateOwnerNameVisibility(joinedPlayer, onlinePlayer);
                } catch (Throwable ignored) {
                }

                try {
                    updateOwnerNameVisibility(onlinePlayer, joinedPlayer);
                } catch (Throwable ignored) {
                }
            }
        }, 10L);
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // ビューアとして保持しているクライアント側の情報を先にクリーンアップ
        try { cleanupViewerData(player); } catch (Throwable ignored) {}

        // その後オーナーのネームタグを削除（退出中フラグを有効にして、退出中プレイヤー自身への処理をスキップ）
        removeNametag(player, true);
        // ログアウト時はカスタム名をクリアしてデフォルトに戻す
        customStaticLines.remove(player.getUniqueId());
        // 注意: PlayerQuit イベント中はまだプレイヤーはオンライン扱いになるため
        // 即時 reload を行うと切断中のプレイヤーのネームタグが再生成されてしまう。
        // そのため、1 tick 遅延で再ロードを行い、切断処理が完了してから再生成します。
        Bukkit.getScheduler().runTaskLater(plugin, this::reloadNametags, 1L);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        // 死亡・リスポーン後も保存済みの表示を維持する。
        // 行数が1行（デフォルト）のままならメタデータのみ更新
        NametagEntity existingEntity = playerNametags.get(player.getUniqueId());
        if (existingEntity != null && existingEntity.getEntityIds().size() == 1) {
            updateOwnerVisibilityNametag(player);
        } else {
            // 行数が変わる場合のみ再生成
            removeNametag(player);
            Bukkit.getScheduler().runTask(plugin, () -> spawnNametag(player));
        }
    }

    @EventHandler
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerChangedWorld(PlayerChangedWorldEvent event) {
        // プレイヤーがワールドを変更したときは即時にネームタグを削除して
        // 新ワールドでのみ再生成する（重複防止）
        Player player = event.getPlayer();
        try {
            // First: cleanup this player as a viewer (remove any nametag entities that were shown to them)
            cleanupViewerData(player);

            // Then remove & respawn this player's own nametag in the new world
            Bukkit.getScheduler().runTask(plugin, () -> removeNametag(player));
            Bukkit.getScheduler().runTaskLater(plugin, () -> spawnNametag(player), 2L);
        } catch (RuntimeException ignored) {
            // ワールド変更時の処理に失敗した場合は何もしない
        }
    }

    @EventHandler
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        // ワールド跨ぎテレポートの場合は同様に即時削除 + 再生成を行う
        if (!event.getFrom().getWorld().getUID().equals(event.getTo().getWorld().getUID())) {
            Player player = event.getPlayer();
            try {
                Bukkit.getScheduler().runTask(plugin, () -> removeNametag(player));
                Bukkit.getScheduler().runTaskLater(plugin, () -> spawnNametag(player), 2L);
            } catch (RuntimeException ignored) {
                // テレポート時の処理に失敗した場合は何もしない
            }
        } else {
            reloadTimeOut();
        }
    }

    @EventHandler
    public void onPlayerGameModeChange(PlayerGameModeChangeEvent event) {
        reloadTimeOut();
    }

    @EventHandler
    public void onPlayerToggleSneak(PlayerToggleSneakEvent event) {
        updateOwnerVisibilityNametag(event.getPlayer());
        enforceVisibilityMatrix();
    }

    @EventHandler
    public void onEntityPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player))
            return;
        if (event.getModifiedType() != PotionEffectType.INVISIBILITY)
            return;
        Player player = (Player) event.getEntity();
        Bukkit.getScheduler().runTask(plugin, () -> {
            updateOwnerVisibilityNametag(player);
            enforceVisibilityMatrix();
        });
    }

    private void reloadTimeOut() {
        Bukkit.getScheduler().runTaskLater(plugin, this::reloadNametags, 20L);
    }

    /**
     * ビューア（プレイヤー）がワールドを抜ける／ワールド移動する際にそのプレイヤー向けの
     * 全てのネームタグ表示を破棄し、関連するキャッシュ／チーム情報をクリアします。
     */
    private void cleanupViewerData(Player viewer) {
        if (viewer == null) return;
        UUID viewerId = viewer.getUniqueId();

        // 1) Destroy any spawned nametag entities that may still be present on the viewer's client
        for (NametagEntity entity : playerNametags.values()) {
            if (entity == null) continue;
            try { sendDestroyToViewer(viewer, entity.getEntityIds()); } catch (Throwable ignored) {}
        }

        // 2) Restore any owner-name hides applied to this viewer (remove nh_teams / restore backups)
        try {
            for (UUID ownerId : new ArrayList<>(playerNametags.keySet())) {
                Player owner = Bukkit.getPlayer(ownerId);
                if (owner == null) continue;
                try { sendRestoreOwnerNameToViewer(owner, viewer); } catch (Throwable ignored) {}
            }
        } catch (Throwable ignored) {}

        // 3) Clear viewer-scoped caches
        clearViewerFromVisibilityCache(viewerId);
        createdTeams.remove(viewerId);
        backedUpTeams.remove(viewerId);
        lastKnownTeams.remove(viewerId);
        lookModeEnabled.remove(viewerId);
        viewerForcedTarget.remove(viewerId);
    }

    public void spawnNametag(Player player) {
        removeNametag(player);

        org.bukkit.Location baseLoc = player.getLocation().clone();
        baseLoc.setY(baseLoc.getY() + nametagSpawnHeight);

        // カスタム名があればそれを使用、なければデフォルト名（§r + プレイヤー名）
        List<Component> lines = customStaticLines.get(player.getUniqueId());
        if (lines == null || lines.isEmpty()) {
            lines = Collections.singletonList(createDefaultName(player));
        }

        List<Component> linesBottomUp = new ArrayList<>(lines);
        Collections.reverse(linesBottomUp);

        List<Integer> entityIds = new ArrayList<>(linesBottomUp.size());
        for (int remaining = linesBottomUp.size(); remaining > 0; remaining--) {
            entityIds.add(entityIdCounter.decrementAndGet());
        }

        NametagEntity nametagEntity = new NametagEntity(entityIds, linesBottomUp);
        playerNametags.put(player.getUniqueId(), nametagEntity);

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (!viewer.equals(player)) {
                if (!shouldHideForViewer(player, viewer)) {
                    showNametagToPlayer(player, viewer);
                    setLastVisibility(viewer, player, true);
                } else {
                    sendDestroyToViewer(viewer, nametagEntity.getEntityIds());
                    setLastVisibility(viewer, player, false);
                    
                    // 統合版ビューアには壁越しでも本名を隠すチーム設定を適用
                    // これにより、壁の裏にいる状態でスポーン/チーム変更しても生のネームタグが表示されない
                    if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(viewer)) {
                        sendHideOwnerNameToViewer(player, viewer);
                    }
                }
            }
        }

        if (isGloballyHidden(player)) {
            updateOwnerVisibilityNametag(player);
        }
    }

    /**
     * ビューアに対してネームタグを表示（このメソッドは表示可能な状態でのみ呼ぶこと）
     */
    private void showNametagToPlayer(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null)
            return;

        try {
            org.bukkit.Location bukkitLocation = owner.getLocation().clone();
            bukkitLocation.setY(bukkitLocation.getY() + nametagSpawnHeight);

            Location packeteventsLocation = new Location(
                    bukkitLocation.getX(),
                    bukkitLocation.getY(),
                    bukkitLocation.getZ(),
                    bukkitLocation.getYaw(),
                    bukkitLocation.getPitch());

            float defaultViewDistance = 1.0f;
            float blocksPerDefault = 80.0f;
            float oneBlockViewDistance = defaultViewDistance / blocksPerDefault;
            int blocks = 64;
            double bedrockLineSpacing = getBedrockAbsoluteLineSpacing();

            List<Integer> lineEntityIds = entity.getEntityIds();
            List<Component> linesBottomUp = entity.getLines();

            // Ensure client's vanilla name visibility is set BEFORE spawning text-display entities
            // (prevents a short frame where the client's native nametag/scoreboard prefix is visible).
            try {
                updateOwnerNameVisibility(owner, viewer);
            } catch (Throwable ignored) {
            }

            for (int i = 0; i < lineEntityIds.size(); i++) {
                int lineEntityId = lineEntityIds.get(i);

                // Spawn the TEXT_DISPLAY for both Java and Bedrock viewers

                if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(viewer)) {
                    WrapperPlayServerSpawnEntity spawnPacketBed = new WrapperPlayServerSpawnEntity(
                            lineEntityId,
                            UUID.randomUUID(),
                            EntityTypes.TEXT_DISPLAY,
                            packeteventsLocation,
                            0f,
                            0,
                            new Vector3d(0, 0, 0));

                    List<EntityData<?>> metadataBed = new ArrayList<>();
                    metadataBed.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x03));
                    metadataBed.add(new EntityData<>(17, EntityDataTypes.FLOAT, oneBlockViewDistance * blocks));
                        float yOffset = (float) ((i == 0 ? BEDROCK_NAMETAG_HEIGHT_OFFSET : 0.0D)
                            + (i + 1) * bedrockLineSpacing);
                    metadataBed.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f)));

                    Component lineTextBed = linesBottomUp.get(i);
                    if (isGloballyHidden(owner)) {
                        lineTextBed = Component.text("");
                    }
                    metadataBed.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, lineTextBed));
                    metadataBed.add(new EntityData<>(TEXT_DISPLAY_STYLE_FLAGS_METADATA_INDEX, EntityDataTypes.BYTE,
                            buildTextDisplayFlags(owner, viewer)));

                    WrapperPlayServerEntityMetadata metadataPacketBed = new WrapperPlayServerEntityMetadata(
                            lineEntityId, metadataBed);

                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacketBed);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacketBed);
                } else {
                    // Java viewers: spawn relative and use passenger offsets so they follow owner
                    // naturally
                    WrapperPlayServerSpawnEntity spawnPacket = new WrapperPlayServerSpawnEntity(
                            lineEntityId,
                            UUID.randomUUID(),
                            EntityTypes.TEXT_DISPLAY,
                            packeteventsLocation,
                            0f,
                            0,
                            new Vector3d(0, 0, 0));

                    List<EntityData<?>> metadata = new ArrayList<>();
                    metadata.add(new EntityData<>(15, EntityDataTypes.BYTE, (byte) 0x03));
                    metadata.add(new EntityData<>(17, EntityDataTypes.FLOAT, oneBlockViewDistance * blocks));

                    float yOffset = (float) ((i + 1) * lineSpacing);
                    metadata.add(new EntityData<>(11, EntityDataTypes.VECTOR3F, new Vector3f(0f, yOffset, 0f)));

                    Component lineText = linesBottomUp.get(i);
                    if (isGloballyHidden(owner)) {
                        lineText = Component.text("");
                    }
                    metadata.add(new EntityData<>(23, EntityDataTypes.ADV_COMPONENT, lineText));
                    metadata.add(new EntityData<>(TEXT_DISPLAY_STYLE_FLAGS_METADATA_INDEX, EntityDataTypes.BYTE,
                            buildTextDisplayFlags(owner, viewer)));

                    WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(lineEntityId,
                            metadata);

                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, spawnPacket);
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);
                }
            }

            int parentId = owner.getEntityId();
            for (int lineEntityId : lineEntityIds) {
                WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(parentId,
                        new int[] { lineEntityId });
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                parentId = lineEntityId;
            }

            setLastVisibility(viewer, owner, true);

            // オーナーがグローバルに非表示なら、すべてのビューワーに対して本名を隠す
            if (isGloballyHidden(owner)) {
                sendHideOwnerNameToViewer(owner, viewer);
            } else {
                // Bedrock ビューアー向けに本名を隠す既存の振る舞いを維持
                if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(viewer)) {
                    sendHideOwnerNameToViewer(owner, viewer);
                } else {
                    // オーナーが Bedrock の場合、Java ビューアーに本名を復元する
                    if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(owner)) {
                        sendRestoreOwnerNameToViewer(owner, viewer);
                    }
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Error showing nametag", e);
        }
    }

    private void updateNametagTexts() {

        for (Player player : Bukkit.getOnlinePlayers()) {
            NametagEntity entity = playerNametags.get(player.getUniqueId());
            if (entity == null)
                continue;

            List<Component> newLinesBottomUp;
            if (isGloballyHidden(player)) {
                newLinesBottomUp = emptyLines(entity.getEntityIds().size());
            } else {
                List<Component> rendered = customStaticLines.get(player.getUniqueId());
                if (rendered == null || rendered.isEmpty()) {
                    rendered = Collections.singletonList(createDefaultName(player));
                }
                List<Component> bottomUp = new ArrayList<>(rendered);
                Collections.reverse(bottomUp);
                newLinesBottomUp = normalizeToSize(bottomUp, entity.getEntityIds().size());
            }

            boolean anyChanged = false;
            List<Component> old = entity.getLines();
            if (old.size() != newLinesBottomUp.size()) {
                newLinesBottomUp = normalizeToSize(newLinesBottomUp, old.size());
            }
            for (int i = 0; i < newLinesBottomUp.size(); i++) {
                if (!newLinesBottomUp.get(i).equals(old.get(i))) {
                    anyChanged = true;
                    break;
                }
            }

            if (anyChanged) {
                entity.setLines(newLinesBottomUp);
                sendNametagTextUpdate(player, entity);
            }
        }
    }

    private void updateOwnerVisibilityNametag(Player player) {
        NametagEntity entity = playerNametags.get(player.getUniqueId());
        if (entity == null)
            return;

        List<Component> target;
        if (isGloballyHidden(player)) {
            target = emptyLines(entity.getEntityIds().size());
        } else {
            List<Component> rendered = customStaticLines.get(player.getUniqueId());
            if (rendered == null || rendered.isEmpty()) {
                rendered = Collections.singletonList(createDefaultName(player));
            }
            List<Component> bottomUp = new ArrayList<>(rendered);
            Collections.reverse(bottomUp);
            target = normalizeToSize(bottomUp, entity.getEntityIds().size());
        }

        boolean anyChanged = false;
        List<Component> old = entity.getLines();
        for (int i = 0; i < target.size(); i++) {
            if (!target.get(i).equals(old.get(i))) {
                anyChanged = true;
                break;
            }
        }
        if (!anyChanged)
            return;

        entity.setLines(target);
        sendNametagTextUpdate(player, entity);
    }

    private Component createDefaultName(Player player) {
        return Component.text(player.getName(), NamedTextColor.WHITE);
    }

    private void softRefreshNametags() {
        cleanupOrphans();
        for (Player owner : Bukkit.getOnlinePlayers()) {
            NametagEntity entity = playerNametags.get(owner.getUniqueId());
            if (entity == null) {
                spawnNametag(owner);
                continue;
            }
            updateOwnerVisibilityNametag(owner);
            resendPassengerChain(owner, entity);
        }
    }

    /**
     * 可視性マトリックスを強制適用（壁越し判定等による表示/非表示を反映）
     */
    private void enforceVisibilityMatrix() {
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            UUID ownerId = entry.getKey();
            Player owner = Bukkit.getPlayer(ownerId);
            if (owner == null || !owner.isOnline())
                continue;

            NametagEntity entity = entry.getValue();
            List<Integer> ids = entity.getEntityIds();

            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.getUniqueId().equals(ownerId))
                    continue;

                boolean shouldBeVisible = !shouldHideForViewer(owner, viewer);
                boolean currentlyVisible = getLastVisibility(viewer, owner);

                // 状態が変わった場合のみ処理
                if (shouldBeVisible == currentlyVisible)
                    continue;

                if (shouldBeVisible) {
                    // 非表示 → 表示への遷移
                    // エンティティを再スポーンしてパッセンジャーチェーンを送信
                    // （showNametagToPlayer内で即座に送信）
                    showNametagToPlayer(owner, viewer);
                    setLastVisibility(viewer, owner, true);
                } else {
                    // 表示 → 非表示への遷移
                    hideNametagFromViewer(owner, viewer, ids);
                }
            }
        }
    }

    /**
     * ビューアからネームタグを隠す処理（壁越し等で見えなくなった場合）
     */
    private void hideNametagFromViewer(Player owner, Player viewer, List<Integer> entityIds) {
        // カスタムネームタグを削除
        sendDestroyToViewer(viewer, entityIds);

        // 本名も隠す（壁越しで見えないため）
        sendHideOwnerNameToViewer(owner, viewer);

        setLastVisibility(viewer, owner, false);
    }

    private void resendPassengerChain(Player owner, NametagEntity entity) {
        int parentId = owner.getEntityId();
        for (int lineEntityId : entity.getEntityIds()) {
            WrapperPlayServerSetPassengers passengersPacket = new WrapperPlayServerSetPassengers(parentId,
                    new int[] { lineEntityId });
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(owner))
                    continue;
                if (!shouldHideForViewer(owner, viewer)) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, passengersPacket);
                } else {
                    sendDestroyToViewer(viewer, entity.getEntityIds());
                    if (!isGloballyHidden(owner)) {
                        if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(viewer)) {
                            sendRestoreOwnerNameToViewer(owner, viewer);
                        }
                    } else {
                        sendHideOwnerNameToViewer(owner, viewer);
                    }
                    setLastVisibility(viewer, owner, false);
                }
            }
            parentId = lineEntityId;
        }
    }

    private boolean isGloballyHidden(Player owner) {
        return manuallyHidden.contains(owner.getUniqueId())
                || owner.isSneaking()
                || owner.hasPotionEffect(PotionEffectType.INVISIBILITY)
                || owner.isInvisible();
    }

    private boolean shouldHideForViewer(Player owner, Player viewer) {
        // 違うワールドにいる場合は非表示
        if (!sameWorld(owner, viewer)) {
            return true;
        }

        // viewer が owner を見ることができない（hidePlayer や Vanish）場合は非表示
        // Bukkit の `Player.canSee(...)` を尊重することで他のプラグインの hide/show に自動対応します
        try {
            if (!viewer.canSee(owner))
                return true;
        } catch (Throwable ignored) {
            // API が古い場合や例外が発生した場合はフォールバック
        }

        if (visibilityResolver != null) {
            try {
                if (!visibilityResolver.test(owner, viewer)) {
                    return true;
                }
            } catch (Throwable ignored) {
                return true;
            }
        }

        // 統合版（Bedrock）ユーザーの場合は壁越しチェック
        if (FloodgateUtil.isFloodgateInstalled() && FloodgateUtil.isBedrock(viewer)) {
            if (isSeeThroughEnabled(owner, viewer)) {
                return false;
            }
            return !canViewerSeeOwner(viewer, owner);
        }

        // Java版は壁越しでも表示（デフォルト動作）
        return false;
    }

    /**
     * ビューアがオーナーを視界内で見ることができるかをチェック
     * 統合版向けに壁越し判定を行う（視野角チェックなし - 後ろにいても表示）
     */
    private boolean canViewerSeeOwner(Player viewer, Player owner) {
        org.bukkit.Location viewerLoc = viewer.getEyeLocation();
        org.bukkit.Location ownerLoc = owner.getLocation().add(0, owner.getHeight() / 2, 0);

        // 距離チェック（64ブロック以内）
        double distance = viewerLoc.distance(ownerLoc);
        if (distance > 64.0) {
            return false;
        }

        // レイキャスト（視線追跡）で壁越しチェックのみ実施
        // 後ろにいるプレイヤーも壁がなければ表示する
        try {
            org.bukkit.util.Vector direction = ownerLoc.toVector().subtract(viewerLoc.toVector()).normalize();
            org.bukkit.util.RayTraceResult result = viewer.getWorld().rayTraceBlocks(
                    viewerLoc,
                    direction,
                    distance,
                    org.bukkit.FluidCollisionMode.NEVER,
                    true);

            // ブロックに当たった場合は壁越し = 見えない
            return result == null || result.getHitBlock() == null;

        } catch (Exception e) {
            // エラーが発生した場合は安全のため表示
            plugin.getLogger().log(java.util.logging.Level.WARNING, "レイキャスト判定エラー", e);
            return true;
        }
    }

    private boolean sameWorld(Player a, Player b) {
        return a.getWorld().getUID().equals(b.getWorld().getUID());
    }

    private boolean getLastVisibility(Player viewer, Player owner) {
        Map<UUID, Boolean> m = viewerVisibility.get(viewer.getUniqueId());
        if (m == null)
            return false;
        return m.getOrDefault(owner.getUniqueId(), false);
    }

    private void setLastVisibility(Player viewer, Player owner, boolean visible) {
        viewerVisibility
                .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                .put(owner.getUniqueId(), visible);
    }

    private void clearOwnerFromVisibilityCache(UUID ownerId) {
        for (Map<UUID, Boolean> m : viewerVisibility.values()) {
            if (m != null) {
                m.remove(ownerId);
            }
        }
    }

    private void clearViewerFromVisibilityCache(UUID viewerId) {
        viewerVisibility.remove(viewerId);
    }

    private List<Component> normalizeToSize(List<Component> src, int size) {
        List<Component> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            if (i < src.size()) {
                out.add(src.get(i));
            } else {
                out.add(Component.text(""));
            }
        }
        if (out.size() > size) {
            return new ArrayList<>(out.subList(0, size));
        }
        return out;
    }

    private List<Component> emptyLines(int size) {
        List<Component> out = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            out.add(Component.text(""));
        }
        return out;
    }

    /**
     * ネームタグのテキストを更新（表示中のビューアにのみ送信）
     */
    private void sendNametagTextUpdate(Player owner, NametagEntity entity) {
        List<Integer> ids = entity.getEntityIds();
        List<Component> lines = entity.getLines();

        for (int i = 0; i < ids.size(); i++) {
            int entityId = ids.get(i);
            Component text = lines.get(i);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(owner))
                    continue;

                // 現在表示されているビューアにのみテキスト更新を送信
                boolean currentlyVisible = getLastVisibility(viewer, owner);
                if (!currentlyVisible)
                    continue;

                List<EntityData<?>> metadata = new ArrayList<>(2);
                metadata.add(new EntityData<>(TEXT_DISPLAY_TEXT_METADATA_INDEX, EntityDataTypes.ADV_COMPONENT, text));
                metadata.add(new EntityData<>(TEXT_DISPLAY_STYLE_FLAGS_METADATA_INDEX, EntityDataTypes.BYTE,
                        buildTextDisplayFlags(owner, viewer)));
                WrapperPlayServerEntityMetadata metadataPacket = new WrapperPlayServerEntityMetadata(entityId, metadata);

                // テキストメタデータを送信
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, metadataPacket);

                // 本名の表示状態を更新（グローバル非表示や統合版対応）
                updateOwnerNameVisibility(owner, viewer);
            }
        }
    }

    /**
     * オーナーの本名表示状態を更新（統合版対応とグローバル非表示対応）
     */
    private void updateOwnerNameVisibility(Player owner, Player viewer) {
        NametagEntity entity = playerNametags.get(owner.getUniqueId());
        if (entity == null) {
            sendRestoreOwnerNameToViewer(owner, viewer);
            return;
        }

        // TEXT_DISPLAY名札を表示している間は、Java/Bedrock共通でvanilla名札を隠す。
        boolean hasVisibleNametag = !shouldHideForViewer(owner, viewer) && getLastVisibility(viewer, owner);
        if (isGloballyHidden(owner) || hasVisibleNametag) {
            sendHideOwnerNameToViewer(owner, viewer);
        } else {
            sendRestoreOwnerNameToViewer(owner, viewer);
        }
    }

    private void sendDestroyToViewer(Player viewer, List<Integer> entityIds) {
        for (int entityId : entityIds) {
            WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
            PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
        }
    }

    private byte buildTextDisplayFlags(Player owner, Player viewer) {
        byte flags = 0;
        if (isSeeThroughEnabled(owner, viewer)) {
            flags |= TEXT_DISPLAY_FLAG_SEE_THROUGH;
        }
        return flags;
    }

    private boolean isSeeThroughEnabled(Player owner, Player viewer) {
        if (!seeThrough) {
            return false;
        }
        if (owner == null || viewer == null) {
            return false;
        }
        return seeThroughResolver == null || seeThroughResolver.test(owner, viewer);
    }

    public void removeNametag(Player player) {
        removeNametag(player, false);
    }

    /**
     * プレイヤーのネームタグを削除
     * @param player 対象プレイヤー
     * @param isPlayerQuitting プレイヤーが退出中かどうか（true の場合、退出中プレイヤー自身への処理をスキップ）
     */
    public void removeNametag(Player player, boolean isPlayerQuitting) {
        NametagEntity entity = playerNametags.remove(player.getUniqueId());
        clearOwnerFromVisibilityCache(player.getUniqueId());

        // Restore names / cleanup teams for all viewers
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            // 退出中のプレイヤー自身には処理を送らない（接続が不安定なため）
            if (isPlayerQuitting && viewer.getUniqueId().equals(player.getUniqueId())) {
                continue;
            }

            try {
                if (!isGloballyHidden(player)) {
                    sendRestoreOwnerNameToViewer(player, viewer);
                } else {
                    sendHideOwnerNameToViewer(player, viewer);
                }
            } catch (Throwable ignored) {
            }
        }

        if (entity != null) {
            for (int entityId : entity.getEntityIds()) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    // 退出中のプレイヤー自身には削除パケットを送らない
                    if (isPlayerQuitting && viewer.getUniqueId().equals(player.getUniqueId())) {
                        continue;
                    }
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            }
            // 他のビューワーでこのオーナーを強制ターゲットにしている場合はクリア
            viewerForcedTarget.values().removeIf(v -> v.equals(player.getUniqueId()));
        }
    }

    private void cleanupOrphans() {
        for (UUID uuid : new ArrayList<>(playerNametags.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                playerNametags.remove(uuid);
                clearOwnerFromVisibilityCache(uuid);
            }
        }
        for (UUID viewerId : new ArrayList<>(viewerVisibility.keySet())) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                clearViewerFromVisibilityCache(viewerId);
            }
        }
        // look-mode 関連のクリーンアップ
        for (UUID v : new ArrayList<>(lookModeEnabled)) {
            Player pv = Bukkit.getPlayer(v);
            if (pv == null || !pv.isOnline()) {
                lookModeEnabled.remove(v);
                viewerForcedTarget.remove(v);
            }
        }
    }

    public void shutdown() {
        if (timer != null)
            timer.cancel();
        if (hardReloadTimer != null)
            hardReloadTimer.cancel();
        if (visibilityTimer != null)
            visibilityTimer.cancel();
        if (passengerChainTimer != null)
            passengerChainTimer.cancel();
        removeAllNametagsOnShutdown();
        customStaticLines.clear();
        createdTeams.clear();
        backedUpTeams.clear();
        lastKnownTeams.clear();
        manuallyHidden.clear();
        lookModeEnabled.clear();
        viewerForcedTarget.clear();
        HandlerList.unregisterAll(this);
        instance = null;
    }

    // ---------- 新機能: 視線で表示 / 手動hide制御 ----------

    public void setLookModeEnabled(Player viewer, boolean enabled) {
        if (enabled) {
            lookModeEnabled.add(viewer.getUniqueId());
        } else {
            lookModeEnabled.remove(viewer.getUniqueId());
            viewerForcedTarget.remove(viewer.getUniqueId());
        }
    }

    public boolean isLookModeEnabled(Player viewer) {
        return lookModeEnabled.contains(viewer.getUniqueId());
    }

    public void hideNametag(Player owner) {
        manuallyHidden.add(owner.getUniqueId());
        // 即時反映
        updateOwnerVisibilityNametag(owner);
        enforceVisibilityMatrix();
    }

    /**
     * 個別のカスタムネームタグを設定します（null を渡すとデフォルト名に戻る）
     * エンティティの再生成を最小限にし、行数が同じならメタデータのみ更新します。
     */
    public void setCustomName(Player player, List<Component> lines) {
        NametagEntity existingEntity = playerNametags.get(player.getUniqueId());

        if (lines == null || lines.isEmpty()) {
            customStaticLines.remove(player.getUniqueId());
        } else {
            customStaticLines.put(player.getUniqueId(), new ArrayList<>(lines));
        }

        // 既存のエンティティがない、または行数が変わる場合のみ再生成
        if (existingEntity == null ||
                (lines != null && existingEntity.getEntityIds().size() != lines.size()) ||
                (lines == null && existingEntity.getEntityIds().size() != 1)) {
            // 行数が変わるので再生成が必要
            removeNametag(player);
            spawnNametag(player);
        } else {
            // 行数が同じなので、メタデータのみ更新（チカチカ防止）
            updateOwnerVisibilityNametag(player);
        }
    }

    public void clearCustomName(Player player) {
        NametagEntity existingEntity = playerNametags.get(player.getUniqueId());
        customStaticLines.remove(player.getUniqueId());

        // デフォルト名は1行なので、既存が1行ならメタデータのみ更新
        if (existingEntity != null && existingEntity.getEntityIds().size() == 1) {
            // 行数が同じ（1行）なので、メタデータのみ更新
            updateOwnerVisibilityNametag(player);
        } else {
            // 行数が変わるので再生成
            removeNametag(player);
            spawnNametag(player);
        }
    }

    public void showNametag(Player owner) {
        manuallyHidden.remove(owner.getUniqueId());
        updateOwnerVisibilityNametag(owner);
        enforceVisibilityMatrix();
    }

    public boolean isNametagHidden(Player owner) {
        return manuallyHidden.contains(owner.getUniqueId());
    }

    /**
     * 視線でのターゲット表示を更新します（visibilityTimer から定期的に呼ばれる）
     */
    /**
     * 視線でのターゲット表示を更新します（visibilityTimer から定期的に呼ばれる）
     */
    private void updateLookTargets() {
        if (lookModeEnabled.isEmpty())
            return;

        for (UUID viewerId : new ArrayList<>(lookModeEnabled)) {
            Player viewer = Bukkit.getPlayer(viewerId);
            if (viewer == null || !viewer.isOnline()) {
                lookModeEnabled.remove(viewerId);
                viewerForcedTarget.remove(viewerId);
                continue;
            }

            try {
                // 視線判定距離はプレイヤーのリーチ属性（可能なら）を優先して使用します。存在しない場合はデフォルトの 5.0 を使用。
                int reachInt = (int) Math.ceil(getViewerReach(viewer));
                org.bukkit.entity.Entity target = null;
                try {
                    target = viewer.getTargetEntity(reachInt);
                } catch (Throwable ignored) {
                    // getTargetEntity が失敗した場合は安全にスキップ
                }

                UUID newTargetUUID = null;
                if (target instanceof Player owner) {
                    if (!owner.equals(viewer) && sameWorld(owner, viewer) && !isGloballyHidden(owner)) {
                        newTargetUUID = owner.getUniqueId();
                    }
                }

                UUID previousTarget = viewerForcedTarget.get(viewerId);

                // ターゲットが変わった場合のみ処理
                if (previousTarget != null && !Objects.equals(previousTarget, newTargetUUID)) {
                    // 前のターゲットの表示を通常状態に戻す
                    Player oldOwner = Bukkit.getPlayer(previousTarget);
                    if (oldOwner != null && oldOwner.isOnline()) {
                        NametagEntity ent = playerNametags.get(oldOwner.getUniqueId());
                        if (ent != null) {
                            boolean shouldBeVisible = !shouldHideForViewer(oldOwner, viewer);
                            if (shouldBeVisible) {
                                // 通常表示に戻す（enforceVisibilityMatrixに任せる）
                                // ここでは何もしない
                            } else {
                                // 非表示にする
                                hideNametagFromViewer(oldOwner, viewer, ent.getEntityIds());
                            }
                        }
                    }
                }

                // 新しいターゲットを表示
                if (newTargetUUID != null && !Objects.equals(previousTarget, newTargetUUID)) {
                    Player newOwner = Bukkit.getPlayer(newTargetUUID);
                    if (newOwner != null && newOwner.isOnline()) {
                        showNametagToPlayer(newOwner, viewer);
                        setLastVisibility(viewer, newOwner, true);
                    }
                }

                // ターゲットを更新
                if (newTargetUUID != null) {
                    viewerForcedTarget.put(viewerId, newTargetUUID);
                } else {
                    viewerForcedTarget.remove(viewerId);
                }
            } catch (Throwable ignored) {
                // エラーが発生した場合はスキップ
            }
        }
    }

    /**
     * ビューアのリーチ（視線判定距離）を取得します。
     * 可能であれば API の Attribute.ENTITY_INTERACTION_RANGE を利用し、存在しない場合は 5.0 を返します。
     */
    private double getViewerReach(Player viewer) {
        try {
            AttributeInstance inst = viewer.getAttribute(Attribute.ENTITY_INTERACTION_RANGE);
            if (inst != null) {
                double v = inst.getValue();
                if (v > 0)
                    return v;
            }
        } catch (Throwable ignored) {
            // 安全にフォールバック
        }
        // fallback: 5.0 ブロック（デフォルトのインタラクション/視線距離）
        return 5.0;
    }

    /**
     * PlayerInfo パケットをインターセプトして Bedrock ビューア向けに displayName を空にする
     */
    private double getBedrockAbsoluteLineSpacing() {
        return lineSpacing + BEDROCK_ABSOLUTE_LINE_SPACING_EXTRA;
    }

    private void purgeStartupOrphans() {
        List<Integer> reservedEntityIds = new ArrayList<>(STARTUP_ORPHAN_PURGE_COUNT);
        int firstReservedEntityId = RESERVED_ENTITY_ID_START - 1;
        int lastReservedEntityId = firstReservedEntityId - STARTUP_ORPHAN_PURGE_COUNT;
        for (int entityId = firstReservedEntityId; entityId > lastReservedEntityId; entityId--) {
            reservedEntityIds.add(entityId);
        }

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            for (int entityId : reservedEntityIds) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
            }
        }
    }

    public void removeAllNametagsOnShutdown() {
        for (Map.Entry<UUID, NametagEntity> entry : playerNametags.entrySet()) {
            for (int entityId : entry.getValue().getEntityIds()) {
                WrapperPlayServerDestroyEntities destroyPacket = new WrapperPlayServerDestroyEntities(entityId);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, destroyPacket);
                }
            }
        }
        playerNametags.clear();
        viewerVisibility.clear();
    }

    private void reloadNametags() {
        removeAllNametagsOnShutdown();
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            spawnNametag(onlinePlayer);
        }
    }

    /**
     * 指定ビューアに対して、オーナー（Player）の本名を非表示にする
     */
    private void sendHideOwnerNameToViewer(Player owner, Player viewer) {
        try {
            Scoreboard sb = viewer.getScoreboard();
            String ownerName = owner.getName();

            // 既に作成済みのチームがある場合は再利用
            String existingTeamName = createdTeams
                    .computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .get(owner.getUniqueId());

            if (existingTeamName != null) {
                Team existingTeam = sb.getTeam(existingTeamName);
                if (existingTeam != null) {
                    // 既にチームが存在し、メンバーも登録済みなら何もしない（最適化）
                    if (existingTeam.hasEntry(ownerName) &&
                            existingTeam.getOption(Team.Option.NAME_TAG_VISIBILITY) == Team.OptionStatus.NEVER) {
                        // 現在のチーム状態を記録
                        lastKnownTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                                .put(owner.getUniqueId(), existingTeamName);
                        return;
                    }
                    existingTeam.addEntry(ownerName);
                    existingTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                    // 現在のチーム状態を記録
                    lastKnownTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                            .put(owner.getUniqueId(), existingTeamName);
                    return;
                }
            }

            // 現在所属しているチームをチェック
            Team currentTeam = sb.getEntryTeam(ownerName);
            if (currentTeam != null) {
                String currentTeamName = currentTeam.getName();
                
                // nh_チームの場合は削除して空なら登録解除
                if (currentTeamName.startsWith(TEAM_PREFIX)) {
                    try {
                        currentTeam.removeEntry(ownerName);
                        if (currentTeam.getEntries().isEmpty()) {
                            currentTeam.unregister();
                        }
                    } catch (Throwable ignored) {
                    }
                }
            }
            
            // 再度現在のチームを確認（nh_削除後に他のチームに所属している可能性）
            currentTeam = sb.getEntryTeam(ownerName);
            
            if (currentTeam != null && !currentTeam.getName().startsWith(TEAM_PREFIX)) {
                // ゲーム内チーム等に既に所属している場合、そのチーム内で直接NAME_TAG_VISIBILITYを設定
                String currentTeamName = currentTeam.getName();
                
                // 現在の設定をバックアップ（復元時のため）
                Team.OptionStatus currentVisibility = currentTeam.getOption(Team.Option.NAME_TAG_VISIBILITY);
                Map<UUID, String> backupMap = backedUpTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>());
                
                // まだバックアップしていない場合のみバックアップ
                if (!backupMap.containsKey(owner.getUniqueId())) {
                    backupMap.put(owner.getUniqueId(), currentTeamName + ":" + currentVisibility.name());
                }
                
                // 既存チーム内で直接NAME_TAG_VISIBILITYをNEVERに設定
                currentTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
                
                // createdTeamsには記録しない（既存チームを使用しているため）
                // 現在のチーム状態を記録
                lastKnownTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                        .put(owner.getUniqueId(), currentTeamName);
                
                return; // 既存チームで処理完了
            }

            // どのチームにも所属していない、またはnh_チームのみだった場合、新しいnh_チームを作成
            String shortOwner = owner.getUniqueId().toString().substring(0, 6);
            String shortViewer = viewer.getUniqueId().toString().substring(0, 4);
            String newTeamName = TEAM_PREFIX + shortOwner + shortViewer; // e.g., nh_abcd12ef34
            Team team = sb.getTeam(newTeamName);
            if (team == null) {
                team = sb.registerNewTeam(newTeamName);
            }
            team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.NEVER);
            team.addEntry(ownerName);

            createdTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(owner.getUniqueId(), newTeamName);
            
            // 現在のチーム状態を記録
            lastKnownTeams.computeIfAbsent(viewer.getUniqueId(), k -> new ConcurrentHashMap<>())
                    .put(owner.getUniqueId(), newTeamName);
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Failed to hide owner name via team", e);
            // fallback to metadata if team approach fails
            try {
                int ownerId = owner.getEntityId();
                List<EntityData<?>> metadata = new ArrayList<>();
                metadata.add(new EntityData<>(2, EntityDataTypes.OPTIONAL_ADV_COMPONENT, java.util.Optional.empty()));
                metadata.add(new EntityData<>(3, EntityDataTypes.BOOLEAN, false));
                WrapperPlayServerEntityMetadata packet = new WrapperPlayServerEntityMetadata(ownerId, metadata);
                PacketEvents.getAPI().getPlayerManager().sendPacket(viewer, packet);
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, "Fallback hide via metadata also failed", ex);
            }
        }
    }

    /**
     * 指定ビューアに対して、オーナー（Player）の本名表示を復元する
     */
    private void sendRestoreOwnerNameToViewer(Player owner, Player viewer) {
        try {
            Scoreboard sb = viewer.getScoreboard();
            String ownerName = owner.getName();

            // バックアップからチーム情報を取得
            Map<UUID, String> backupMap = backedUpTeams.get(viewer.getUniqueId());
            String backupInfo = backupMap != null ? backupMap.get(owner.getUniqueId()) : null;
            
            // バックアップがある場合は元の設定を復元
            if (backupInfo != null && backupInfo.contains(":") && backupMap != null) {
                String[] parts = backupInfo.split(":", 2);
                String origTeamName = parts[0];
                String visibilityStr = parts[1];
                
                Team origTeam = sb.getTeam(origTeamName);
                if (origTeam != null) {
                    try {
                        // 元のNAME_TAG_VISIBILITY設定を復元
                        Team.OptionStatus origVisibility = Team.OptionStatus.valueOf(visibilityStr);
                        origTeam.setOption(Team.Option.NAME_TAG_VISIBILITY, origVisibility);
                    } catch (Throwable ignored) {
                    }
                }
                
                // バックアップを削除
                backupMap.remove(owner.getUniqueId());
                if (backupMap.isEmpty()) {
                    backedUpTeams.remove(viewer.getUniqueId());
                }
                
                // lastKnownTeamsからも削除
                Map<UUID, String> lastTeams = lastKnownTeams.get(viewer.getUniqueId());
                if (lastTeams != null) {
                    lastTeams.remove(owner.getUniqueId());
                    if (lastTeams.isEmpty()) {
                        lastKnownTeams.remove(viewer.getUniqueId());
                    }
                }
                
                return; // 既存チームの復元完了
            }

            // createdTeamsに記録されているnh_チームを削除
            Map<UUID, String> viewerTeams = createdTeams.get(viewer.getUniqueId());
            if (viewerTeams != null) {
                String teamName = viewerTeams.get(owner.getUniqueId());
                if (teamName != null) {
                    Team team = sb.getTeam(teamName);
                    if (team != null) {
                        try {
                            team.removeEntry(ownerName);
                        } catch (Throwable ignored) {
                        }

                        if (team.getEntries().isEmpty()) {
                            try {
                                team.unregister();
                            } catch (Throwable ignored) {
                            }
                        }
                    }

                    viewerTeams.remove(owner.getUniqueId());
                    if (viewerTeams.isEmpty()) {
                        createdTeams.remove(viewer.getUniqueId());
                    }
                }
            }
            
            // lastKnownTeamsからも削除
            Map<UUID, String> lastTeams = lastKnownTeams.get(viewer.getUniqueId());
            if (lastTeams != null) {
                lastTeams.remove(owner.getUniqueId());
                if (lastTeams.isEmpty()) {
                    lastKnownTeams.remove(viewer.getUniqueId());
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(java.util.logging.Level.WARNING, "チーム復元エラー", e);
        }
    }
    
    /**
     * チーム変更を検知して処理する
     * 定期的に呼ばれ、プレイヤーのチームが変わっていたら再度hideを適用
     * 統合版で素の名前が見える問題を1秒ごとにチェックして修正
     */
    private void checkAndHandleTeamChanges() {
        for (Player owner : Bukkit.getOnlinePlayers()) {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (owner.equals(viewer)) continue;

                try {
                    Scoreboard sb = viewer.getScoreboard();
                    String ownerName = owner.getName();
                    Team currentTeam = sb.getEntryTeam(ownerName);
                    String currentTeamName = currentTeam != null ? currentTeam.getName() : null;
                    
                    // 前回記録したチーム名を取得
                    Map<UUID, String> lastTeams = lastKnownTeams.get(viewer.getUniqueId());
                    String lastTeamName = lastTeams != null ? lastTeams.get(owner.getUniqueId()) : null;
                    
                    // チームが変わった場合、または初期状態（nh_チーム以外）の場合
                    if (!Objects.equals(currentTeamName, lastTeamName) || 
                        (currentTeamName != null && !currentTeamName.startsWith(TEAM_PREFIX)) ||
                        currentTeamName == null) {
                        
                        // 以下の場合にチーム設定を適用：
                        // 1. チームが変更された
                        // 2. nh_チーム以外に所属している（ゲーム内チーム等）
                        // 3. どのチームにも所属していない（初期状態）
                        
                        // 再度隠す処理を適用して素の名前を隠す
                        sendHideOwnerNameToViewer(owner, viewer);
                    }
                } catch (Throwable ignored) {
                    // エラーは無視（次回のチェックで再試行）
                }
            }
        }
    }
}
