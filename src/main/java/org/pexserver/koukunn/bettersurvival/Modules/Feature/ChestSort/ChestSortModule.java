package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.*;

/**
 * ChestSort: スニークしながら木の棒でチェスト等を右クリックすると整理します。
 */
public class ChestSortModule implements Listener {

    private final ToggleModule toggle;

    public ChestSortModule(ToggleModule toggle) {
        this.toggle = toggle;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();

        if (!p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (e.getItem().getType() != Material.STICK) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        if (!(clicked.getState() instanceof InventoryHolder)) return;
        InventoryHolder holder = (InventoryHolder) clicked.getState();
        Inventory inv = holder.getInventory();

        String key = "chestsort";

        if (!toggle.getGlobal(key)) return;
        if (!toggle.isEnabledFor(p.getUniqueId().toString(), key)) return;

        e.setCancelled(true);
        List<ItemStack> before = new ArrayList<>();
        for (ItemStack it : inv.getContents()) {
            before.add(it);
        }

        sortInventory(inv);

        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 1.2f);
        p.sendMessage("§aチェストを整理しました");
    }

    private void sortInventory(Inventory inv) {
        ItemStack[] old = inv.getContents();
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack it : old) {
            if (it == null) continue;
            if (it.getType() == Material.AIR) continue;
            items.add(it.clone());
        }

        // ソート: マテリアル名 → カスタム名 → 耐久 → エンチャント量
        Comparator<ItemStack> cmp = (a, b) -> {
            int r = a.getType().name().compareTo(b.getType().name());
            if (r != 0) return r;
            String aname = (a.hasItemMeta() && a.getItemMeta().hasDisplayName()) ? a.getItemMeta().getDisplayName() : null;
            String bname = (b.hasItemMeta() && b.getItemMeta().hasDisplayName()) ? b.getItemMeta().getDisplayName() : null;
            if (aname == null && bname != null) return 1;
            if (aname != null && bname == null) return -1;
            if (aname != null) {
                r = aname.compareTo(bname);
                if (r != 0) return r;
            }
            r = Short.compare(a.getDurability(), b.getDurability());
            if (r != 0) return r;
            int ae = a.getEnchantments().size();
            int be = b.getEnchantments().size();
            return Integer.compare(be, ae); 
        };

        items.sort(cmp);

        // マージ (同一種/耐久/エンチャントなら合算可能)
        List<ItemStack> merged = new ArrayList<>();
        for (ItemStack it : items) {
            boolean mergedFlag = false;
            for (ItemStack m : merged) {
                if (canMerge(m, it)) {
                    int space = m.getMaxStackSize() - m.getAmount();
                    if (space <= 0) continue;
                    int toMove = Math.min(space, it.getAmount());
                    m.setAmount(m.getAmount() + toMove);
                    it.setAmount(it.getAmount() - toMove);
                    if (it.getAmount() <= 0) {
                        mergedFlag = true;
                        break;
                    }
                }
            }
            if (!mergedFlag) {
                merged.add(it.clone());
            }
        }

        // クリア & 再投入
        inv.clear();
        int i = 0;
        for (ItemStack it : merged) {
            if (i >= inv.getSize()) break;
            inv.setItem(i++, it);
        }
    }

    private boolean canMerge(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (a.getDurability() != b.getDurability()) return false;
        if (a.getEnchantments().size() != b.getEnchantments().size()) return false;
        if (a.hasItemMeta() && b.hasItemMeta()) {
            if (a.getItemMeta().hasDisplayName() || b.getItemMeta().hasDisplayName()) {
                String an = a.getItemMeta().hasDisplayName() ? a.getItemMeta().getDisplayName() : null;
                String bn = b.getItemMeta().hasDisplayName() ? b.getItemMeta().getDisplayName() : null;
                if (!Objects.equals(an, bn)) return false;
            }
        }
        if (!a.getEnchantments().equals(b.getEnchantments())) return false;
        return true;
    }
}
