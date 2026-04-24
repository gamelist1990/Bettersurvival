package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestSort;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop.ChestShopModule;

import java.util.*;

/**
 * ChestSort: スニークしながら木の棒でチェスト等を右クリックすると整理します。
 */
@SuppressWarnings("deprecation")
public class ChestSortModule implements Listener {

    private final ToggleModule toggle;
    private final ChestLockModule chestLock;
    private final ChestShopModule chestShop;

    public ChestSortModule(ToggleModule toggle, ChestLockModule chestLock, ChestShopModule chestShop) {
        this.toggle = toggle;
        this.chestLock = chestLock;
        this.chestShop = chestShop;
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = e.getPlayer();

        if (!p.isSneaking()) return;
        if (e.getItem() == null) return;
        if (e.getItem().getType() != Material.STICK) return;
        // Only trigger if the stick is explicitly named to indicate sorting (exactly "sort")
        boolean stickTrigger = false;
        try {
            if (e.getItem().hasItemMeta() && e.getItem().getItemMeta().hasDisplayName()) {
                String d = e.getItem().getItemMeta().getDisplayName();
                if (d != null) {
                    String low = d.toLowerCase();
                    if (low.equals("sort") || low.equals("整理") || low.equals("整列")) {
                        stickTrigger = true;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        if (!stickTrigger) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        if (!(clicked.getState() instanceof InventoryHolder)) return;
        InventoryHolder holder = (InventoryHolder) clicked.getState();
        Inventory inv = holder.getInventory();

        String key = "chestsort";

        if (!toggle.getGlobal(key)) return;
        if (!toggle.isEnabledFor(p.getUniqueId().toString(), key)) return;

        // enforce shop/lock rules: exclude shop chests, respect locks
        Location holderLoc = clicked.getLocation();

        if (holderLoc != null) {
            // exclude chest shops
            try {
                if (this.chestShop != null && this.chestShop.isShopChest(holderLoc)) {
                    p.sendMessage("§cこのチェストはショップに紐づいているため整理できません");
                    return;
                }
            } catch (Exception ignored) {
            }
            // enforce chest lock rules
            try {
                if (this.chestLock != null && !this.chestLock.canAccess(p, holderLoc)) {
                    p.sendMessage("§cこのチェストは保護されているため整理できません");
                    return;
                }
            } catch (Exception ignored) {
            }
        }

        e.setCancelled(true);
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

        // ソート: カテゴリ (ブロック系 > アイテム系 > 装備系 > ツール系 > 食べ物系) → マテリアル名 → カスタム名 → 耐久 → エンチャント量
        Comparator<ItemStack> cmp = (a, b) -> {
            int priA = determineCategoryPriority(a.getType());
            int priB = determineCategoryPriority(b.getType());
            int pr = Integer.compare(priA, priB);
            if (pr != 0) return pr;
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
            r = Integer.compare(damage(a), damage(b));
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

    private static int determineCategoryPriority(Material m) {
        if (m == null) return Integer.MAX_VALUE / 2;
        if (m.isBlock()) return 0;
        if (m.isEdible()) return 4;

        EquipmentSlot slot = m.getEquipmentSlot();
        if (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST || slot == EquipmentSlot.LEGS || slot == EquipmentSlot.FEET) {
            return 2;
        }

        String name = m.name();
        if (name.endsWith("_SWORD") || name.endsWith("_AXE") || name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE")
                || name.equals("BOW") || name.equals("CROSSBOW") || name.equals("TRIDENT") || name.equals("SHEARS") || name.equals("FISHING_ROD")) {
            return 3;
        }
        return 1;
    }

    private boolean canMerge(ItemStack a, ItemStack b) {
        if (a == null || b == null) return false;
        if (a.getType() != b.getType()) return false;
        if (damage(a) != damage(b)) return false;
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

    private static int damage(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof Damageable) {
            return ((Damageable) meta).getDamage();
        }
        return 0;
    }
}
