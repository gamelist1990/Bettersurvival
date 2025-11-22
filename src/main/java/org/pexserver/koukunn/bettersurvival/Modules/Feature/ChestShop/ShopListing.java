package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShopListing {
    private String material; // Material name
    private String displayName; // original display name (may include price/desc markers)
    private int price; // price in units of currency item
    private String description; // seller provided description (with <br> for newlines)
    private int stock; // current stock
    private int count; // quantity sold per purchase (1..64)
    private int originalCount; // original count when first registered (used for returns to avoid duplication)
    private Map<String,Integer> enchants; // stored enchantments (name -> level)
    private int damage; // stored damage value (0 == undamaged)
    // Serialized item data (ConfigurationSerializable output from ItemStack.serialize())
    private Map<String,Object> itemData;

    public ShopListing() {}

    public ShopListing(String material, String displayName, int price, String description, int stock) {
        this.material = material;
        setDisplayName(displayName); // clean display name
        this.price = price;
        this.description = description;
        this.stock = stock;
        this.count = 1;
        this.originalCount = 1;
        this.enchants = new LinkedHashMap<>();
        this.damage = 0;
    }

    public ShopListing(String material, String displayName, int price, String description, int stock, Map<String,Integer> enchants, int damage) {
        this.material = material;
        setDisplayName(displayName); // clean display name
        this.price = price;
        this.description = description;
        this.stock = stock;
        this.count = 1;
        this.originalCount = 1;
        this.enchants = enchants == null ? new LinkedHashMap<>() : enchants;
        this.damage = damage;
        this.itemData = null;
    }

    public ShopListing(String material, String displayName, int price, String description, int stock, Map<String,Integer> enchants, int damage, Map<String,Object> itemData) {
        this.material = material;
        setDisplayName(displayName); // clean display name
        this.price = price;
        this.description = description;
        this.stock = stock;
        this.count = 1;
        this.originalCount = 1;
        this.enchants = enchants == null ? new LinkedHashMap<>() : enchants;
        this.damage = damage;
        this.itemData = itemData;
    }

    public ShopListing(String material, String displayName, int price, String description, int stock, int count, Map<String,Integer> enchants, int damage, Map<String,Object> itemData) {
        this.material = material;
        setDisplayName(displayName); // clean display name
        this.price = price;
        this.description = description;
        this.stock = stock;
        // ensure count in [1,64]
        this.count = Math.max(1, Math.min(64, count));
        this.originalCount = this.count; // ← originalCountを初期化（登録時の個数を記録）
        this.enchants = enchants == null ? new LinkedHashMap<>() : enchants;
        this.damage = damage;
        this.itemData = itemData;
    }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) {
        // Remove {} and their contents from display name (supports both ASCII and fullwidth braces and mixed)
        // Handles: {}, ｛｝, {｝, ｛}
        if (displayName != null) {
            displayName = displayName.replaceAll("[{｛][^}｝]*[}｝]", "").trim();
            if (displayName.isEmpty()) displayName = null;
        }
        this.displayName = displayName;
    }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public Map<String,Integer> getEnchants() { return enchants; }
    public void setEnchants(Map<String,Integer> enchants) { this.enchants = enchants; }

    public int getDamage() { return damage; }
    public void setDamage(int damage) { this.damage = damage; }

    public Map<String,Object> getItemData() { return itemData; }
    public void setItemData(Map<String,Object> itemData) { this.itemData = itemData; }

    public Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("displayName", displayName);
        m.put("price", price);
        m.put("description", description);
        m.put("stock", stock);
        m.put("count", count);
        m.put("originalCount", originalCount);
        if (enchants != null && !enchants.isEmpty()) m.put("enchants", enchants);
        m.put("damage", damage);
        if (itemData != null && !itemData.isEmpty()) m.put("item", itemData);
        return m;
    }

    @SuppressWarnings("unchecked")
    public static ShopListing fromMap(Object obj) {
        if (!(obj instanceof Map)) return null;
        Map<String,Object> map = (Map<String,Object>) obj;
        String material = (String) map.get("material");
        String displayName = (String) map.get("displayName");
        int price = map.get("price") instanceof Number ? ((Number) map.get("price")).intValue() : 0;
        String description = (String) map.get("description");
        int stock = map.get("stock") instanceof Number ? ((Number) map.get("stock")).intValue() : 0;
        int count = map.get("count") instanceof Number ? ((Number) map.get("count")).intValue() : 1;
        int originalCount = map.get("originalCount") instanceof Number ? ((Number) map.get("originalCount")).intValue() : count;
        Map<String,Integer> ench = new LinkedHashMap<>();
        Object eobj = map.get("enchants");
        if (eobj instanceof Map) {
            Map<String,Object> eMap = (Map<String,Object>) eobj;
            for (Map.Entry<String,Object> en : eMap.entrySet()) {
                Object val = en.getValue();
                int lvl = val instanceof Number ? ((Number) val).intValue() : 0;
                ench.put(en.getKey(), lvl);
            }
        }
        int damage = map.get("damage") instanceof Number ? ((Number) map.get("damage")).intValue() : 0;
        Map<String,Object> itemData = null;
        Object iobj = map.get("item");
        if (iobj instanceof Map) itemData = (Map<String,Object>) iobj;
        ShopListing sl = new ShopListing(material, displayName, price, description, stock, count, ench, damage, itemData);
        sl.setOriginalCount(originalCount);
        // Re-save with cleaned displayName to ensure old data gets cleaned up
        sl.setDisplayName(displayName);
        return sl;
    }

    public int getCount() { return count; }
    public void setCount(int count) { this.count = Math.max(1, Math.min(64, count)); }

    public int getOriginalCount() { return originalCount; }
    public void setOriginalCount(int originalCount) { this.originalCount = Math.max(1, Math.min(64, originalCount)); }}