package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.bukkit.OfflinePlayer;

import java.util.*;
import java.util.UUID;

public class ChestLockModule implements Listener {

    private final ChestLockStore store;
    private final ToggleModule toggle;
    private final ChestShopStore shopStore;

    public ChestLockModule(ToggleModule toggle, ConfigManager manager) {
        this.toggle = toggle;
        this.store = new ChestLockStore(manager);
        this.shopStore = new ChestShopStore(manager);
    }

    public static Location toLocation(Block b) { return b.getLocation(); }

    public static List<Location> getChestRelatedLocations(Block b) {
        List<Location> list = new ArrayList<>();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST && b.getType() != Material.BARREL) return list;
        list.add(b.getLocation());

        for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block nb = b.getRelative(face);
            if (nb.getType() == b.getType()) {
                list.add(nb.getLocation());
            }
        }
        return list;
    }

    public boolean isLocked(Location loc) {
        return store.get(loc).isPresent();
    }

    public Optional<ChestLock> getLock(Location loc) { return store.get(loc); }

    public void lock(Location loc, ChestLock lock) { store.save(loc, lock); }

    public void unlock(Location loc) { store.remove(loc); }

    public boolean canAccess(Player p, Location loc) {
        if (!toggle.getGlobal("chestlock")) return true;
        Optional<ChestLock> opt = store.get(loc);
        if (!opt.isPresent()) return true;
        ChestLock lock = opt.get();
        if (p.isOp()) return true;
        String uuid = p.getUniqueId().toString();
        if (lock.getOwner() != null && lock.getOwner().equals(uuid)) return true;
        if (lock.isMember(uuid)) return true;
        return false;
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        Block b = e.getBlock();
        for (Location loc : getChestRelatedLocations(b)) {
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            ChestLock lock = opt.get();
            Player p = e.getPlayer();
            String playerId = p.getUniqueId().toString();

            // OP は破壊を許可するが保護は解除する
            if (p.isOp()) {
                for (Location l : getChestRelatedLocations(b)) store.remove(l);
                p.sendMessage("§aチェストのロックを強制解除しました");
                // allow break
                return;
            }

            // オーナーなら保護を解除して破壊を許可
            if (lock.getOwner().equals(playerId)) {
                for (Location l : getChestRelatedLocations(b)) store.remove(l);
                p.sendMessage("§aあなたはオーナーのため保護を解除してチェストを壊しました");
                return;
            }

            // メンバー・その他は破壊不可
            e.setCancelled(true);
            String ownerName = "不明";
            try {
                UUID ownerUuid = UUID.fromString(lock.getOwner());
                OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
                if (op != null && op.getName() != null) ownerName = op.getName();
            } catch (Exception ex) {
                ownerName = lock.getOwner();
            }
            e.getPlayer().sendMessage("§cこのチェストは " + ownerName + " によって保護されています");
            return;
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        if (e.getInventory() == null) return;
        if (e.getInventory().getType() != InventoryType.CHEST && e.getInventory().getType() != InventoryType.BARREL) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        // InventoryOpenEvent においては、プレイヤーの視線ブロックではなく
        // インベントリのホルダーから正確なチェスト位置を取るようにする。
        Location holderLoc = null;
        if (e.getInventory().getHolder() instanceof BlockState) {
            holderLoc = ((BlockState) e.getInventory().getHolder()).getLocation();
        } else {
            Block b = p.getTargetBlockExact(6);
            if (b != null) holderLoc = b.getLocation();
        }
        if (holderLoc == null) return;

        // 近隣のチェスト(ラージチェストを含む)を全て取得
        List<Location> related = getChestRelatedLocations(holderLoc.getBlock());

        // まず、関連するどれかの位置にロックが存在するか調べる
        Optional<ChestLock> anyLock = Optional.empty();
        Location lockAt = null;
        for (Location loc : related) {
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            anyLock = opt; lockAt = loc; break;
        }

        if (!anyLock.isPresent()) return;

        // ロックを発見 → まずはアクセス許可をチェック
        if (!canAccess(p, lockAt)) {
            e.setCancelled(true);
            ChestLock l = anyLock.get();
            String ownerName = "不明";
            try {
                UUID ownerUuid = UUID.fromString(l.getOwner());
                OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
                if (op != null && op.getName() != null) ownerName = op.getName();
            } catch (Exception ex) {
                ownerName = l.getOwner();
            }
            p.sendMessage("§cこのチェストは " + ownerName + " によって保護されています");
            return;
        }

        // アクセス可能な場合、隣接チェストが後から追加されているケースに対応するため
        // 見つかったロック情報を関連する全ての位置に同期しておく
        ChestLock found = anyLock.get();
        for (Location loc : related) {
            Optional<ChestLock> exist = store.get(loc);
            if (!exist.isPresent()) {
                store.save(loc, found);
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView() == null || e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        boolean isChestLockUI = title.startsWith(ChestLockUI.TITLE_PREFIX) ||
                                title.startsWith(ChestLockUI.LIST_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_ADD_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_REMOVE_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_MANAGE_TITLE);
        if (!isChestLockUI) return;
        if (!(e.getPlayer() instanceof Player)) return;
        ChestLockUI.closeForPlayer((Player) e.getPlayer());
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        if (e.getSource() == null || e.getDestination() == null) return;
        Location srcLoc = (e.getSource().getHolder() instanceof BlockState) ? ((BlockState) e.getSource().getHolder()).getLocation() : null;
        Location dstLoc = (e.getDestination().getHolder() instanceof BlockState) ? ((BlockState) e.getDestination().getHolder()).getLocation() : null;
        if (srcLoc != null && store.get(srcLoc).isPresent()) {
            e.setCancelled(true);
            return;
        }
        if (dstLoc != null && store.get(dstLoc).isPresent()) {
            e.setCancelled(true);
            return;
        }
    }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        for (Block b : e.getBlocks()) {
            if (store.get(b.getLocation()).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        for (Block b : e.getBlocks()) {
            if (store.get(b.getLocation()).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();
        Block placed = e.getBlockPlaced();
        if (placed == null) return;
        if (placed.getType() != Material.CHEST && placed.getType() != Material.TRAPPED_CHEST) return;

        // チェストの隣接方向を調べて、ロックされているものがあるか確認
        Set<String> owners = new HashSet<>();
        Map<String, ChestLock> ownerLockMap = new HashMap<>();
        for (BlockFace face : Arrays.asList(BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST)) {
            Block nb = placed.getRelative(face);
            if (nb.getType() != Material.CHEST && nb.getType() != Material.TRAPPED_CHEST) continue;
            Location loc = nb.getLocation();
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            ChestLock lock = opt.get();
            owners.add(lock.getOwner());
            ownerLockMap.put(lock.getOwner(), lock);

            // もしプレイヤーが隣接保護チェストにアクセスできないなら設置をキャンセル
            if (!canAccess(p, loc)) {
                e.setCancelled(true);
                p.sendMessage("§cこのチェストは隣接する保護により設置できません");
                return;
            }
        }

        if (owners.isEmpty()) return; // 隣接するロックはなし

        if (owners.size() > 1) {
            // 複数の異なるオーナーに挟まれる二重保護は混乱を招くため禁止
            e.setCancelled(true);
            p.sendMessage("§c隣接するチェストは別のプレイヤーによって保護されています");
            return;
        }

        // オーナーは一意なので、新設チェストに同じロックを保存して同期する
        String owner = owners.iterator().next();
        ChestLock lockToApply = ownerLockMap.get(owner);
        if (lockToApply != null) {
            for (Location l : getChestRelatedLocations(placed)) {
                store.save(l, lockToApply);
            }
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        List<Block> remove = new ArrayList<>();
        for (Block b : e.blockList()) {
            if (store.get(b.getLocation()).isPresent()) {
                remove.add(b);
            }
        }
        e.blockList().removeAll(remove);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryView view = e.getView();
        if (view == null || view.getTitle() == null) return;
        String title = view.getTitle();
        
        // UI タイトルのチェック
        boolean isChestLockUI = title.startsWith(ChestLockUI.TITLE_PREFIX) ||
                                title.startsWith(ChestLockUI.LIST_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_ADD_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_REMOVE_TITLE) ||
                                title.startsWith(ChestLockUI.MEMBER_MANAGE_TITLE);
        
        if (!isChestLockUI) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        String uiType = ChestLockUI.getOpenUIType(p.getUniqueId());
        Location loc = ChestLockUI.getOpenLocation(p.getUniqueId());
        ChestLock lock = ChestLockUI.getOpenLock(p.getUniqueId());
        
        // メイン画面の処理
        if ("main".equals(uiType) || title.startsWith(ChestLockUI.TITLE_PREFIX)) {
            handleMainUIClick(p, clicked, lock, loc);
            return;
        }
        
        // メンバー管理画面の処理
        if ("member_manage".equals(uiType) || title.startsWith(ChestLockUI.MEMBER_MANAGE_TITLE)) {
            handleMemberManageClick(p, clicked, lock, loc);
            return;
        }
        
        // メンバー追加画面の処理
        if ("member_add".equals(uiType) || title.startsWith(ChestLockUI.MEMBER_ADD_TITLE)) {
            handleMemberAddClick(p, clicked, lock, loc, e.getSlot());
            return;
        }
        
        // メンバー削除画面の処理
        if ("member_remove".equals(uiType) || title.startsWith(ChestLockUI.MEMBER_REMOVE_TITLE)) {
            handleMemberRemoveClick(p, clicked, lock, loc, e.getSlot());
            return;
        }
        
        // 保護済みチェスト一覧画面の処理
        if ("protected_list".equals(uiType) || title.startsWith(ChestLockUI.LIST_TITLE)) {
            handleProtectedListClick(p, clicked, loc, e.getSlot());
            return;
        }
    }
    
    private void handleMainUIClick(Player p, ItemStack clicked, ChestLock lock, Location loc) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // ロックする
        if (d.contains("ロックする")) {
            if (loc == null) return;
            // ショップチェックを防ぐ
            for (Location r : getChestRelatedLocations(loc.getBlock())) {
                try { 
                    if (shopStore != null && shopStore.get(r).isPresent()) { 
                        p.sendMessage("§cこのチェストはショップに紐づいているためロックできません"); 
                        return; 
                    } 
                } catch (Exception ignored) {}
            }
            ChestLock newLock = new ChestLock(p.getUniqueId().toString(), "lock-" + UUID.randomUUID().toString().substring(0,6));
            for (Location l : getChestRelatedLocations(loc.getBlock())) store.save(l, newLock);
            p.sendMessage("§aチェストをロックしました: " + newLock.getName());
            ChestLockUI.openForPlayer(p, newLock, loc, store, shopStore);
            return;
        }
        
        // ロック解除
        if (d.contains("ロック解除")) {
            if (loc == null) return;
            Optional<ChestLock> existed = store.get(loc);
            if (!existed.isPresent()) { 
                p.sendMessage("§cこのチェストはロックされていません"); 
                return; 
            }
            ChestLock lockToRemove = existed.get();
            if (!p.isOp() && !lockToRemove.getOwner().equals(p.getUniqueId().toString())) { 
                p.sendMessage("§cあなたはオーナーではありません"); 
                return; 
            }
            for (Location l : getChestRelatedLocations(loc.getBlock())) store.remove(l);
            p.sendMessage("§aチェストのロックを解除しました");
            ChestLockUI.openForPlayer(p, null, loc, store, shopStore);
            return;
        }
        
        // メンバー管理
        if (d.contains("メンバー管理")) {
            if (lock == null) return;
            ChestLockUI.openMemberManageUI(p, lock, loc, store, shopStore);
            return;
        }
        
        // 保護済みチェスト一覧
        if (d.contains("保護済みチェスト一覧")) {
            ChestLockUI.openProtectedListUI(p, loc, store, shopStore, 0);
            return;
        }
        
        // 閉じる
        if (d.contains("閉じる") || clicked.getType() == Material.BARRIER) {
            p.closeInventory();
            return;
        }
    }
    
    private void handleMemberManageClick(Player p, ItemStack clicked, ChestLock lock, Location loc) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // メンバー追加
        if (d.contains("メンバー追加")) {
            if (lock == null) return;
            ChestLockUI.openMemberAddUI(p, lock, loc, store, shopStore);
            return;
        }
        
        // メンバー削除
        if (d.contains("メンバー削除")) {
            if (lock == null) return;
            ChestLockUI.openMemberRemoveUI(p, lock, loc, store, shopStore);
            return;
        }
        
        // 戻る
        if (d.contains("戻る")) {
            ChestLockUI.openForPlayer(p, lock, loc, store, shopStore);
            return;
        }
    }
    
    private void handleMemberAddClick(Player p, ItemStack clicked, ChestLock lock, Location loc, int slot) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // 戻る
        if (d.contains("戻る")) {
            ChestLockUI.openMemberManageUI(p, lock, loc, store, shopStore);
            return;
        }
        
        // プレイヤーヘッドのクリック
        if (clicked.getType() == Material.PLAYER_HEAD && m instanceof SkullMeta) {
            SkullMeta sm = (SkullMeta) m;
            if (sm.getOwningPlayer() != null) {
                String uuid = sm.getOwningPlayer().getUniqueId().toString();
                String name = sm.getOwningPlayer().getName();
                
                if (lock != null && !lock.isMember(uuid)) {
                    lock.addMember(uuid);
                    for (Location l : getChestRelatedLocations(loc.getBlock())) {
                        store.save(l, lock);
                    }
                    p.sendMessage("§aメンバーに追加しました: " + name);
                }
                ChestLockUI.openMemberAddUI(p, lock, loc, store, shopStore);
            }
        }
    }
    
    private void handleMemberRemoveClick(Player p, ItemStack clicked, ChestLock lock, Location loc, int slot) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // 戻る
        if (d.contains("戻る")) {
            ChestLockUI.openMemberManageUI(p, lock, loc, store, shopStore);
            return;
        }
        
        // プレイヤーヘッドのクリック
        if (clicked.getType() == Material.PLAYER_HEAD && m instanceof SkullMeta) {
            SkullMeta sm = (SkullMeta) m;
            if (sm.getOwningPlayer() != null) {
                String uuid = sm.getOwningPlayer().getUniqueId().toString();
                String name = sm.getOwningPlayer().getName();
                
                if (lock != null && lock.isMember(uuid)) {
                    lock.removeMember(uuid);
                    for (Location l : getChestRelatedLocations(loc.getBlock())) {
                        store.save(l, lock);
                    }
                    p.sendMessage("§aメンバーから削除しました: " + name);
                }
                ChestLockUI.openMemberRemoveUI(p, lock, loc, store, shopStore);
            }
        }
    }
    
    private void handleProtectedListClick(Player p, ItemStack clicked, Location contextLoc, int slot) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // 戻る
        if (d.contains("戻る") || clicked.getType() == Material.BARRIER) {
            ChestLock lock = ChestLockUI.getOpenLock(p.getUniqueId());
            ChestLockUI.openForPlayer(p, lock, contextLoc, store, shopStore);
            return;
        }
        
        // 前のページ
        if (d.contains("前のページ")) {
            int page = ChestLockUI.getListPage(p.getUniqueId());
            ChestLockUI.openProtectedListUI(p, contextLoc, store, shopStore, page - 1);
            return;
        }
        
        // 次のページ
        if (d.contains("次のページ")) {
            int page = ChestLockUI.getListPage(p.getUniqueId());
            ChestLockUI.openProtectedListUI(p, contextLoc, store, shopStore, page + 1);
            return;
        }
        
        // チェストをクリック - 座標をチャットに送信
        if (clicked.getType() == Material.CHEST) {
            List<String> keys = ChestLockUI.getListKeys(p.getUniqueId());
            int page = ChestLockUI.getListPage(p.getUniqueId());
            int index = page * 45 + slot;
            if (keys != null && index < keys.size()) {
                String key = keys.get(index);
                String[] parts = key.split(":");
                if (parts.length >= 4) {
                    String tpCmd = "/tp " + parts[1] + " " + parts[2] + " " + parts[3];
                    p.sendMessage("§e座標: §f" + parts[1] + ", " + parts[2] + ", " + parts[3]);
                    p.sendMessage("§7テレポートコマンド: §f" + tpCmd);
                }
            }
        }
    }

}
