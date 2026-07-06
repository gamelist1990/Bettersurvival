package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * プレイヤーごとの「ツール設定」(各エンチャント効果の ON/OFF) を保持する。
 *
 * 設定は {@link ToolSetting} として登録し、ON/OFF はプレイヤー単位でメモリ上に
 * 持つ (サーバー再起動でリセット)。エンチャント側は {@link #isEnabled} を見て
 * 効果を出すかどうか判断する。
 */
public class ToolSettingsStore {

    /** 登録順を保つため LinkedHashMap */
    private final Map<String, ToolSetting> settings = new LinkedHashMap<>();
    /** playerId -> (settingId -> ON/OFF)。未登録なら既定値を使う */
    private final Map<UUID, Map<String, Boolean>> states = new ConcurrentHashMap<>();

    public void register(ToolSetting setting) {
        settings.put(setting.id(), setting);
    }

    public Collection<ToolSetting> settings() {
        return Collections.unmodifiableCollection(settings.values());
    }

    public ToolSetting get(String id) {
        return settings.get(id);
    }

    public boolean isEnabled(UUID playerId, String id) {
        ToolSetting setting = settings.get(id);
        if (setting == null) {
            return false;
        }
        Map<String, Boolean> state = states.get(playerId);
        if (state != null) {
            Boolean value = state.get(id);
            if (value != null) {
                return value;
            }
        }
        return setting.defaultEnabled();
    }

    /** ON/OFF を反転して新しい状態を返す */
    public boolean toggle(UUID playerId, String id) {
        boolean next = !isEnabled(playerId, id);
        states.computeIfAbsent(playerId, ignored -> new ConcurrentHashMap<>()).put(id, next);
        return next;
    }

    public void clearPlayer(UUID playerId) {
        states.remove(playerId);
    }

    public void clear() {
        states.clear();
    }
}
