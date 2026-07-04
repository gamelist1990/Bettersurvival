package org.pexserver.koukunn.bettersurvival.Modules.Feature.WarpStone;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * ワープストーン (Waystones 風のワープネットワーク)。
 *
 * - エンダーパール×石レンガの合成でアイテムを作成し、設置して名前を付ける
 * - 他のワープストーンを右クリックすると「発見」され、以後ワープ先に選べる
 * - ワープUIから目的地を選んでテレポート
 * - GTA Animation 設定が ON の場合、透明なアーマースタンドを召喚して
 *   プレイヤーをスペクテイター視点で乗り移らせ、雲の上 (y≈210+) まで上昇 →
 *   目的地上空へ高速パン → 降下、という GTA のマップ移動風カメラで演出する
 */
public class WarpStoneModule implements Listener {

    public static final String FEATURE_KEY = "warpstone";
    public static final String ITEM_NAME = "§bワープストーン";

    private static final String PREFIX = "§b[ワープストーン]§r ";
    private static final int DISPLAY_RADIUS_SQUARED = 16 * 16;

    // ===== GTA アニメーションのパラメータ =====
    // GTA V の Switch Scene 仕様 (https://gta.fandom.com/wiki/Switch_Scenes):
    //  - カメラは最初から真上 (真下 90° 視点) のオーバーヘッドビュー
    //  - 高度が段階的に「3回ズームアウト」→ マップ上をパン → 「3回ズームイン」
    //  - 2地点が近い場合はズームせず直接切り替え
    /** 巡航高度の最低値 (雲 y≈192 の少し上) */
    private static final double MIN_CRUISE_Y = 210.0D;
    /** ズームの段数 (GTA は最大3回) */
    private static final int ZOOM_STEPS = 3;
    /** 1ズームの移動 tick / 停止 tick (カクッと寄る→一瞬止まる) */
    private static final int ZOOM_MOVE_TICKS = 7;
    private static final int ZOOM_HOLD_TICKS = 3;
    private static final int ZOOM_PHASE_TICKS = (ZOOM_MOVE_TICKS + ZOOM_HOLD_TICKS) * ZOOM_STEPS;
    /** ズームアウト後の各高度の割合 (低→高) */
    private static final double[] ZOOM_FRACTIONS = {0.30D, 0.65D, 1.00D};
    /** 着地間際の引き起こし */
    private static final int SETTLE_TICKS = 10;
    /**
     * パン速度。クライアントのチャンク受信+描画が確実に追従できる上限に抑える
     * (これ以上はどれだけ先読みしてもクライアントがクラッシュし得る)
     */
    private static final double CRUISE_BLOCKS_PER_TICK = 6.0D;
    /** 600〜1000m帯のチャンク壁対策。長距離だけ速度を落としてクライアント受信を追いつかせる */
    private static final double LONG_CRUISE_BLOCKS_PER_TICK = 4.0D;
    private static final double LONG_CRUISE_DISTANCE = 500.0D;
    private static final int MIN_CRUISE_TICKS = 35;
    private static final int MAX_CRUISE_TICKS = 260;
    /** 通常パン中、カメラを進行方向へこの距離だけ先出しして移動感を演出する */
    private static final double PAN_LOOK_AHEAD_BLOCKS = 3.0D;
    /** 移動中、チャンクロード用に約この距離ごとにSpectatorTargetを外す */
    private static final double PAN_LOAD_PULSE_DISTANCE_BLOCKS = 300.0D;
    /** 1周期のうちプレイヤー本体視点へ戻すtick数。1tickでもロードが走る前提で最小化する */
    private static final int PAN_LOAD_PULSE_TICKS = 1;
    /** これ以下の距離は GTA の「直接切り替え」相当 (アニメーション無し) */
    private static final double DIRECT_SWITCH_DISTANCE = 48.0D;
    /** 経路プリロードの最大チャンク数 (安全弁) */
    private static final int MAX_PRELOAD_CHUNKS = 220;
    /** 超軽量化のため中心線のみ先読みする */
    private static final int PRELOAD_CORRIDOR_RADIUS = 0;
    /** 1tickあたりの非同期チャンクロード上限 (バーストによるクラッシュ防止) */
    private static final int MAX_CHUNK_LOADS_PER_TICK = 2;
    /** カメラより何tick分先のチャンクまで先読みするか */
    private static final int PRELOAD_LOOKAHEAD_TICKS = 160;

    private final Loader plugin;
    private final ToggleModule toggle;
    private final WarpStoneStore store;
    private final NamespacedKey itemKey;
    private final NamespacedKey displayKey;

    /** locationKey → ワープストーン情報 */
    private final Map<String, WarpStoneStore.StoneData> stones = new LinkedHashMap<>();
    /** プレイヤー → 発見済み locationKey */
    private final Map<UUID, Set<String>> discovered = new HashMap<>();
    /** プレイヤー → GTA アニメーション設定 */
    private final Map<UUID, Boolean> gtaSettings = new HashMap<>();
    /** 進行中の GTA アニメーション */
    private final Map<UUID, WarpAnimation> animations = new HashMap<>();
    private final Map<String, UUID> displayIds = new LinkedHashMap<>();
    private final BukkitTask displayTask;

    public record StoneView(String key, String name, Location location) {
    }

    public WarpStoneModule(Loader plugin, ToggleModule toggle, ItemCombineModule itemCombineModule) {
        this.plugin = plugin;
        this.toggle = toggle;
        this.store = new WarpStoneStore(plugin.getConfigManager());
        this.itemKey = new NamespacedKey(plugin, "warp_stone");
        this.displayKey = new NamespacedKey(plugin, "warp_stone_display");

        itemCombineModule.recipe("warp_stone")
                .first(stack -> stack != null && stack.getType() == Material.ENDER_PEARL)
                .second(stack -> stack != null && stack.getType() == Material.STONE_BRICKS)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftWarpStone);

        WarpStoneStore.LoadResult loaded = store.loadAll();
        stones.putAll(loaded.stones());
        discovered.putAll(loaded.discovered());
        gtaSettings.putAll(loaded.gta());

        displayTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickDisplays, 40L, 40L);
    }

    private boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    private void save() {
        store.saveAll(stones, discovered, gtaSettings);
    }

    // ================= クラフト / アイテム =================

    private ItemStack createWarpStoneItem() {
        ItemStack stack = new ItemStack(Material.LODESTONE);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        ComponentUtils.setLore(meta,
                "§7設置して名前を付けるとワープ地点になる",
                "§7他のワープストーンを右クリックで発見し、",
                "§7発見済みの地点へワープできる",
                "§7GTA Animation 対応 ✈");
        meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, "true");
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isWarpStoneItem(ItemStack stack) {
        if (stack == null || stack.getType() != Material.LODESTONE || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(itemKey, PersistentDataType.STRING);
    }

    private void craftWarpStone(ItemCombineModule.CombineMatch match) {
        if (!isFeatureEnabled()) {
            return;
        }
        if (!match.first().isValid() || !match.second().isValid()) {
            return;
        }
        Location center = match.center();
        World world = center.getWorld();
        if (world == null) {
            return;
        }
        match.consumeMatchedItems(1, 1);
        world.dropItemNaturally(center, createWarpStoneItem());
        world.playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.2F);
        world.spawnParticle(Particle.PORTAL, center, 40, 0.4D, 0.4D, 0.4D, 0.5D);
    }

    // ================= 設置 / 破壊 =================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!isWarpStoneItem(event.getItemInHand())) {
            return;
        }
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "§cワープストーン機能は現在無効です");
            return;
        }
        Player player = event.getPlayer();
        Location location = event.getBlockPlaced().getLocation();
        String key = WarpStoneStore.toKey(location);
        String defaultName = player.getName() + "のワープストーン";
        stones.put(key, new WarpStoneStore.StoneData(defaultName, player.getUniqueId()));
        discoveredSet(player.getUniqueId()).add(key);
        save();
        ensureDisplay(location);
        player.sendMessage(PREFIX + "§a設置しました。名前を入力してください");
        // 設置操作と同tickにダイアログを開くとクライアント側で閉じられるため遅延させる
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline() && stones.containsKey(key)) {
                promptRename(player, key);
            }
        }, 3L);
    }

    private void promptRename(Player player, String key) {
        WarpStoneStore.StoneData data = stones.get(key);
        if (data == null) {
            return;
        }
        ChestUI.openChat(player, "ワープストーンの名前", data.name(), input -> {
            WarpStoneStore.StoneData current = stones.get(key);
            if (current == null) {
                return;
            }
            String name = input == null || input.isBlank() ? current.name() : input.trim();
            if (name.length() > 24) {
                name = name.substring(0, 24);
            }
            stones.put(key, new WarpStoneStore.StoneData(name, current.owner()));
            save();
            Location loc = WarpStoneStore.fromKey(key);
            if (loc != null) {
                refreshDisplayText(key, loc);
            }
            player.sendMessage(PREFIX + "§a名前を「§b" + name + "§a」にしました");
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String key = WarpStoneStore.toKey(block.getLocation());
        if (!stones.containsKey(key)) {
            return;
        }
        event.setDropItems(false);
        removeStone(key, block.getLocation(), event.getPlayer().getGameMode() != GameMode.CREATIVE);
        event.getPlayer().sendMessage(PREFIX + "§eワープストーンを撤去しました");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        removeExploded(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        removeExploded(event.blockList());
    }

    private void removeExploded(List<Block> blocks) {
        for (Block block : new ArrayList<>(blocks)) {
            String key = WarpStoneStore.toKey(block.getLocation());
            if (stones.containsKey(key)) {
                removeStone(key, block.getLocation(), true);
            }
        }
    }

    private void removeStone(String key, Location location, boolean dropItem) {
        stones.remove(key);
        for (Set<String> seen : discovered.values()) {
            seen.remove(key);
        }
        removeDisplay(key);
        save();
        World world = location.getWorld();
        if (dropItem && world != null) {
            world.dropItemNaturally(location.clone().add(0.5D, 0.3D, 0.5D), createWarpStoneItem());
        }
    }

    // ================= 発見 / UI =================

    private Set<String> discoveredSet(UUID uuid) {
        return discovered.computeIfAbsent(uuid, id -> new LinkedHashSet<>());
    }

    List<StoneView> discoveredStones(UUID uuid, String excludeKey) {
        List<StoneView> views = new ArrayList<>();
        for (String key : discoveredSet(uuid)) {
            if (key.equals(excludeKey)) {
                continue;
            }
            WarpStoneStore.StoneData data = stones.get(key);
            Location location = WarpStoneStore.fromKey(key);
            if (data == null || location == null || location.getWorld() == null) {
                continue;
            }
            views.add(new StoneView(key, data.name(), location));
        }
        views.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        return views;
    }

    boolean isGtaEnabled(UUID uuid) {
        return gtaSettings.getOrDefault(uuid, true);
    }

    boolean toggleGta(UUID uuid) {
        boolean now = !isGtaEnabled(uuid);
        gtaSettings.put(uuid, now);
        save();
        return now;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        String key = WarpStoneStore.toKey(block.getLocation());
        WarpStoneStore.StoneData data = stones.get(key);
        if (data == null) {
            return;
        }
        Player player = event.getPlayer();
        event.setCancelled(true);
        if (!isFeatureEnabled()) {
            player.sendMessage(PREFIX + "§cワープストーン機能は現在無効です");
            return;
        }
        if (animations.containsKey(player.getUniqueId())) {
            return;
        }
        // オーナーがスニーク+素手で右クリック → 名前変更
        if (player.isSneaking() && player.getUniqueId().equals(data.owner())
                && player.getInventory().getItemInMainHand().getType().isAir()) {
            promptRename(player, key);
            return;
        }
        // 未発見なら発見登録
        if (discoveredSet(player.getUniqueId()).add(key)) {
            save();
            player.sendMessage(PREFIX + "§a「§b" + data.name() + "§a」を発見しました！");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.5F);
            player.getWorld().spawnParticle(Particle.PORTAL,
                    block.getLocation().add(0.5D, 1.0D, 0.5D), 40, 0.4D, 0.5D, 0.4D, 0.5D);
        }
        new WarpStoneUI(this, player, key).open(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof WarpStoneUI ui)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < WarpStoneUI.SIZE && ui.viewerId().equals(player.getUniqueId())) {
            ui.handleClick(player, rawSlot);
        }
    }

    // ================= テレポート =================

    void requestTeleport(Player player, String destKey) {
        if (!isFeatureEnabled() || animations.containsKey(player.getUniqueId())) {
            return;
        }
        WarpStoneStore.StoneData data = stones.get(destKey);
        Location stoneLoc = WarpStoneStore.fromKey(destKey);
        if (data == null || stoneLoc == null || stoneLoc.getWorld() == null) {
            player.sendMessage(PREFIX + "§cそのワープストーンは失われています");
            return;
        }
        if (stoneLoc.getBlock().getType() != Material.LODESTONE) {
            player.sendMessage(PREFIX + "§cワープストーンが破壊されています");
            removeStone(destKey, stoneLoc, false);
            return;
        }
        Location dest = stoneLoc.clone().add(0.5D, 1.0D, 0.5D);
        dest.setYaw(player.getLocation().getYaw());
        boolean sameWorld = player.getWorld().equals(dest.getWorld());
        // 最新の Geyser はスペクテイターの視点乗り移りに対応したため Bedrock も対象
        boolean gta = isGtaEnabled(player.getUniqueId()) && sameWorld
                && player.getGameMode() != GameMode.SPECTATOR
                && player.getLocation().distance(dest) > DIRECT_SWITCH_DISTANCE; // 近距離は直接切り替え (GTA仕様)
        if (gta) {
            startGtaAnimation(player, dest, data.name());
        } else {
            player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 40, 0.3D, 0.6D, 0.3D, 0.5D);
            player.teleport(dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
            arrivalEffects(player, dest, data.name());
        }
    }

    private void arrivalEffects(Player player, Location dest, String name) {
        World world = dest.getWorld();
        world.playSound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        world.spawnParticle(Particle.PORTAL, dest, 60, 0.4D, 0.8D, 0.4D, 0.6D);
        player.sendMessage(PREFIX + "§a「§b" + name + "§a」にワープしました");
    }

    // ================= GTA アニメーション =================

    private void startGtaAnimation(Player player, Location dest, String destName) {
        Location start = player.getLocation();
        World world = start.getWorld();
        double cruiseY = Math.min(world.getMaxHeight() - 8,
                Math.max(MIN_CRUISE_Y, Math.max(start.getY(), dest.getY()) + 40.0D));
        double dx = dest.getX() - start.getX();
        double dz = dest.getZ() - start.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float travelYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));

        double cruiseSpeed = horizontal > LONG_CRUISE_DISTANCE ? LONG_CRUISE_BLOCKS_PER_TICK : CRUISE_BLOCKS_PER_TICK;
        int cruiseTicks = (int) Math.max(MIN_CRUISE_TICKS,
            Math.min(MAX_CRUISE_TICKS, horizontal / cruiseSpeed));

        // ズームアウト/ズームインの高度段 (GTA の3段階ズーム)
        double startBaseY = start.getY() + 12.0D;
        double destBaseY = dest.getY() + 12.0D;
        // ズームアウト: base → 30% → 65% → 100%(cruise)、ズームインはその逆順
        double[] outLevels = {
                startBaseY,
                lerp(startBaseY, cruiseY, ZOOM_FRACTIONS[0]),
                lerp(startBaseY, cruiseY, ZOOM_FRACTIONS[1]),
                cruiseY};
        double[] inLevels = {
                cruiseY,
                lerp(destBaseY, cruiseY, ZOOM_FRACTIONS[1]),
                lerp(destBaseY, cruiseY, ZOOM_FRACTIONS[0]),
                destBaseY};

        // カメラ: 開始時点から真上・真下 90° 視点 (GTA のオーバーヘッドビュー)
        Location cameraStart = new Location(world, start.getX(), startBaseY, start.getZ(), travelYaw, 90.0F);
        ArmorStand camera = world.spawn(cameraStart, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.setSilent(true);
        });

        // プレイヤーの分身 (Mannequin): スペクテイター化で消える自分の姿を
        // 上空へ行くまで地上に見せておく
        Mannequin dummy = null;
        if (horizontal <= LONG_CRUISE_DISTANCE) {
            try {
                dummy = world.spawn(start, Mannequin.class, m -> {
                    try {
                        m.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                    } catch (Throwable ignored) {
                    }
                    m.setImmovable(true);
                    m.setInvulnerable(true);
                    m.setSilent(true);
                    m.setPersistent(false);
                    m.setCollidable(false);
                    EntityEquipment equipment = m.getEquipment();
                    equipment.setArmorContents(player.getInventory().getArmorContents());
                    equipment.setItemInMainHand(player.getInventory().getItemInMainHand().clone());
                });
            } catch (Throwable ignored) {
            }
        }

        WarpAnimation animation = new WarpAnimation(player.getUniqueId(), camera, dummy, dest.clone(), destName,
                player.getGameMode(), start.clone(), cruiseY, cruiseTicks, travelYaw, outLevels, inLevels, destBaseY,
            horizontal);
        animations.put(player.getUniqueId(), animation);

        player.setGameMode(GameMode.SPECTATOR);
        player.setSpectatorTarget(camera);
        world.playSound(start, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.6F);

        // 経路チャンクの読み込みキューを構築 (実際のロードはカメラの少し先を
        // 毎tick少量ずつ行う。バーストロードはサーバー/クライアント双方を殺すため)
        buildPreloadQueue(animation);

        animation.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickAnimation(animation), 1L, 1L);
    }

    /** 経路チャンクを進行度付きでキューに詰める。全距離で通常経路のみ使う */
    private void buildPreloadQueue(WarpAnimation anim) {
        java.util.LinkedHashMap<Long, Double> ordered = new java.util.LinkedHashMap<>();
        addCorridor(ordered, anim.start.getX(), anim.start.getZ(), anim.dest.getX(), anim.dest.getZ(), 0.0D, 1.0D);
        for (Map.Entry<Long, Double> entry : ordered.entrySet()) {
            anim.preloadQueue.add(new PreloadEntry(entry.getKey(), entry.getValue()));
        }
    }

    private static void addCorridor(java.util.LinkedHashMap<Long, Double> ordered,
                                    double fromX, double fromZ, double toX, double toZ, double tStart, double tEnd) {
        double dx = toX - fromX;
        double dz = toZ - fromZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        int samples = Math.max(1, (int) Math.ceil(distance / 32.0D));
        for (int i = 0; i <= samples && ordered.size() < MAX_PRELOAD_CHUNKS; i++) {
            double local = (double) i / samples;
            double t = tStart + (tEnd - tStart) * local;
            int chunkX = (int) Math.floor((fromX + dx * local) / 16.0D);
            int chunkZ = (int) Math.floor((fromZ + dz * local) / 16.0D);
            for (int ox = -PRELOAD_CORRIDOR_RADIUS; ox <= PRELOAD_CORRIDOR_RADIUS; ox++) {
                for (int oz = -PRELOAD_CORRIDOR_RADIUS; oz <= PRELOAD_CORRIDOR_RADIUS; oz++) {
                    ordered.putIfAbsent(chunkKey(chunkX + ox, chunkZ + oz), t);
                }
            }
        }
    }

    /** カメラの少し先までのチャンクを、1tickの上限内で非同期ロードする */
    private void drainPreload(WarpAnimation anim, double loadProgress, double releaseProgress) {
        World world = anim.start.getWorld();
        if (world == null || anim.preloadIndex >= anim.preloadQueue.size()) {
            releaseOldPathChunks(anim, releaseProgress);
            return;
        }
        double lookahead = (double) PRELOAD_LOOKAHEAD_TICKS / Math.max(1, anim.cruiseTicks);
        double targetT = Math.min(1.0D, loadProgress + lookahead);
        int loads = 0;
        while (anim.preloadIndex < anim.preloadQueue.size() && loads < MAX_CHUNK_LOADS_PER_TICK) {
            PreloadEntry entry = anim.preloadQueue.get(anim.preloadIndex);
            if (entry.t() > targetT) {
                break;
            }
            anim.preloadIndex++;
            loads++;
            int chunkX = (int) entry.key();
            int chunkZ = (int) (entry.key() >> 32);
            world.getChunkAtAsync(chunkX, chunkZ).thenAccept(chunk -> {
                if (anim.finished) {
                    return; // 既に終了していたらチケットを張らない
                }
                world.addPluginChunkTicket(chunkX, chunkZ, plugin);
                anim.ticketChunks.add(entry.key());
                anim.ticketProgress.put(entry.key(), entry.t());
            });
        }
        releaseOldPathChunks(anim, releaseProgress);
    }

    /** 進行済みチャンクのチケットを早めに外し、長距離でも保持数を増やさない */
    private void releaseOldPathChunks(WarpAnimation anim, double panProgress) {
        World world = anim.start.getWorld();
        if (world == null || anim.ticketChunks.isEmpty()) {
            return;
        }
        double keepBehind = Math.max(0.12D, 18.0D / Math.max(1.0D, anim.horizontalDistance));
        double releaseBefore = panProgress - keepBehind;
        if (releaseBefore <= 0.0D) {
            return;
        }
        for (Long key : new ArrayList<>(anim.ticketChunks)) {
            double t = anim.ticketProgress.getOrDefault(key, 1.0D);
            if (t < releaseBefore) {
                world.removePluginChunkTicket((int) (long) key, (int) (key >> 32), plugin);
                anim.ticketChunks.remove(key);
                anim.ticketProgress.remove(key);
            }
        }
    }

    private void releasePathChunks(WarpAnimation anim) {
        World world = anim.start.getWorld();
        if (world == null) {
            anim.ticketChunks.clear();
            return;
        }
        for (long key : anim.ticketChunks) {
            world.removePluginChunkTicket((int) key, (int) (key >> 32), plugin);
        }
        anim.ticketChunks.clear();
        anim.ticketProgress.clear();
    }

    private static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkZ << 32) | (chunkX & 0xFFFFFFFFL);
    }

    private void tickAnimation(WarpAnimation anim) {
        Player player = Bukkit.getPlayer(anim.playerId);
        if (player == null || !player.isOnline() || !anim.camera.isValid()) {
            finishAnimation(anim, player, false);
            return;
        }
        anim.tick++;
        int total = ZOOM_PHASE_TICKS + anim.cruiseTicks + ZOOM_PHASE_TICKS + SETTLE_TICKS;
        if (anim.tick >= total) {
            finishAnimation(anim, player, true);
            return;
        }

        // カメラの少し先のチャンクを毎tick少量ずつ先読みする。
        // ズームアウト中は経路全体を先行ロードし、パン開始後に中間チャンクを新規ロードしない形へ寄せる。
        double panProgress = Math.max(0.0D, Math.min(1.0D,
                (double) (anim.tick - ZOOM_PHASE_TICKS) / Math.max(1, anim.cruiseTicks)));
        double loadProgress = anim.tick <= ZOOM_PHASE_TICKS ? 1.0D : panProgress;
        drainPreload(anim, loadProgress, panProgress);

        Location pos;
        boolean loadPulse = false;
        float pitch = 90.0F; // 常時オーバーヘッド (真下) 視点
        boolean useCameraTarget = true;
        if (anim.tick <= ZOOM_PHASE_TICKS) {
            // フェーズ1: 3段階ズームアウト (自分の分身を見下ろしながら)
            pos = new Location(anim.start.getWorld(), anim.start.getX(),
                    steppedZoomY(anim.outLevels, anim.tick, player), anim.start.getZ());
            if (anim.tick == ZOOM_PHASE_TICKS) {
                removeDummy(anim); // 上空に到達したら分身を撤去
            }
        } else if (anim.tick <= ZOOM_PHASE_TICKS + anim.cruiseTicks) {
            // フェーズ2: 基本はArmorStand視点で滑らかにパンする。
            // ただし一定周期で1tickだけSpectatorTargetを外し、プレイヤー本体視点へ戻して
            // バニラのチャンクロードを走らせる。
            int panTick = anim.tick - ZOOM_PHASE_TICKS;
            loadPulse = shouldPulseLoadPlayer(panTick, anim.cruiseTicks, anim.horizontalDistance);
            // ロードパルスでもカメラ対象は外さない。
            // 視点ズレの原因になるため、チャンク中心更新だけをNMS packetで行う。
            useCameraTarget = true;
            double raw = (double) panTick / anim.cruiseTicks;
            double[] xz = panPosition(anim, raw);
            // 進行方向へ先出しして移動感を演出 (GrandTeleport の body glide 相当)
            double fwdX = -Math.sin(Math.toRadians(anim.travelYaw));
            double fwdZ = Math.cos(Math.toRadians(anim.travelYaw));
            pos = new Location(anim.start.getWorld(),
                    xz[0] + fwdX * PAN_LOOK_AHEAD_BLOCKS,
                    anim.cruiseY,
                    xz[1] + fwdZ * PAN_LOOK_AHEAD_BLOCKS);
        } else if (anim.tick <= ZOOM_PHASE_TICKS + anim.cruiseTicks + ZOOM_PHASE_TICKS) {
            // フェーズ3: 目的地上空で3段階ズームイン
            int local = anim.tick - ZOOM_PHASE_TICKS - anim.cruiseTicks;
            pos = new Location(anim.start.getWorld(), anim.dest.getX(),
                    steppedZoomY(anim.inLevels, local, player), anim.dest.getZ());
        } else {
            // フェーズ4: 着地。間際でカメラを水平へ引き起こして一人称に繋げる
            double s = (double) (anim.tick - ZOOM_PHASE_TICKS - anim.cruiseTicks - ZOOM_PHASE_TICKS) / SETTLE_TICKS;
            pos = new Location(anim.start.getWorld(), anim.dest.getX(),
                    lerp(anim.destBaseY, anim.dest.getY() + 2.0D, easeOut(s)), anim.dest.getZ());
            pitch = s < 0.5D ? 90.0F : (float) lerp(90.0D, 10.0D, (s - 0.5D) / 0.5D);
        }
        pos.setYaw(anim.travelYaw);
        pos.setPitch(pitch);
        Location playerViewAligned = playerLocationForCameraView(player, pos);
        Location cameraEntityAligned = cameraLocationForView(anim.camera, pos);
        if (useCameraTarget) {
            player.teleport(playerViewAligned, PlayerTeleportEvent.TeleportCause.PLUGIN);
            anim.camera.teleport(cameraEntityAligned);
            setClientCamera(player, anim.camera);
            if (loadPulse) {
                sendChunkCacheCenterPacket(player, pos);
            }
        } else {
            // 移動中は乗り移らないが、下降フェーズへの切り替えでズレないよう
            // カメラ用 ArmorStand の位置だけは常に同期しておく。
            anim.camera.teleport(cameraEntityAligned);
            player.teleport(playerViewAligned, PlayerTeleportEvent.TeleportCause.PLUGIN);
            // Bukkit の setSpectatorTarget(null) はクライアント側で一瞬ズレが出やすいので、
            // NMS camera packet で「カメラ対象をプレイヤー本人」に直接戻す。
            // これにより降りる位置をプレイヤー目線補正済み座標に固定できる。
            setClientCamera(player, player);
        }
    }

    /**
     * クライアントのカメラ対象を直接指定する。
     * Bukkit API の setSpectatorTarget(null) は「解除」扱いで1フレームずれることがあるため、
     * ロードパルス時は NMS の ClientboundSetCameraPacket で player 本人を camera にする。
     * バージョン差を避けるため reflection で呼び、失敗時だけ Bukkit API にフォールバックする。
     */
    private void setClientCamera(Player viewer, org.bukkit.entity.Entity target) {
        if (sendSetCameraPacket(viewer, target)) {
            return;
        }
        if (target == viewer) {
            viewer.setSpectatorTarget(null);
        } else {
            viewer.setSpectatorTarget(target);
        }
    }

    private boolean sendSetCameraPacket(Player viewer, org.bukkit.entity.Entity target) {
        try {
            Object serverPlayer = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = serverPlayer.getClass().getField("connection").get(serverPlayer);
            Object nmsTarget = target.getClass().getMethod("getHandle").invoke(target);

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetCameraPacket");
            Object packet = null;
            for (java.lang.reflect.Constructor<?> constructor : packetClass.getConstructors()) {
                Class<?>[] parameterTypes = constructor.getParameterTypes();
                if (parameterTypes.length == 1 && parameterTypes[0].isAssignableFrom(nmsTarget.getClass())) {
                    packet = constructor.newInstance(nmsTarget);
                    break;
                }
            }
            if (packet == null) {
                return false;
            }
            return sendPacketByReflection(connection, packet);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * カメラ対象を降ろさず、クライアントのチャンク送信中心だけを指定する。
     * これにより ArmorStand 視点を維持したまま、約300ブロックごとにロード中心を進める。
     */
    private boolean sendChunkCacheCenterPacket(Player viewer, Location center) {
        try {
            Object serverPlayer = viewer.getClass().getMethod("getHandle").invoke(viewer);
            Object connection = serverPlayer.getClass().getField("connection").get(serverPlayer);
            int chunkX = center.getBlockX() >> 4;
            int chunkZ = center.getBlockZ() >> 4;

            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.game.ClientboundSetChunkCacheCenterPacket");
            Object packet = packetClass.getConstructor(int.class, int.class).newInstance(chunkX, chunkZ);
            return sendPacketByReflection(connection, packet);
        } catch (Throwable ignored) {
        }
        return false;
    }

    /**
     * Paper 26.2 では対象パケットクラス自体は存在するが、javap上 ServerGamePacketListenerImpl の
     * public send メソッドが見えない場合がある。継承元/非publicの declared method まで探索して送る。
     */
    private boolean sendPacketByReflection(Object connection, Object packet) {
        Class<?> type = connection.getClass();
        while (type != null) {
            for (java.lang.reflect.Method method : type.getDeclaredMethods()) {
                if (!method.getName().equals("send") || method.getParameterCount() != 1) {
                    continue;
                }
                Class<?> parameterType = method.getParameterTypes()[0];
                if (!parameterType.isAssignableFrom(packet.getClass())) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    method.invoke(connection, packet);
                    return true;
                } catch (Throwable ignored) {
                }
            }
            type = type.getSuperclass();
        }
        return false;
    }

    /**
     * SpectatorTarget を外した瞬間に画面が上下にズレないよう、
     * プレイヤー自身の目線位置が ArmorStand カメラ位置と一致する足元座標へ補正する。
     */
    private Location playerLocationForCameraView(Player player, Location cameraView) {
        Location location = cameraView.clone();
        location.setY(cameraView.getY() - player.getEyeHeight());
        location.setYaw(cameraView.getYaw());
        location.setPitch(cameraView.getPitch());
        return location;
    }

    /**
     * ArmorStand に乗り移った瞬間の視点ズレを抑えるため、
     * ArmorStand の目線位置が cameraView と一致する実体座標へ補正する。
     */
    private Location cameraLocationForView(ArmorStand camera, Location cameraView) {
        Location location = cameraView.clone();
        location.setY(cameraView.getY() - camera.getEyeHeight());
        location.setYaw(cameraView.getYaw());
        location.setPitch(cameraView.getPitch());
        return location;
    }

    /**
     * 段階ズームの高度計算。各段は「素早く移動 (easeInOut) → 一瞬停止」を繰り返し、
     * GTA の衛星マップがカクッカクッと切り替わる質感を再現する。
     */
    private double steppedZoomY(double[] levels, int localTick, Player player) {
        int perStep = ZOOM_MOVE_TICKS + ZOOM_HOLD_TICKS;
        int step = Math.min(ZOOM_STEPS - 1, Math.max(0, (localTick - 1) / perStep));
        int within = (localTick - 1) % perStep;
        if (within == 0 && player != null) {
            // 各ズーム開始時のヒュンという効果音 (段ごとにピッチを上げる)
            player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.16F, 1.2F + step * 0.25F);
        }
        if (within < ZOOM_MOVE_TICKS) {
            double t = easeInOutCubic((double) (within + 1) / ZOOM_MOVE_TICKS);
            return lerp(levels[step], levels[step + 1], t);
        }
        return levels[step + 1];
    }

    /** パン進行度 (0〜1) からカメラの XZ を求める */
    private double[] panPosition(WarpAnimation anim, double raw) {
        double t = smootherStep(raw);
        return new double[]{
                lerp(anim.start.getX(), anim.dest.getX(), t),
                lerp(anim.start.getZ(), anim.dest.getZ(), t)};
    }

    /**
     * 移動中は大半をArmorStand視点にして滑らかさを優先する。
     * ただし約300ブロックごとに1tickだけSpectatorTargetを外し、その地点のバニラチャンクロードを走らせる。
     */
    private boolean shouldPulseLoadPlayer(int panTick, int cruiseTicks, double horizontalDistance) {
        if (panTick <= PAN_LOAD_PULSE_TICKS) {
            return false;
        }
        if (cruiseTicks - panTick <= PAN_LOAD_PULSE_TICKS) {
            return false;
        }
        if (horizontalDistance < PAN_LOAD_PULSE_DISTANCE_BLOCKS) {
            return false;
        }
        double previousDistance = horizontalDistance * (double) (panTick - 1) / Math.max(1, cruiseTicks);
        double currentDistance = horizontalDistance * (double) panTick / Math.max(1, cruiseTicks);
        int previousSegment = (int) (previousDistance / PAN_LOAD_PULSE_DISTANCE_BLOCKS);
        int currentSegment = (int) (currentDistance / PAN_LOAD_PULSE_DISTANCE_BLOCKS);
        return currentSegment > previousSegment;
    }

    private void removeDummy(WarpAnimation anim) {
        if (anim.dummy != null && anim.dummy.isValid()) {
            anim.dummy.remove();
        }
        anim.dummy = null;
    }

    private void finishAnimation(WarpAnimation anim, Player player, boolean success) {
        anim.finished = true;
        if (anim.task != null) {
            anim.task.cancel();
        }
        animations.remove(anim.playerId);
        removeDummy(anim);
        releasePathChunks(anim);
        if (player != null && player.isOnline()) {
            player.setSpectatorTarget(null);
            player.teleport(anim.dest, PlayerTeleportEvent.TeleportCause.PLUGIN);
            player.setGameMode(anim.previousMode);
            if (success) {
                arrivalEffects(player, anim.dest, anim.destName);
            }
        }
        if (anim.camera.isValid()) {
            anim.camera.remove();
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        WarpAnimation anim = animations.get(event.getPlayer().getUniqueId());
        if (anim != null) {
            // 切断時はその場で完了扱いにして、スペクテイターのまま残さない
            finishAnimation(anim, event.getPlayer(), false);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerToggleSneak(org.bukkit.event.player.PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        WarpAnimation anim = animations.get(player.getUniqueId());
        if (anim == null) {
            return;
        }
        // Spectator中のSneakはカメラ対象から離脱してアニメーションが壊れるため完全にキャンセルする。
        event.setCancelled(true);
        if (player.getGameMode() != GameMode.SPECTATOR || !anim.camera.isValid()) {
            return;
        }
        // クライアント側で一瞬降りかけても即座にArmorStandカメラへ戻す。
        setClientCamera(player, anim.camera);
        Bukkit.getScheduler().runTask(plugin, () -> {
            WarpAnimation current = animations.get(player.getUniqueId());
            if (current == anim && player.isOnline() && anim.camera.isValid()) {
                setClientCamera(player, anim.camera);
            }
        });
    }

    private static double lerp(double from, double to, double t) {
        return from + (to - from) * Math.max(0.0D, Math.min(1.0D, t));
    }

    private static double easeOut(double t) {
        return 1.0D - (1.0D - t) * (1.0D - t);
    }

    private static double easeInOutCubic(double t) {
        return t < 0.5D ? 4.0D * t * t * t : 1.0D - Math.pow(-2.0D * t + 2.0D, 3.0D) / 2.0D;
    }

    private static double smootherStep(double t) {
        t = Math.max(0.0D, Math.min(1.0D, t));
        return t * t * t * (t * (t * 6.0D - 15.0D) + 10.0D);
    }

    // ================= 頭上ネームプレート =================

    private void tickDisplays() {
        for (String key : new ArrayList<>(stones.keySet())) {
            Location location = WarpStoneStore.fromKey(key);
            if (location == null || location.getWorld() == null) {
                removeDisplay(key);
                continue;
            }
            World world = location.getWorld();
            if (!world.isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
                removeDisplay(key);
                continue;
            }
            if (location.getBlock().getType() != Material.LODESTONE) {
                removeStone(key, location, false);
                continue;
            }
            ensureDisplay(location);
        }
    }

    private void ensureDisplay(Location location) {
        String key = WarpStoneStore.toKey(location);
        if (!hasNearbyPlayer(location)) {
            removeDisplay(key);
            return;
        }
        TextDisplay display = getTrackedDisplay(key);
        if (display == null) {
            display = findNearbyDisplay(location, key);
            if (display == null) {
                display = spawnDisplay(location, key);
            }
            if (display != null) {
                displayIds.put(key, display.getUniqueId());
                refreshDisplayText(key, location);
            }
        }
    }

    private void refreshDisplayText(String key, Location location) {
        TextDisplay display = getTrackedDisplay(key);
        WarpStoneStore.StoneData data = stones.get(key);
        if (display != null && data != null) {
            display.text(ComponentUtils.legacy("§b◈ " + data.name() + " ◈\n§7右クリックでワープ"));
        }
    }

    private boolean hasNearbyPlayer(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return false;
        }
        for (Player player : world.getPlayers()) {
            if (player.getLocation().distanceSquared(location) <= DISPLAY_RADIUS_SQUARED) {
                return true;
            }
        }
        return false;
    }

    private TextDisplay getTrackedDisplay(String key) {
        UUID uuid = displayIds.get(key);
        if (uuid == null) {
            return null;
        }
        if (!(Bukkit.getEntity(uuid) instanceof TextDisplay display) || !display.isValid()) {
            displayIds.remove(key);
            return null;
        }
        return display;
    }

    private TextDisplay findNearbyDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        for (org.bukkit.entity.Entity entity : world.getNearbyEntities(location.clone().add(0.5D, 1.3D, 0.5D), 0.8D, 1.2D, 0.8D)) {
            if (!(entity instanceof TextDisplay display)) {
                continue;
            }
            PersistentDataContainer container = display.getPersistentDataContainer();
            if (key.equals(container.get(displayKey, PersistentDataType.STRING))) {
                return display;
            }
        }
        return null;
    }

    private TextDisplay spawnDisplay(Location location, String key) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        Location spawn = location.clone().add(0.5D, 1.4D, 0.5D);
        return world.spawn(spawn, TextDisplay.class, display -> {
            display.setBillboard(Display.Billboard.CENTER);
            display.setSeeThrough(false);
            display.setShadowed(false);
            display.setTransformation(new Transformation(new Vector3f(), new AxisAngle4f(), new Vector3f(0.9F, 0.9F, 0.9F), new AxisAngle4f()));
            display.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, key);
        });
    }

    private void removeDisplay(String key) {
        TextDisplay display = getTrackedDisplay(key);
        if (display != null) {
            display.remove();
        }
        displayIds.remove(key);
    }

    // ================= 終了 =================

    public void shutdown() {
        displayTask.cancel();
        for (WarpAnimation anim : new ArrayList<>(animations.values())) {
            finishAnimation(anim, Bukkit.getPlayer(anim.playerId), false);
        }
        animations.clear();
        for (String key : new ArrayList<>(displayIds.keySet())) {
            removeDisplay(key);
        }
        save();
    }

    private static final class WarpAnimation {
        final UUID playerId;
        final ArmorStand camera;
        final Location dest;
        final String destName;
        final GameMode previousMode;
        final Location start;
        final double cruiseY;
        final int cruiseTicks;
        final float travelYaw;
        final double[] outLevels;
        final double[] inLevels;
        final double destBaseY;
        final double horizontalDistance;
        Mannequin dummy;
        BukkitTask task;
        int tick;
        boolean finished;
        final Set<Long> ticketChunks = new LinkedHashSet<>();
        final Map<Long, Double> ticketProgress = new HashMap<>();
        final List<PreloadEntry> preloadQueue = new ArrayList<>();
        int preloadIndex;

        WarpAnimation(UUID playerId, ArmorStand camera, Mannequin dummy, Location dest, String destName,
                      GameMode previousMode, Location start, double cruiseY, int cruiseTicks, float travelYaw,
                      double[] outLevels, double[] inLevels, double destBaseY, double horizontalDistance) {
            this.playerId = playerId;
            this.camera = camera;
            this.dummy = dummy;
            this.dest = dest;
            this.destName = destName;
            this.previousMode = previousMode;
            this.start = start;
            this.cruiseY = cruiseY;
            this.cruiseTicks = cruiseTicks;
            this.travelYaw = travelYaw;
            this.outLevels = outLevels;
            this.inLevels = inLevels;
            this.destBaseY = destBaseY;
            this.horizontalDistance = horizontalDistance;
        }
    }

    private record PreloadEntry(long key, double t) {
    }
}
