package org.pexserver.koukunn.bettersurvival.Modules;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule.ToggleFeature;

import java.util.Iterator;

public class ToggleListener implements Listener {

    private final ToggleModule module;

    public ToggleListener(ToggleModule module) {
        this.module = module;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        Inventory inv = e.getInventory();
        if (inv == null || e.getView() == null || e.getView().getTitle() == null) return;

        String title = e.getView().getTitle();
        if (!title.startsWith(ToggleModule.TOGGLE_INVENTORY_TITLE)) return;

        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player clicker = (Player) e.getWhoClicked();

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        // map slot -> feature
        int slot = e.getRawSlot();
        int index = 0;
        boolean adminMode = title.contains("(OP)");
        Iterator<ToggleFeature> it = module.getVisibleFeatures(adminMode).iterator();
        while (it.hasNext()) {
            ToggleFeature f = it.next();
            if (index == slot) {
                String display = f.getDisplayName();
                if (adminMode) {
                    boolean current = module.getGlobal(f.getKey());
                    module.setGlobal(f.getKey(), !current);
                    clicker.sendMessage((!current ? "§a『" + display + "』のグローバルを有効にしました" : "§c『" + display + "』のグローバルを無効にしました"));
                } else {
                    boolean current = module.isEnabledFor(clicker.getUniqueId().toString(), f.getKey());
                    module.setEnabledFor(clicker.getUniqueId().toString(), f.getKey(), !current);
                    clicker.sendMessage((!current ? "§a『" + display + "』の機能を有効にしました" : "§c『" + display + "』の機能を無効にしました"));
                }
                // close & reopen to refresh
                clicker.closeInventory();
                module.openToggleUI(clicker, adminMode);
                return;
            }
            index++;
        }
    }
}
