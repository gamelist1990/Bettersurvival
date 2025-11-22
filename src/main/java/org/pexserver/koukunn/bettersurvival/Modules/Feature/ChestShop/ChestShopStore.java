package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestShop;

import org.bukkit.Location;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;

public class ChestShopStore {

    private final ConfigManager cfg;
    private final String path = "ChestShop/shops.json";

    public ChestShopStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    private String keyFor(Location loc) {
        // use smallest canonical location among adjacent chest blocks
        List<Location> related = org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock());
        if (related == null || related.isEmpty()) {
            return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        }
        Location smallest = related.get(0);
        for (Location l : related) {
            if (compareLocationKey(l, smallest) < 0) smallest = l;
        }
        return smallest.getWorld().getName() + ":" + smallest.getBlockX() + ":" + smallest.getBlockY() + ":" + smallest.getBlockZ();
    }

    private int compareLocationKey(Location a, Location b) {
        int cmp = a.getWorld().getName().compareTo(b.getWorld().getName());
        if (cmp != 0) return cmp;
        if (a.getBlockX() != b.getBlockX()) return Integer.compare(a.getBlockX(), b.getBlockX());
        if (a.getBlockY() != b.getBlockY()) return Integer.compare(a.getBlockY(), b.getBlockY());
        return Integer.compare(a.getBlockZ(), b.getBlockZ());
    }

    public Optional<ChestShop> get(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object obj = pc.get(canonical);
        if (obj instanceof Map) {
            Map<String,Object> map = (Map<String,Object>) obj;
            ChestShop shop = new ChestShop((String) map.get("owner"), (String) map.get("name"), (String) map.get("currency"));
            return Optional.of(shop);
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public Map<Integer, ShopListing> getListings(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object obj = pc.get(canonical);
        Map<Integer, ShopListing> out = new LinkedHashMap<>();
        if (!(obj instanceof Map)) return out;
        Map<String,Object> map = (Map<String,Object>) obj;
        Object listings = map.get("listings");
        if (!(listings instanceof Map)) return out;
        Map<String,Object> lm = (Map<String,Object>) listings;
        for (Map.Entry<String,Object> e : lm.entrySet()) {
            try {
                int slot = Integer.parseInt(e.getKey());
                ShopListing sl = ShopListing.fromMap(e.getValue());
                if (sl != null) out.put(slot, sl);
            } catch (Exception ignore) {}
        }
        return out;
    }

    public boolean save(Location loc, ChestShop shop) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object existing = pc.get(canonical);
        Map<String, Object> entry = new LinkedHashMap<>();
        if (existing instanceof Map) entry = (Map<String,Object>) existing;
        entry.put("owner", shop.getOwner());
        entry.put("name", shop.getName());
        if (shop.getCurrency() != null) entry.put("currency", shop.getCurrency()); else entry.remove("currency");
        pc.put(canonical, entry);
        // remove legacy per-block keys inside the same set to avoid duplicates
        List<Location> related = org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock());
        for (Location l : related) {
            String k = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
            if (!k.equals(canonical)) pc.getData().remove(k);
        }
        return cfg.saveConfig(path, pc);
    }

    public boolean saveListings(Location loc, Map<Integer, ShopListing> listings) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object obj = pc.get(canonical);
        Map<String,Object> entry;
        if (obj instanceof Map) entry = (Map<String,Object>) obj; else entry = new LinkedHashMap<>();
        Map<String,Object> lm = new LinkedHashMap<>();
        for (Map.Entry<Integer, ShopListing> e : listings.entrySet()) {
            lm.put(String.valueOf(e.getKey()), e.getValue().toMap());
        }
        entry.put("listings", lm);
        pc.put(canonical, entry);
        return cfg.saveConfig(path, pc);
    }

    public boolean saveShopCurrency(Location loc, String currency) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object obj = pc.get(canonical);
        Map<String,Object> entry;
        if (obj instanceof Map) entry = (Map<String,Object>) obj; else entry = new LinkedHashMap<>();
        if (currency == null) entry.remove("currency"); else entry.put("currency", currency);
        pc.put(canonical, entry);
        return cfg.saveConfig(path, pc);
    }

    public boolean updateStock(Location loc, int slot, int newStock) {
        Map<Integer, ShopListing> listings = getListings(loc);
        ShopListing s = listings.get(slot);
        if (s == null) return false;
        s.setStock(newStock);
        return saveListings(loc, listings);
    }

    public boolean remove(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        pc.getData().remove(canonical);
        List<Location> related = org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock.ChestLockModule.getChestRelatedLocations(loc.getBlock());
        for (Location l : related) pc.getData().remove(l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ());
        return cfg.saveConfig(path, pc);
    }

    public Map<String, ChestShop> getAll() {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        Map<String, ChestShop> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : pc.getData().entrySet()) {
            Object obj = e.getValue();
            if (!(obj instanceof Map)) continue;
            Map<String, Object> map = (Map<String, Object>) obj;
            ChestShop shop = new ChestShop((String) map.get("owner"), (String) map.get("name"), (String) map.get("currency"));
            out.put(e.getKey(), shop);
        }
        return out;
    }

}
