package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.bukkit.Material;

import java.util.*;

/**
 * ChestLock UI - 完全リファクタリング版
 * 
 * 設計方針:
 * - 各UIはカスタムInventoryHolderを持ち、状態をInventory自体に保持
 * - 外部Mapへの依存を排除し、Inventory Holderから状態を取得
 * - 画面遷移時もHolderを通じて完全なコンテキストを維持
 */
public class ChestLockUI {

    public static final String TITLE_PREFIX = "§8Chest UI - ";
    public static final String LIST_TITLE = "§8保護済みチェスト一覧";
    public static final String MEMBER_ADD_TITLE = "§8メンバー追加";
    public static final String MEMBER_REMOVE_TITLE = "§8メンバー削除";
    public static final String MEMBER_MANAGE_TITLE = "§8メンバー管理";

    // ========== カスタム InventoryHolder ==========
    
    /**
     * ChestLock UI用の基底Holder
     * すべての状態をこのHolderに保持する
     */
    public static class ChestLockHolder implements InventoryHolder {
        private Inventory inventory;
        private final UIType uiType;
        private final ChestLock lock;
        private final Location chestLocation;
        private final ChestLockStore store;
        private final org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore;
        
        // 一覧用
        private List<String> listKeys;
        private int page;
        
        public ChestLockHolder(UIType uiType, ChestLock lock, Location chestLocation, 
                               ChestLockStore store, 
                               org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
            this.uiType = uiType;
            this.lock = lock;
            this.chestLocation = chestLocation;
            this.store = store;
            this.shopStore = shopStore;
        }
        
        @Override
        public Inventory getInventory() {
            return inventory;
        }
        
        public void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }
        
        public UIType getUIType() { return uiType; }
        public ChestLock getLock() { return lock; }
        public Location getChestLocation() { return chestLocation; }
        public ChestLockStore getStore() { return store; }
        public org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore getShopStore() { return shopStore; }
        
        public List<String> getListKeys() { return listKeys; }
        public void setListKeys(List<String> keys) { this.listKeys = keys; }
        public int getPage() { return page; }
        public void setPage(int page) { this.page = page; }
    }
    
    public enum UIType {
        MAIN,
        MEMBER_MANAGE,
        MEMBER_ADD,
        MEMBER_REMOVE,
        PROTECTED_LIST
    }
    
    // ========== Holderからの状態取得 ==========
    
    public static ChestLockHolder getHolder(Inventory inv) {
        if (inv == null) return null;
        if (inv.getHolder() instanceof ChestLockHolder) {
            return (ChestLockHolder) inv.getHolder();
        }
        return null;
    }
    
    public static boolean isChestLockUI(Inventory inv) {
        return getHolder(inv) != null;
    }
    
    // ========== メイン画面 ==========
    
    @SuppressWarnings("deprecation")
    public static void openMainUI(Player p, ChestLock lock, Location loc, 
                                   ChestLockStore store, 
                                   org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        // Bedrockプレイヤーの場合は専用フォームを開く
        if (FloodgateUtil.isBedrock(p)) {
            openBedrockMainForm(p, lock, loc, store, shopStore);
            return;
        }
        
        // Holder作成
        ChestLockHolder holder = new ChestLockHolder(UIType.MAIN, lock, loc, store, shopStore);
        
        String name = (lock == null ? "(未ロック)" : lock.getName());
        Inventory inv = Bukkit.createInventory(holder, 27, TITLE_PREFIX + name);
        holder.setInventory(inv);
        
        
        
        boolean isOwnerOrOp = lock != null && (p.isOp() || 
            (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString())));
        
        // ガラス装飾
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }
        
        if (lock == null) {
            // 未ロック: ロックボタンのみ
            inv.setItem(13, createItem(Material.LIME_STAINED_GLASS_PANE, "§a§lロックする",
                "§7チェストをロックしてアクセスを制限します",
                "§7クリックで自動名付けでロック"));
        } else {
            // ロック済み
            
            // スロット11: メンバー管理
            ItemStack memberBtn = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) memberBtn.getItemMeta();
            if (sm != null) {
                sm.setDisplayName("§b§lメンバー管理");
                sm.setLore(Arrays.asList(
                    "§7メンバーの追加・削除を行います",
                    "§7現在のメンバー数: §f" + lock.getMembers().size() + "人"
                ));
                memberBtn.setItemMeta(sm);
            }
            inv.setItem(11, memberBtn);
            
            // スロット13: 情報
            String ownerName = getPlayerName(lock.getOwner());
            inv.setItem(13, createItem(Material.BOOK, "§e§l" + lock.getName(),
                "§7オーナー: §f" + ownerName,
                "§7メンバー: §f" + lock.getMembers().size() + "人",
                "§7座標: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()));
            
            // スロット15: ロック解除 (オーナー/OPのみ)
            if (isOwnerOrOp) {
                inv.setItem(15, createItem(Material.RED_STAINED_GLASS_PANE, "§c§lロック解除",
                    "§7このチェストのロックを解除します"));
            }
        }
        
        // スロット22: 保護済みチェスト一覧
        inv.setItem(22, createItem(Material.CHEST, "§e§l保護済みチェスト一覧",
            "§7あなたが保護しているチェストを表示します"));
        
        // スロット26: 閉じる
        inv.setItem(26, createItem(Material.BARRIER, "§c閉じる"));
        
        p.openInventory(inv);
    }
    
    // ========== メンバー管理画面 ==========
    
    @SuppressWarnings("deprecation")
    public static void openMemberManageUI(Player p, ChestLock lock, Location loc,
                                          ChestLockStore store,
                                          org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;
        
        ChestLockHolder holder = new ChestLockHolder(UIType.MEMBER_MANAGE, lock, loc, store, shopStore);
        Inventory inv = Bukkit.createInventory(holder, 27, MEMBER_MANAGE_TITLE + " - " + lock.getName());
        holder.setInventory(inv);
        
        
        
        // 装飾
        ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }
        
        // スロット11: メンバー追加
        inv.setItem(11, createItem(Material.LIME_STAINED_GLASS_PANE, "§a§lメンバー追加",
            "§7近くのプレイヤーをメンバーに追加します"));
        
        // スロット13: 現在のメンバー一覧
        List<String> memberLore = new ArrayList<>();
        if (lock.getMembers().isEmpty()) {
            memberLore.add("§7メンバーはいません");
        } else {
            for (int i = 0; i < Math.min(lock.getMembers().size(), 10); i++) {
                memberLore.add("§f- " + getPlayerName(lock.getMembers().get(i)));
            }
            if (lock.getMembers().size() > 10) {
                memberLore.add("§7...他 " + (lock.getMembers().size() - 10) + "人");
            }
        }
        inv.setItem(13, createItem(Material.BOOK, "§e§l現在のメンバー", memberLore.toArray(new String[0])));
        
        // スロット15: メンバー削除
        inv.setItem(15, createItem(Material.RED_STAINED_GLASS_PANE, "§c§lメンバー削除",
            "§7登録されているメンバーを削除します"));
        
        // スロット22: 戻る
        inv.setItem(22, createItem(Material.ARROW, "§e戻る"));
        
        p.openInventory(inv);
    }
    
    // ========== メンバー追加画面 ==========
    
    @SuppressWarnings("deprecation")
    public static void openMemberAddUI(Player p, ChestLock lock, Location loc,
                                       ChestLockStore store,
                                       org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;
        
        List<Player> candidates = new ArrayList<>();
        for (Player pl : getNearbyPlayers(p, loc)) {
            if (!lock.isMember(pl.getUniqueId().toString()) &&
                (lock.getOwner() == null || !lock.getOwner().equals(pl.getUniqueId().toString()))) {
                candidates.add(pl);
            }
        }
        
        int size = Math.max(27, 9 * ((candidates.size() + 8) / 9 + 1));
        size = Math.min(size, 54);
        
        ChestLockHolder holder = new ChestLockHolder(UIType.MEMBER_ADD, lock, loc, store, shopStore);
        Inventory inv = Bukkit.createInventory(holder, size, MEMBER_ADD_TITLE + " - " + lock.getName());
        holder.setInventory(inv);
        
        
        
        // プレイヤーヘッド配置
        int slot = 0;
        for (Player pl : candidates) {
            if (slot >= size - 9) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(pl);
                meta.setDisplayName("§a" + pl.getName());
                double dist = pl.getLocation().distance(loc);
                meta.setLore(Arrays.asList(
                    "§7クリックでメンバーに追加",
                    "§7距離: §f" + String.format("%.1f", dist) + "m"
                ));
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }
        
        // 候補がいない場合
        if (candidates.isEmpty()) {
            inv.setItem(13, createItem(Material.BARRIER, "§c追加可能なプレイヤーがいません",
                "§7近くに他のプレイヤーがいないか、",
                "§7すでにメンバーに追加済みです"));
        }
        
        // 最後の行: 戻る
        int lastRow = size - 9;
        inv.setItem(lastRow + 4, createItem(Material.ARROW, "§e戻る"));
        
        p.openInventory(inv);
    }
    
    // ========== メンバー削除画面 ==========
    
    @SuppressWarnings("deprecation")
    public static void openMemberRemoveUI(Player p, ChestLock lock, Location loc,
                                          ChestLockStore store,
                                          org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;
        
        List<String> members = lock.getMembers();
        int size = Math.max(27, 9 * ((members.size() + 8) / 9 + 1));
        size = Math.min(size, 54);
        
        ChestLockHolder holder = new ChestLockHolder(UIType.MEMBER_REMOVE, lock, loc, store, shopStore);
        Inventory inv = Bukkit.createInventory(holder, size, MEMBER_REMOVE_TITLE + " - " + lock.getName());
        holder.setInventory(inv);
        
        
        
        // メンバーのヘッド配置
        int slot = 0;
        for (String memberUuid : members) {
            if (slot >= size - 9) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                try {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(memberUuid));
                    meta.setOwningPlayer(op);
                    String name = op.getName() != null ? op.getName() : memberUuid.substring(0, 8);
                    meta.setDisplayName("§c" + name);
                    meta.setLore(Arrays.asList(
                        "§7クリックでメンバーから削除",
                        op.isOnline() ? "§aオンライン" : "§7オフライン"
                    ));
                    head.setItemMeta(meta);
                } catch (Exception e) {
                    meta.setDisplayName("§c" + memberUuid.substring(0, 8));
                    head.setItemMeta(meta);
                }
            }
            inv.setItem(slot++, head);
        }
        
        // メンバーがいない場合
        if (members.isEmpty()) {
            inv.setItem(13, createItem(Material.BARRIER, "§cメンバーがいません",
                "§7まずメンバーを追加してください"));
        }
        
        // 最後の行: 戻る
        int lastRow = size - 9;
        inv.setItem(lastRow + 4, createItem(Material.ARROW, "§e戻る"));
        
        p.openInventory(inv);
    }
    
    // ========== 保護済みチェスト一覧 ==========
    
    @SuppressWarnings("deprecation")
    public static void openProtectedListUI(Player p, Location contextLoc,
                                           ChestLockStore store,
                                           org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore,
                                           int page) {
        Map<String, ChestLock> all = store.getAll();
        String playerUuid = p.getUniqueId().toString();
        
        // プレイヤーがオーナーのチェストのみフィルタ (OPは全て表示)
        List<Map.Entry<String, ChestLock>> filtered = new ArrayList<>();
        for (Map.Entry<String, ChestLock> e : all.entrySet()) {
            if (p.isOp() || (e.getValue().getOwner() != null && e.getValue().getOwner().equals(playerUuid))) {
                filtered.add(e);
            }
        }
        
        int itemsPerPage = 45;
        int totalPages = Math.max(1, (filtered.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));
        
        // contextLocをHolderに保持
        ChestLockHolder holder = new ChestLockHolder(UIType.PROTECTED_LIST, null, contextLoc, store, shopStore);
        holder.setPage(page);
        
        // キーリストを保存
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ChestLock> e : filtered) {
            keys.add(e.getKey());
        }
        holder.setListKeys(keys);
        
        Inventory inv = Bukkit.createInventory(holder, 54, LIST_TITLE + " (" + (page + 1) + "/" + totalPages + ")");
        holder.setInventory(inv);
        
        
        
        // チェストアイテム配置
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filtered.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Map.Entry<String, ChestLock> entry = filtered.get(i);
            String key = entry.getKey();
            ChestLock lock = entry.getValue();
            
            List<String> lore = new ArrayList<>();
            String[] parts = key.split(":");
            if (parts.length >= 4) {
                lore.add("§7ワールド: §f" + parts[0]);
                lore.add("§7座標: §f" + parts[1] + ", " + parts[2] + ", " + parts[3]);
            }
            lore.add("§7オーナー: §f" + getPlayerName(lock.getOwner()));
            lore.add("§7メンバー: §f" + lock.getMembers().size() + "人");
            lore.add("");
            lore.add("§7クリックで座標を表示");
            
            inv.setItem(slot++, createItem(Material.CHEST, "§e" + lock.getName(), lore.toArray(new String[0])));
        }
        
        // チェストがない場合
        if (filtered.isEmpty()) {
            inv.setItem(22, createItem(Material.BARRIER, "§c保護済みチェストがありません"));
        }
        
        // ページング (最後の行)
        if (page > 0) {
            inv.setItem(45, createItem(Material.ARROW, "§e前のページ"));
        }
        
        inv.setItem(49, createItem(Material.BARRIER, "§c戻る"));
        
        if (page < totalPages - 1) {
            inv.setItem(53, createItem(Material.ARROW, "§e次のページ"));
        }
        
        p.openInventory(inv);
    }
    
    // ========== ユーティリティ ==========
    
    private static ItemStack createItem(Material mat, String name, String... lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            if (lore.length > 0) {
                meta.setLore(Arrays.asList(lore));
            }
            item.setItemMeta(meta);
        }
        return item;
    }
    
    private static List<Player> getNearbyPlayers(Player p, Location loc) {
        List<Player> nearby = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getWorld().equals(p.getWorld())) continue;
            if (pl.getUniqueId().equals(p.getUniqueId())) continue;
            if (pl.getLocation().distance(loc) <= 50.0) nearby.add(pl);
        }
        nearby.sort(Comparator.comparingDouble(pl -> pl.getLocation().distance(loc)));
        return nearby;
    }
    
    private static String getPlayerName(String uuid) {
        if (uuid == null) return "不明";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            return op.getName() != null ? op.getName() : uuid.substring(0, 8);
        } catch (Exception e) {
            return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        }
    }
    
    // ========== Bedrock対応 ==========
    
    private static void openBedrockMainForm(Player p, ChestLock lock, Location loc,
                                            ChestLockStore store,
                                            org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        List<Player> nearby = getNearbyPlayers(p, loc);
        List<String> buttons = new ArrayList<>();
        
        if (lock == null) {
            buttons.add("ロックする (自動名)");
        } else {
            boolean ownerOrOp = p.isOp() || 
                (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
            if (ownerOrOp) buttons.add("ロック解除");
            buttons.add("メンバー管理");
        }
        buttons.add("保護済みチェスト一覧");
        buttons.add("閉じる");
        
        String title = "Chest UI - " + (lock == null ? "(未ロック)" : lock.getName());
        FormsUtil.openSimpleForm(p, title, buttons, idx -> {
            if (idx < 0) return;
            
            if (lock == null) {
                if (idx == 0) {
                    // ロック
                    for (Location r : ChestLockModule.getChestRelatedLocations(loc.getBlock())) {
                        try {
                            if (shopStore != null && shopStore.get(r).isPresent()) {
                                p.sendMessage("§cこのチェストはショップに紐づいているためロックできません");
                                return;
                            }
                        } catch (Exception ignored) {}
                    }
                    ChestLock newLock = new ChestLock(p.getUniqueId().toString(),
                        "lock-" + UUID.randomUUID().toString().substring(0, 6));
                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) {
                        store.save(l, newLock);
                    }
                    p.sendMessage("§aチェストをロックしました: " + newLock.getName());
                    openBedrockMainForm(p, newLock, loc, store, shopStore);
                    return;
                }
                if (idx == 1) {
                    openBedrockListForm(p, loc, store, shopStore);
                    return;
                }
                return;
            }
            
            boolean ownerOrOp = p.isOp() || 
                (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
            int base = 0;
            
            if (ownerOrOp) {
                if (idx == base) {
                    // ロック解除
                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) {
                        store.remove(l);
                    }
                    p.sendMessage("§aチェストのロックを解除しました");
                    openBedrockMainForm(p, null, loc, store, shopStore);
                    return;
                }
                base++;
            }
            
            if (idx == base) {
                // メンバー管理
                openBedrockMemberManageForm(p, lock, loc, nearby, store, shopStore);
                return;
            }
            base++;
            
            if (idx == base) {
                // 一覧
                openBedrockListForm(p, loc, store, shopStore);
                return;
            }
        });
    }
    
    private static void openBedrockMemberManageForm(Player p, ChestLock lock, Location loc,
                                                    List<Player> nearby,
                                                    ChestLockStore store,
                                                    org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        List<String> mm = Arrays.asList("追加", "一覧/削除", "戻る");
        FormsUtil.openSimpleForm(p, "メンバー管理 - " + lock.getName(), mm, midx -> {
            if (midx < 0) return;
            if (midx == 2) {
                openBedrockMainForm(p, lock, loc, store, shopStore);
                return;
            }
            if (midx == 0) {
                // 追加
                List<FormsUtil.ButtonSpec> addBtns = new ArrayList<>();
                List<Player> candidates = new ArrayList<>();
                for (Player pl : nearby) {
                    if (pl.getUniqueId().equals(p.getUniqueId())) continue;
                    String su = pl.getUniqueId().toString();
                    if (lock.isMember(su)) continue;
                    if (lock.getOwner() != null && lock.getOwner().equals(su)) continue;
                    candidates.add(pl);
                    String rawName = pl.getName();
                    String san = sanitizeName(rawName);
                    String url = "https://minotar.net/avatar/" + san + "/64";
                    addBtns.add(FormsUtil.ButtonSpec.ofUrl(rawName, url));
                }
                addBtns.add(FormsUtil.ButtonSpec.ofText("戻る"));
                
                FormsUtil.openSimpleForm(p, "メンバー追加 - " + lock.getName(), addBtns, aidx -> {
                    if (aidx < 0) return;
                    if (aidx == addBtns.size() - 1) {
                        openBedrockMemberManageForm(p, lock, loc, nearby, store, shopStore);
                        return;
                    }
                    Player target = candidates.get(aidx);
                    lock.addMember(target.getUniqueId().toString());
                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) {
                        store.save(l, lock);
                    }
                    p.sendMessage("§aメンバーに追加しました: " + target.getName());
                    openBedrockMemberManageForm(p, lock, loc, nearby, store, shopStore);
                });
                return;
            }
            if (midx == 1) {
                // 一覧/削除
                List<FormsUtil.ButtonSpec> mbtns = new ArrayList<>();
                for (String m : lock.getMembers()) {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(m));
                    String name = op.getName() == null ? m : op.getName();
                    String san = sanitizeName(name);
                    String url = "https://minotar.net/avatar/" + san + "/64";
                    mbtns.add(FormsUtil.ButtonSpec.ofUrl(name, url));
                }
                mbtns.add(FormsUtil.ButtonSpec.ofText("戻る"));
                
                FormsUtil.openSimpleForm(p, "メンバー一覧 - " + lock.getName(), mbtns, remIdx -> {
                    if (remIdx < 0) return;
                    if (remIdx == mbtns.size() - 1) {
                        openBedrockMemberManageForm(p, lock, loc, nearby, store, shopStore);
                        return;
                    }
                    String removeUuid = lock.getMembers().get(remIdx);
                    lock.removeMember(removeUuid);
                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) {
                        store.save(l, lock);
                    }
                    p.sendMessage("§aメンバーから削除しました");
                    openBedrockMemberManageForm(p, lock, loc, nearby, store, shopStore);
                });
            }
        });
    }
    
    private static void openBedrockListForm(Player p, Location contextLoc,
                                            ChestLockStore store,
                                            org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        Map<String, ChestLock> all = store.getAll();
        String playerUuid = p.getUniqueId().toString();
        
        List<String> rows = new ArrayList<>();
        List<String> keyList = new ArrayList<>();
        
        for (Map.Entry<String, ChestLock> e : all.entrySet()) {
            if (!p.isOp() && (e.getValue().getOwner() == null || !e.getValue().getOwner().equals(playerUuid))) {
                continue;
            }
            String key = e.getKey();
            ChestLock lk = e.getValue();
            String ownerName = getPlayerName(lk.getOwner());
            rows.add(lk.getName() + " (オーナー: " + ownerName + ")");
            keyList.add(key);
            if (rows.size() >= 50) break;
        }
        
        if (rows.isEmpty()) {
            rows.add("保護済みチェストはありません");
        }
        rows.add("戻る");
        
        FormsUtil.openSimpleForm(p, "保護済みチェスト一覧", rows, ridx -> {
            if (ridx < 0) return;
            if (ridx == rows.size() - 1 || keyList.isEmpty()) {
                // 戻る or 空の場合
                ChestLock lock = contextLoc != null ? store.get(contextLoc).orElse(null) : null;
                openBedrockMainForm(p, lock, contextLoc, store, shopStore);
                return;
            }
            // 座標表示
            if (ridx < keyList.size()) {
                String key = keyList.get(ridx);
                String[] parts = key.split(":");
                if (parts.length >= 4) {
                    p.sendMessage("§e座標: §f" + parts[1] + ", " + parts[2] + ", " + parts[3]);
                    p.sendMessage("§7ワールド: §f" + parts[0]);
                }
            }
        });
    }
    
    private static String sanitizeName(String name) {
        if (name == null) return "";
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }
}
