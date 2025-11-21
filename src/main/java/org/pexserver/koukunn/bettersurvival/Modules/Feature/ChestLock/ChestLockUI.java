package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;

import java.util.*;

public class ChestLockUI {

    public static final String TITLE_PREFIX = "Chest UI - ";

    private static final Map<UUID, ChestLock> openLocks = new HashMap<>();
    private static final Map<UUID, Location> openLocations = new HashMap<>();

    public static ChestLock getOpenLock(UUID uid) { return openLocks.get(uid); }
    public static Location getOpenLocation(UUID uid) { return openLocations.get(uid); }

    public static void openForPlayer(Player p, ChestLock lock, Location loc, ChestLockStore store) {
        // nearby players same world within 50 blocks
        List<Player> nearby = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getWorld().equals(p.getWorld())) continue;
            if (pl.getUniqueId().equals(p.getUniqueId())) continue;
            if (pl.getLocation().distance(loc) <= 50.0) nearby.add(pl);
        }

        nearby.sort(Comparator.comparingDouble(pl -> pl.getLocation().distance(loc)));

        int size = 9 * ((nearby.size() + 8) / 9);
        if (size == 0) size = 9;
        String name = (lock == null ? "(未ロック)" : lock.getName());
        Inventory inv = Bukkit.createInventory(null, Math.max(size, 9), TITLE_PREFIX + name);

        int slot = 0;
        for (Player pl : nearby) {
            ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            if (meta != null) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(pl.getUniqueId());
                meta.setOwningPlayer(op);
                meta.setDisplayName(pl.getName());
                java.util.List<String> lore = new ArrayList<>();
                if (lock != null && lock.isMember(pl.getUniqueId().toString())) lore.add("§aメンバー");
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        // register context
        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);

        // Lock/unlock button
        int btnSlot = Math.max(inv.getSize() - 1, 0);
        if (lock == null) {
            ItemStack lockBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta meta = lockBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aロックする (自動名付け)");
                java.util.List<String> lore = new ArrayList<>();
                lore.add("チェストをロックしてアクセスを制限します");
                meta.setLore(lore);
                lockBtn.setItemMeta(meta);
            }
            inv.setItem(btnSlot, lockBtn);
        } else {
            ItemStack unlockBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            org.bukkit.inventory.meta.ItemMeta meta = unlockBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cロック解除");
                java.util.List<String> lore = new ArrayList<>();
                lore.add("このチェストのロックを解除します (オーナー/OPのみ)");
                meta.setLore(lore);
                unlockBtn.setItemMeta(meta);
            }
            inv.setItem(btnSlot, unlockBtn);
        }

        // If Bedrock (Floodgate), try SimpleForm — fully manage via forms
        if (org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(p)) {
            // Fully build button list and handlers — if shown, don't open inventory
            boolean shown = openBedrockMainForm(p, nearby, lock, loc, store);
            if (shown) return;
        }

        p.openInventory(inv);
    }

    public static void closeForPlayer(Player p) {
        openLocks.remove(p.getUniqueId());
        openLocations.remove(p.getUniqueId());
    }

    // Bedrock form menus
    private static boolean openBedrockMainForm(Player p, List<Player> nearby, ChestLock lock, Location loc, ChestLockStore store) {
        java.util.List<String> buttons = new ArrayList<>();
        // Lock state and actions
        if (lock == null) {
            buttons.add("🔒 ロックする (自動名)");
        } else {
            // show unlock if owner or op
            boolean ownerOrOp = p.isOp() || (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
            if (ownerOrOp) buttons.add("🔓 ロック解除");
            buttons.add("👥 メンバー管理");
        }

        // Nearby players for adding (only if locked)
        if (lock != null) {
            for (Player pl : nearby) {
                if (pl.getUniqueId().equals(p.getUniqueId())) continue;
                String label = lock.isMember(pl.getUniqueId().toString()) ? "- " + pl.getName() + " (メンバー)" : "+ " + pl.getName();
                buttons.add(label);
            }
        }

        // Close
        buttons.add("閉じる");

        boolean shown = org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil.openSimpleForm(p, TITLE_PREFIX + (lock == null ? "(未ロック)" : lock.getName()), buttons, idx -> {
            if (idx < 0) return;
            int action = idx;
            if (lock == null) {
                if (action == 0) {
                    // create lock
                    ChestLock newLock = new ChestLock(p.getUniqueId().toString(), "lock-" + java.util.UUID.randomUUID().toString().substring(0,6));
                    for (Location l : org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.save(l, newLock);
                    // reopen
                    openForPlayer(p, newLock, loc, store);
                }
                return;
            }

            // if locked, handle unlock/member/players
            boolean ownerOrOp = p.isOp() || (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
            int base = 0;
            if (ownerOrOp) {
                if (action == base) {
                    // unlock
                    for (Location l : org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.remove(l);
                    openForPlayer(p, null, loc, store);
                    return;
                }
                base++;
            }
            // member manage
            if (action == base) {
                // open member list
                java.util.List<String> mbtns = new ArrayList<>();
                for (String m : lock.getMembers()) {
                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(java.util.UUID.fromString(m));
                    mbtns.add(op.getName() == null ? m : op.getName());
                }
                mbtns.add("戻る");
                org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil.openSimpleForm(p, "メンバー管理 - " + lock.getName(), mbtns, midx -> {
                    if (midx < 0) return;
                    if (midx == mbtns.size() - 1) { openForPlayer(p, lock, loc, store); return; }
                    String removeUuid = lock.getMembers().get(midx);
                    lock.removeMember(removeUuid);
                    for (Location l : org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.save(l, lock);
                    openForPlayer(p, lock, loc, store);
                });
                return;
            }
            base++;

            // nearby players: find clicked player index
            int idxPlayers = action - base;
            if (idxPlayers >= 0 && idxPlayers < nearby.size()) {
                Player target = nearby.get(idxPlayers);
                String uid = target.getUniqueId().toString();
                if (lock.isMember(uid)) {
                    lock.removeMember(uid);
                } else {
                    lock.addMember(uid);
                }
                for (Location l : org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.save(l, lock);
                openForPlayer(p, lock, loc, store);
                return;
            }
            // close
        });

        return shown;
    }
}
