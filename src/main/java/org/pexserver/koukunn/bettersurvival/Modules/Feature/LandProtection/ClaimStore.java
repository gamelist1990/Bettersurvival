package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 保護領域の JSON 永続化 (PEXConfig/LandProtection/claims.json)。
 */
@SuppressWarnings("unchecked")
public class ClaimStore {

    private static final String PATH = "LandProtection/claims.json";

    private final ConfigManager cfg;

    public ClaimStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    public Map<String, ClaimRegion> loadAll() {
        Map<String, ClaimRegion> out = new LinkedHashMap<>();
        PEXConfig pc = cfg.loadConfig(PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : pc.getData().entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            try {
                ClaimRegion claim = readClaim(entry.getKey(), (Map<String, Object>) entry.getValue());
                if (claim != null) {
                    out.put(claim.key(), claim);
                }
            } catch (Exception ignored) {
                // 壊れたエントリはスキップ
            }
        }
        return out;
    }

    public boolean saveAll(Collection<ClaimRegion> claims) {
        PEXConfig pc = new PEXConfig();
        for (ClaimRegion claim : claims) {
            pc.put(claim.key(), writeClaim(claim));
        }
        return cfg.saveConfig(PATH, pc);
    }

    private Map<String, Object> writeClaim(ClaimRegion claim) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("owner", claim.getOwner() == null ? null : claim.getOwner().toString());
        map.put("ownerName", claim.getOwnerName());
        map.put("party", claim.getPartyId() == null ? null : claim.getPartyId().toString());
        map.put("level", claim.getLevel());
        map.put("fuel", claim.getFuelUnits());
        map.put("lastUpkeep", claim.getLastUpkeepMillis());
        Map<String, Object> whitelist = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : claim.getWhitelist().entrySet()) {
            whitelist.put(e.getKey().toString(), e.getValue());
        }
        map.put("whitelist", whitelist);
        map.put("settings", claim.getSettings().toMap());
        map.put("createdAt", claim.getCreatedAt());
        return map;
    }

    private ClaimRegion readClaim(String key, Map<String, Object> map) {
        String[] parts = key.split(":");
        if (parts.length != 4) {
            return null;
        }
        ClaimRegion claim = new ClaimRegion(parts[0],
                Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
        if (map.get("owner") instanceof String s && !s.isEmpty()) {
            claim.setOwner(UUID.fromString(s));
        }
        if (map.get("ownerName") instanceof String s) {
            claim.setOwnerName(s);
        }
        if (map.get("party") instanceof String s && !s.isEmpty()) {
            claim.setPartyId(UUID.fromString(s));
        }
        if (map.get("level") instanceof Number n) {
            claim.setLevel(n.intValue());
        }
        if (map.get("fuel") instanceof Number n) {
            claim.setFuelUnits(n.doubleValue());
        }
        if (map.get("lastUpkeep") instanceof Number n) {
            claim.setLastUpkeepMillis(n.longValue());
        }
        if (map.get("whitelist") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) map.get("whitelist")).entrySet()) {
                try {
                    claim.getWhitelist().put(UUID.fromString(e.getKey()), String.valueOf(e.getValue()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (map.get("settings") instanceof Map) {
            claim.setSettings(ClaimSettings.fromMap((Map<String, Object>) map.get("settings")));
        }
        if (map.get("createdAt") instanceof Number n) {
            claim.setCreatedAt(n.longValue());
        }
        return claim;
    }
}
