package org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.Map;

/**
 * トーナメントの JSON 永続化 (PEXConfig/Tournament/tournament.json)。
 */
@SuppressWarnings("unchecked")
public class TournamentStore {

    private static final String PATH = "Tournament/tournament.json";

    private final ConfigManager cfg;

    public TournamentStore(ConfigManager cfg) {
        this.cfg = cfg;
    }

    /** 保存済みのトーナメント（なければ null）。 */
    public Tournament load() {
        PEXConfig pc = cfg.loadConfig(PATH).orElse(null);
        if (pc == null || !(pc.getData().get("tournament") instanceof Map<?, ?> map)) {
            return null;
        }
        try {
            return Tournament.fromMap((Map<String, Object>) map);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean save(Tournament tournament) {
        PEXConfig pc = new PEXConfig();
        pc.put("tournament", tournament == null ? null : tournament.toMap());
        return cfg.saveConfig(PATH, pc);
    }
}
