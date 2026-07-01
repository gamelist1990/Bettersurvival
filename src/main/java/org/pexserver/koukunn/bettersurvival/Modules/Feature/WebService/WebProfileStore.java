package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.Writer;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class WebProfileStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type PROFILE_MAP_TYPE = new TypeToken<Map<String, WebProfile>>() {
    }.getType();

    private final ConfigManager configManager;
    private final File profileFile;
    private final Map<String, WebProfile> profiles = new LinkedHashMap<>();

    public WebProfileStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.profileFile = new File(configManager.getBaseDir(), "WebService/profiles.json");
        load();
    }

    public synchronized Optional<WebProfile> findByUuid(String uuid) {
        if (uuid == null || uuid.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(uuid));
    }

    public synchronized Optional<WebProfile> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return Optional.empty();
        }
        String normalized = normalizeUsername(username);
        return profiles.values().stream()
                .filter(profile -> normalizeUsername(profile.getUsername()).equals(normalized))
                .findFirst();
    }

    public synchronized WebProfile save(WebProfile profile) {
        profile.setUpdatedAt(System.currentTimeMillis());
        profiles.put(profile.getUuid(), profile);
        flush();
        return profile;
    }

    private void load() {
        if (!profileFile.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(profileFile), StandardCharsets.UTF_8)) {
            Map<String, WebProfile> loaded = GSON.fromJson(reader, PROFILE_MAP_TYPE);
            if (loaded != null) {
                profiles.clear();
                profiles.putAll(loaded);
            }
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService profiles 読み込み失敗: " + error.getMessage());
        }
    }

    private void flush() {
        File parent = profileFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(profileFile), StandardCharsets.UTF_8)) {
            GSON.toJson(profiles, PROFILE_MAP_TYPE, writer);
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService profiles 保存失敗: " + error.getMessage());
        }
    }

    private String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
