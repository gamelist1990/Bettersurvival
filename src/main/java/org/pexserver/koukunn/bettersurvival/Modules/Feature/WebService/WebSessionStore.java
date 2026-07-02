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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class WebSessionStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type SESSION_MAP_TYPE = new TypeToken<Map<String, WebSession>>() {
    }.getType();

    private final ConfigManager configManager;
    private final File sessionFile;
    private final Map<String, WebSession> sessions = new LinkedHashMap<>();

    public WebSessionStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.sessionFile = new File(configManager.getBaseDir(), "WebService/sessions.json");
        load();
    }

    public synchronized Map<String, WebSession> loadActive(long now) {
        boolean removed = sessions.entrySet().removeIf(entry -> entry.getValue() == null || entry.getValue().isExpired(now));
        if (removed) {
            flush();
        }
        return new LinkedHashMap<>(sessions);
    }

    public synchronized void put(WebSession session) {
        if (session == null || session.token() == null || session.token().isBlank()) {
            return;
        }
        sessions.put(session.token(), session);
        flush();
    }

    public synchronized void remove(String token) {
        if (token == null || token.isBlank()) {
            return;
        }
        if (sessions.remove(token) != null) {
            flush();
        }
    }

    public synchronized void removeAll(Collection<String> tokens) {
        if (tokens == null || tokens.isEmpty()) {
            return;
        }
        boolean changed = false;
        for (String token : tokens) {
            if (token != null && sessions.remove(token) != null) {
                changed = true;
            }
        }
        if (changed) {
            flush();
        }
    }

    private void load() {
        if (!sessionFile.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(sessionFile), StandardCharsets.UTF_8)) {
            Map<String, WebSession> loaded = GSON.fromJson(reader, SESSION_MAP_TYPE);
            if (loaded != null) {
                sessions.clear();
                sessions.putAll(loaded);
            }
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService sessions 読み込み失敗: " + error.getMessage());
        }
    }

    private void flush() {
        File parent = sessionFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(sessionFile), StandardCharsets.UTF_8)) {
            GSON.toJson(sessions, SESSION_MAP_TYPE, writer);
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService sessions 保存失敗: " + error.getMessage());
        }
    }
}
