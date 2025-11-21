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

        p.openInventory(inv);
    }

    public static void closeForPlayer(Player p) {
        openLocks.remove(p.getUniqueId());
        openLocations.remove(p.getUniqueId());
    }
}
