package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api;

import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * カスタムエンチャントの登録簿。
 * 登録と同時にイベントリスナーとしても有効化する。
 */
public class CustomEnchantRegistry {

    private final Loader plugin;
    private final Map<String, CustomEnchant> enchants = new LinkedHashMap<>();

    public CustomEnchantRegistry(Loader plugin) {
        this.plugin = plugin;
    }

    public void register(CustomEnchant enchant) {
        enchants.put(enchant.id(), enchant);
        plugin.getServer().getPluginManager().registerEvents(enchant, plugin);
    }

    public CustomEnchant byId(String id) {
        return enchants.get(id);
    }

    public Collection<CustomEnchant> all() {
        return Collections.unmodifiableCollection(enchants.values());
    }

    public void shutdownAll() {
        for (CustomEnchant enchant : enchants.values()) {
            enchant.shutdown();
        }
    }
}
