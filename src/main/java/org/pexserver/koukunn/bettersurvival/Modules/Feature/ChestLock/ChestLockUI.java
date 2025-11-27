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
    public static final String LIST_TITLE = "保護済みチェスト一覧";
    public static final String MEMBER_ADD_TITLE = "メンバー追加";
    public static final String MEMBER_REMOVE_TITLE = "メンバー削除";
    public static final String MEMBER_MANAGE_TITLE = "メンバー管理";

    private static final Map<UUID, ChestLock> openLocks = new HashMap<>();
    private static final Map<UUID, Location> openLocations = new HashMap<>();
    private static final Map<UUID, String> openUIType = new HashMap<>();
    private static final Map<UUID, List<String>> listKeys = new HashMap<>();
    private static final Map<UUID, Integer> listPages = new HashMap<>();

    public static ChestLock getOpenLock(UUID uid) {
        return openLocks.get(uid);
    }

    public static Location getOpenLocation(UUID uid) {
        return openLocations.get(uid);
    }

    public static String getOpenUIType(UUID uid) {
        return openUIType.get(uid);
    }

    public static List<String> getListKeys(UUID uid) {
        return listKeys.get(uid);
    }

    public static int getListPage(UUID uid) {
        return listPages.getOrDefault(uid, 0);
    }

    @SuppressWarnings("deprecation")
    public static void openForPlayer(Player p, ChestLock lock, Location loc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        // Bedrockプレイヤーの場合は専用フォームを開く
        if (FloodgateUtil.isBedrock(p)) {
            List<Player> nearby = getNearbyPlayers(p, loc);
            boolean shown = openBedrockMainForm(p, nearby, lock, loc, store, shopStore);
            if (shown)
                return;
        }

        // Java版UI: 27スロットのメインメニュー
        String name = (lock == null ? "(未ロック)" : lock.getName());
        @SuppressWarnings("deprecation")
        Inventory inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + name);

        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);
        openUIType.put(p.getUniqueId(), "main");

        boolean isOwnerOrOp = lock != null && (p.isOp() || 
            (lock.getOwner() != null && lock.getOwner().equals(p.getUniqueId().toString())));

        // ガラス装飾で囲む
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            border.setItemMeta(bm);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        if (lock == null) {
            // 未ロック状態: ロックボタンのみ
            ItemStack lockBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
            ItemMeta meta = lockBtn.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§lロックする");
                List<String> lore = new ArrayList<>();
                lore.add("§7チェストをロックしてアクセスを制限します");
                lore.add("§7クリックで自動名付けでロック");
                meta.setLore(lore);
                lockBtn.setItemMeta(meta);
            }
            inv.setItem(13, lockBtn);
        } else {
            // ロック済み状態: メニューボタン表示

            // スロット11: メンバー管理
            ItemStack memberBtn = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta sm = (SkullMeta) memberBtn.getItemMeta();
            if (sm != null) {
                sm.setDisplayName("§b§lメンバー管理");
                List<String> lore = new ArrayList<>();
                lore.add("§7メンバーの追加・削除を行います");
                lore.add("§7現在のメンバー数: §f" + lock.getMembers().size() + "人");
                sm.setLore(lore);
                memberBtn.setItemMeta(sm);
            }
            inv.setItem(11, memberBtn);

            // スロット13: 情報表示
            ItemStack infoBtn = new ItemStack(Material.BOOK);
            ItemMeta im = infoBtn.getItemMeta();
            if (im != null) {
                im.setDisplayName("§e§l" + lock.getName());
                List<String> lore = new ArrayList<>();
                String ownerName = getPlayerName(lock.getOwner());
                lore.add("§7オーナー: §f" + ownerName);
                lore.add("§7メンバー: §f" + lock.getMembers().size() + "人");
                lore.add("§7座標: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ());
                im.setLore(lore);
                infoBtn.setItemMeta(im);
            }
            inv.setItem(13, infoBtn);

            // スロット15: ロック解除 (オーナー/OPのみ)
            if (isOwnerOrOp) {
                ItemStack unlockBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
                ItemMeta um = unlockBtn.getItemMeta();
                if (um != null) {
                    um.setDisplayName("§c§lロック解除");
                    List<String> lore = new ArrayList<>();
                    lore.add("§7このチェストのロックを解除します");
                    um.setLore(lore);
                    unlockBtn.setItemMeta(um);
                }
                inv.setItem(15, unlockBtn);
            }
        }

        // スロット22: 保護済みチェスト一覧
        ItemStack listBtn = new ItemStack(Material.CHEST);
        ItemMeta lm = listBtn.getItemMeta();
        if (lm != null) {
            lm.setDisplayName("§e§l保護済みチェスト一覧");
            List<String> lore = new ArrayList<>();
            lore.add("§7あなたが保護しているチェストを表示します");
            lm.setLore(lore);
            listBtn.setItemMeta(lm);
        }
        inv.setItem(22, listBtn);

        // スロット26: 閉じる
        ItemStack closeBtn = new ItemStack(Material.BARRIER);
        ItemMeta cm = closeBtn.getItemMeta();
        if (cm != null) {
            cm.setDisplayName("§c閉じる");
            closeBtn.setItemMeta(cm);
        }
        inv.setItem(26, closeBtn);

        p.openInventory(inv);
    }

    /**
     * メンバー管理画面を開く
     */
    public static void openMemberManageUI(Player p, ChestLock lock, Location loc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;

        @SuppressWarnings("deprecation")
        Inventory inv = Bukkit.createInventory(null, 27, MEMBER_MANAGE_TITLE + " - " + lock.getName());

        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);
        openUIType.put(p.getUniqueId(), "member_manage");

        // ガラス装飾
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta bm = border.getItemMeta();
        if (bm != null) {
            bm.setDisplayName(" ");
            border.setItemMeta(bm);
        }
        for (int i = 0; i < 27; i++) {
            inv.setItem(i, border);
        }

        // スロット11: メンバー追加
        ItemStack addBtn = new ItemStack(Material.LIME_STAINED_GLASS_PANE);
        ItemMeta am = addBtn.getItemMeta();
        if (am != null) {
            am.setDisplayName("§a§lメンバー追加");
            List<String> lore = new ArrayList<>();
            lore.add("§7近くのプレイヤーをメンバーに追加します");
            am.setLore(lore);
            addBtn.setItemMeta(am);
        }
        inv.setItem(11, addBtn);

        // スロット13: 現在のメンバー一覧
        ItemStack infoBtn = new ItemStack(Material.BOOK);
        ItemMeta im = infoBtn.getItemMeta();
        if (im != null) {
            im.setDisplayName("§e§l現在のメンバー");
            List<String> lore = new ArrayList<>();
            if (lock.getMembers().isEmpty()) {
                lore.add("§7メンバーはいません");
            } else {
                for (int i = 0; i < Math.min(lock.getMembers().size(), 10); i++) {
                    String name = getPlayerName(lock.getMembers().get(i));
                    lore.add("§f- " + name);
                }
                if (lock.getMembers().size() > 10) {
                    lore.add("§7...他 " + (lock.getMembers().size() - 10) + "人");
                }
            }
            im.setLore(lore);
            infoBtn.setItemMeta(im);
        }
        inv.setItem(13, infoBtn);

        // スロット15: メンバー削除
        ItemStack removeBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
        ItemMeta rm = removeBtn.getItemMeta();
        if (rm != null) {
            rm.setDisplayName("§c§lメンバー削除");
            List<String> lore = new ArrayList<>();
            lore.add("§7登録されているメンバーを削除します");
            rm.setLore(lore);
            removeBtn.setItemMeta(rm);
        }
        inv.setItem(15, removeBtn);

        // スロット22: 戻る
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bkm = backBtn.getItemMeta();
        if (bkm != null) {
            bkm.setDisplayName("§e戻る");
            backBtn.setItemMeta(bkm);
        }
        inv.setItem(22, backBtn);

        p.openInventory(inv);
    }

    /**
     * メンバー追加画面を開く - 近くのプレイヤーをヘッドで表示
     */
    public static void openMemberAddUI(Player p, ChestLock lock, Location loc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;

        List<Player> nearby = getNearbyPlayers(p, loc);
        // メンバーでないプレイヤーのみフィルタ
        List<Player> candidates = new ArrayList<>();
        for (Player pl : nearby) {
            String uuid = pl.getUniqueId().toString();
            if (lock.isMember(uuid)) continue;
            if (lock.getOwner() != null && lock.getOwner().equals(uuid)) continue;
            candidates.add(pl);
        }

        int size = Math.max(27, 9 * ((candidates.size() + 8) / 9 + 1));
        size = Math.min(size, 54);

        @SuppressWarnings("deprecation")
        Inventory inv = Bukkit.createInventory(null, size, MEMBER_ADD_TITLE + " - " + lock.getName());

        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);
        openUIType.put(p.getUniqueId(), "member_add");

        // プレイヤーヘッドを配置
        int slot = 0;
        for (Player pl : candidates) {
            if (slot >= size - 9) break; // 最後の行は操作ボタン用
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(pl);
                meta.setDisplayName("§a" + pl.getName());
                List<String> lore = new ArrayList<>();
                lore.add("§7クリックでメンバーに追加");
                double dist = pl.getLocation().distance(loc);
                lore.add("§7距離: §f" + String.format("%.1f", dist) + "m");
                meta.setLore(lore);
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        // 候補がいない場合のメッセージ
        if (candidates.isEmpty()) {
            ItemStack noPlayer = new ItemStack(Material.BARRIER);
            ItemMeta npm = noPlayer.getItemMeta();
            if (npm != null) {
                npm.setDisplayName("§c追加可能なプレイヤーがいません");
                List<String> lore = new ArrayList<>();
                lore.add("§7近くに他のプレイヤーがいないか、");
                lore.add("§7すでにメンバーに追加済みです");
                npm.setLore(lore);
                noPlayer.setItemMeta(npm);
            }
            inv.setItem(13, noPlayer);
        }

        // 最後の行: 戻るボタン
        int lastRow = size - 9;
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bm = backBtn.getItemMeta();
        if (bm != null) {
            bm.setDisplayName("§e戻る");
            backBtn.setItemMeta(bm);
        }
        inv.setItem(lastRow + 4, backBtn);

        p.openInventory(inv);
    }

    /**
     * メンバー削除画面を開く - 登録済みメンバーをヘッドで表示
     */
    public static void openMemberRemoveUI(Player p, ChestLock lock, Location loc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore) {
        if (lock == null) return;

        List<String> members = lock.getMembers();
        int size = Math.max(27, 9 * ((members.size() + 8) / 9 + 1));
        size = Math.min(size, 54);

        @SuppressWarnings("deprecation")
        Inventory inv = Bukkit.createInventory(null, size, MEMBER_REMOVE_TITLE + " - " + lock.getName());

        openLocks.put(p.getUniqueId(), lock);
        openLocations.put(p.getUniqueId(), loc);
        openUIType.put(p.getUniqueId(), "member_remove");

        // メンバーのヘッドを配置
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
                    List<String> lore = new ArrayList<>();
                    lore.add("§7クリックでメンバーから削除");
                    if (op.isOnline()) {
                        lore.add("§aオンライン");
                    } else {
                        lore.add("§7オフライン");
                    }
                    meta.setLore(lore);
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
            ItemStack noMember = new ItemStack(Material.BARRIER);
            ItemMeta nm = noMember.getItemMeta();
            if (nm != null) {
                nm.setDisplayName("§cメンバーがいません");
                List<String> lore = new ArrayList<>();
                lore.add("§7まずメンバーを追加してください");
                nm.setLore(lore);
                noMember.setItemMeta(nm);
            }
            inv.setItem(13, noMember);
        }

        // 最後の行: 戻るボタン
        int lastRow = size - 9;
        ItemStack backBtn = new ItemStack(Material.ARROW);
        ItemMeta bm = backBtn.getItemMeta();
        if (bm != null) {
            bm.setDisplayName("§e戻る");
            backBtn.setItemMeta(bm);
        }
        inv.setItem(lastRow + 4, backBtn);

        p.openInventory(inv);
    }

    /**
     * 保護済みチェスト一覧を開く - チェストブロックで座標表示
     */
    public static void openProtectedListUI(Player p, Location contextLoc, ChestLockStore store, org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopStore shopStore, int page) {
        Map<String, ChestLock> all = store.getAll();
        String playerUuid = p.getUniqueId().toString();
        
        // プレイヤーがオーナーのチェストのみフィルタ (OPは全て表示)
        List<Map.Entry<String, ChestLock>> filtered = new ArrayList<>();
        for (Map.Entry<String, ChestLock> e : all.entrySet()) {
            if (p.isOp() || (e.getValue().getOwner() != null && e.getValue().getOwner().equals(playerUuid))) {
                filtered.add(e);
            }
        }

        int itemsPerPage = 45; // 最後の行は操作用
        int totalPages = Math.max(1, (filtered.size() + itemsPerPage - 1) / itemsPerPage);
        page = Math.max(0, Math.min(page, totalPages - 1));

        @SuppressWarnings("deprecation")
        Inventory inv = Bukkit.createInventory(null, 54, LIST_TITLE + " (" + (page + 1) + "/" + totalPages + ")");

        openLocations.put(p.getUniqueId(), contextLoc);
        openUIType.put(p.getUniqueId(), "protected_list");
        listPages.put(p.getUniqueId(), page);
        
        // キーリストを保存
        List<String> keys = new ArrayList<>();
        for (Map.Entry<String, ChestLock> e : filtered) {
            keys.add(e.getKey());
        }
        listKeys.put(p.getUniqueId(), keys);

        // チェストアイテムを配置
        int start = page * itemsPerPage;
        int end = Math.min(start + itemsPerPage, filtered.size());
        int slot = 0;
        for (int i = start; i < end; i++) {
            Map.Entry<String, ChestLock> entry = filtered.get(i);
            String key = entry.getKey();
            ChestLock lock = entry.getValue();

            ItemStack chest = new ItemStack(Material.CHEST);
            ItemMeta cm = chest.getItemMeta();
            if (cm != null) {
                cm.setDisplayName("§e" + lock.getName());
                List<String> lore = new ArrayList<>();
                
                // 座標を解析
                String[] parts = key.split(":");
                if (parts.length >= 4) {
                    lore.add("§7ワールド: §f" + parts[0]);
                    lore.add("§7座標: §f" + parts[1] + ", " + parts[2] + ", " + parts[3]);
                }
                
                String ownerName = getPlayerName(lock.getOwner());
                lore.add("§7オーナー: §f" + ownerName);
                lore.add("§7メンバー: §f" + lock.getMembers().size() + "人");
                lore.add("");
                cm.setLore(lore);
                chest.setItemMeta(cm);
            }
            inv.setItem(slot++, chest);
        }

        // チェストがない場合
        if (filtered.isEmpty()) {
            ItemStack noChest = new ItemStack(Material.BARRIER);
            ItemMeta nm = noChest.getItemMeta();
            if (nm != null) {
                nm.setDisplayName("§c保護済みチェストがありません");
                noChest.setItemMeta(nm);
            }
            inv.setItem(22, noChest);
        }

        // 最後の行: ページング
        // 前のページ
        if (page > 0) {
            ItemStack prevBtn = new ItemStack(Material.ARROW);
            ItemMeta pm = prevBtn.getItemMeta();
            if (pm != null) {
                pm.setDisplayName("§e前のページ");
                prevBtn.setItemMeta(pm);
            }
            inv.setItem(45, prevBtn);
        }

        // 戻る
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta bm = backBtn.getItemMeta();
        if (bm != null) {
            bm.setDisplayName("§c戻る");
            backBtn.setItemMeta(bm);
        }
        inv.setItem(49, backBtn);

        // 次のページ
        if (page < totalPages - 1) {
            ItemStack nextBtn = new ItemStack(Material.ARROW);
            ItemMeta nm = nextBtn.getItemMeta();
            if (nm != null) {
                nm.setDisplayName("§e次のページ");
                nextBtn.setItemMeta(nm);
            }
            inv.setItem(53, nextBtn);
        }

        p.openInventory(inv);
    }

    private static List<Player> getNearbyPlayers(Player p, Location loc) {
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

    public static void closeForPlayer(Player p) {
        openLocks.remove(p.getUniqueId());
        openLocations.remove(p.getUniqueId());
        openUIType.remove(p.getUniqueId());
        listKeys.remove(p.getUniqueId());
        listPages.remove(p.getUniqueId());
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
