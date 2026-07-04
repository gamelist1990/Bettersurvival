package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Switch;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Hanging;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ui.LandMenu;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyRank;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 土地保護モジュール（Rust の Tool Cupboard 風）。
 *
 * ロデストーン × ダイヤモンド を投げて合成すると「土地保護コア」ができる。
 * 設置して右クリックするとオーナー登録され、燃料（原木・板材・木炭・石炭など）を
 * 投入している間だけ保護が維持される。レベルアップには鉱石が必要で、
 * レベル・オーナー・燃料などのデータはコアを壊してもアイテムに引き継がれる。
 */
public class LandProtectionModule implements Listener {

    public static final String FEATURE_KEY = "landprotect";
    public static final String ITEM_NAME = "§6土地保護コア";
    public static final Material CORE_MATERIAL = Material.LODESTONE;

    private static final long UPKEEP_PERIOD_TICKS = 20L * 60L; // 1分ごと
    private static final long DENY_MESSAGE_COOLDOWN_MILLIS = 1500L;

    private final ToggleModule toggle;
    private final PartyModule partyModule;
    private final ClaimStore store;
    private final LandMenu menu;
    private final ClaimVisualizer visualizer;
    /** リング(闘技場)モジュール。Loader から注入される。 */
    private org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule ringModule;

    private final Map<String, ClaimRegion> claims = new LinkedHashMap<>();
    /** ワールド名 -> そのワールドの保護領域一覧（高速参照用） */
    private final Map<String, List<ClaimRegion>> worldIndex = new LinkedHashMap<>();
    /** プレイヤーが現在滞在している保護領域キー（侵入通知用） */
    private final Map<UUID, String> insideClaim = new ConcurrentHashMap<>();
    /** 拒否メッセージのスパム防止 */
    private final Map<UUID, Long> denyMessageAt = new ConcurrentHashMap<>();
    /** claimKey -> 進行中のレイド */
    private final Map<String, RaidSession> activeRaids = new ConcurrentHashMap<>();

    private final NamespacedKey coreKey;
    private final NamespacedKey dataOwnerKey;
    private final NamespacedKey dataOwnerNameKey;
    private final NamespacedKey dataPartyKey;
    private final NamespacedKey dataLevelKey;
    private final NamespacedKey dataFuelKey;
    private final NamespacedKey dataWhitelistKey;
    private final NamespacedKey dataSettingsKey;

    private final BukkitTask upkeepTask;
    private final BukkitTask raidTask;

    public LandProtectionModule(Loader plugin, ToggleModule toggle,
                                ItemCombineModule itemCombineModule, PartyModule partyModule) {
        this.toggle = toggle;
        this.partyModule = partyModule;
        this.store = new ClaimStore(plugin.getConfigManager());
        this.coreKey = new NamespacedKey(plugin, "land_core");
        this.dataOwnerKey = new NamespacedKey(plugin, "land_core_owner");
        this.dataOwnerNameKey = new NamespacedKey(plugin, "land_core_owner_name");
        this.dataPartyKey = new NamespacedKey(plugin, "land_core_party");
        this.dataLevelKey = new NamespacedKey(plugin, "land_core_level");
        this.dataFuelKey = new NamespacedKey(plugin, "land_core_fuel");
        this.dataWhitelistKey = new NamespacedKey(plugin, "land_core_whitelist");
        this.dataSettingsKey = new NamespacedKey(plugin, "land_core_settings");

        itemCombineModule.recipe("land_protection_core")
                .first(stack -> stack != null && stack.getType() == Material.LODESTONE && !isCoreItem(stack))
                .second(stack -> stack != null && stack.getType() == Material.DIAMOND)
                .groundRadius(0.5D)
                .airRadius(1.5D)
                .verticalRadius(2.0D)
                .allowAirCombine(true)
                .then(this::craftCore);

        for (ClaimRegion claim : store.loadAll().values()) {
            claims.put(claim.key(), claim);
        }
        rebuildWorldIndex();

        this.menu = new LandMenu(plugin, this);
        this.visualizer = new ClaimVisualizer(plugin, this);
        this.upkeepTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickUpkeep,
                UPKEEP_PERIOD_TICKS, UPKEEP_PERIOD_TICKS);
        this.raidTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickRaids,
                20L, 20L);
    }

    public boolean isFeatureEnabled() {
        return toggle.getGlobal(FEATURE_KEY);
    }

    public LandMenu getMenu() {
        return menu;
    }

    public ClaimVisualizer getVisualizer() {
        return visualizer;
    }

    public PartyModule getPartyModule() {
        return partyModule;
    }

    public org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule getRingModule() {
        return ringModule;
    }

    public void setRingModule(org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring.RingModule ringModule) {
        this.ringModule = ringModule;
    }

    public void shutdown() {
        upkeepTask.cancel();
        raidTask.cancel();
        for (RaidSession raid : new ArrayList<>(activeRaids.values())) {
            endRaid(raid, false);
        }
        visualizer.shutdown();
        saveAll();
    }

    // ================= クラフト =================

    private void craftCore(ItemCombineModule.CombineMatch match) {
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
        world.dropItemNaturally(center, createCoreItem(null));
        world.playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 0.8F, 1.4F);
        world.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, center, 12, 0.4, 0.4, 0.4);
    }

    // ================= 参照 API =================

    public ClaimRegion getClaimByKey(String key) {
        return claims.get(key);
    }

    public ClaimRegion getClaimAt(Block block) {
        return claims.get(ClaimRegion.toKey(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ()));
    }

    public List<ClaimRegion> getClaimsInWorld(String worldName) {
        List<ClaimRegion> list = worldIndex.get(worldName);
        return list == null ? List.of() : list;
    }

    public List<ClaimRegion> getAllClaims() {
        return new ArrayList<>(claims.values());
    }

    /** 指定座標を含む有効な保護領域を返す（なければ null）。 */
    public ClaimRegion getActiveClaimAt(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return null;
        }
        for (ClaimRegion claim : getClaimsInWorld(world.getName())) {
            if (claim.isActive() && claim.containsHorizontal(location)) {
                resolvePartyLazy(claim);
                return claim;
            }
        }
        return null;
    }

    /** パーティーが解散済みの場合、共有を個人所有へ戻す。 */
    private void resolvePartyLazy(ClaimRegion claim) {
        if (claim.getPartyId() != null && partyModule.getParty(claim.getPartyId()) == null) {
            claim.setPartyId(null);
            saveAll();
        }
    }

    /**
     * 制限を受けずに行動できるか
     * （オーナー・ホワイトリスト・共有パーティーのメンバー）。
     */
    public boolean canBypass(Player player, ClaimRegion claim) {
        UUID uuid = player.getUniqueId();
        if (uuid.equals(claim.getOwner())) {
            return true;
        }
        if (claim.getWhitelist().containsKey(uuid)) {
            return true;
        }
        resolvePartyLazy(claim);
        if (claim.getPartyId() != null) {
            Party party = partyModule.getParty(claim.getPartyId());
            if (party != null && party.isMember(uuid)) {
                return true;
            }
        }
        return isRaidAttacker(player, claim);
    }

    /**
     * コアの管理（メニュー・破壊・設定変更）ができるか
     * （オーナー、パーティー共有時はサブリーダー以上も可）。
     */
    public boolean canManage(Player player, ClaimRegion claim) {
        UUID uuid = player.getUniqueId();
        if (uuid.equals(claim.getOwner())) {
            return true;
        }
        resolvePartyLazy(claim);
        if (claim.getPartyId() != null) {
            Party party = partyModule.getParty(claim.getPartyId());
            if (party != null) {
                PartyRank rank = party.rankOf(uuid);
                return rank != null && rank.isAtLeast(PartyRank.CO_LEADER);
            }
        }
        return false;
    }

    /** %owner% プレースホルダーの解決（パーティー共有中はパーティー名）。 */
    public String resolveOwnerDisplay(ClaimRegion claim) {
        resolvePartyLazy(claim);
        if (claim.getPartyId() != null) {
            Party party = partyModule.getParty(claim.getPartyId());
            if (party != null) {
                return party.getColoredName();
            }
        }
        return claim.getOwnerName().isEmpty() ? "誰か" : claim.getOwnerName();
    }

    public void saveAll() {
        store.saveAll(claims.values());
    }

    private void rebuildWorldIndex() {
        worldIndex.clear();
        for (ClaimRegion claim : claims.values()) {
            worldIndex.computeIfAbsent(claim.getWorldName(), k -> new ArrayList<>()).add(claim);
        }
    }

    private void registerClaim(ClaimRegion claim) {
        claims.put(claim.key(), claim);
        rebuildWorldIndex();
        saveAll();
    }

    private void unregisterClaim(ClaimRegion claim) {
        claims.remove(claim.key());
        rebuildWorldIndex();
        saveAll();
    }

    // ================= 設置 / 破壊 =================

    @EventHandler(ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack hand = event.getItemInHand();
        Player player = event.getPlayer();

        if (isCoreItem(hand)) {
            handleCorePlace(event, hand, player);
            return;
        }

        // 通常ブロックの設置保護
        ClaimRegion claim = getActiveClaimAt(event.getBlockPlaced().getLocation());
        if (claim != null && claim.getSettings().isBlockPlace() && !canBypass(player, claim)) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "ブロックを設置できません");
        }
    }

    private void handleCorePlace(BlockPlaceEvent event, ItemStack hand, Player player) {
        if (!isFeatureEnabled()) {
            event.setCancelled(true);
            player.sendMessage("§c土地保護機能は現在無効です");
            return;
        }
        Block placed = event.getBlockPlaced();
        String worldName = placed.getWorld().getName();

        // アイテムに引き継がれたデータを読む（未使用のコアならデフォルト）
        ClaimRegion incoming = readClaimFromItem(hand, worldName, placed.getX(), placed.getY(), placed.getZ());

        // 個人/ギルドごとに保護コアは 1 つまで
        UUID placingOwner = incoming.getOwner() != null ? incoming.getOwner() : player.getUniqueId();
        ClaimRegion existingPersonal = findClaimByOwner(placingOwner);
        if (existingPersonal != null && !existingPersonal.key().equals(incoming.key())) {
            event.setCancelled(true);
            player.sendMessage("§c既に土地保護コアを設置済みです");
            player.sendMessage("§7現在既に x: " + existingPersonal.getX()
                    + " y: " + existingPersonal.getY()
                    + " z: " + existingPersonal.getZ() + " に設置済みです");
            return;
        }
        if (incoming.getPartyId() != null) {
            ClaimRegion existingParty = findClaimByParty(incoming.getPartyId());
            if (existingParty != null && !existingParty.key().equals(incoming.key())) {
                event.setCancelled(true);
                player.sendMessage("§c所属ギルドは既に別の土地保護コアを設置済みです");
                player.sendMessage("§7現在既に x: " + existingParty.getX()
                        + " y: " + existingParty.getY()
                        + " z: " + existingParty.getZ() + " に設置済みです");
                return;
            }
        }

        // 他人の保護エリアと重複する場所には設置できない
        for (ClaimRegion existing : getClaimsInWorld(worldName)) {
            if (!existing.intersects(worldName, placed.getX(), placed.getZ(), incoming.getRadius())) {
                continue;
            }
            if (!canManage(player, existing)) {
                event.setCancelled(true);
                player.sendMessage("§c他人の保護エリアと重なるため設置できません");
                player.sendMessage("§7(重複相手: " + resolveOwnerDisplay(existing) + " §7Lv." + existing.getLevel()
                        + " / 座標 " + existing.getX() + ", " + existing.getY() + ", " + existing.getZ() + ")");
                return;
            }
        }

        incoming.setLastUpkeepMillis(System.currentTimeMillis());
        registerClaim(incoming);

        if (incoming.getOwner() == null) {
            player.sendMessage("§6土地保護コアを設置しました");
            player.sendMessage("§eコアを右クリックしてオーナー登録を行ってください");
        } else {
            player.sendMessage("§6土地保護コアを再設置しました §7(Lv." + incoming.getLevel()
                    + " / オーナー: " + incoming.getOwnerName() + ")");
            if (incoming.getFuelUnits() <= 0) {
                player.sendMessage("§c燃料がありません。コアを開いて燃料を投入してください");
            }
        }
        placed.getWorld().playSound(placed.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.7F, 1.2F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 保護コア自体の破壊
        ClaimRegion coreClaim = getClaimAt(block);
        if (coreClaim != null && block.getType() == CORE_MATERIAL) {
            handleCoreBreak(event, coreClaim, player);
            return;
        }

        // 通常ブロックの破壊保護
        ClaimRegion claim = getActiveClaimAt(block.getLocation());
        if (claim != null && claim.getSettings().isBlockBreak() && !canBypass(player, claim)) {
            // 国レベル（Lv.10 超）のギルド領地は別ギルドリーダーによるレイドが可能
            if (tryStartRaid(player, claim)) {
                return;
            }
            event.setCancelled(true);
            sendDenyMessage(player, claim, "ブロックを破壊できません");
        }
    }

    private void handleCoreBreak(BlockBreakEvent event, ClaimRegion claim, Player player) {
        boolean manager = canManage(player, claim);
        RaidSession raid = activeRaids.get(claim.key());

        // レイド中に攻撃側がコアを破壊しようとしたら領地を乗っ取る
        if (raid != null && !raid.isEnded() && isRaidAttacker(player, claim)) {
            event.setCancelled(true);
            endRaid(raid, true);
            player.sendMessage("§c§l領地を乗っ取りました！");
            return;
        }

        // 有効な保護コアはオーナー(管理者)以外壊せない。
        // 燃料切れ・オーナー未登録のコアは誰でも壊せる（Rust の腐敗仕様に相当）。
        if (claim.isActive() && !manager) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "この保護コアは壊せません");
            return;
        }

        // 管理者がコアを回収・破壊した場合、進行中のレイドは防衛成功で終了する
        if (raid != null && !raid.isEnded()) {
            endRaid(raid, false);
        }

        unregisterClaim(claim);
        insideClaim.values().removeIf(key -> key.equals(claim.key()));
        if (player.getGameMode() != GameMode.CREATIVE || manager) {
            event.setDropItems(false);
            event.getBlock().getWorld().dropItemNaturally(
                    event.getBlock().getLocation().add(0.5D, 0.1D, 0.5D), createCoreItem(claim));
        }
        player.sendMessage(manager
                ? "§e土地保護コアを回収しました。データはアイテムに引き継がれています"
                : "§e燃料切れの土地保護コアを破壊しました");
    }

    // ================= コアの右クリック / 各種保護 =================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Player player = event.getPlayer();

        // 感圧板・トリップワイヤー（PHYSICAL）
        if (event.getAction() == Action.PHYSICAL) {
            handlePhysical(event, block, player);
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        // 保護コアの右クリック
        ClaimRegion coreClaim = getClaimAt(block);
        if (coreClaim != null && block.getType() == CORE_MATERIAL) {
            event.setCancelled(true);
            if (event.getHand() != EquipmentSlot.HAND) {
                return;
            }
            handleCoreInteract(player, coreClaim);
            return;
        }

        // 保護範囲内での操作制限
        ClaimRegion claim = getActiveClaimAt(block.getLocation());
        if (claim == null || canBypass(player, claim)) {
            return;
        }
        ClaimSettings settings = claim.getSettings();

        boolean isContainer = block.getState() instanceof InventoryHolder
                || block.getType() == Material.JUKEBOX
                || block.getType() == Material.LECTERN;
        if (isContainer && settings.isBlockContainers()) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "コンテナを開けません");
            return;
        }
        if (block.getBlockData() instanceof Openable && settings.isBlockDoors()) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "ドアを開けません");
            return;
        }
        if (block.getBlockData() instanceof Switch && settings.isBlockSwitches()) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "スイッチを操作できません");
        }
    }

    private void handlePhysical(PlayerInteractEvent event, Block block, Player player) {
        ClaimRegion claim = getActiveClaimAt(block.getLocation());
        if (claim == null || canBypass(player, claim)) {
            return;
        }
        String name = block.getType().name();
        if ((name.endsWith("_PRESSURE_PLATE") || block.getType() == Material.TRIPWIRE)
                && claim.getSettings().isBlockSwitches()) {
            event.setCancelled(true);
            return;
        }
        if (block.getType() == Material.FARMLAND && claim.getSettings().isBlockBreak()) {
            event.setCancelled(true);
        }
    }

    private void handleCoreInteract(Player player, ClaimRegion claim) {
        if (!isFeatureEnabled()) {
            player.sendMessage("§c土地保護機能は現在無効です");
            return;
        }
        // オーナー未登録なら開いた人をオーナーとして登録する
        if (claim.getOwner() == null) {
            claim.setOwner(player.getUniqueId());
            claim.setOwnerName(player.getName());
            claim.setLastUpkeepMillis(System.currentTimeMillis());
            saveAll();
            player.sendMessage("§a土地保護コアのオーナーとして登録されました！");
            player.sendMessage("§e燃料（原木・板材・木炭・石炭など）を投入すると保護が有効になります");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8F, 1.2F);
            menu.openMain(player, claim);
            return;
        }
        if (!canManage(player, claim)) {
            // ホワイトリストやメンバーであってもコアの操作はブロックする
            sendDenyMessage(player, claim, "このコアを操作する権限がありません");
            return;
        }
        menu.openMain(player, claim);
    }

    // ================= エンティティ関連の保護 =================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof ItemFrame) && !(event.getRightClicked() instanceof ArmorStand)) {
            return;
        }
        Player player = event.getPlayer();
        ClaimRegion claim = getActiveClaimAt(event.getRightClicked().getLocation());
        if (claim != null && claim.getSettings().isBlockContainers() && !canBypass(player, claim)) {
            event.setCancelled(true);
            sendDenyMessage(player, claim, "この装飾には触れません");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onArmorStandManipulate(PlayerArmorStandManipulateEvent event) {
        ClaimRegion claim = getActiveClaimAt(event.getRightClicked().getLocation());
        if (claim != null && claim.getSettings().isBlockContainers() && !canBypass(event.getPlayer(), claim)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer(), claim, "この装飾には触れません");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        // プレイヤー同士の攻撃（PVP / フレンドリーファイア）
        if (event.getEntity() instanceof Player victim) {
            Player attacker = resolvePlayer(event.getDamager());
            if (attacker != null) {
                // リング(闘技場)内では PVP 制限・フレンドリーファイアを適用しない
                // (Duel モード時の未開始チェックは RingModule 側で行う)
                if (ringModule != null && ringModule.allowsPvp(attacker, victim)) {
                    return;
                }
                // パーティー内の味方攻撃設定
                Party attackerParty = partyModule.getPartyOf(attacker.getUniqueId());
                if (attackerParty != null
                        && attackerParty.equals(partyModule.getPartyOf(victim.getUniqueId()))
                        && !attackerParty.isFriendlyFire()) {
                    event.setCancelled(true);
                    return;
                }
                // 領地内 PVP 設定（レイド中は無視）
                ClaimRegion claim = getActiveClaimAt(victim.getLocation());
                if (claim != null && !claim.getSettings().isPvpEnabled() && !isUnderRaid(claim)) {
                    event.setCancelled(true);
                    sendDenyMessage(attacker, claim, "この領地ではPVPは無効です");
                    return;
                }
            }
        }

        if (!(event.getEntity() instanceof ItemFrame) && !(event.getEntity() instanceof ArmorStand)
                && !(event.getEntity() instanceof Hanging)) {
            return;
        }
        Player attacker = resolvePlayer(event.getDamager());
        if (attacker == null) {
            return;
        }
        ClaimRegion claim = getActiveClaimAt(event.getEntity().getLocation());
        if (claim != null && claim.getSettings().isBlockBreak() && !canBypass(attacker, claim)) {
            event.setCancelled(true);
            sendDenyMessage(attacker, claim, "この装飾は壊せません");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Player remover = resolvePlayer(event.getRemover());
        if (remover == null) {
            return;
        }
        ClaimRegion claim = getActiveClaimAt(event.getEntity().getLocation());
        if (claim != null && claim.getSettings().isBlockBreak() && !canBypass(remover, claim)) {
            event.setCancelled(true);
            sendDenyMessage(remover, claim, "この装飾は壊せません");
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

    // ================= バケツ =================

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        ClaimRegion claim = getActiveClaimAt(event.getBlock().getLocation());
        if (claim != null && claim.getSettings().isBlockPlace() && !canBypass(event.getPlayer(), claim)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer(), claim, "液体を設置できません");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        ClaimRegion claim = getActiveClaimAt(event.getBlock().getLocation());
        if (claim != null && claim.getSettings().isBlockBreak() && !canBypass(event.getPlayer(), claim)) {
            event.setCancelled(true);
            sendDenyMessage(event.getPlayer(), claim, "液体を回収できません");
        }
    }

    // ================= 爆発 / ピストン =================

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        protectExplosion(event.blockList());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        protectExplosion(event.blockList());
    }

    private void protectExplosion(List<Block> blockList) {
        blockList.removeIf(block -> {
            // コアブロックは燃料切れでも爆破からは守る（データ喪失防止）
            if (block.getType() == CORE_MATERIAL && getClaimAt(block) != null) {
                return true;
            }
            return getActiveClaimAt(block.getLocation()) != null;
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (isPistonIntrusion(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (isPistonIntrusion(event.getBlock(), event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    /** 保護範囲外のピストンが保護範囲内のブロックを動かそうとしていないか。 */
    private boolean isPistonIntrusion(Block piston, List<Block> moved, org.bukkit.block.BlockFace direction) {
        ClaimRegion pistonClaim = getActiveClaimAt(piston.getLocation());
        for (Block block : moved) {
            ClaimRegion claim = getActiveClaimAt(block.getLocation());
            ClaimRegion destClaim = getActiveClaimAt(block.getRelative(direction).getLocation());
            ClaimRegion target = claim != null ? claim : destClaim;
            if (target != null && (pistonClaim == null || !pistonClaim.key().equals(target.key()))) {
                return true;
            }
        }
        return false;
    }

    // ================= 侵入通知 =================

    @EventHandler(ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ()
                && from.getWorld() == to.getWorld()) {
            return;
        }
        updateClaimPresence(event.getPlayer(), to);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (event.getTo() != null) {
            updateClaimPresence(event.getPlayer(), event.getTo());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        insideClaim.remove(event.getPlayer().getUniqueId());
        denyMessageAt.remove(event.getPlayer().getUniqueId());
        visualizer.disable(event.getPlayer().getUniqueId());
    }

    private void updateClaimPresence(Player player, Location to) {
        ClaimRegion claim = getActiveClaimAt(to);
        String newKey = claim == null ? null : claim.key();
        String oldKey = insideClaim.get(player.getUniqueId());
        if (newKey == null) {
            if (oldKey != null) {
                insideClaim.remove(player.getUniqueId());
            }
            return;
        }
        if (newKey.equals(oldKey)) {
            return;
        }
        insideClaim.put(player.getUniqueId(), newKey);
        if (!canManage(player, claim)) {
            notifyEnter(player, claim);
        }
    }

    private void notifyEnter(Player player, ClaimRegion claim) {
        ClaimSettings settings = claim.getSettings();
        String owner = resolveOwnerDisplay(claim);
        switch (settings.getNotifyMode()) {
            case TITLE -> player.showTitle(Title.title(
                    ComponentUtils.legacy(applyPlaceholders(settings.getTitleText(), owner)),
                    ComponentUtils.legacy(applyPlaceholders(settings.getSubtitleText(), owner)),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofMillis(2200), Duration.ofMillis(500))));
            case ACTIONBAR -> player.sendActionBar(
                    ComponentUtils.legacy(applyPlaceholders(settings.getActionbarText(), owner)));
            case NONE -> {
            }
        }
    }

    private String applyPlaceholders(String text, String owner) {
        return text == null ? "" : text.replace(ClaimSettings.PLACEHOLDER_OWNER, owner);
    }

    private void sendDenyMessage(Player player, ClaimRegion claim, String action) {
        long now = System.currentTimeMillis();
        Long last = denyMessageAt.get(player.getUniqueId());
        if (last != null && now - last < DENY_MESSAGE_COOLDOWN_MILLIS) {
            return;
        }
        denyMessageAt.put(player.getUniqueId(), now);
        player.sendActionBar(ComponentUtils.legacy(
                "§c" + resolveOwnerDisplay(claim) + "§cの保護エリアです: " + action));
    }

    // ================= 燃料メニューの残留アイテム返却 =================

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        if (!(event.getInventory().getHolder() instanceof ChestUI ui)) {
            return;
        }
        if (!LandMenu.FUEL_MENU_TYPE.equals(ui.getType())) {
            return;
        }
        for (int slot : LandMenu.FUEL_SLOTS) {
            ItemStack stack = event.getInventory().getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            event.getInventory().setItem(slot, null);
            Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
            for (ItemStack rest : overflow.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), rest);
            }
        }
    }

    // ================= 燃料 / レベルアップ =================

    /** 燃料メニューの中身を燃料へ変換する。戻り値は加算されたユニット。 */
    public double depositFuel(Player player, ClaimRegion claim, org.bukkit.inventory.Inventory inventory) {
        double added = 0;
        for (int slot : LandMenu.FUEL_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            double value = FuelValues.valueOf(stack);
            if (value <= 0) {
                // 燃料でないアイテムは返す
                inventory.setItem(slot, null);
                Map<Integer, ItemStack> overflow = player.getInventory().addItem(stack);
                for (ItemStack rest : overflow.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), rest);
                }
                continue;
            }
            inventory.setItem(slot, null);
            added += value;
        }
        if (added > 0) {
            boolean wasActive = claim.isActive();
            claim.addFuelUnits(added);
            if (!wasActive && claim.isActive()) {
                claim.setLastUpkeepMillis(System.currentTimeMillis());
            }
            saveAll();
            player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.6F, 1.2F);
        }
        return added;
    }

    /** レベルアップを試みる。失敗時はエラーメッセージ、成功時は null。 */
    public String tryUpgrade(Player player, ClaimRegion claim) {
        int level = claim.getLevel();
        if (!ClaimLevel.canUpgrade(level)) {
            return "既に最大レベルです";
        }
        int nextLevel = level + 1;

        // 個人所有は Lv.10 まで
        if (claim.getPartyId() == null && nextLevel > ClaimLevel.PERSONAL_MAX_LEVEL) {
            return "個人の保護コアは Lv." + ClaimLevel.PERSONAL_MAX_LEVEL + " までです。ギルド共有にしてからレベルアップしてください";
        }

        // Lv.10 以降はギルドのオーナー・副リーダーのみ
        if (nextLevel > ClaimLevel.PERSONAL_MAX_LEVEL) {
            Party party = partyModule.getParty(claim.getPartyId());
            PartyRank rank = party == null ? null : party.rankOf(player.getUniqueId());
            if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) {
                return "Lv." + ClaimLevel.PERSONAL_MAX_LEVEL + "以降のレベルアップはギルドのオーナー又は副リーダーのみ実行できます";
            }
        } else if (!canManage(player, claim)) {
            return "このコアを管理できる権限がありません";
        }

        List<ClaimLevel.Requirement> requirements = ClaimLevel.upgradeRequirements(level);
        for (ClaimLevel.Requirement req : requirements) {
            if (!player.getInventory().containsAtLeast(new ItemStack(req.material()), req.amount())) {
                return "素材が足りません: " + materialDisplayName(req.material(), player) + " ×" + req.amount();
            }
        }

        int newRadius = ClaimLevel.radius(nextLevel);
        // 拡大後の範囲が他人の保護エリアと重ならないか確認
        for (ClaimRegion existing : getClaimsInWorld(claim.getWorldName())) {
            if (existing.key().equals(claim.key())) {
                continue;
            }
            if (existing.intersects(claim.getWorldName(), claim.getX(), claim.getZ(), newRadius)
                    && !canManage(player, existing)) {
                return "拡大後の範囲が他人の保護エリアと重なるためレベルアップできません";
            }
        }

        for (ClaimLevel.Requirement req : requirements) {
            player.getInventory().removeItem(new ItemStack(req.material(), req.amount()));
        }
        claim.setLevel(nextLevel);
        saveAll();
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0F, 1.0F);
        return null;
    }

    public static String materialDisplayName(Material material) {
        return ItemNameUtil.serverReadableName(material);
    }

    public static String materialDisplayName(Material material, Player player) {
        return ItemNameUtil.localizedPlainText(material, player == null ? null : player.locale());
    }

    // ================= ホワイトリスト =================

    public void addWhitelist(ClaimRegion claim, UUID uuid, String name) {
        claim.getWhitelist().put(uuid, name);
        saveAll();
    }

    public void removeWhitelist(ClaimRegion claim, UUID uuid) {
        claim.getWhitelist().remove(uuid);
        saveAll();
    }

    // ================= パーティー共有 =================

    /** パーティー共有の切替。失敗時はエラーメッセージ、成功時は null。 */
    public String togglePartyShare(Player player, ClaimRegion claim) {
        if (!player.getUniqueId().equals(claim.getOwner())) {
            return "パーティー共有の切替はオーナーのみ実行できます";
        }
        if (claim.getPartyId() != null) {
            claim.setPartyId(null);
            saveAll();
            return null;
        }
        Party party = partyModule.getPartyOf(player.getUniqueId());
        if (party == null) {
            return "パーティーに所属していません";
        }
        ClaimRegion existingParty = findClaimByParty(party.getId());
        if (existingParty != null && !existingParty.key().equals(claim.key())) {
            return "所属ギルドは既に別の土地保護コアを設置済みです (x: " + existingParty.getX()
                    + " y: " + existingParty.getY() + " z: " + existingParty.getZ() + ")";
        }
        claim.setPartyId(party.getId());
        saveAll();
        return null;
    }

    // ================= 所有制限 =================

    public ClaimRegion findClaimByOwner(UUID owner) {
        if (owner == null) {
            return null;
        }
        for (ClaimRegion claim : claims.values()) {
            if (owner.equals(claim.getOwner())) {
                return claim;
            }
        }
        return null;
    }

    public ClaimRegion findClaimByParty(UUID partyId) {
        if (partyId == null) {
            return null;
        }
        for (ClaimRegion claim : claims.values()) {
            if (partyId.equals(claim.getPartyId())) {
                return claim;
            }
        }
        return null;
    }

    // ================= レイド =================

    public boolean isUnderRaid(ClaimRegion claim) {
        return claim != null && activeRaids.containsKey(claim.key());
    }

    private boolean isRaidAttacker(Player player, ClaimRegion claim) {
        RaidSession raid = activeRaids.get(claim.key());
        if (raid == null || raid.isEnded()) {
            return false;
        }
        Party attacker = partyModule.getParty(raid.getAttackerPartyId());
        return attacker != null && attacker.isMember(player.getUniqueId());
    }

    private boolean tryStartRaid(Player player, ClaimRegion claim) {
        Party attackerParty = partyModule.getPartyOf(player.getUniqueId());
        if (attackerParty == null) {
            return false;
        }
        if (attackerParty.getId().equals(claim.getPartyId())) {
            return false;
        }
        if (activeRaids.containsKey(claim.key())) {
            return false;
        }

        // 国レベル（Lv.10 超）のギルド領地：別ギルドのリーダーがレイド可能
        if (claim.getLevel() > ClaimLevel.PERSONAL_MAX_LEVEL && claim.getPartyId() != null) {
            if (attackerParty.rankOf(player.getUniqueId()) != PartyRank.LEADER) {
                return false;
            }
            startRaid(claim, attackerParty, player.getUniqueId());
            return true;
        }

        // Lv.10 以下の個人領地は原則レイド不可だが、
        // ギルドに所属するオーナー・副リーダーはその制限を受けない
        if (claim.getLevel() <= ClaimLevel.PERSONAL_MAX_LEVEL && claim.getPartyId() == null) {
            PartyRank rank = attackerParty.rankOf(player.getUniqueId());
            if (rank == null || !rank.isAtLeast(PartyRank.CO_LEADER)) {
                return false;
            }
            startRaid(claim, attackerParty, player.getUniqueId());
            return true;
        }

        return false;
    }

    private void startRaid(ClaimRegion claim, Party attackerParty, UUID attackerLeaderId) {
        Party defenderParty = partyModule.getParty(claim.getPartyId());
        String defenderDisplay = defenderParty == null ? resolveOwnerDisplay(claim) : defenderParty.getColoredName();
        String attackerDisplay = attackerParty.getColoredName();
        RaidSession raid = new RaidSession(claim.key(), attackerParty.getId(), attackerLeaderId,
                defenderDisplay, attackerDisplay);
        activeRaids.put(claim.key(), raid);
        raid.syncPlayers(defenderParty, attackerParty);

        broadcastRaidMessage("§c§l[レイド] " + attackerDisplay + " §c§lが " + defenderDisplay
                + " §c§lの領地にレイドを開始しました！");
        broadcastRaidMessage("§7制限時間 30 分。コアを破壊すれば領地を乗っ取れます");
    }

    private void tickRaids() {
        if (activeRaids.isEmpty()) {
            return;
        }
        for (RaidSession raid : new ArrayList<>(activeRaids.values())) {
            if (raid.isEnded()) {
                activeRaids.remove(raid.getClaimKey());
                continue;
            }
            raid.updateProgress();
            ClaimRegion claim = getClaimByKey(raid.getClaimKey());
            Party defender = claim == null ? null : partyModule.getParty(claim.getPartyId());
            Party attacker = partyModule.getParty(raid.getAttackerPartyId());
            raid.syncPlayers(defender, attacker);
            if (raid.getRemainingMillis() <= 0) {
                endRaid(raid, false);
            }
        }
    }

    private void endRaid(RaidSession raid, boolean attackerWon) {
        if (raid.isEnded()) {
            return;
        }
        raid.setEnded(true);
        raid.removeAllPlayers();
        activeRaids.remove(raid.getClaimKey());

        ClaimRegion claim = getClaimByKey(raid.getClaimKey());
        Party attacker = partyModule.getParty(raid.getAttackerPartyId());
        String attackerDisplay = attacker == null ? "?" : attacker.getColoredName();
        String defenderDisplay = claim == null ? "?" : resolveOwnerDisplay(claim);

        if (attackerWon && claim != null && attacker != null) {
            Player leader = Bukkit.getPlayer(raid.getAttackerLeaderId());
            claim.setOwner(raid.getAttackerLeaderId());
            claim.setOwnerName(leader != null ? leader.getName() : attacker.nameOf(raid.getAttackerLeaderId()));
            claim.setPartyId(attacker.getId());
            saveAll();
            broadcastRaidMessage("§c§l[レイド] " + attackerDisplay + " §c§lが " + defenderDisplay
                    + " §c§lの領地を乗っ取りました！");
        } else {
            broadcastRaidMessage("§c§l[レイド] " + defenderDisplay + " §c§lが " + attackerDisplay
                    + " §c§lのレイドを防衛しました");
        }
    }

    private void broadcastRaidMessage(String message) {
        for (Player online : Bukkit.getOnlinePlayers()) {
            online.sendMessage(message);
        }
    }

    // ================= 維持コスト =================

    private void tickUpkeep() {
        if (claims.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (ClaimRegion claim : new ArrayList<>(claims.values())) {
            // コアブロックが何らかの理由で消えていたら領域を削除しアイテム化する
            World world = claim.getWorld();
            if (world != null) {
                int chunkX = claim.getX() >> 4;
                int chunkZ = claim.getZ() >> 4;
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    Block block = world.getBlockAt(claim.getX(), claim.getY(), claim.getZ());
                    if (block.getType() != CORE_MATERIAL) {
                        unregisterClaim(claim);
                        world.dropItemNaturally(block.getLocation().add(0.5D, 0.1D, 0.5D), createCoreItem(claim));
                        changed = true;
                        continue;
                    }
                }
            }
            if (claim.getOwner() == null || claim.getFuelUnits() <= 0) {
                claim.setLastUpkeepMillis(now);
                continue;
            }
            double elapsedHours = (now - claim.getLastUpkeepMillis()) / 3600000.0;
            if (elapsedHours <= 0) {
                continue;
            }
            double cost = ClaimLevel.upkeepPerHour(claim.getLevel()) * elapsedHours;
            claim.setLastUpkeepMillis(now);
            double before = claim.getFuelUnits();
            claim.setFuelUnits(before - cost);
            changed = true;
            if (before > 0 && claim.getFuelUnits() <= 0) {
                notifyFuelDepleted(claim);
            }
        }
        if (changed) {
            saveAll();
        }
    }

    private void notifyFuelDepleted(ClaimRegion claim) {
        String message = "§c[土地保護] §f座標 " + claim.getX() + ", " + claim.getY() + ", " + claim.getZ()
                + " §fの保護コアの燃料が切れました！保護が無効になっています";
        Player owner = claim.getOwner() == null ? null : Bukkit.getPlayer(claim.getOwner());
        if (owner != null) {
            owner.sendMessage(message);
        }
        if (claim.getPartyId() != null) {
            Party party = partyModule.getParty(claim.getPartyId());
            if (party != null) {
                partyModule.broadcast(party, message);
            }
        }
    }

    // ================= アイテム変換 =================

    /** 保護コアのアイテムを生成する。claim が null の場合は新品。 */
    public ItemStack createCoreItem(ClaimRegion claim) {
        ItemStack stack = new ItemStack(CORE_MATERIAL);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, ITEM_NAME);
        List<String> lore = new ArrayList<>();
        lore.add("§7設置して右クリックするとオーナー登録できます");
        lore.add("§7燃料(原木/板材/木炭/石炭)を投入している間、");
        lore.add("§7周囲の土地を保護します");
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(coreKey, PersistentDataType.STRING, "true");
        if (claim != null) {
            lore.add("");
            lore.add("§6--- 引き継ぎデータ ---");
            lore.add("§7レベル: §eLv." + claim.getLevel() + " §7(半径" + claim.getRadius() + ")");
            if (claim.getOwner() != null) {
                lore.add("§7オーナー: §e" + claim.getOwnerName());
                pdc.set(dataOwnerKey, PersistentDataType.STRING, claim.getOwner().toString());
                pdc.set(dataOwnerNameKey, PersistentDataType.STRING, claim.getOwnerName());
            }
            if (claim.getPartyId() != null) {
                pdc.set(dataPartyKey, PersistentDataType.STRING, claim.getPartyId().toString());
            }
            lore.add("§7燃料: §e" + String.format("%.1f", claim.getFuelUnits()) + " ユニット");
            pdc.set(dataLevelKey, PersistentDataType.INTEGER, claim.getLevel());
            pdc.set(dataFuelKey, PersistentDataType.DOUBLE, claim.getFuelUnits());
            if (!claim.getWhitelist().isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (Map.Entry<UUID, String> entry : claim.getWhitelist().entrySet()) {
                    if (sb.length() > 0) {
                        sb.append(';');
                    }
                    sb.append(entry.getKey()).append('|').append(entry.getValue());
                }
                pdc.set(dataWhitelistKey, PersistentDataType.STRING, sb.toString());
            }
            pdc.set(dataSettingsKey, PersistentDataType.STRING, claim.getSettings().toCompact());
        }
        ComponentUtils.setLore(meta, lore);
        stack.setItemMeta(meta);
        return stack;
    }

    /** アイテムから保護領域データを復元する。 */
    private ClaimRegion readClaimFromItem(ItemStack stack, String worldName, int x, int y, int z) {
        ClaimRegion claim = new ClaimRegion(worldName, x, y, z);
        if (stack == null || !stack.hasItemMeta()) {
            return claim;
        }
        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        String ownerRaw = pdc.get(dataOwnerKey, PersistentDataType.STRING);
        if (ownerRaw != null) {
            try {
                claim.setOwner(UUID.fromString(ownerRaw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        String ownerName = pdc.get(dataOwnerNameKey, PersistentDataType.STRING);
        if (ownerName != null) {
            claim.setOwnerName(ownerName);
        }
        String partyRaw = pdc.get(dataPartyKey, PersistentDataType.STRING);
        if (partyRaw != null) {
            try {
                claim.setPartyId(UUID.fromString(partyRaw));
            } catch (IllegalArgumentException ignored) {
            }
        }
        Integer level = pdc.get(dataLevelKey, PersistentDataType.INTEGER);
        if (level != null) {
            claim.setLevel(level);
        }
        Double fuel = pdc.get(dataFuelKey, PersistentDataType.DOUBLE);
        if (fuel != null) {
            claim.setFuelUnits(fuel);
        }
        String whitelistRaw = pdc.get(dataWhitelistKey, PersistentDataType.STRING);
        if (whitelistRaw != null && !whitelistRaw.isEmpty()) {
            for (String entry : whitelistRaw.split(";")) {
                int idx = entry.indexOf('|');
                if (idx <= 0) {
                    continue;
                }
                try {
                    claim.getWhitelist().put(UUID.fromString(entry.substring(0, idx)), entry.substring(idx + 1));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        String settingsRaw = pdc.get(dataSettingsKey, PersistentDataType.STRING);
        if (settingsRaw != null) {
            claim.setSettings(ClaimSettings.fromCompact(settingsRaw));
        }
        return claim;
    }

    public boolean isCoreItem(ItemStack stack) {
        if (stack == null || stack.getType() != CORE_MATERIAL || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(coreKey, PersistentDataType.STRING);
    }
}
