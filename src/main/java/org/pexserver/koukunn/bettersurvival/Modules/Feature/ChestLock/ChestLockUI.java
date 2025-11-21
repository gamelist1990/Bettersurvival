package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.FormsUtil;
import org.bukkit.Material;

import java.util.*;

public class ChestLockUI {

    public static final String TITLE_PREFIX = "Chest UI - ";

    private static final Map<UUID, ChestLock> openLocks = new HashMap<>();
    private static final Map<UUID, Location> openLocations = new HashMap<>();

    public static ChestLock getOpenLock(UUID uid) {
        return openLocks.get(uid);
    }

    public static Location getOpenLocation(UUID uid) {
        return openLocations.get(uid);
    }

    public static void openForPlayer(Player p, ChestLock lock, Location loc, ChestLockStore store) {
        List<Player> nearby = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getWorld().equals(p.getWorld()))
                continue;
            if (pl.getUniqueId().equals(p.getUniqueId()))
                continue;
            if (pl.getLocation().distance(loc) <= 50.0)
                nearby.add(pl);
        }

        nearby.sort(Comparator.comparingDouble(pl -> pl.getLocation().distance(loc)));

        int size = 9 * ((nearby.size() + 8) / 9);
        if (size == 0)
            size = 9;
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
                List<String> lore = new ArrayList<>();
                if (lock != null && lock.isMember(pl.getUniqueId().toString()))
                    lore.add("§aメンバー");
                meta.setLore(lore);
                skull.setItemMeta(meta);
            }
            inv.setItem(slot++, skull);
        }

        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);

        int btnSlot = Math.max(inv.getSize() - 1, 0);
        if (lock == null) {
            ItemStack lockBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = lockBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aロックする (自動名付け)");
                List<String> lore = new ArrayList<>();
                lore.add("チェストをロックしてアクセスを制限します");
                meta.setLore(lore);
                lockBtn.setItemMeta(meta);
            }
            inv.setItem(btnSlot, lockBtn);
        } else {
            ItemStack unlockBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta meta = unlockBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§cロック解除");
                List<String> lore = new ArrayList<>();
                lore.add("このチェストのロックを解除します (オーナー/OPのみ)");
                meta.setLore(lore);
                unlockBtn.setItemMeta(meta);
            }
            inv.setItem(btnSlot, unlockBtn);
        }

        if (FloodgateUtil.isBedrock(p)) {
            boolean shown = openBedrockMainForm(p, nearby, lock, loc, store);
            if (shown)
                return;
        }

        p.openInventory(inv);
    }

    public static void closeForPlayer(Player p) {
        openLocks.remove(p.getUniqueId());
        openLocations.remove(p.getUniqueId());
    }

    private static boolean openBedrockMainForm(Player p, List<Player> nearby, ChestLock lock, Location loc,
            ChestLockStore store) {
        List<String> buttons = new ArrayList<>();
        if (lock == null) {
            buttons.add("ロックする (自動名)");
        } else {
            boolean ownerOrOp = p.isOp()
                    || (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
            if (ownerOrOp)
                buttons.add("ロック解除");
            buttons.add("メンバー管理");
        }

        if (lock != null) {
            for (Player pl : nearby) {
                if (pl.getUniqueId().equals(p.getUniqueId()))
                    continue;
                String label = lock.isMember(pl.getUniqueId().toString()) ? "- " + pl.getName() + " (メンバー)"
                        : "+ " + pl.getName();
                buttons.add(label);
            }
        }

        buttons.add("閉じる");

        boolean shown = FormsUtil.openSimpleForm(p, TITLE_PREFIX + (lock == null ? "(未ロック)" : lock.getName()), buttons,
                idx -> {
                    if (idx < 0)
                        return;
                    int action = idx;
                    if (lock == null) {
                        if (action == 0) {
                            ChestLock newLock = new ChestLock(p.getUniqueId().toString(),
                                    "lock-" + UUID.randomUUID().toString().substring(0, 6));
                            for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                                store.save(l, newLock);
                            openForPlayer(p, newLock, loc, store);
                        }
                        return;
                    }

                    boolean ownerOrOp = p.isOp()
                            || (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
                    int base = 0;
                    if (ownerOrOp) {
                        if (action == base) {
                            for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                                store.remove(l);
                            openForPlayer(p, null, loc, store);
                            return;
                        }
                        base++;
                    }
                    if (action == base) {
                        List<String> mbtns = new ArrayList<>();
                        for (String m : lock.getMembers()) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(m));
                            mbtns.add(op.getName() == null ? m : op.getName());
                        }
                        mbtns.add("戻る");
                        FormsUtil.openSimpleForm(p, "メンバー管理 - " + lock.getName(), mbtns, midx -> {
                            if (midx < 0)
                                return;
                            if (midx == mbtns.size() - 1) {
                                openForPlayer(p, lock, loc, store);
                                return;
                            }
                            String removeUuid = lock.getMembers().get(midx);
                            lock.removeMember(removeUuid);
                            for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                                store.save(l, lock);
                            openForPlayer(p, lock, loc, store);
                        });
                        return;
                    }
                    base++;

                    int idxPlayers = action - base;
                    if (idxPlayers >= 0 && idxPlayers < nearby.size()) {
                        Player target = nearby.get(idxPlayers);
                        String uid = target.getUniqueId().toString();
                        if (lock.isMember(uid)) {
                            lock.removeMember(uid);
                        } else {
                            lock.addMember(uid);
                        }
                        for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                            store.save(l, lock);
                        openForPlayer(p, lock, loc, store);
                        return;
                    }
                });

        return shown;
    }
}
