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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 開示・削除等の本人請求の JSON 永続化 (PEXConfig/WebService/privacy_requests.json)。
 */
public class PrivacyRequestStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type LIST_TYPE = new TypeToken<List<PrivacyRequest>>() {}.getType();

    private final ConfigManager configManager;
    private final File file;
    private final List<PrivacyRequest> requests = new ArrayList<>();

    public PrivacyRequestStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.file = new File(configManager.getBaseDir(), "WebService/privacy_requests.json");
        load();
    }

    public synchronized PrivacyRequest add(PrivacyRequest request) {
        requests.add(request);
        flush();
        return request;
    }

    public synchronized Optional<PrivacyRequest> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return requests.stream().filter(request -> id.equals(request.getId())).findFirst();
    }

    public synchronized List<PrivacyRequest> listByUuid(String uuid) {
        return requests.stream()
                .filter(request -> request.getUuid().equals(uuid))
                .sorted(Comparator.comparingLong(PrivacyRequest::getCreatedAt).reversed())
                .toList();
    }

    public synchronized List<PrivacyRequest> listAll() {
        return requests.stream()
                .sorted(Comparator.comparing(PrivacyRequest::isOpen).reversed()
                        .thenComparing(Comparator.comparingLong(PrivacyRequest::getCreatedAt).reversed()))
                .toList();
    }

    public synchronized long countOpen() {
        return requests.stream().filter(PrivacyRequest::isOpen).count();
    }

    public synchronized long countOpenByUuid(String uuid) {
        return requests.stream().filter(request -> request.isOpen() && request.getUuid().equals(uuid)).count();
    }

    public synchronized void save() {
        flush();
    }

    private void load() {
        if (!file.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            List<PrivacyRequest> loaded = GSON.fromJson(reader, LIST_TYPE);
            if (loaded != null) {
                requests.clear();
                requests.addAll(loaded);
            }
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService privacy_requests 読み込み失敗: " + error.getMessage());
        }
    }

    private void flush() {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(requests, LIST_TYPE, writer);
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService privacy_requests 保存失敗: " + error.getMessage());
        }
    }
}
