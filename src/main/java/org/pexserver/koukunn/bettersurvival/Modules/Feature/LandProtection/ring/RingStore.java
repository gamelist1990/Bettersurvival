package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * リングと対戦ボタンの JSON 永続化 (PEXConfig/LandProtection/rings.json)。
 */
@SuppressWarnings("unchecked")
public class RingStore {

    private static final String PATH = "LandProtection/rings.json";

    private final ConfigManager cfg;

    public RingStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    /** ringId (claimKey#name) -> リング */
    public Map<String, RingRegion> loadRings() {
        Map<String, RingRegion> out = new LinkedHashMap<>();
        PEXConfig pc = cfg.loadConfig(PATH).orElseGet(PEXConfig::new);
        if (pc.getData().get("rings") instanceof Map<?, ?> rings) {
            for (Map.Entry<?, ?> entry : rings.entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                try {
                    Map<String, Object> data = (Map<String, Object>) entry.getValue();
                    // 旧フォーマットはキーが claimKey だったため補完する
                    data.putIfAbsent("claimKey", String.valueOf(entry.getKey()));
                    RingRegion ring = RingRegion.fromMap(data);
                    if (ring != null) {
                        out.put(ring.ringId(), ring);
                    }
                } catch (Exception ignored) {
                    // 壊れたエントリはスキップ
                }
            }
        }
        return out;
    }

    /** 対戦ボタンの位置キー (world:x:y:z) 一覧 */
    public Set<String> loadButtons() {
        Set<String> out = new LinkedHashSet<>();
        PEXConfig pc = cfg.loadConfig(PATH).orElseGet(PEXConfig::new);
        if (pc.getData().get("buttons") instanceof List<?> list) {
            for (Object o : list) {
                if (o instanceof String s && !s.isEmpty()) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    public boolean saveAll(Collection<RingRegion> rings, Collection<String> buttons) {
        PEXConfig pc = new PEXConfig();
        Map<String, Object> ringMap = new LinkedHashMap<>();
        for (RingRegion ring : rings) {
            ringMap.put(ring.ringId(), ring.toMap());
        }
        pc.put("rings", ringMap);
        pc.put("buttons", List.copyOf(buttons));
        return cfg.saveConfig(PATH, pc);
    }
}
