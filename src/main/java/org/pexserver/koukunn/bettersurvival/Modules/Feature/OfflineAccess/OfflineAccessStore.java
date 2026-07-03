package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * オフラインアクセス許可プレイヤー名を永続化するストア。
 * <p>
 * PEXConfig/JSON 形式で {@code PEXConfig/OfflineAccess/allowed_names.json} に保存する。
 * </p>
 */
public class OfflineAccessStore {

    private static final String CONFIG_PATH = "OfflineAccess/allowed_names.json";
    private static final String KEY_ALLOWED = "allowed";

    private final ConfigManager configManager;
    private final Set<String> allowed = ConcurrentHashMap.newKeySet();

    public OfflineAccessStore(ConfigManager configManager) {
        this.configManager = configManager;
        loadFromDisk();
    }

    /**
     * 許可リストに追加する。
     *
     * @param name プレイヤー名
     * @return 新規追加された場合 true
     */
    public synchronized boolean add(String name) {
        String normalized = normalize(name);
        if (normalized.isEmpty()) {
            return false;
        }
        boolean added = allowed.add(normalized);
        if (added) {
            saveToDisk();
        }
        return added;
    }

    /**
     * 許可リストから削除する。
     *
     * @param name プレイヤー名
     * @return 削除された場合 true
     */
    public synchronized boolean remove(String name) {
        String normalized = normalize(name);
        boolean removed = allowed.remove(normalized);
        if (removed) {
            saveToDisk();
        }
        return removed;
    }

    /**
     * 許可リストに含まれるか確認する。
     *
     * @param name プレイヤー名
     * @return 含まれる場合 true
     */
    public boolean contains(String name) {
        return allowed.contains(normalize(name));
    }

    /**
     * 許可されているプレイヤー名の一覧を返す。
     *
     * @return プレイヤー名セット（コピー）
     */
    public Set<String> getAll() {
        return new HashSet<>(allowed);
    }

    private void loadFromDisk() {
        configManager.loadConfig(CONFIG_PATH).ifPresent(cfg -> {
            Object data = cfg.getData().get(KEY_ALLOWED);
            if (!(data instanceof List<?> list)) {
                return;
            }
            allowed.clear();
            for (Object obj : list) {
                if (obj instanceof String s) {
                    String normalized = normalize(s);
                    if (!normalized.isEmpty()) {
                        allowed.add(normalized);
                    }
                }
            }
        });
    }

    private synchronized void saveToDisk() {
        PEXConfig cfg = new PEXConfig();
        List<String> list = new ArrayList<>(allowed);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        cfg.put(KEY_ALLOWED, list);
        configManager.saveConfig(CONFIG_PATH, cfg);
    }

    private String normalize(String name) {
        return name == null ? "" : name.trim().toLowerCase(Locale.ROOT);
    }
}
