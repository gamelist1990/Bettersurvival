package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Location;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

@SuppressWarnings("unchecked")
public class ChestLockStore {

    private final ConfigManager cfg;
    private final String path = "ChestLock/chestlocks.json";

    public ChestLockStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    private String keyFor(Location loc) {
        List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
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

    public Optional<ChestLock> get(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        Object obj = pc.get(canonical);
        if (obj == null) {
            List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
            for (Location l : related) {
                String k = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
                Object o2 = pc.get(k);
                if (o2 instanceof Map) {
                    obj = o2;
                    pc.put(canonical, obj);
                    for (Location rem : related) pc.getData().remove(rem.getWorld().getName() + ":" + rem.getBlockX() + ":" + rem.getBlockY() + ":" + rem.getBlockZ());
                    cfg.saveConfig(path, pc);
                    break;
                }
            }
        }
        if (obj instanceof Map) {
            return Optional.of(readLock((Map<String, Object>) obj));
        }
        return Optional.empty();
    }

    public boolean save(Location loc, ChestLock lock) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("ownerName", lock.getOwnerName());
        entry.put("ownerUuids", lock.getOwnerUuids());
        entry.put("name", lock.getName());
        entry.put("memberUuids", lock.getMemberUuids());
        entry.put("memberNames", lock.getMemberNames());
        String canonical = keyFor(loc);
        pc.put(canonical, entry);
        List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
        for (Location l : related) {
            String k = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
            if (!k.equals(canonical)) pc.getData().remove(k);
        }
        return cfg.saveConfig(path, pc);
    }

    public boolean remove(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        String canonical = keyFor(loc);
        pc.getData().remove(canonical);
        List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
        for (Location l : related) {
            pc.getData().remove(l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ());
        }
        return cfg.saveConfig(path, pc);
    }

    public Map<String, ChestLock> getAll() {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        Map<String, ChestLock> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : pc.getData().entrySet()) {
            Object obj = e.getValue();
            if (!(obj instanceof Map)) continue;
            out.put(e.getKey(), readLock((Map<String, Object>) obj));
        }
        return out;
    }

    private ChestLock readLock(Map<String, Object> map) {
        ChestLock lock = new ChestLock(null, (String) map.get("ownerName"), (String) map.get("name"));
        lock.setOwnerUuids(readStringList(map.get("ownerUuids")));
        lock.setMemberUuids(readStringList(map.get("memberUuids")));
        lock.setMemberNames(readStringList(map.get("memberNames")));
        return lock;
    }

    private List<String> readStringList(Object value) {
        List<String> result = new ArrayList<>();
        if (!(value instanceof List)) return result;
        List<Object> list = (List<Object>) value;
        for (Object o : list) result.add(String.valueOf(o));
        return result;
    }
}
