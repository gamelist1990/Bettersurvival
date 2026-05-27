package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;

import java.util.*;

@SuppressWarnings("deprecation")
public class ChestShopUI {

    public static final String TITLE_PREFIX = "Shop UI - ";

    public static final String OWNER_TITLE_PREFIX = "Shop Owner - ";
    public static final String EDITOR_TITLE_PREFIX = "Shop Editor - ";

    private static final Map<UUID, ChestShop> openShops = new HashMap<>();
    private static final Map<UUID, Location> openLocations = new HashMap<>();

    public static ChestShop getOpenShop(UUID uid) {
        return openShops.get(uid);
    }

    public static Location getOpenLocation(UUID uid) {
        return openLocations.get(uid);
    }

    // Explicitly register an open mapping when switching to editor UI so modules
    // can find shop/location
    public static void registerOpen(UUID uid, ChestShop shop, Location loc) {
        if (uid == null)
            return;
        openShops.put(uid, shop);
        openLocations.put(uid, loc);
    }

    public static void openForPlayer(Player p, ChestShop shop, Location loc, ChestShopStore store) {
        String name = (shop == null ? "(無名)" : shop.getName());
        // if owner, open owner main UI; otherwise buyer UI
        boolean isOwner = shop != null && shop.getOwner() != null && shop.getOwner().equals(p.getUniqueId().toString());
        Inventory inv;
        if (isOwner)
            inv = ComponentUtils.createInventory(null, 27, OWNER_TITLE_PREFIX + name);
        else
            inv = ComponentUtils.createInventory(null, 27, TITLE_PREFIX + name);

        openShops.put(p.getUniqueId(), shop);

        if (isOwner) {
            // Owner main UI: slot 0 = open editor button
            ItemStack edit = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta em = edit.getItemMeta();
            if (em != null) {
                ComponentUtils.setDisplayName(em, "§a編集ページを開く");
                List<String> lore = new ArrayList<>();
                lore.add("クリックで出品アイテム編集画面を開きます");
                ComponentUtils.setLore(em, lore);
                edit.setItemMeta(em);
            }
            inv.setItem(0, edit);
            // leave the supply slot (10) and currency slot (12) empty so owner can place
            // items there
            // but populate currency slot (12) with current currency if set
            if (shop != null && shop.getCurrency() != null && !shop.getCurrency().isEmpty()) {
                Material curMat = Material.matchMaterial(shop.getCurrency());
                if (curMat != null) {
                    ItemStack curItem = new ItemStack(curMat, 1);
                    inv.setItem(12, curItem);
                }
            }
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta im = info.getItemMeta();
            if (im != null) {
                ComponentUtils.setDisplayName(im, "§6在庫と売切れ表示");
                // populate summary using store listings
                Map<Integer, ShopListing> listings = store.getListings(loc);
                int total = 0;
                List<String> soldOut = new ArrayList<>();
                for (Map.Entry<Integer, ShopListing> e : listings.entrySet()) {
                    ShopListing sl = e.getValue();
                    // 備考: editor/buyer UI の "サンプル" アイテム分 (1) は在庫カウントに含めない
                    total += Math.max(0, sl.getStock());
                    if (sl.getStock() < sl.getCount())
                        soldOut.add(resolveListingLabel(sl, p));
                }
                List<String> lore = new ArrayList<>();
                lore.add("総在庫: " + total);
                lore.add("売切れ: " + (soldOut.isEmpty() ? "なし" : String.join(", ", soldOut)));
                ComponentUtils.setLore(im, lore);
                info.setItemMeta(im);
            }
            inv.setItem(19, info);

            // slot 15: earnings display
            if (shop != null) {
                ItemStack earningsItem = new ItemStack(Material.GOLD_INGOT);
                ItemMeta em2 = earningsItem.getItemMeta();
                if (em2 != null) {
                    em2.displayName(buildEarningsDisplayName(shop.getEarnings(), shop.getCurrency(), shop.getCustomCurrencyName()));
                    List<String> lore = new ArrayList<>();
                    lore.add("§aクリックで収益を回収");
                    ComponentUtils.setLore(em2, lore);
                    earningsItem.setItemMeta(em2);
                }
                inv.setItem(15, earningsItem);
            }

            // fill unused owner slots with barriers to prevent interaction
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            if (bm != null) {
                ComponentUtils.setDisplayName(bm, "§c使用不可");
                barrier.setItemMeta(bm);
            }
            for (int i = 0; i < inv.getSize(); i++) {
                // allowed interactive slots for owner: 0 (editor), 10 (supply), 12 (currency),
                // 15 (earnings), 19 (info), last slot (close)
                if (i == 0 || i == 10 || i == 12 || i == 15 || i == 19 || i == inv.getSize() - 1)
                    continue;
                if (inv.getItem(i) == null)
                    inv.setItem(i, barrier);
            }
        }
        openLocations.put(p.getUniqueId(), loc);

        int btnSlot = Math.max(inv.getSize() - 1, 0);
        // Owner controls
        if (shop != null && shop.getOwner() != null && shop.getOwner().equals(p.getUniqueId().toString())) {
            ItemStack closeBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta m = closeBtn.getItemMeta();
            if (m != null) {
                ComponentUtils.setDisplayName(m, "§e閉じる");
                closeBtn.setItemMeta(m);
            }
            inv.setItem(btnSlot, closeBtn);
        }

        // populate buyer listing view when not owner
        if (!isOwner) {
            Map<Integer, ShopListing> listings = store.getListings(loc);
            for (int i = 0; i < 26; i++) {
                ShopListing sl = listings.get(i);
                if (sl == null)
                    continue;
                Material mat = Material.matchMaterial(sl.getMaterial());
                if (mat == null)
                    mat = Material.PAPER;
                ItemStack it = null;
                // prefer serialized item data (preserves enchantments / NBT)
                if (sl.getItemData() != null) {
                    try {
                        it = ItemStack.deserialize(sl.getItemData());
                    } catch (Exception ignored) {
                        it = null;
                    }
                }
                if (it == null)
                    it = new ItemStack(mat);
                it.setAmount(Math.max(1, Math.min(64, sl.getCount())));
                ItemMeta im2 = it.getItemMeta();
                if (im2 != null) {
                    // apply stored enchant map if the serialized item didn't include them
                    try {
                        if ((im2.getEnchants() == null || im2.getEnchants().isEmpty()) && sl.getEnchants() != null
                                && !sl.getEnchants().isEmpty()) {
                            for (Map.Entry<String, Integer> en : sl.getEnchants().entrySet()) {
                                try {
                                    Enchantment ec = Enchantment.getByKey(NamespacedKey.minecraft(en.getKey()));
                                    if (ec != null && en.getValue() != null)
                                        im2.addEnchant(ec, en.getValue(), true);
                                } catch (Exception ignored) {
                                }
                            }
                        }
                    } catch (Exception ignored) {
                    }

                    applyListingDisplayName(im2, sl, mat);
                    List<Component> lore2 = new ArrayList<>();
                    if (shop == null || shop.getCurrency() == null || shop.getCurrency().isEmpty()) {
                        lore2.add(ComponentUtils.legacy("§c通貨未設定 (販売不可)"));
                    } else if ((long) sl.getPrice() > 64L) {
                        lore2.add(ComponentUtils.legacy("§c価格が最大を超えています (販売不可)"));
                    } else {
                        lore2.add(buildPriceLoreLine(sl.getPrice(), shop.getCurrency(), shop.getCustomCurrencyName()));
                    }
                    if (sl.getStock() < sl.getCount()) {
                        lore2.add(ComponentUtils.legacy("§c品切れ中"));
                    } else {
                        lore2.add(ComponentUtils.legacy("在庫: " + sl.getStock()));
                        lore2.add(ComponentUtils.legacy("個数: " + sl.getCount()));
                    }
                    if (sl.getDescription() != null && !sl.getDescription().isEmpty()) {
                        String desc = sl.getDescription().replace("<br>", "\n");
                        String[] lines = desc.split("\\n");
                        // 一行目のみ "説明: " プレフィックスを付け、複数行は続けて表示する
                        if (lines.length > 0) {
                            lore2.add(ComponentUtils.legacy("説明: " + lines[0]));
                            for (int j = 1; j < lines.length; j++)
                                lore2.add(ComponentUtils.legacy(lines[j]));
                        }
                    }
                    ComponentUtils.setLoreComponents(im2, lore2);
                    it.setItemMeta(im2);
                }
                inv.setItem(i, it);
            }
            // fill unused buyer slots (0..25) with barrier
            ItemStack barrierB = new ItemStack(Material.BARRIER);
            ItemMeta bmB = barrierB.getItemMeta();
            if (bmB != null) {
                ComponentUtils.setDisplayName(bmB, "§c使用不可");
                barrierB.setItemMeta(bmB);
            }
            for (int i = 0; i < 26; i++) {
                if (inv.getItem(i) == null)
                    inv.setItem(i, barrierB);
            }
            // slot 26: 購入方法の説明（ヘルプ）
            ItemStack help = new ItemStack(Material.BOOK);
            ItemMeta hm = help.getItemMeta();
            if (hm != null) {
                ComponentUtils.setDisplayName(hm, "§b購入方法");
                List<String> hl = new ArrayList<>();
                hl.add("§e価格の仕組み");
                hl.add("§f表示価格は §f1セット(個数分) §fの値段です");
                hl.add("§f例: 個数5, 価格10 の場合");
                hl.add("§f10通貨支払って、5個入手します");
                hl.add("");
                hl.add("§e購入ルール");
                hl.add("§fクリックで1セット購入します");
                hl.add("§f在庫がセット個数より少なくなると");
                hl.add("§f品切れとなり購入できません");
                ComponentUtils.setLore(hm, hl);
                help.setItemMeta(hm);
            }
            inv.setItem(26, help);
        }

        p.openInventory(inv);
    }

    /**
     * Get display name for material, with optional custom currency name override.
     * If customCurrencyName is provided and non-empty, it will be used instead of
     * the material name.
     */
    public static String displayNameForMaterial(Player viewer, String matName, String customCurrencyName) {
        // If custom currency name is provided, use it
        if (customCurrencyName != null && !customCurrencyName.trim().isEmpty()) {
            return customCurrencyName;
        }
        // Otherwise fall back to default material display name
        return displayNameForMaterial(viewer, matName);
    }

    public static String displayNameForMaterial(Player viewer, String matName) {
        if (matName == null || matName.isEmpty())
            return "未設定";
        Material m = Material.matchMaterial(matName);
        if (m == null)
            return matName;
        Locale locale = viewer == null ? Locale.US : viewer.locale();
        return ItemNameUtil.localizedPlainText(m, locale);
    }

    private static String resolveListingLabel(ShopListing listing, Player viewer) {
        if (listing == null)
            return "未設定";
        String display = listing.getDisplayName();
        if (display != null && !display.isBlank())
            return display;
        return displayNameForMaterial(viewer, listing.getMaterial());
    }

    private static void applyListingDisplayName(ItemMeta meta, ShopListing listing, Material material) {
        if (meta == null || material == null)
            return;
        String display = listing == null ? null : listing.getDisplayName();
        if (display == null || display.isBlank() || display.equals(material.name())) {
            meta.displayName(ItemNameUtil.localizedComponent(material));
            return;
        }
        ComponentUtils.setDisplayName(meta, display);
    }

    private static Component buildPriceLoreLine(int price, String currencyMaterialName, String customCurrencyName) {
        return ComponentUtils.legacy("§a販売中 - 価格: " + price + " ")
                .append(buildCurrencyNameComponent(currencyMaterialName, customCurrencyName));
    }

    private static Component buildEarningsDisplayName(int earnings, String currencyMaterialName, String customCurrencyName) {
        return ComponentUtils.legacy("§6収益: " + earnings + " ")
                .append(buildCurrencyNameComponent(currencyMaterialName, customCurrencyName));
    }

    private static Component buildCurrencyNameComponent(String currencyMaterialName, String customCurrencyName) {
        if (customCurrencyName != null && !customCurrencyName.trim().isEmpty())
            return Component.text(customCurrencyName);
        Material currency = Material.matchMaterial(currencyMaterialName);
        if (currency == null)
            return Component.text(currencyMaterialName == null ? "未設定" : currencyMaterialName);
        return ItemNameUtil.localizedComponent(currency);
    }

    // open a dedicated delete page where owner can view and delete items from
    // inventory
    public static void openDeletePage(Player p, ChestShop shop, Location loc, ChestShopStore store) {
        // REMOVED: Delete page functionality is no longer used
        // Deletion is now handled directly by removing items from the editor
        p.sendMessage("§c削除ページは削除されました");
    }

    public static void closeForPlayer(Player p) {
        openShops.remove(p.getUniqueId());
        openLocations.remove(p.getUniqueId());
    }

    // Update the owner info item in real-time when stock changes
    public static void updateOwnerInfo(Player p, ChestShopStore store) {
        if (p == null || store == null)
            return;
        ChestShop shop = getOpenShop(p.getUniqueId());
        Location loc = getOpenLocation(p.getUniqueId());
        if (shop == null || loc == null)
            return;
        // Check if owner UI is open
        InventoryView view = p.getOpenInventory();
        if (view == null || ComponentUtils.legacyText(view.title()) == null || !ComponentUtils.legacyText(view.title()).startsWith(OWNER_TITLE_PREFIX))
            return;
        Inventory inv = view.getTopInventory();
        if (inv == null)
            return;

        // Update earnings display (slot 15)
        ItemStack earningsItem = new ItemStack(Material.GOLD_INGOT);
        ItemMeta em = earningsItem.getItemMeta();
        if (em != null) {
            em.displayName(buildEarningsDisplayName(shop.getEarnings(), shop.getCurrency(), shop.getCustomCurrencyName()));
            List<String> lore = new ArrayList<>();
            lore.add("§aクリックで収益を回収");
            ComponentUtils.setLore(em, lore);
            earningsItem.setItemMeta(em);
        }
        inv.setItem(15, earningsItem);

        // Create info item (slot 19)
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            ComponentUtils.setDisplayName(im, "§6在庫と売切れ表示");
            // populate summary using store listings
            Map<Integer, ShopListing> listings = store.getListings(loc);
            int total = 0;
            List<String> soldOut = new ArrayList<>();
            for (Map.Entry<Integer, ShopListing> e : listings.entrySet()) {
                ShopListing sl = e.getValue();
                // サンプル本体の個数を含めず、実際の在庫のみ合計する
                total += Math.max(0, sl.getStock());
                if (sl.getStock() < sl.getCount())
                    soldOut.add(resolveListingLabel(sl, p));
            }
            List<String> lore = new ArrayList<>();
            lore.add("総在庫: " + total);
            lore.add("売切れ: " + (soldOut.isEmpty() ? "なし" : String.join(", ", soldOut)));
            ComponentUtils.setLore(im, lore);
            info.setItemMeta(im);
        }
        inv.setItem(19, info);
    }

    // Update the buyer UI in real-time when items are purchased
    public static void updateBuyerUI(Player p, ChestShopStore store) {
        if (p == null || store == null)
            return;
        ChestShop shop = getOpenShop(p.getUniqueId());
        Location loc = getOpenLocation(p.getUniqueId());
        if (shop == null || loc == null)
            return;
        // Check if buyer UI is open
        InventoryView view = p.getOpenInventory();
        if (view == null || ComponentUtils.legacyText(view.title()) == null || !ComponentUtils.legacyText(view.title()).startsWith(TITLE_PREFIX))
            return;
        Inventory inv = view.getTopInventory();
        if (inv == null)
            return;

        // Update all listing items (slots 0-25)
        Map<Integer, ShopListing> listings = store.getListings(loc);
        for (int i = 0; i < 26; i++) {
            ShopListing sl = listings.get(i);
            if (sl == null) {
                // Clear slot if no listing
                if (inv.getItem(i) != null && inv.getItem(i).getType() != Material.BARRIER) {
                    ItemStack barrier = new ItemStack(Material.BARRIER);
                    ItemMeta bm = barrier.getItemMeta();
                    if (bm != null) {
                        ComponentUtils.setDisplayName(bm, "§c使用不可");
                        barrier.setItemMeta(bm);
                    }
                    inv.setItem(i, barrier);
                }
                continue;
            }
            Material mat = Material.matchMaterial(sl.getMaterial());
            if (mat == null)
                mat = Material.PAPER;
            ItemStack it = null;
            // prefer serialized item data (preserves enchantments / NBT)
            if (sl.getItemData() != null) {
                try {
                    it = ItemStack.deserialize(sl.getItemData());
                } catch (Exception ignored) {
                    it = null;
                }
            }
            if (it == null)
                it = new ItemStack(mat);
            it.setAmount(Math.max(1, Math.min(64, sl.getCount())));
            ItemMeta im = it.getItemMeta();
            if (im != null) {
                // apply stored enchant map if the serialized item didn't include them
                try {
                    if ((im.getEnchants() == null || im.getEnchants().isEmpty()) && sl.getEnchants() != null
                            && !sl.getEnchants().isEmpty()) {
                        for (Map.Entry<String, Integer> en : sl.getEnchants().entrySet()) {
                            try {
                                Enchantment ec = Enchantment.getByKey(NamespacedKey.minecraft(en.getKey()));
                                if (ec != null && en.getValue() != null)
                                    im.addEnchant(ec, en.getValue(), true);
                            } catch (Exception ignored) {
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                applyListingDisplayName(im, sl, mat);
                List<Component> lore = new ArrayList<>();
                if (shop.getCurrency() == null || shop.getCurrency().isEmpty()) {
                    lore.add(ComponentUtils.legacy("§c通貨未設定 (販売不可)"));
                } else if ((long) sl.getPrice() > 64L) {
                    lore.add(ComponentUtils.legacy("§c価格が最大を超えています (販売不可)"));
                } else {
                    lore.add(buildPriceLoreLine(sl.getPrice(), shop.getCurrency(), shop.getCustomCurrencyName()));
                }
                if (sl.getStock() < sl.getCount()) {
                    lore.add(ComponentUtils.legacy("§c品切れ中"));
                } else {
                    lore.add(ComponentUtils.legacy("在庫: " + sl.getStock()));
                    lore.add(ComponentUtils.legacy("個数: " + sl.getCount()));
                }
                if (sl.getDescription() != null && !sl.getDescription().isEmpty()) {
                    String desc = sl.getDescription().replace("<br>", "\n");
                    String[] lines = desc.split("\\\\n");
                    if (lines.length > 0) {
                        lore.add(ComponentUtils.legacy("説明: " + lines[0]));
                        for (int j = 1; j < lines.length; j++)
                            lore.add(ComponentUtils.legacy(lines[j]));
                    }
                }
                ComponentUtils.setLoreComponents(im, lore);
                it.setItemMeta(im);
            }
            inv.setItem(i, it);
        }
    }

}
