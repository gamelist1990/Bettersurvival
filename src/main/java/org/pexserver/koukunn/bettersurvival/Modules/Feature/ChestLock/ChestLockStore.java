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
    private final String path = "chestlocks.json";

    public ChestLockStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    private String keyFor(Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + ":" + loc.getBlockY() + ":" + loc.getBlockZ();
    }

    public Optional<ChestLock> get(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        Object obj = pc.get(keyFor(loc));
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
        pc.put(keyFor(loc), entry);
        return cfg.saveConfig(path, pc);
    }

    public boolean remove(Location loc) {
        PEXConfig pc = cfg.loadConfig(path).orElseGet(PEXConfig::new);
        pc.getData().remove(keyFor(loc));
        return cfg.saveConfig(path, pc);
    }

}
