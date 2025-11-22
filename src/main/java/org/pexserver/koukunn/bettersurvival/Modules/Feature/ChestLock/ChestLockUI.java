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

    @SuppressWarnings("deprecation")
    public static void openForPlayer(Player p, ChestLock lock, Location loc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
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
        @SuppressWarnings("deprecation")
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
            // 追加: 保護済みチェスト一覧ボタン
            if (btnSlot - 1 >= 0) {
                ItemStack listBtn = new ItemStack(Material.PAPER);
                ItemMeta lm = listBtn.getItemMeta();
                if (lm != null) {
                    lm.setDisplayName("§e保護済みチェスト一覧");
                    List<String> lore = new ArrayList<>();
                    lore.add("保護されているチェストの数と座標を表示します");
                    lm.setLore(lore);
                    listBtn.setItemMeta(lm);
                }
                inv.setItem(btnSlot - 1, listBtn);
            }
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
            // 追加: 保護済みチェスト一覧ボタン
            if (btnSlot - 1 >= 0) {
                ItemStack listBtn = new ItemStack(Material.PAPER);
                ItemMeta lm = listBtn.getItemMeta();
                if (lm != null) {
                    lm.setDisplayName("§e保護済みチェスト一覧");
                    List<String> lore = new ArrayList<>();
                    lore.add("保護されているチェストの数と座標を表示します");
                    lm.setLore(lore);
                    listBtn.setItemMeta(lm);
                }
                inv.setItem(btnSlot - 1, listBtn);
            }
        }

        if (FloodgateUtil.isBedrock(p)) {
            boolean shown = openBedrockMainForm(p, nearby, lock, loc, store, shopStore);
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
            ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
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

        // nearby プレイヤーの直接追加は削除し、メンバー追加は「メンバー管理」内に移動します

        buttons.add("保護済みチェスト一覧");
        buttons.add("閉じる");

        boolean shown = FormsUtil.openSimpleForm(p, TITLE_PREFIX + (lock == null ? "(未ロック)" : lock.getName()), buttons,
                idx -> {
                    if (idx < 0)
                        return;
                    int action = idx;
                    if (lock == null) {
                        if (action == 0) {
                            // prevent locking if chest (or related) is a shop
                            List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
                            for (Location r : related) {
                                try { if (shopStore != null && shopStore.get(r).isPresent()) { p.sendMessage("§cこのチェストはショップに紐づいているためロックできません"); return; } } catch (Exception ignored) {}
                            }
                            ChestLock newLock = new ChestLock(p.getUniqueId().toString(),
                                    "lock-" + UUID.randomUUID().toString().substring(0, 6));
                            for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                                store.save(l, newLock);
                            openForPlayer(p, newLock, loc, store, shopStore);
                            return;
                        }
                        // handle list button for unlocked view
                        if (action == 1) {
                            Map<String, ChestLock> all = store.getAll();
                            List<String> rows = new ArrayList<>();
                            if (all.isEmpty()) rows.add("なし");
                            else {
                                for (Map.Entry<String, ChestLock> e : all.entrySet()) {
                                    String key = e.getKey();
                                    ChestLock lk = e.getValue();
                                    String ownerName = lk.getOwner();
                                    try { ownerName = Bukkit.getOfflinePlayer(UUID.fromString(lk.getOwner())).getName(); } catch (Exception ex) {}
                                    rows.add(key + " - " + lk.getName() + " (オーナー: " + ownerName + ")");
                                    if (rows.size() >= 50) break;
                                }
                            }
                            rows.add("戻る");
                            FormsUtil.openSimpleForm(p, "保護済みチェスト一覧", rows, ridx -> {
                                if (ridx < 0) return;
                                if (ridx == rows.size() - 1) {
                                    openForPlayer(p, null, loc, store, shopStore);
                                    return;
                                }
                            });
                        }
                        return;
                    }

                    // handle protected list button index (which is always last before close)
                    int listIndex = buttons.size() - 2; // second last is our list
                    if (action == listIndex) {
                        Map<String, ChestLock> all = store.getAll();
                        List<String> rows = new ArrayList<>();
                        if (all.isEmpty()) rows.add("なし");
                        else {
                            for (Map.Entry<String, ChestLock> e : all.entrySet()) {
                                String key = e.getKey();
                                ChestLock lk = e.getValue();
                                String ownerName = lk.getOwner();
                                try { ownerName = Bukkit.getOfflinePlayer(UUID.fromString(lk.getOwner())).getName(); } catch (Exception ex) {}
                                rows.add(key + " - " + lk.getName() + " (オーナー: " + ownerName + ")");
                                if (rows.size() >= 50) break;
                            }
                        }
                        rows.add("戻る");
                        FormsUtil.openSimpleForm(p, "保護済みチェスト一覧", rows, ridx -> {
                            if (ridx < 0) return;
                            if (ridx == rows.size() - 1) {
                                openForPlayer(p, lock, loc, store, shopStore);
                                return;
                            }
                            // nothing on selection
                        });
                        return;
                    }

                    boolean ownerOrOp = p.isOp()
                            || (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString()));
                    int base = 0;
                    if (ownerOrOp) {
                        if (action == base) {
                            for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock()))
                                store.remove(l);
                            openForPlayer(p, null, loc, store, shopStore);
                            return;
                        }
                        base++;
                    }
                    if (action == base) {
                        // メンバー管理のメニューを開く: 追加 / 一覧
                        List<String> mm = new ArrayList<>();
                        mm.add("追加");
                        mm.add("一覧");
                        mm.add("戻る");
                        FormsUtil.openSimpleForm(p, "メンバー管理 - " + lock.getName(), mm, midx -> {
                            if (midx < 0) return;
                            if (midx == 2) { // 戻る
                                openForPlayer(p, lock, loc, store, shopStore);
                                return;
                            }

                            if (midx == 0) { // 追加
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
                                        openForPlayer(p, lock, loc, store, shopStore);
                                        return;
                                    }
                                    Player target = candidates.get(aidx);
                                    String uid = target.getUniqueId().toString();
                                    lock.addMember(uid);
                                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.save(l, lock);
                                    p.sendMessage("§aメンバーに追加しました: " + target.getName());
                                    openForPlayer(p, lock, loc, store, shopStore);
                                });
                                return;
                            }

                            if (midx == 1) { // 一覧
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
                                        openForPlayer(p, lock, loc, store, shopStore);
                                        return;
                                    }
                                    String removeUuid = lock.getMembers().get(remIdx);
                                    lock.removeMember(removeUuid);
                                    for (Location l : ChestLockModule.getChestRelatedLocations(loc.getBlock())) store.save(l, lock);
                                    p.sendMessage("§aメンバーから削除しました");
                                    openForPlayer(p, lock, loc, store, shopStore);
                                });
                                return;
                            }
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
                        openForPlayer(p, lock, loc, store, shopStore);
                        return;
                    }
                });

        return shown;
    }

    private static String sanitizeName(String name) {
        if (name == null) return "";
        // 保守的に英数字とアンダースコアのみ残す
        return name.replaceAll("[^A-Za-z0-9_]", "");
    }
}
