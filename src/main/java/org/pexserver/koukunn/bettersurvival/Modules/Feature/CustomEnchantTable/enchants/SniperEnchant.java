package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Event;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import io.papermc.paper.event.player.PlayerStopUsingItemEvent;
import org.pexserver.koukunn.bettersurvival.Core.NMS.NMSApi;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * スナイパー (sniper)。
 *
 * クロスボウの挙動を FPS のスナイパーライフル風に完全カスタマイズする。
 *
 * - 右クリック長押し: ADS (本物の望遠鏡スコープUI。離すと解除)
 * - 左クリック: 腰だめ射撃 / ADS 中はスニークまたは左クリックで精密射撃
 * - ズーム中は精密射撃 + ダメージボーナス、腰だめ撃ちは弾がブレる
 * - 射撃ごとにボルトアクション風クールダウン
 * - 矢はインベントリから Vanilla 同様に消費。耐久消費あり (耐久力対応)
 *
 * <h3>ADS の仕組み (武器スワップ方式)</h3>
 * スコープUIはクライアント自身がアイテム使用を開始した時にしか描画されない
 * (自プレイヤーの使用状態はローカル管理で、サーバーからのパケットでは
 * スコープ描画を開始できない)。そのため右クリックを受けたら
 * メインハンドのクロスボウを照準用の本物の望遠鏡 (PDCタグ付き) に差し替える。
 * 右クリックを押し続けているクライアントは、次の使用試行で手元の望遠鏡を
 * 自分から使用開始し、完全に正規のフローでスコープUIが表示される。
 * 離すと {@link PlayerStopUsingItemEvent} が発火し、クロスボウを復元する。
 */
public class SniperEnchant extends CustomEnchant {

    private static final boolean DEBUG_SCOPE = Boolean.getBoolean("bettersurvival.debugSpyglass");

    /** レベル別射撃クールダウン (tick) */
    private static final int[] COOLDOWN_TICKS = {50, 40, 30};
    /** 矢の初速 (通常クロスボウは約3.15) */
    private static final double ARROW_SPEED = 6.5D;
    /** 腰だめ撃ち(非ズーム)の拡散量 */
    private static final double HIPFIRE_SPREAD = 0.08D;
    /** ズーム射撃のレベルあたりダメージボーナス */
    private static final double ZOOM_DAMAGE_BONUS_PER_LEVEL = 0.3D;
    /** ADS 状態の監視間隔 (tick) */
    private static final long SCOPE_WATCH_TICKS = 10L;
    /**
     * ADS 開始からクライアントが望遠鏡の使用を始めるまでの猶予 (ms)。
     * これを過ぎても使用が始まらない場合 (右クリックを一瞬で離した等) は
     * クロスボウへ戻す。
     */
    private static final long SCOPE_START_GRACE_MS = 600L;

    /** ADS 中プレイヤー */
    private final Set<UUID> zoomed = new HashSet<>();
    /** ADS 中プレイヤー → 差し替え前のクロスボウ */
    private final Map<UUID, ItemStack> savedWeapon = new HashMap<>();
    /** ADS 中プレイヤー → クロスボウがあったホットバースロット */
    private final Map<UUID, Integer> savedSlot = new HashMap<>();
    /** ADS 開始時刻 (ms) */
    private final Map<UUID, Long> zoomStartMs = new HashMap<>();
    /** 射撃クールダウン。CROSSBOW の Vanilla cooldown は右クリックADSも止めるため使わない */
    private final Map<UUID, Long> shotCooldownUntilMs = new HashMap<>();
    /** 照準用望遠鏡の識別タグ */
    private final NamespacedKey scopeItemKey;
    private final BukkitTask scopeWatchTask;

    public SniperEnchant(Loader plugin) {
        super(plugin);
        this.scopeItemKey = new NamespacedKey(plugin, "sniper_scope_spyglass");
        scopeWatchTask = plugin.getServer().getScheduler().runTaskTimer(
                plugin, this::watchScopes, SCOPE_WATCH_TICKS, SCOPE_WATCH_TICKS);
    }

    @Override
    public String id() {
        return "sniper";
    }

    @Override
    public String displayName() {
        return "スナイパー";
    }

    @Override
    public String description() {
        return "§7クロスボウをFPSのスナイパーライフル化する"
                + "\n§7右クリック長押し: ADS(望遠鏡スコープ) / 離して解除"
                + "\n§7左クリック: 腰だめ射撃 / ADS中はスニークで精密射撃"
                + "\n§7矢はインベントリから消費。ズーム中は精密+高威力、"
                + "\n§7腰だめ撃ちは弾がブレる"
                + "\n§7連射: Lv1 2.5秒 / Lv2 2秒 / Lv3 1.5秒";
    }

    @Override
    public Material icon() {
        return Material.SPYGLASS;
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.CROSSBOW;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.SPYGLASS, 1));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.AMETHYST_BLOCK, 4));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 64), new ItemStack(Material.ENDER_PEARL, 8));
        };
    }

    @Override
    public void shutdown() {
        scopeWatchTask.cancel();
        for (UUID uuid : Set.copyOf(zoomed)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                unzoom(player, false);
            }
        }
        zoomed.clear();
        savedWeapon.clear();
        savedSlot.clear();
        zoomStartMs.clear();
        shotCooldownUntilMs.clear();
    }

    // ================= 入力 =================

    @SuppressWarnings("deprecation")
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Action action = event.getAction();

        // ADS 中 (メインハンドは照準用望遠鏡): 右クリックは Vanilla の望遠鏡使用に任せる
        if (isScopeSpyglass(mainHand)) {
            if (zoomed.contains(player.getUniqueId())
                    && (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK)) {
                event.setCancelled(true);
                fireSavedWeapon(player);
            }
            return;
        }

        int level = mainHand.getType() == Material.CROSSBOW ? levelOf(mainHand) : 0;
        if (level <= 0) {
            return;
        }
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK) {
            // チェスト等のブロック操作はスニークしていなければ優先する
            if (action == Action.RIGHT_CLICK_BLOCK && !player.isSneaking()
                    && event.getClickedBlock() != null && event.getClickedBlock().getType().isInteractable()) {
                event.setUseItemInHand(Event.Result.DENY); // 装填だけは常に禁止
                return;
            }
            event.setCancelled(true);
            zoom(player, mainHand);
        } else if (action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK) {
            event.setCancelled(true);
            fire(player, mainHand, level);
        }
    }

    /** 至近距離の敵を直接左クリックした場合も殴らず射撃する */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isScopeSpyglass(mainHand) && zoomed.contains(player.getUniqueId())) {
            event.setCancelled(true);
            fireSavedWeapon(player);
            return;
        }
        if (mainHand.getType() != Material.CROSSBOW) {
            return;
        }
        int level = levelOf(mainHand);
        if (level <= 0) {
            return;
        }
        event.setCancelled(true);
        fire(player, mainHand, level);
    }

    /** ADS 中に額縁等へ照準用望遠鏡を入れられないようにする */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (isScopeSpyglass(event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
        }
    }

    // ================= ADS (武器スワップ + 本物の望遠鏡使用) =================

    /**
     * ADS を開始する。メインハンドのクロスボウを照準用望遠鏡へ差し替える。
     * 右クリックを押し続けているクライアントは次の使用試行で望遠鏡を
     * 自分から使用開始し、本物のスコープUIが表示される。
     */
    private void zoom(Player player, ItemStack crossbow) {
        UUID uuid = player.getUniqueId();
        if (zoomed.contains(uuid)) {
            debug(player, "zoom ignored already zoomed");
            return;
        }
        PlayerInventory inventory = player.getInventory();
        int slot = inventory.getHeldItemSlot();
        debug(player, "zoom start slot=" + slot + " weapon=" + describe(crossbow));
        savedWeapon.put(uuid, crossbow.clone());
        savedSlot.put(uuid, slot);
        zoomStartMs.put(uuid, System.currentTimeMillis());
        inventory.setItemInMainHand(createScopeSpyglass());
        zoomed.add(uuid);
        player.sendActionBar(ComponentUtils.legacy(
                "§b⌖ ADS中 §7| §fスニーク/左クリック: 射撃 §7/ §f右クリックを離して解除"));
    }

    /** ADS を解除し、クロスボウを元のスロットへ復元する */
    private void unzoom(Player player, boolean sound) {
        UUID uuid = player.getUniqueId();
        if (!zoomed.remove(uuid)) {
            return;
        }
        debug(player, "unzoom sound=" + sound);
        zoomStartMs.remove(uuid);
        stopActiveScopeUse(player);
        restoreWeapon(player);
        if (sound) {
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_STOP_USING, 0.8F, 1.0F);
            player.sendActionBar(ComponentUtils.legacy("§7スコープ解除"));
        }
    }

    /** サーバー側の使用状態が照準用望遠鏡なら止める */
    private void stopActiveScopeUse(Player player) {
        try {
            if (isScopeSpyglass(player.getActiveItem())) {
                player.clearActiveItem();
            }
        } catch (Throwable ignored) {
        }
    }

    /** 保存しておいたクロスボウを元のスロットへ戻す。想定外の状態でも武器を失わせない */
    private void restoreWeapon(Player player) {
        UUID uuid = player.getUniqueId();
        ItemStack weapon = savedWeapon.remove(uuid);
        Integer slot = savedSlot.remove(uuid);
        PlayerInventory inventory = player.getInventory();
        if (slot != null && isScopeSpyglass(inventory.getItem(slot))) {
            inventory.setItem(slot, weapon);
            return;
        }
        // 元スロットに照準用望遠鏡が無い場合 (想定外): 残骸を掃除しつつ武器は返す
        removeScopeItems(inventory);
        if (weapon != null && !weapon.getType().isAir()) {
            for (ItemStack leftover : inventory.addItem(weapon).values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), leftover);
            }
        }
    }

    /** インベントリ中の照準用望遠鏡をすべて除去する */
    private void removeScopeItems(PlayerInventory inventory) {
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isScopeSpyglass(contents[i])) {
                inventory.setItem(i, null);
            }
        }
        if (isScopeSpyglass(inventory.getItemInOffHand())) {
            inventory.setItemInOffHand(null);
        }
    }

    /** 照準用の望遠鏡アイテム (PDCタグで識別し、ADS解除時に自動でクロスボウへ戻る) */
    private ItemStack createScopeSpyglass() {
        ItemStack stack = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§bスナイパースコープ");
        ComponentUtils.setLore(meta,
                "§7スナイパーエンチャントの照準用アイテム",
                "§7ADS解除で自動的にクロスボウへ戻ります");
        meta.getPersistentDataContainer().set(scopeItemKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    private boolean isScopeSpyglass(ItemStack stack) {
        if (stack == null || stack.getType() != Material.SPYGLASS || !stack.hasItemMeta()) {
            return false;
        }
        return stack.getItemMeta().getPersistentDataContainer().has(scopeItemKey, PersistentDataType.BYTE);
    }

    private boolean isZoomed(Player player) {
        return zoomed.contains(player.getUniqueId());
    }

    /**
     * 右クリックを離した瞬間の解除。クライアント自身が望遠鏡を使用しているため、
     * このイベントが Vanilla の正規フローで確実に発火する。
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onStopUsingItem(PlayerStopUsingItemEvent event) {
        Player player = event.getPlayer();
        if (!zoomed.contains(player.getUniqueId())) {
            return;
        }
        if (!isScopeSpyglass(event.getItem())) {
            return;
        }
        debug(player, "onStopUsingItem -> unzoom");
        // 停止音は Vanilla (SpyglassItem#releaseUsing) が鳴らすため無音で解除
        unzoom(player, false);
    }

    /**
     * ADS 状態の監視。右クリックを一瞬で離した場合 (使用が始まらない) の
     * 巻き戻しと、持ち替え等の取りこぼしの後始末を行う。
     */
    private void watchScopes() {
        long now = System.currentTimeMillis();
        for (UUID uuid : Set.copyOf(zoomed)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline()) {
                zoomed.remove(uuid);
                savedWeapon.remove(uuid);
                savedSlot.remove(uuid);
                zoomStartMs.remove(uuid);
                continue;
            }
            Integer slot = savedSlot.get(uuid);
            if (slot == null || player.getInventory().getHeldItemSlot() != slot
                    || !isScopeSpyglass(player.getInventory().getItemInMainHand())) {
                debug(player, "watchScopes cleanup slotChangedOrItemGone");
                unzoom(player, false);
                continue;
            }
            boolean scoping = false;
            try {
                scoping = isScopeSpyglass(player.getActiveItem());
            } catch (Throwable ignored) {
            }
            if (scoping) {
                // サーバー側の使用時間 (60秒) 切れを防ぐ。クライアント側は期限が来ても
                // 右クリックが押されている限り自分から使用を再開する
                NMSApi.startUsingItem(player, false, true);
                continue;
            }
            // まだ使用が始まっていない: 開始猶予を過ぎていたら右クリックは離されたと判断
            long started = zoomStartMs.getOrDefault(uuid, 0L);
            if (now - started > SCOPE_START_GRACE_MS) {
                debug(player, "watchScopes cleanup useNeverStarted");
                unzoom(player, false);
            }
        }
    }

    private static void debug(Player player, String message) {
        if (!DEBUG_SCOPE) return;
        player.getServer().getLogger().info("[Bettersurvival/SniperEnchant] "
                + player.getName() + " " + message);
    }

    private static String describe(ItemStack item) {
        if (item == null) return "null";
        return item.getType() + "x" + item.getAmount();
    }

    // ================= 射撃 =================

    /** ADS 中の射撃: 保存中のクロスボウを武器として使う */
    private void fireSavedWeapon(Player player) {
        ItemStack weapon = savedWeapon.get(player.getUniqueId());
        if (weapon == null) {
            return;
        }
        int level = levelOf(weapon);
        if (level <= 0) {
            return;
        }
        fire(player, weapon, level);
    }

    private void fire(Player player, ItemStack crossbow, int level) {
        long now = System.currentTimeMillis();
        long cooldownUntil = shotCooldownUntilMs.getOrDefault(player.getUniqueId(), 0L);
        if (now < cooldownUntil) {
            debug(player, "fire ignored cooldownLeftMs=" + (cooldownUntil - now));
            return; // ボルトアクション中 (連打対策も兼ねる)
        }
        // 弾の確保 (Vanilla 同様: クリエイティブは消費なし)
        ItemStack ammo = null;
        if (player.getGameMode() != GameMode.CREATIVE) {
            ammo = findAndConsumeArrow(player.getInventory());
            if (ammo == null) {
                player.playSound(player.getLocation(), Sound.BLOCK_DISPENSER_FAIL, 0.8F, 1.2F);
                player.sendActionBar(ComponentUtils.legacy("§c矢がありません"));
                return;
            }
        }

        boolean zoomedShot = isZoomed(player);
        Vector direction = player.getEyeLocation().getDirection().normalize();
        if (!zoomedShot) {
            direction = addSpread(direction);
        }
        Vector velocity = direction.multiply(ARROW_SPEED);

        AbstractArrow arrow;
        if (ammo != null && ammo.getType() == Material.SPECTRAL_ARROW) {
            arrow = player.launchProjectile(SpectralArrow.class, velocity);
        } else {
            Arrow launched = player.launchProjectile(Arrow.class, velocity);
            if (ammo != null && ammo.getType() == Material.TIPPED_ARROW
                    && ammo.getItemMeta() instanceof PotionMeta potionMeta) {
                if (potionMeta.getBasePotionType() != null) {
                    launched.setBasePotionType(potionMeta.getBasePotionType());
                }
                for (PotionEffect effect : potionMeta.getCustomEffects()) {
                    launched.addCustomEffect(effect, true);
                }
            }
            arrow = launched;
        }
        arrow.setCritical(true);
        arrow.setPickupStatus(player.getGameMode() == GameMode.CREATIVE
                ? AbstractArrow.PickupStatus.CREATIVE_ONLY
                : AbstractArrow.PickupStatus.ALLOWED);
        if (zoomedShot) {
            double multiplier = 1.0D + ZOOM_DAMAGE_BONUS_PER_LEVEL * level;
            arrow.getPersistentDataContainer().set(key(), PersistentDataType.DOUBLE, multiplier);
        }

        int index = Math.max(0, Math.min(COOLDOWN_TICKS.length - 1, level - 1));
        shotCooldownUntilMs.put(player.getUniqueId(), now + COOLDOWN_TICKS[index] * 50L);
        damageCrossbow(player, crossbow);

        Location muzzle = player.getEyeLocation().add(direction.clone().multiply(0.8D));
        player.getWorld().playSound(muzzle, Sound.ITEM_CROSSBOW_SHOOT, 1.0F, 0.7F);
        player.getWorld().playSound(muzzle, Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.5F, 1.6F);
        player.getWorld().spawnParticle(Particle.CRIT, muzzle, 8, 0.05D, 0.05D, 0.05D, 0.1D);
    }

    /** ズーム射撃のダメージボーナスを適用する */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArrowDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Projectile projectile)) {
            return;
        }
        Double multiplier = projectile.getPersistentDataContainer().get(key(), PersistentDataType.DOUBLE);
        if (multiplier == null || multiplier <= 1.0D) {
            return;
        }
        event.setDamage(event.getDamage() * multiplier);
    }

    /**
     * インベントリから矢を1本探して消費する (Vanilla 同様: オフハンド優先)。
     *
     * @return 消費した矢のコピー (種類判定用)。見つからなければ null
     */
    private ItemStack findAndConsumeArrow(PlayerInventory inventory) {
        ItemStack offhand = inventory.getItemInOffHand();
        if (isArrow(offhand)) {
            ItemStack copy = offhand.clone();
            copy.setAmount(1);
            if (offhand.getAmount() <= 1) {
                inventory.setItemInOffHand(null);
            } else {
                offhand.setAmount(offhand.getAmount() - 1);
            }
            return copy;
        }
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!isArrow(stack)) {
                continue;
            }
            ItemStack copy = stack.clone();
            copy.setAmount(1);
            if (stack.getAmount() <= 1) {
                inventory.setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                inventory.setItem(i, stack);
            }
            return copy;
        }
        return null;
    }

    private static boolean isArrow(ItemStack stack) {
        if (stack == null) {
            return false;
        }
        Material type = stack.getType();
        return type == Material.ARROW || type == Material.SPECTRAL_ARROW || type == Material.TIPPED_ARROW;
    }

    private Vector addSpread(Vector direction) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        return direction.clone().add(new Vector(
                rng.nextDouble(-HIPFIRE_SPREAD, HIPFIRE_SPREAD),
                rng.nextDouble(-HIPFIRE_SPREAD, HIPFIRE_SPREAD),
                rng.nextDouble(-HIPFIRE_SPREAD, HIPFIRE_SPREAD))).normalize();
    }

    /**
     * 耐久消費 (耐久力エンチャント対応)。耐久が尽きたら破壊する。
     * ADS 中は保存中のクロスボウ (手元には無い) に対して適用される。
     */
    private void damageCrossbow(Player player, ItemStack crossbow) {
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemMeta meta = crossbow.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int unbreaking = meta.getEnchantLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return;
        }
        int newDamage = damageable.getDamage() + 1;
        if (newDamage >= crossbow.getType().getMaxDurability()) {
            UUID uuid = player.getUniqueId();
            if (zoomed.contains(uuid)) {
                savedWeapon.remove(uuid); // 壊れたので返却しない
                unzoom(player, false);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0F, 1.0F);
        } else {
            damageable.setDamage(newDamage);
            crossbow.setItemMeta(meta);
        }
    }

    // ================= ADS中射撃 (スニーク) =================

    /** ADS 中はスニーク入力でも射撃できる */
    @EventHandler(priority = EventPriority.HIGH)
    public void onToggleSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return;
        }
        Player player = event.getPlayer();
        if (!zoomed.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        fireSavedWeapon(player);
    }

    // ================= ズームの後始末 =================

    /** ADS 中のインベントリ操作は照準用望遠鏡を触られる恐れがあるため解除する */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (!zoomed.contains(player.getUniqueId())) {
            return;
        }
        event.setCancelled(true);
        unzoom(player, false);
    }

    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent event) {
        if (zoomed.contains(event.getPlayer().getUniqueId())) {
            unzoom(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        if (zoomed.contains(event.getPlayer().getUniqueId())) {
            // 照準用望遠鏡がオフハンドへ移らないよう入れ替え自体を止める
            event.setCancelled(true);
            unzoom(event.getPlayer(), true);
        }
    }

    @EventHandler
    public void onDrop(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        // 照準用望遠鏡そのものは絶対にドロップさせない
        if (isScopeSpyglass(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
            unzoom(player, true);
            return;
        }
        if (zoomed.contains(player.getUniqueId())) {
            unzoom(player, true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        UUID uuid = player.getUniqueId();
        shotCooldownUntilMs.remove(uuid);
        if (!zoomed.remove(uuid)) {
            return;
        }
        zoomStartMs.remove(uuid);
        savedSlot.remove(uuid);
        ItemStack weapon = savedWeapon.remove(uuid);
        boolean returned = false;
        // ドロップに混ざった照準用望遠鏡をクロスボウへ差し替える
        List<ItemStack> drops = event.getDrops();
        for (int i = 0; i < drops.size(); i++) {
            if (!isScopeSpyglass(drops.get(i))) {
                continue;
            }
            if (weapon == null || weapon.getType().isAir()) {
                drops.remove(i);
            } else {
                drops.set(i, weapon);
            }
            returned = true;
            break;
        }
        // keepInventory などでインベントリに残った場合
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (isScopeSpyglass(contents[i])) {
                inventory.setItem(i, returned ? null : weapon);
                returned = true;
                break;
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        shotCooldownUntilMs.remove(uuid);
        if (zoomed.remove(uuid)) {
            zoomStartMs.remove(uuid);
            stopActiveScopeUse(player);
            restoreWeapon(player);
        }
    }

    /** サーバークラッシュ等で照準用望遠鏡が残ってしまった場合の回収 */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        removeScopeItems(event.getPlayer().getInventory());
    }
}
