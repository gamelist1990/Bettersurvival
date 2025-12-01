package org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI.InvseeHolder;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI.InvseeUIType;

import java.util.List;

/**
 * InvSee イベントリスナー
 * 
 * UIのクリックイベント処理とアイテム同期を担当
 * オンラインプレイヤーの場合はリアルタイム同期
 * オフラインプレイヤーの場合はUI閉じる時に保存
 */
public class InvseeListener implements Listener {

    private final Loader plugin;

    public InvseeListener(Loader plugin) {
        this.plugin = plugin;
    }

    // ========== プレイヤーログイン・ログアウトイベント ==========

    /**
     * プレイヤーがログアウトした時、そのプレイヤーを見ているInvSee UIを閉じる
     */
    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        
        // 全オンラインプレイヤーをチェック
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(quittingPlayer)) continue;
            
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            InvseeHolder holder = InvseeUI.getHolder(openInv);
            
            if (holder != null && holder.getTargetPlayer() != null) {
                // 閲覧対象がログアウトしたプレイヤーの場合、UIを閉じる
                if (holder.getTargetPlayer().getUniqueId().equals(quittingPlayer.getUniqueId())) {
                    // オンラインの変更を保存してから閉じる
                    InvseeUIType uiType = holder.getUIType();
                    if (uiType == InvseeUIType.MAIN_INVENTORY) {
                        saveMainInventory(openInv, quittingPlayer);
                    } else if (uiType == InvseeUIType.EQUIPMENT) {
                        saveEquipment(openInv, quittingPlayer);
                    }
                    // エンダーチェストはオンラインの場合直接開いているので保存不要
                    
                    viewer.closeInventory();
                    viewer.sendMessage("§c[InvSee] §f" + quittingPlayer.getName() + " §7がログアウトしたためUIを閉じました");
                }
            }
        }
    }

    /**
     * プレイヤーがログインした時、そのプレイヤーのオフラインデータを見ているUIを閉じる
     * （オンラインに変わったためデータ不整合を防ぐ）
     */
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        
        // 1tick後に実行（プレイヤー完全参加後）
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(joiningPlayer)) continue;
                
                Inventory openInv = viewer.getOpenInventory().getTopInventory();
                InvseeHolder holder = InvseeUI.getHolder(openInv);
                
                if (holder != null && holder.getTargetPlayer() != null) {
                    // 閲覧対象がログインしたプレイヤーの場合、UIを閉じる
                    if (holder.getTargetPlayer().getUniqueId().equals(joiningPlayer.getUniqueId())) {
                        viewer.closeInventory();
                        viewer.sendMessage("§a[InvSee] §f" + joiningPlayer.getName() + " §7がログインしました。UIを再度開いてください");
                    }
                }
            }
        }, 1L);
    }

    // ========== インベントリイベント ==========

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory inv = event.getInventory();
        InvseeHolder holder = InvseeUI.getHolder(inv);
        if (holder == null) return;

        Player viewer = (Player) event.getWhoClicked();
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();

        InvseeUIType uiType = holder.getUIType();

        switch (uiType) {
            case PLAYER_SELECT:
                handlePlayerSelectClick(event, holder, viewer, slot, clicked);
                break;
            case MAIN_INVENTORY:
                handleMainInventoryClick(event, holder, viewer, slot, clicked);
                break;
            case EQUIPMENT:
                handleEquipmentClick(event, holder, viewer, slot, clicked);
                break;
            case ENDERCHEST:
                handleEnderchestClick(event, holder, viewer, slot, clicked);
                break;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        Inventory inv = event.getInventory();
        InvseeHolder holder = InvseeUI.getHolder(inv);
        if (holder == null) return;

        InvseeUIType uiType = holder.getUIType();
        OfflinePlayer target = holder.getTargetPlayer();

        // プレイヤー選択画面ではドラッグ禁止
        if (uiType == InvseeUIType.PLAYER_SELECT) {
            event.setCancelled(true);
            return;
        }

        // インベントリ/装備/エンダーチェスト画面でのドラッグ
        // ナビゲーション行へのドラッグは禁止
        for (int slot : event.getRawSlots()) {
            if (isNavigationSlot(uiType, slot, inv.getSize())) {
                event.setCancelled(true);
                return;
            }
        }
        
        // オンラインプレイヤーへのリアルタイム同期
        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                syncToOnlinePlayer(inv, target, uiType);
            });
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        Inventory inv = event.getInventory();
        InvseeHolder holder = InvseeUI.getHolder(inv);
        if (holder == null) return;

        OfflinePlayer target = holder.getTargetPlayer();
        InvseeUIType uiType = holder.getUIType();

        // オフラインプレイヤーの場合のみ保存（オンラインは既にリアルタイム同期済み）
        if (target != null && !target.isOnline()) {
            switch (uiType) {
                case MAIN_INVENTORY:
                    saveMainInventory(inv, target);
                    break;
                case EQUIPMENT:
                    saveEquipment(inv, target);
                    break;
                case ENDERCHEST:
                    saveEnderchest(inv, target);
                    break;
                default:
                    break;
            }
        }
    }

    // ========== プレイヤー選択画面 ==========

    private void handlePlayerSelectClick(InventoryClickEvent event, InvseeHolder holder, 
                                          Player viewer, int slot, ItemStack clicked) {
        event.setCancelled(true);
        
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;

        int page = holder.getPage();
        List<OfflinePlayer> playerList = holder.getPlayerList();

        // 閉じるボタン
        if (slot == 49) {
            viewer.closeInventory();
            return;
        }

        // 前のページ
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InvseeUI.openPlayerSelectUI(viewer, plugin, page - 1);
            });
            return;
        }

        // 次のページ
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InvseeUI.openPlayerSelectUI(viewer, plugin, page + 1);
            });
            return;
        }

        // プレイヤーヘッドをクリック
        if (slot < 45 && clicked.getType() == Material.PLAYER_HEAD) {
            int index = page * 45 + slot;
            if (index >= 0 && index < playerList.size()) {
                OfflinePlayer target = playerList.get(index);
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openInventoryUI(viewer, target, plugin);
                });
            }
        }
    }

    // ========== メインインベントリ画面 ==========

    private void handleMainInventoryClick(InventoryClickEvent event, InvseeHolder holder,
                                           Player viewer, int slot, ItemStack clicked) {
        OfflinePlayer target = holder.getTargetPlayer();
        int invSize = event.getInventory().getSize();

        // ナビゲーション行（スロット36-53）のクリック処理
        if (slot >= 36 && slot < invSize) {
            event.setCancelled(true);

            if (clicked == null || clicked.getType() == Material.AIR) return;
            if (clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
            if (clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;

            // 戻る（スロット45）
            if (slot == 45) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openPlayerSelectUI(viewer, plugin, 0);
                });
                return;
            }

            // 装備スロット（スロット47）
            if (slot == 47) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openEquipmentUI(viewer, target, plugin);
                });
                return;
            }

            // エンダーチェスト（スロット51）
            if (slot == 51) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openEnderchestUI(viewer, target, plugin);
                });
                return;
            }

            // 閉じる（スロット53）
            if (slot == 53) {
                viewer.closeInventory();
                return;
            }

            return;
        }

        // メインインベントリ領域（スロット0-35）はアイテム操作を許可
        // オンラインプレイヤーの場合、クリック後にリアルタイム同期
        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                syncToOnlinePlayer(event.getInventory(), target, InvseeUIType.MAIN_INVENTORY);
            });
        }
    }

    // ========== 装備画面 ==========

    private void handleEquipmentClick(InventoryClickEvent event, InvseeHolder holder,
                                       Player viewer, int slot, ItemStack clicked) {
        OfflinePlayer target = holder.getTargetPlayer();

        // 装備スロット以外はキャンセル
        boolean isEquipmentSlot = (slot == 10 || slot == 11 || slot == 12 || slot == 13 || slot == 15);
        boolean isBackButton = slot == 22;

        if (!isEquipmentSlot && slot < 27) {
            event.setCancelled(true);

            // 戻るボタン
            if (isBackButton && clicked != null && clicked.getType() == Material.ARROW) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openInventoryUI(viewer, target, plugin);
                });
            }
            return;
        }

        // 装備スロットはアイテム操作を許可
        // ただしプレースホルダーアイテム（GLASS_PANE）の場合は特別処理
        if (isEquipmentSlot && clicked != null && clicked.getType() == Material.GLASS_PANE) {
            // プレースホルダーを削除して空きスロットとして扱う
            event.getInventory().setItem(slot, null);
        }

        // オンラインプレイヤーの場合、クリック後にリアルタイム同期
        if (isEquipmentSlot && target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                syncToOnlinePlayer(event.getInventory(), target, InvseeUIType.EQUIPMENT);
            });
        }
    }

    // ========== エンダーチェスト画面 ==========

    private void handleEnderchestClick(InventoryClickEvent event, InvseeHolder holder,
                                        Player viewer, int slot, ItemStack clicked) {
        OfflinePlayer target = holder.getTargetPlayer();

        // ナビゲーション行（スロット27-35）
        if (slot >= 27 && slot < 36) {
            event.setCancelled(true);

            if (clicked == null) return;
            
            // 戻るボタン（スロット31）
            if (slot == 31 && clicked.getType() == Material.ARROW) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> {
                    InvseeUI.openInventoryUI(viewer, target, plugin);
                });
            }
            return;
        }

        // エンダーチェスト領域（スロット0-26）はアイテム操作を許可
    }

    // ========== 保存処理 ==========

    private void saveMainInventory(Inventory inv, OfflinePlayer target) {
        ItemStack[] contents = new ItemStack[36];
        
        // UIスロットからMinecraftスロットへ変換
        for (int i = 0; i < 36; i++) {
            int uiSlot;
            if (i < 9) {
                uiSlot = i + 27; // ホットバー
            } else {
                uiSlot = i - 9;  // メインインベ
            }
            
            ItemStack item = inv.getItem(uiSlot);
            if (item != null && item.getType() != Material.AIR) {
                // ナビゲーションアイテムは除外
                if (item.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                    item.getType() == Material.GRAY_STAINED_GLASS_PANE) {
                    continue;
                }
                contents[i] = item.clone();
            }
        }

        InvseeUI.setPlayerInventoryContents(target, contents);
    }

    private void saveEquipment(Inventory inv, OfflinePlayer target) {
        ItemStack[] armor = new ItemStack[4];
        
        // スロット10: ヘルメット (index 3)
        ItemStack helmet = inv.getItem(10);
        if (helmet != null && helmet.getType() != Material.GLASS_PANE && 
            helmet.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            armor[3] = helmet.clone();
        }
        
        // スロット11: チェストプレート (index 2)
        ItemStack chestplate = inv.getItem(11);
        if (chestplate != null && chestplate.getType() != Material.GLASS_PANE && 
            chestplate.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            armor[2] = chestplate.clone();
        }
        
        // スロット12: レギンス (index 1)
        ItemStack leggings = inv.getItem(12);
        if (leggings != null && leggings.getType() != Material.GLASS_PANE && 
            leggings.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            armor[1] = leggings.clone();
        }
        
        // スロット13: ブーツ (index 0)
        ItemStack boots = inv.getItem(13);
        if (boots != null && boots.getType() != Material.GLASS_PANE && 
            boots.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            armor[0] = boots.clone();
        }

        InvseeUI.setPlayerArmorContents(target, armor);

        // オフハンド（スロット15）
        ItemStack offhand = inv.getItem(15);
        if (offhand != null && offhand.getType() != Material.GLASS_PANE && 
            offhand.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            InvseeUI.setPlayerOffhand(target, offhand.clone());
        } else {
            InvseeUI.setPlayerOffhand(target, null);
        }
    }

    private void saveEnderchest(Inventory inv, OfflinePlayer target) {
        ItemStack[] contents = new ItemStack[27];
        
        for (int i = 0; i < 27; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && item.getType() != Material.AIR) {
                contents[i] = item.clone();
            }
        }

        InvseeUI.setPlayerEnderchestContents(target, contents);
    }

    // ========== ユーティリティ ==========

    private boolean isNavigationSlot(InvseeUIType uiType, int slot, int invSize) {
        switch (uiType) {
            case PLAYER_SELECT:
                return slot >= 45;
            case MAIN_INVENTORY:
                return slot >= 36;
            case EQUIPMENT:
                return slot != 10 && slot != 11 && slot != 12 && slot != 13 && slot != 15;
            case ENDERCHEST:
                return slot >= 27;
            default:
                return false;
        }
    }

    /**
     * オンラインプレイヤーにUIの変更をリアルタイムで同期する
     */
    private void syncToOnlinePlayer(Inventory inv, OfflinePlayer target, InvseeUIType uiType) {
        if (target == null || !target.isOnline()) return;
        
        Player player = target.getPlayer();
        if (player == null) return;

        switch (uiType) {
            case MAIN_INVENTORY:
                syncMainInventoryToPlayer(inv, player);
                break;
            case EQUIPMENT:
                syncEquipmentToPlayer(inv, player);
                break;
            case ENDERCHEST:
                // エンダーチェストはオンラインの場合、直接開いているので同期不要
                break;
            default:
                break;
        }
    }

    /**
     * メインインベントリをオンラインプレイヤーに同期
     */
    private void syncMainInventoryToPlayer(Inventory inv, Player player) {
        PlayerInventory playerInv = player.getInventory();
        
        // UIスロットからプレイヤーインベントリへ変換
        for (int i = 0; i < 36; i++) {
            int uiSlot;
            if (i < 9) {
                uiSlot = i + 27; // ホットバー：UIの27-35 → プレイヤーの0-8
            } else {
                uiSlot = i - 9;  // メインインベ：UIの0-26 → プレイヤーの9-35
            }
            
            ItemStack item = inv.getItem(uiSlot);
            
            // ナビゲーションアイテムは除外
            if (item != null && (item.getType() == Material.BLACK_STAINED_GLASS_PANE ||
                item.getType() == Material.GRAY_STAINED_GLASS_PANE)) {
                continue;
            }
            
            playerInv.setItem(i, item);
        }
        
        player.updateInventory();
    }

    /**
     * 装備をオンラインプレイヤーに同期
     */
    private void syncEquipmentToPlayer(Inventory inv, Player player) {
        PlayerInventory playerInv = player.getInventory();
        
        // ヘルメット (スロット10)
        ItemStack helmet = inv.getItem(10);
        if (helmet != null && helmet.getType() != Material.GLASS_PANE && 
            helmet.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            playerInv.setHelmet(helmet);
        } else {
            playerInv.setHelmet(null);
        }
        
        // チェストプレート (スロット11)
        ItemStack chestplate = inv.getItem(11);
        if (chestplate != null && chestplate.getType() != Material.GLASS_PANE && 
            chestplate.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            playerInv.setChestplate(chestplate);
        } else {
            playerInv.setChestplate(null);
        }
        
        // レギンス (スロット12)
        ItemStack leggings = inv.getItem(12);
        if (leggings != null && leggings.getType() != Material.GLASS_PANE && 
            leggings.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            playerInv.setLeggings(leggings);
        } else {
            playerInv.setLeggings(null);
        }
        
        // ブーツ (スロット13)
        ItemStack boots = inv.getItem(13);
        if (boots != null && boots.getType() != Material.GLASS_PANE && 
            boots.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            playerInv.setBoots(boots);
        } else {
            playerInv.setBoots(null);
        }
        
        // オフハンド (スロット15)
        ItemStack offhand = inv.getItem(15);
        if (offhand != null && offhand.getType() != Material.GLASS_PANE && 
            offhand.getType() != Material.GRAY_STAINED_GLASS_PANE) {
            playerInv.setItemInOffHand(offhand);
        } else {
            playerInv.setItemInOffHand(null);
        }
        
        player.updateInventory();
    }
}
