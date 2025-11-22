package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

import java.util.LinkedHashMap;
import java.util.Map;

public class ShopListing {
    private String material; // Material name
    private String displayName; // original display name (may include price/desc markers)
    private int price; // price in units of currency item
    private String description; // seller provided description (with <br> for newlines)
    private int stock; // current stock

    public ShopListing() {}

    public ShopListing(String material, String displayName, int price, String description, int stock) {
        this.material = material;
        this.displayName = displayName;
        this.price = price;
        this.description = description;
        this.stock = stock;
    }

    public String getMaterial() { return material; }
    public void setMaterial(String material) { this.material = material; }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public int getPrice() { return price; }
    public void setPrice(int price) { this.price = price; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getStock() { return stock; }
    public void setStock(int stock) { this.stock = stock; }

    public Map<String,Object> toMap() {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("material", material);
        m.put("displayName", displayName);
        m.put("price", price);
        m.put("description", description);
        m.put("stock", stock);
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
        return new ShopListing(material, displayName, price, description, stock);
    }
}
