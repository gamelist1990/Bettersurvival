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

public class WebPostStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type POST_LIST_TYPE = new TypeToken<List<WebPost>>() {}.getType();

    private final ConfigManager configManager;
    private final File postFile;
    private final List<WebPost> posts = new ArrayList<>();

    public WebPostStore(ConfigManager configManager) {
        this.configManager = configManager;
        this.postFile = new File(configManager.getBaseDir(), "WebService/posts.json");
        load();
    }

    public synchronized WebPost add(WebPost post, int retentionDays) {
        posts.add(post);
        prune(retentionDays);
        flush();
        return post;
    }

    public synchronized WebPost save(WebPost post) {
        flush();
        return post;
    }

    public synchronized List<WebPost> listSince(long since, int limit, int retentionDays) {
        prune(retentionDays);
        return posts.stream()
                .filter(post -> !post.isDeleted())
                .filter(post -> post.getCreatedAt() > since)
                .sorted(Comparator.comparingLong(WebPost::getCreatedAt).reversed())
                .limit(Math.max(1, Math.min(200, limit)))
                .toList();
    }

    public synchronized List<WebPost> listLatest(int limit, int retentionDays) {
        return listSince(0L, limit, retentionDays);
    }

    public synchronized Optional<WebPost> findById(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return posts.stream().filter(post -> !post.isDeleted()).filter(post -> id.equals(post.getId())).findFirst();
    }

    public synchronized boolean softDelete(String id, String ownerUuid) {
        Optional<WebPost> optionalPost = findById(id);
        if (optionalPost.isEmpty()) {
            return false;
        }
        WebPost post = optionalPost.get();
        if (ownerUuid == null || ownerUuid.isBlank() || !ownerUuid.equals(post.getUuid())) {
            return false;
        }
        post.setDeleted(true);
        flush();
        return true;
    }

    public synchronized void prune(int retentionDays) {
        long retentionMillis = Math.max(1, Math.min(30, retentionDays)) * 24L * 60L * 60L * 1000L;
        long threshold = System.currentTimeMillis() - retentionMillis;
        boolean removed = posts.removeIf(post -> post.getCreatedAt() < threshold);
        if (removed) {
            flush();
        }
    }

    private void load() {
        if (!postFile.exists()) {
            return;
        }
        try (Reader reader = new InputStreamReader(new FileInputStream(postFile), StandardCharsets.UTF_8)) {
            List<WebPost> loaded = GSON.fromJson(reader, POST_LIST_TYPE);
            if (loaded != null) {
                posts.clear();
                posts.addAll(loaded);
            }
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService posts 読み込み失敗: " + error.getMessage());
        }
    }

    private void flush() {
        File parent = postFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(postFile), StandardCharsets.UTF_8)) {
            GSON.toJson(posts, POST_LIST_TYPE, writer);
        } catch (IOException | RuntimeException error) {
            configManager.getPlugin().getLogger().warning("WebService posts 保存失敗: " + error.getMessage());
        }
    }
}
