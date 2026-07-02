package org.pexserver.koukunn.bettersurvival.Modules.Feature.Party;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * パーティーの JSON 永続化 (PEXConfig/Party/parties.json)。
 */
@SuppressWarnings("unchecked")
public class PartyStore {

    private static final String PATH = "Party/parties.json";

    private final ConfigManager cfg;

    public PartyStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    public Map<UUID, Party> loadAll() {
        Map<UUID, Party> out = new LinkedHashMap<>();
        PEXConfig pc = cfg.loadConfig(PATH).orElseGet(PEXConfig::new);
        for (Map.Entry<String, Object> entry : pc.getData().entrySet()) {
            if (!(entry.getValue() instanceof Map)) {
                continue;
            }
            try {
                Party party = readParty(UUID.fromString(entry.getKey()), (Map<String, Object>) entry.getValue());
                if (party != null) {
                    out.put(party.getId(), party);
                }
            } catch (Exception ignored) {
                // 壊れたエントリはスキップ
            }
        }
        return out;
    }

    public boolean saveAll(Collection<Party> parties) {
        PEXConfig pc = new PEXConfig();
        for (Party party : parties) {
            pc.put(party.getId().toString(), writeParty(party));
        }
        return cfg.saveConfig(PATH, pc);
    }

    private Map<String, Object> writeParty(Party party) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("name", party.getName());
        map.put("color", party.getColorKey());
        map.put("description", party.getDescription());
        map.put("leader", party.getLeader().toString());
        map.put("coLeaders", toStringList(party.getCoLeaders()));
        map.put("members", toStringList(party.getMembers()));
        Map<String, Object> names = new LinkedHashMap<>();
        for (Map.Entry<UUID, String> e : party.getKnownNames().entrySet()) {
            names.put(e.getKey().toString(), e.getValue());
        }
        map.put("knownNames", names);
        map.put("createdAt", party.getCreatedAt());
        return map;
    }

    private Party readParty(UUID id, Map<String, Object> map) {
        Object leaderRaw = map.get("leader");
        if (!(leaderRaw instanceof String)) {
            return null;
        }
        Party party = new Party(
                id,
                String.valueOf(map.getOrDefault("name", "party")),
                String.valueOf(map.getOrDefault("color", "WHITE")),
                String.valueOf(map.getOrDefault("description", "")),
                UUID.fromString((String) leaderRaw));
        for (UUID uuid : readUuidList(map.get("coLeaders"))) {
            party.getCoLeaders().add(uuid);
        }
        for (UUID uuid : readUuidList(map.get("members"))) {
            party.getMembers().add(uuid);
        }
        if (map.get("knownNames") instanceof Map) {
            for (Map.Entry<String, Object> e : ((Map<String, Object>) map.get("knownNames")).entrySet()) {
                try {
                    party.rememberName(UUID.fromString(e.getKey()), String.valueOf(e.getValue()));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        if (map.get("createdAt") instanceof Number number) {
            party.setCreatedAt(number.longValue());
        }
        return party;
    }

    private List<String> toStringList(Collection<UUID> uuids) {
        List<String> list = new ArrayList<>();
        for (UUID uuid : uuids) {
            list.add(uuid.toString());
        }
        return list;
    }

    private List<UUID> readUuidList(Object value) {
        List<UUID> out = new ArrayList<>();
        if (!(value instanceof List)) {
            return out;
        }
        for (Object o : (List<Object>) value) {
            try {
                out.add(UUID.fromString(String.valueOf(o)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        return out;
    }
}
