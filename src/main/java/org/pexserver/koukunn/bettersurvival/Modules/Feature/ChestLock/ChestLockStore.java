package org.pexserver.koukunn.bettersurvival.Modules.Feature.ChestLock;

import org.bukkit.Location;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;

public class ChestLockStore {

    private final ConfigManager cfg;
    private final String path = "ChestLock/chestlocks.json";

    public ChestLockStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    private String keyFor(Location loc) {
        // canonicalize key for single or large chests so that adjacent chest pairs share one key
        List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
        if (related == null || related.isEmpty()) {
            return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
        }
        // pick a deterministic smallest coordinate to represent the set
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
        // migration: if not found at canonical key, check other related keys and normalize
        if (obj == null) {
            List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
            for (Location l : related) {
                String k = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
                Object o2 = pc.get(k);
                if (o2 instanceof Map) {
                    obj = o2;
                    // migrate to canonical key and remove old keys
                    pc.put(canonical, obj);
                    for (Location rem : related) pc.getData().remove(rem.getWorld().getName() + ":" + rem.getBlockX() + ":" + rem.getBlockY() + ":" + rem.getBlockZ());
                    cfg.saveConfig(path, pc);
                    break;
                }
            }
        }
        if (obj instanceof Map) {
            Map<String,Object> map = (Map<String,Object>) obj;
            ChestLock lock = new ChestLock((String) map.get("owner"), (String) map.get("name"));
            Object mem = map.get("members");
            if (mem instanceof List) {
                List<Object> list = (List<Object>) mem;
                List<String> members = new ArrayList<>();
                for (Object o : list) members.add(String.valueOf(o));
                lock.setMembers(members);
            }
            return Optional.of(lock);
        }
        return Optional.empty();
    }

    public boolean save(Location loc, ChestLock lock) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("owner", lock.getOwner());
        entry.put("name", lock.getName());
        entry.put("members", lock.getMembers());
        // always save under canonical set key
        String canonical = keyFor(loc);
        pc.put(canonical, entry);
        // also remove legacy per-block keys inside the same set to avoid duplicates
        List<Location> related = ChestLockModule.getChestRelatedLocations(loc.getBlock());
        for (Location l : related) {
            String k = l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
            if (!k.equals(canonical)) pc.getData().remove(k);
        }
        return cfg.saveConfig(path, pc);
    }

    public boolean remove(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        // remove canonical and any legacy per-block keys in the set
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
            Map<String, Object> map = (Map<String, Object>) obj;
            ChestLock lock = new ChestLock((String) map.get("owner"), (String) map.get("name"));
            Object mem = map.get("members");
            if (mem instanceof List) {
                List<Object> list = (List<Object>) mem;
                List<String> members = new ArrayList<>();
                for (Object o : list) members.add(String.valueOf(o));
                lock.setMembers(members);
            }
            out.put(e.getKey(), lock);
        }
        return out;
    }

}
