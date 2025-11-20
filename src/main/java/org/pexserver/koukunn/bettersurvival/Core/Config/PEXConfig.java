package org.pexserver.koukunn.bettersurvival.Core.Config;

import java.util.HashMap;
import java.util.Map;

/**
 * シンプルな PEXConfig の POJO。実際の設定フィールドはここに追加してください。
 */
public class PEXConfig {
    private Map<String, Object> data = new HashMap<>();

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }

    public void put(String key, Object value) {
        data.put(key, value);
    }

    public Object get(String key) {
        return data.get(key);
    }
}
