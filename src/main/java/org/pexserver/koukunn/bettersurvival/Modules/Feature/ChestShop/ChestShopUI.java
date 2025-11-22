package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.Material;

import java.util.*;

public class ChestShopUI {

    public static final String TITLE_PREFIX = "Shop UI - ";

    public static final String OWNER_TITLE_PREFIX = "Shop Owner - ";
    public static final String EDITOR_TITLE_PREFIX = "Shop Editor - ";

    private static final Map<UUID, ChestShop> openShops = new HashMap<>();
    private static final Map<UUID, Location> openLocations = new HashMap<>();

    public static ChestShop getOpenShop(UUID uid) { return openShops.get(uid); }
    public static Location getOpenLocation(UUID uid) { return openLocations.get(uid); }

    // Explicitly register an open mapping when switching to editor UI so modules can find shop/location
    public static void registerOpen(UUID uid, ChestShop shop, Location loc) {
        if (uid == null) return;
        openShops.put(uid, shop);
        openLocations.put(uid, loc);
    }

    public static void openForPlayer(Player p, ChestShop shop, Location loc, ChestShopStore store) {
        List<Player> nearby = new ArrayList<>();
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (!pl.getWorld().equals(p.getWorld())) continue;
            if (pl.getUniqueId().equals(p.getUniqueId())) continue;
            if (pl.getLocation().distance(loc) <= 50.0) nearby.add(pl);
        }
        nearby.sort(Comparator.comparingDouble(pl -> pl.getLocation().distance(loc)));

        int size = 9 * ((nearby.size() + 8) / 9);
        if (size == 0) size = 9;
        // ensure buyer inventories are at least 27 and at most 54
        size = Math.min(Math.max(27, size), 54);
        String name = (shop == null ? "(無名)" : shop.getName());
        // if owner, open owner main UI; otherwise buyer UI
        boolean isOwner = shop != null && shop.getOwner() != null && shop.getOwner().equals(p.getUniqueId().toString());
        Inventory inv;
        if (isOwner) inv = Bukkit.createInventory(null, 27, OWNER_TITLE_PREFIX + name);
        else inv = Bukkit.createInventory(null, 27, TITLE_PREFIX + name);

        // reserve slot 0 for owner UI control if owner; start heads at 1 for owner
        int slot = isOwner ? 1 : 0;
        for (Player pl : nearby) {
            if (slot >= inv.getSize()) break;
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(pl);
                meta.setDisplayName(pl.getName());
                head.setItemMeta(meta);
            }
            inv.setItem(slot++, head);
        }

        openShops.put(p.getUniqueId(), shop);

        if (isOwner) {
            // Owner main UI: slot 0 = open editor button
            ItemStack edit = new ItemStack(Material.WRITABLE_BOOK);
            ItemMeta em = edit.getItemMeta();
            if (em != null) { em.setDisplayName("§a編集ページを開く"); List<String> lore = new ArrayList<>(); lore.add("クリックで出品アイテム編集画面を開きます"); em.setLore(lore); edit.setItemMeta(em); }
            inv.setItem(0, edit);
            // leave the supply slot (10) and currency slot (12) empty so owner can place items there
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
                im.setDisplayName("§6在庫と売切れ表示");
                // populate summary using store listings
                Map<Integer, ShopListing> listings = store.getListings(loc);
                int total = 0;
                List<String> soldOut = new ArrayList<>();
                for (Map.Entry<Integer, ShopListing> e : listings.entrySet()) {
                    ShopListing sl = e.getValue();
                    // 備考: editor/buyer UI の "サンプル" アイテム分 (1) は在庫カウントに含めない
                    total += Math.max(0, sl.getStock());
                    if (sl.getStock() <= 0) soldOut.add(sl.getDisplayName() == null ? sl.getMaterial() : sl.getDisplayName());
                }
                List<String> lore = new ArrayList<>();
                lore.add("総在庫: " + total);
                lore.add("売切れ: " + (soldOut.isEmpty() ? "なし" : String.join(", ", soldOut)));
                im.setLore(lore);
                info.setItemMeta(im);
            }
            inv.setItem(19, info);

            // slot 15: earnings display
            if (shop != null) {
                ItemStack earningsItem = new ItemStack(Material.GOLD_INGOT);
                ItemMeta em2 = earningsItem.getItemMeta();
                if (em2 != null) {
                    String currencyName = displayNameForMaterial(shop.getCurrency());
                    em2.setDisplayName("§6収益: " + shop.getEarnings() + " " + currencyName);
                    List<String> lore = new ArrayList<>();
                    lore.add("§aクリックで収益を回収");
                    em2.setLore(lore);
                    earningsItem.setItemMeta(em2);
                }
                inv.setItem(15, earningsItem);
            }

            // fill unused owner slots with barriers to prevent interaction
            ItemStack barrier = new ItemStack(Material.BARRIER);
            ItemMeta bm = barrier.getItemMeta();
            if (bm != null) { bm.setDisplayName("§c使用不可"); barrier.setItemMeta(bm); }
            for (int i = 0; i < inv.getSize(); i++) {
                // allowed interactive slots for owner: 0 (editor), 10 (supply), 12 (currency), 15 (earnings), 19 (info), last slot (close)
                if (i == 0 || i == 10 || i == 12 || i == 15 || i == 19 || i == inv.getSize()-1) continue;
                if (inv.getItem(i) == null) inv.setItem(i, barrier);
            }
        }
        openLocations.put(p.getUniqueId(), loc);

        int btnSlot = Math.max(inv.getSize() - 1, 0);
        // Owner controls
        if (shop != null && shop.getOwner() != null && shop.getOwner().equals(p.getUniqueId().toString())) {
            ItemStack closeBtn = new ItemStack(Material.RED_STAINED_GLASS_PANE);
            ItemMeta m = closeBtn.getItemMeta();
            if (m != null) { m.setDisplayName("§e閉じる"); closeBtn.setItemMeta(m); }
            inv.setItem(btnSlot, closeBtn);
        }

        // populate buyer listing view when not owner
        if (!isOwner) {
            Map<Integer, ShopListing> listings = store.getListings(loc);
            for (int i = 0; i < 26; i++) {
                ShopListing sl = listings.get(i);
                if (sl == null) continue;
                Material mat = Material.matchMaterial(sl.getMaterial());
                if (mat == null) mat = Material.PAPER;
                ItemStack it = null;
                // prefer serialized item data (preserves enchantments / NBT)
                if (sl.getItemData() != null) {
                    try { it = ItemStack.deserialize(sl.getItemData()); } catch (Exception ignored) { it = null; }
                }
                if (it == null) it = new ItemStack(mat);
                it.setAmount(Math.max(1, Math.min(64, sl.getCount())));
                ItemMeta im2 = it.getItemMeta();
                if (im2 != null) {
                    // apply stored enchant map if the serialized item didn't include them
                    try {
                        if ((im2.getEnchants() == null || im2.getEnchants().isEmpty()) && sl.getEnchants() != null && !sl.getEnchants().isEmpty()) {
                            for (Map.Entry<String,Integer> en : sl.getEnchants().entrySet()) {
                                try {
                                    Enchantment ec = Enchantment.getByKey(NamespacedKey.minecraft(en.getKey()));
                                    if (ec != null && en.getValue() != null) im2.addEnchant(ec, en.getValue(), true);
                                } catch (Exception ignored) {}
                            }
                        }
                    } catch (Exception ignored) {}

                    String disp = sl.getDisplayName();
                    if (disp == null || disp.isEmpty()) disp = mat.name();
                    // displayName is already cleaned by setDisplayName()
                    im2.setDisplayName(disp);
                    List<String> lore2 = new ArrayList<>();
                    if (shop == null || shop.getCurrency() == null || shop.getCurrency().isEmpty()) {
                        lore2.add("§c通貨未設定 (販売不可)");
                    } else if ((long)sl.getPrice() > 64L) {
                        lore2.add("§c価格が最大を超えています (販売不可)");
                    } else {
                        String curDisplay = displayNameForMaterial(shop.getCurrency());
                        lore2.add("§a販売中 - 価格: " + sl.getPrice() + " " + curDisplay);
                    }
                    if (sl.getStock() <= 0) {
                        lore2.add("§c品切れ中");
                    } else {
                        lore2.add("在庫: " + sl.getStock());
                        lore2.add("個数: " + sl.getCount());
                    }
                    if (sl.getDescription() != null && !sl.getDescription().isEmpty()) {
                        String desc = sl.getDescription().replace("<br>", "\n");
                        String[] lines = desc.split("\\n");
                        // 一行目のみ "説明: " プレフィックスを付け、複数行は続けて表示する
                        if (lines.length > 0) {
                            lore2.add("説明: " + lines[0]);
                            for (int j = 1; j < lines.length; j++) lore2.add(lines[j]);
                        }
                    }
                    im2.setLore(lore2);
                    it.setItemMeta(im2);
                }
                inv.setItem(i, it);
            }
            // fill unused buyer slots (0..25) with barrier
            ItemStack barrierB = new ItemStack(Material.BARRIER);
            ItemMeta bmB = barrierB.getItemMeta();
            if (bmB != null) { bmB.setDisplayName("§c使用不可"); barrierB.setItemMeta(bmB); }
            for (int i = 0; i < 26; i++) {
                if (inv.getItem(i) == null) inv.setItem(i, barrierB);
            }
            // slot 26: 購入方法の説明（ヘルプ）
            ItemStack help = new ItemStack(Material.BOOK);
            ItemMeta hm = help.getItemMeta();
            if (hm != null) {
                hm.setDisplayName("§b購入方法");
                List<String> hl = new ArrayList<>();
                hl.add("価格はセット価格です (例: 個数5, 価格10 => 10で購入)");
                hl.add("出品をクリックすると1セットを購入します");
                hl.add("在庫が0になると品切れになります");
                hm.setLore(hl);
                help.setItemMeta(hm);
            }
            inv.setItem(26, help);
        }

        p.openInventory(inv);
    }

    public static String displayNameForMaterial(String matName) {
        if (matName == null || matName.isEmpty()) return "未設定";
        Material m = Material.matchMaterial(matName);
        if (m == null) return matName;
        // Try ItemMeta's display/localized name first (resource packs or server translations)
        try {
            ItemStack probe = new ItemStack(m, 1);
            ItemMeta im = probe.getItemMeta();
            if (im != null) {
                if (im.hasDisplayName()) return im.getDisplayName();
                try {
                    String loc = im.getLocalizedName();
                    if (loc != null && !loc.trim().isEmpty()) return loc;
                } catch (NoSuchMethodError | AbstractMethodError ignored) {}
            }
        } catch (Throwable ignored) {}

        // Fallback: some common translations for nicer display
        switch (m) {
            case EMERALD: return "エメラルド";
            case DIAMOND: return "ダイヤモンド";
            case GOLD_INGOT: return "金の延べ棒";
            case IRON_INGOT: return "鉄の延べ棒";
            case PAPER: return "紙";
            default:
                String s = m.name().toLowerCase().replace('_', ' ');
                String[] parts = s.split(" ");
                StringBuilder sb = new StringBuilder();
                for (String p : parts) {
                    if (p == null || p.isEmpty()) continue;
                    sb.append(Character.toUpperCase(p.charAt(0))).append(p.substring(1)).append(' ');
                }
                return sb.toString().trim();
        }
    }

    // open a dedicated delete page where owner can view and delete items from inventory
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
        if (p == null || store == null) return;
        ChestShop shop = getOpenShop(p.getUniqueId());
        Location loc = getOpenLocation(p.getUniqueId());
        if (shop == null || loc == null) return;
        // Check if owner UI is open
        InventoryView view = p.getOpenInventory();
        if (view == null || view.getTitle() == null || !view.getTitle().startsWith(OWNER_TITLE_PREFIX)) return;
        Inventory inv = view.getTopInventory();
        if (inv == null) return;
        // Create info item
        ItemStack info = new ItemStack(Material.PAPER);
        ItemMeta im = info.getItemMeta();
        if (im != null) {
            im.setDisplayName("§6在庫と売切れ表示");
            // populate summary using store listings
            Map<Integer, ShopListing> listings = store.getListings(loc);
            int total = 0;
            List<String> soldOut = new ArrayList<>();
            for (Map.Entry<Integer, ShopListing> e : listings.entrySet()) {
                ShopListing sl = e.getValue();
                // サンプル本体の個数を含めず、実際の在庫のみ合計する
                total += Math.max(0, sl.getStock());
                if (sl.getStock() <= 0) soldOut.add(sl.getDisplayName() == null ? sl.getMaterial() : sl.getDisplayName());
            }
            List<String> lore = new ArrayList<>();
            lore.add("総在庫: " + total);
            lore.add("売切れ: " + (soldOut.isEmpty() ? "なし" : String.join(", ", soldOut)));
            im.setLore(lore);
            info.setItemMeta(im);
        }
        inv.setItem(19, info);
    }

}
