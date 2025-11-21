package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
// import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
// import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
// import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.event.inventory.InventoryType;
// import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;

import java.util.*;

public class ChestLockModule implements Listener {

    private final ChestLockStore store;
    private final org.pexserver.koukunn.bettersurvival.Modules.ToggleModule toggle;

    public ChestLockModule(org.pexserver.koukunn.bettersurvival.Modules.ToggleModule toggle, ConfigManager manager) {
        this.toggle = toggle;
        this.store = new ChestLockStore(manager);
    }

    public static Location toLocation(Block b) { return b.getLocation(); }

    public static List<Location> getChestRelatedLocations(Block b) {
        List<Location> list = new ArrayList<>();
        if (b.getType() != Material.CHEST && b.getType() != Material.TRAPPED_CHEST && b.getType() != Material.BARREL) return list;
        list.add(b.getLocation());

        // Check double chest neighbors
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
        // no toggle gating: chest locks are always active if present
        Block b = e.getBlock();
        for (Location loc : getChestRelatedLocations(b)) {
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            ChestLock lock = opt.get();
            if (e.getPlayer().isOp()) continue; // op can break
            if (!lock.getOwner().equals(e.getPlayer().getUniqueId().toString())) {
                e.setCancelled(true);
                String ownerName = "不明";
                try {
                    java.util.UUID ownerUuid = java.util.UUID.fromString(lock.getOwner());
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
                    if (op != null && op.getName() != null) ownerName = op.getName();
                } catch (Exception ex) {
                    ownerName = lock.getOwner();
                }
                e.getPlayer().sendMessage("§cこのチェストは " + ownerName + " によって保護されています");
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        // no toggle gating: chest locks are always active if present
        if (e.getInventory() == null) return;
        if (e.getInventory().getType() != InventoryType.CHEST && e.getInventory().getType() != InventoryType.BARREL) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();
        Block b = p.getTargetBlockExact(6);
        if (b == null) return;
        for (Location loc : getChestRelatedLocations(b)) {
            Optional<ChestLock> opt = store.get(loc);
            if (!opt.isPresent()) continue;
            if (!canAccess(p, loc)) {
                e.setCancelled(true);
                Optional<ChestLock> ownerLock = store.get(loc);
                String ownerName = "不明";
                if (ownerLock.isPresent()) {
                    ChestLock l = ownerLock.get();
                    try {
                        java.util.UUID ownerUuid = java.util.UUID.fromString(l.getOwner());
                        org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(ownerUuid);
                        if (op != null && op.getName() != null) ownerName = op.getName();
                    } catch (Exception ex) {
                        ownerName = l.getOwner();
                    }
                }
                p.sendMessage("§cこのチェストは " + ownerName + " によって保護されています");
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getView() == null || e.getView().getTitle() == null) return;
        String title = e.getView().getTitle();
        if (!title.startsWith(ChestLockUI.TITLE_PREFIX)) return;
        if (!(e.getPlayer() instanceof Player)) return;
        ChestLockUI.closeForPlayer((Player) e.getPlayer());
    }

    @EventHandler
    public void onInventoryMoveItem(InventoryMoveItemEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        // no toggle gating: chest locks are always active if present
        if (e.getSource() == null || e.getDestination() == null) return;
        Location srcLoc = (e.getSource().getHolder() instanceof org.bukkit.block.BlockState) ? ((org.bukkit.block.BlockState) e.getSource().getHolder()).getLocation() : null;
        Location dstLoc = (e.getDestination().getHolder() instanceof org.bukkit.block.BlockState) ? ((org.bukkit.block.BlockState) e.getDestination().getHolder()).getLocation() : null;
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
        // no toggle gating: chest locks are always active if present
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
        // no toggle gating: chest locks are always active if present
        for (Block b : e.getBlocks()) {
            if (store.get(b.getLocation()).isPresent()) {
                e.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (!toggle.getGlobal("chestlock")) return;
        // protect against explosion: remove locked chests from exploding blocks
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
        if (!title.startsWith(ChestLockUI.TITLE_PREFIX)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        SkullMeta meta = null;
        if (clicked.getItemMeta() instanceof SkullMeta) meta = (SkullMeta) clicked.getItemMeta();
        if (meta == null || meta.getOwningPlayer() == null) {
            // might be lock/unlock button
            org.bukkit.inventory.meta.ItemMeta m = clicked.getItemMeta();
            if (m != null && m.hasDisplayName()) {
                String d = m.getDisplayName();
                // Lock
                if (d.contains("ロックする")) {
                    Location ctx = ChestLockUI.getOpenLocation(p.getUniqueId());
                    if (ctx == null) return;
                    // create lock with auto name
                    ChestLock newLock = new ChestLock(p.getUniqueId().toString(), "lock-" + java.util.UUID.randomUUID().toString().substring(0,6));
                    for (Location l : getChestRelatedLocations(ctx.getBlock())) store.save(l, newLock);
                    p.sendMessage("§aチェストをロックしました: " + newLock.getName());
                    ChestLockUI.openForPlayer(p, newLock, ctx, store);
                    return;
                }
                // Unlock
                if (d.contains("ロック解除")) {
                    Location ctx = ChestLockUI.getOpenLocation(p.getUniqueId());
                    if (ctx == null) return;
                    Optional<ChestLock> existed = store.get(ctx);
                    if (!existed.isPresent()) { p.sendMessage("§cこのチェストはロックされていません"); return; }
                    ChestLock lock = existed.get();
                    if (!p.isOp() && !lock.getOwner().equals(p.getUniqueId().toString())) { p.sendMessage("§cあなたはオーナーではありません"); return; }
                    for (Location l : getChestRelatedLocations(ctx.getBlock())) store.remove(l);
                    p.sendMessage("§aチェストのロックを解除しました");
                    ChestLockUI.openForPlayer(p, null, ctx, store);
                    return;
                }
            }
            return;
        }
        org.bukkit.OfflinePlayer target = meta.getOwningPlayer();
        ChestLock lock = ChestLockUI.getOpenLock(p.getUniqueId());
        if (lock == null) return;
        String uuid = target.getUniqueId().toString();
        if (lock.isMember(uuid)) {
            lock.removeMember(uuid);
            p.sendMessage("§aメンバーから削除しました: " + target.getName());
        } else {
            lock.addMember(uuid);
            p.sendMessage("§aメンバーに追加しました: " + target.getName());
        }
        // Save
        Location loc = ChestLockUI.getOpenLocation(p.getUniqueId());
        if (loc != null) {
            for (Location l : getChestRelatedLocations(loc.getBlock())) {
                store.save(l, lock);
            }
        }
        // refresh UI
        ChestLockUI.openForPlayer(p, lock, loc, store);
    }

}
