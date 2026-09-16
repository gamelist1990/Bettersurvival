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
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI.InvseeHolder;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI.InvseeUIType;

import java.util.List;

/** InvSee UI と Otherworld スコープ別オフライン編集の同期を担当する。 */
public class InvseeListener implements Listener {
    private final Loader plugin;

    public InvseeListener(Loader plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldChangeBeforeSwap(PlayerChangedWorldEvent event) {
        if (plugin.getOtherworldModule() == null) return;
        String source = plugin.getOtherworldModule().getGroup(event.getFrom());
        String target = plugin.getOtherworldModule().getGroup(event.getPlayer().getWorld());
        if (!source.equals(target)) InvseeOfflineData.saveSnapshot(event.getPlayer(), source);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChangeAfterSwap(PlayerChangedWorldEvent event) {
        if (plugin.getOtherworldModule() == null) return;
        String source = plugin.getOtherworldModule().getGroup(event.getFrom());
        String target = plugin.getOtherworldModule().getGroup(event.getPlayer().getWorld());
        if (source.equals(target)) return;
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTask(plugin, () -> {
            InvseeOfflineData.applyPendingEdits(player, target);
            InvseeOfflineData.saveSnapshot(player, target);
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player quittingPlayer = event.getPlayer();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(quittingPlayer)) continue;
            Inventory openInv = viewer.getOpenInventory().getTopInventory();
            InvseeHolder holder = InvseeUI.getHolder(openInv);
            if (holder == null || holder.getTargetPlayer() == null
                    || !holder.getTargetPlayer().getUniqueId().equals(quittingPlayer.getUniqueId())) continue;
            if (holder.getUIType() == InvseeUIType.MAIN_INVENTORY) {
                saveMainInventory(openInv, quittingPlayer, holder.getScope());
            } else if (holder.getUIType() == InvseeUIType.EQUIPMENT) {
                saveEquipment(openInv, quittingPlayer, holder.getScope());
            }
            viewer.closeInventory();
            viewer.sendMessage("§c[InvSee] §f" + quittingPlayer.getName() + " §7がログアウトしたためUIを閉じました");
        }
        InvseeOfflineData.saveSnapshot(quittingPlayer);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player joiningPlayer = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            InvseeOfflineData.applyPendingEdits(joiningPlayer);
            InvseeOfflineData.saveSnapshot(joiningPlayer);
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (viewer.equals(joiningPlayer)) continue;
                InvseeHolder holder = InvseeUI.getHolder(viewer.getOpenInventory().getTopInventory());
                if (holder != null && holder.getTargetPlayer() != null
                        && holder.getTargetPlayer().getUniqueId().equals(joiningPlayer.getUniqueId())) {
                    viewer.closeInventory();
                    viewer.sendMessage("§a[InvSee] §f" + joiningPlayer.getName() + " §7がログインしました。UIを再度開いてください");
                }
            }
        }, 1L);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        InvseeHolder holder = InvseeUI.getHolder(event.getInventory());
        if (holder == null || !(event.getWhoClicked() instanceof Player viewer)) return;
        int slot = event.getRawSlot();
        ItemStack clicked = event.getCurrentItem();
        switch (holder.getUIType()) {
            case PLAYER_SELECT -> handlePlayerSelectClick(event, holder, viewer, slot, clicked);
            case MAIN_INVENTORY -> handleMainInventoryClick(event, holder, viewer, slot, clicked);
            case EQUIPMENT -> handleEquipmentClick(event, holder, viewer, slot, clicked);
            case ENDERCHEST -> handleEnderchestClick(event, holder, viewer, slot, clicked);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        InvseeHolder holder = InvseeUI.getHolder(event.getInventory());
        if (holder == null) return;
        InvseeUIType uiType = holder.getUIType();
        if (uiType == InvseeUIType.PLAYER_SELECT) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (isNavigationSlot(uiType, slot)) {
                event.setCancelled(true);
                return;
            }
        }
        OfflinePlayer target = holder.getTargetPlayer();
        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> syncToOnlinePlayer(event.getInventory(), target, uiType));
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        InvseeHolder holder = InvseeUI.getHolder(event.getInventory());
        if (holder == null) return;
        OfflinePlayer target = holder.getTargetPlayer();
        if (target == null || target.isOnline()) return;
        switch (holder.getUIType()) {
            case MAIN_INVENTORY -> saveMainInventory(event.getInventory(), target, holder.getScope());
            case EQUIPMENT -> saveEquipment(event.getInventory(), target, holder.getScope());
            case ENDERCHEST -> saveEnderchest(event.getInventory(), target, holder.getScope());
            default -> { }
        }
    }

    private void handlePlayerSelectClick(InventoryClickEvent event, InvseeHolder holder,
                                         Player viewer, int slot, ItemStack clicked) {
        event.setCancelled(true);
        if (clicked == null || clicked.getType().isAir() || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE) return;
        if (slot == 49) {
            viewer.closeInventory();
            return;
        }
        if (slot == 45 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openPlayerSelectUI(viewer, plugin, holder.getPage() - 1));
            return;
        }
        if (slot == 53 && clicked.getType() == Material.ARROW) {
            Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openPlayerSelectUI(viewer, plugin, holder.getPage() + 1));
            return;
        }
        if (slot >= 45 || clicked.getType() != Material.PLAYER_HEAD) return;
        List<OfflinePlayer> playerList = holder.getPlayerList();
        int index = holder.getPage() * 45 + slot;
        if (playerList == null || index < 0 || index >= playerList.size()) return;
        OfflinePlayer target = playerList.get(index);
        viewer.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openInventoryUI(viewer, target, plugin));
    }

    private void handleMainInventoryClick(InventoryClickEvent event, InvseeHolder holder,
                                          Player viewer, int slot, ItemStack clicked) {
        OfflinePlayer target = holder.getTargetPlayer();
        if (slot >= 36 && slot < event.getInventory().getSize()) {
            event.setCancelled(true);
            if (clicked == null || clicked.getType().isAir()
                    || clicked.getType() == Material.GRAY_STAINED_GLASS_PANE
                    || clicked.getType() == Material.BLACK_STAINED_GLASS_PANE) return;
            switch (slot) {
                case 45 -> { viewer.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openPlayerSelectUI(viewer, plugin, 0)); }
                case 47 -> { viewer.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openEquipmentUI(viewer, target, plugin)); }
                case 51 -> { viewer.closeInventory(); Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openEnderchestUI(viewer, target, plugin)); }
                case 53 -> viewer.closeInventory();
                default -> { }
            }
            return;
        }
        if (target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> syncToOnlinePlayer(event.getInventory(), target, InvseeUIType.MAIN_INVENTORY));
        }
    }

    private void handleEquipmentClick(InventoryClickEvent event, InvseeHolder holder,
                                      Player viewer, int slot, ItemStack clicked) {
        OfflinePlayer target = holder.getTargetPlayer();
        boolean equipmentSlot = slot == 10 || slot == 11 || slot == 12 || slot == 13 || slot == 15;
        if (!equipmentSlot && slot < 27) {
            event.setCancelled(true);
            if (slot == 22 && clicked != null && clicked.getType() == Material.ARROW) {
                viewer.closeInventory();
                Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openInventoryUI(viewer, target, plugin));
            }
            return;
        }
        if (equipmentSlot && clicked != null && clicked.getType() == Material.GLASS_PANE) event.getInventory().setItem(slot, null);
        if (equipmentSlot && target != null && target.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> syncToOnlinePlayer(event.getInventory(), target, InvseeUIType.EQUIPMENT));
        }
    }

    private void handleEnderchestClick(InventoryClickEvent event, InvseeHolder holder,
                                       Player viewer, int slot, ItemStack clicked) {
        if (slot < 27 || slot >= 36) return;
        event.setCancelled(true);
        if (slot == 31 && clicked != null && clicked.getType() == Material.ARROW) {
            OfflinePlayer target = holder.getTargetPlayer();
            viewer.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openInventoryUI(viewer, target, plugin));
        }
    }

    private void saveMainInventory(Inventory inv, OfflinePlayer target, String scope) {
        ItemStack[] contents = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            int uiSlot = i < 9 ? i + 27 : i - 9;
            ItemStack item = inv.getItem(uiSlot);
            if (item == null || item.getType().isAir()
                    || item.getType() == Material.BLACK_STAINED_GLASS_PANE
                    || item.getType() == Material.GRAY_STAINED_GLASS_PANE) continue;
            contents[i] = item.clone();
        }
        InvseeUI.setPlayerInventoryContents(target, scope, contents);
    }

    private void saveEquipment(Inventory inv, OfflinePlayer target, String scope) {
        ItemStack[] armor = new ItemStack[4];
        armor[3] = usableEquipment(inv.getItem(10));
        armor[2] = usableEquipment(inv.getItem(11));
        armor[1] = usableEquipment(inv.getItem(12));
        armor[0] = usableEquipment(inv.getItem(13));
        InvseeUI.setPlayerArmorContents(target, scope, armor);
        InvseeUI.setPlayerOffhand(target, scope, usableEquipment(inv.getItem(15)));
    }

    private ItemStack usableEquipment(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getType() == Material.GLASS_PANE
                || item.getType() == Material.GRAY_STAINED_GLASS_PANE) return null;
        return item.clone();
    }

    private void saveEnderchest(Inventory inv, OfflinePlayer target, String scope) {
        ItemStack[] contents = new ItemStack[27];
        for (int i = 0; i < 27; i++) {
            ItemStack item = inv.getItem(i);
            if (item != null && !item.getType().isAir()) contents[i] = item.clone();
        }
        InvseeUI.setPlayerEnderchestContents(target, scope, contents);
    }

    private boolean isNavigationSlot(InvseeUIType uiType, int slot) {
        return switch (uiType) {
            case PLAYER_SELECT -> slot >= 45;
            case MAIN_INVENTORY -> slot >= 36;
            case EQUIPMENT -> slot != 10 && slot != 11 && slot != 12 && slot != 13 && slot != 15;
            case ENDERCHEST -> slot >= 27;
        };
    }

    private void syncToOnlinePlayer(Inventory inv, OfflinePlayer target, InvseeUIType uiType) {
        if (target == null || !target.isOnline()) return;
        Player player = target.getPlayer();
        if (player == null) return;
        if (uiType == InvseeUIType.MAIN_INVENTORY) syncMainInventoryToPlayer(inv, player);
        else if (uiType == InvseeUIType.EQUIPMENT) syncEquipmentToPlayer(inv, player);
    }

    private void syncMainInventoryToPlayer(Inventory inv, Player player) {
        PlayerInventory playerInv = player.getInventory();
        for (int i = 0; i < 36; i++) {
            int uiSlot = i < 9 ? i + 27 : i - 9;
            ItemStack item = inv.getItem(uiSlot);
            if (item != null && (item.getType() == Material.BLACK_STAINED_GLASS_PANE
                    || item.getType() == Material.GRAY_STAINED_GLASS_PANE)) continue;
            playerInv.setItem(i, item);
        }
        player.updateInventory();
    }

    private void syncEquipmentToPlayer(Inventory inv, Player player) {
        PlayerInventory playerInv = player.getInventory();
        playerInv.setHelmet(usableEquipment(inv.getItem(10)));
        playerInv.setChestplate(usableEquipment(inv.getItem(11)));
        playerInv.setLeggings(usableEquipment(inv.getItem(12)));
        playerInv.setBoots(usableEquipment(inv.getItem(13)));
        playerInv.setItemInOffHand(usableEquipment(inv.getItem(15)));
        player.updateInventory();
    }
}
