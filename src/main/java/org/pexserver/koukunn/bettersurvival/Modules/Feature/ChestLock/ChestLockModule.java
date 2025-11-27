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
import org.bukkit.inventory.Inventory;
import org.bukkit.event.inventory.InventoryType;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.bukkit.OfflinePlayer;

import java.util.*;

/**
 * ChestLock Module - 完全リファクタリング版
 * 
 * クリックハンドラはInventoryのHolderから状態を取得するため、
 * 画面遷移時も状態が保持される
 */
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

    public ChestLockStore getStore() { return store; }
    public ChestShopStore getShopStore() { return shopStore; }

    // ========== Block Events ==========

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

            if (p.isOp()) {
                for (Location l : getChestRelatedLocations(b)) store.remove(l);
                p.sendMessage("§aチェストのロックを強制解除しました");
                return;
            }

            if (lock.getOwner().equals(playerId)) {
                for (Location l : getChestRelatedLocations(b)) store.remove(l);
                p.sendMessage("§aあなたはオーナーのため保護を解除してチェストを壊しました");
                return;
            }

            e.setCancelled(true);
            String ownerName = getPlayerName(lock.getOwner());
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
        
        // ChestLock UIの場合はスキップ（BlockStateをホルダーとして持たない）
        if (!(e.getInventory().getHolder() instanceof BlockState)) {
            return;
        }
        
        Player p = (Player) e.getPlayer();
        Location holderLoc = ((BlockState) e.getInventory().getHolder()).getLocation();
        if (holderLoc == null) return;

        List<Location> related = getChestRelatedLocations(holderLoc.getBlock());

        Optional<ChestLock> anyLock = Optional.empty();
        Location lockAt = null;
        for (Location loc : related) {
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            anyLock = opt; lockAt = loc; break;
        }

        if (!anyLock.isPresent()) return;

        Bukkit.getLogger().info("[ChestLock DEBUG] onInventoryOpen player=" + p.getName() + 
            " lockAt=" + lockAt + " lockName=" + anyLock.get().getName() + " owner=" + anyLock.get().getOwner());
        
        if (!canAccess(p, lockAt)) {
            e.setCancelled(true);
            ChestLock l = anyLock.get();
            String ownerName = getPlayerName(l.getOwner());
            p.sendMessage("§cこのチェストは " + ownerName + " によって保護されています");
            return;
        }

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
        // Holderベースなので特に何もする必要なし
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

            if (!canAccess(p, loc)) {
                e.setCancelled(true);
                p.sendMessage("§cこのチェストは隣接する保護により設置できません");
                return;
            }
        }

        if (owners.isEmpty()) return;

        if (owners.size() > 1) {
            e.setCancelled(true);
            p.sendMessage("§c隣接するチェストは別のプレイヤーによって保護されています");
            return;
        }

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

    // ========== Inventory Click Handler ==========

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        
        // ChestLockのカスタムUIかどうか確認
        ChestLockUI.ChestLockHolder holder = ChestLockUI.getHolder(inv);
        if (holder == null) return;
        
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        
        // Holderから状態を取得
        ChestLockUI.UIType uiType = holder.getUIType();
        Location loc = holder.getChestLocation();
        ChestLock lock = holder.getLock();
        ChestLockStore holderStore = holder.getStore();
        ChestShopStore holderShopStore = holder.getShopStore();
        
        Bukkit.getLogger().info("[ChestLock DEBUG] onClick uiType=" + uiType + " player=" + p.getName() + 
            " loc=" + loc + " lock=" + (lock != null ? lock.getName() : "null"));
        
        switch (uiType) {
            case MAIN:
                handleMainClick(p, clicked, lock, loc, holderStore, holderShopStore);
                break;
            case MEMBER_MANAGE:
                handleMemberManageClick(p, clicked, lock, loc, holderStore, holderShopStore);
                break;
            case MEMBER_ADD:
                handleMemberAddClick(p, clicked, lock, loc, holderStore, holderShopStore);
                break;
            case MEMBER_REMOVE:
                handleMemberRemoveClick(p, clicked, lock, loc, holderStore, holderShopStore);
                break;
            case PROTECTED_LIST:
                handleProtectedListClick(p, clicked, holder, holderStore, holderShopStore, e.getSlot());
                break;
        }
    }
    
    private void handleMainClick(Player p, ItemStack clicked, ChestLock lock, Location loc,
                                  ChestLockStore store, ChestShopStore shopStore) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        // ロックする
        if (d.contains("ロックする")) {
            if (loc == null) return;
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
            ChestLockUI.openMainUI(p, newLock, loc, store, shopStore);
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
            ChestLockUI.openMainUI(p, null, loc, store, shopStore);
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
    
    private void handleMemberManageClick(Player p, ItemStack clicked, ChestLock lock, Location loc,
                                          ChestLockStore store, ChestShopStore shopStore) {
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
            ChestLockUI.openMainUI(p, lock, loc, store, shopStore);
            return;
        }
    }
    
    private void handleMemberAddClick(Player p, ItemStack clicked, ChestLock lock, Location loc,
                                       ChestLockStore store, ChestShopStore shopStore) {
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
    
    private void handleMemberRemoveClick(Player p, ItemStack clicked, ChestLock lock, Location loc,
                                          ChestLockStore store, ChestShopStore shopStore) {
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
    
    private void handleProtectedListClick(Player p, ItemStack clicked, ChestLockUI.ChestLockHolder holder,
                                          ChestLockStore store, ChestShopStore shopStore, int slot) {
        if (clicked == null || !clicked.hasItemMeta()) return;
        ItemMeta m = clicked.getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String d = m.getDisplayName();
        
        Location contextLoc = holder.getChestLocation();
        int page = holder.getPage();
        List<String> keys = holder.getListKeys();
        
        Bukkit.getLogger().info("[ChestLock DEBUG] protectedListClick d=" + d + " contextLoc=" + contextLoc + " page=" + page);
        
        // 戻る
        if (d.contains("戻る") || (clicked.getType() == Material.BARRIER && slot == 49)) {
            // コンテキストロケーションからロックを取得してメイン画面へ
            ChestLock lock = contextLoc != null ? store.get(contextLoc).orElse(null) : null;
            ChestLockUI.openMainUI(p, lock, contextLoc, store, shopStore);
            return;
        }
        
        // 前のページ
        if (d.contains("前のページ")) {
            ChestLockUI.openProtectedListUI(p, contextLoc, store, shopStore, page - 1);
            return;
        }
        
        // 次のページ
        if (d.contains("次のページ")) {
            ChestLockUI.openProtectedListUI(p, contextLoc, store, shopStore, page + 1);
            return;
        }
        
        // チェストをクリック - 座標を表示
        if (clicked.getType() == Material.CHEST && keys != null) {
            int index = page * 45 + slot;
            if (index < keys.size()) {
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
    
    private String getPlayerName(String uuid) {
        if (uuid == null) return "不明";
        try {
            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(uuid));
            return op.getName() != null ? op.getName() : uuid.substring(0, 8);
        } catch (Exception e) {
            return uuid.length() > 8 ? uuid.substring(0, 8) : uuid;
        }
    }
}
