package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WebServiceModule implements Listener {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long HOTP_TTL_MS = 5 * 60 * 1000L;
    private static final long SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000L;
    private static final String SETTINGS_PATH = "WebService/settings.json";
    private static final int MAX_POST_LENGTH = 280;

    private final Loader plugin;
    private final WebProfileStore profileStore;
    private final WebPostStore postStore;
    private final Map<String, PendingHotp> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();
    private boolean webMapFeatureEnabled;
    private boolean feedEnabled;
    private boolean minecraftChatRelayEnabled;
    private boolean webPostToMinecraftEnabled;
    private boolean discordIntegrationEnabled;
    private boolean imageUploadEnabled;
    private int feedRetentionDays;

    public WebServiceModule(Loader plugin) {
        this.plugin = plugin;
        this.profileStore = new WebProfileStore(plugin.getConfigManager());
        this.postStore = new WebPostStore(plugin.getConfigManager());
        loadSettings();
    }

    public Loader getPlugin() {
        return plugin;
    }

    public boolean isGloballyEnabled() {
        return plugin.getToggleModule() != null && plugin.getToggleModule().getGlobal("webservice");
    }

    public void openSettings(Player player) {
        WebServiceSettingsMenu.openMainMenu(player, this);
    }

    public boolean isWebMapFeatureEnabled() {
        return webMapFeatureEnabled;
    }

    public boolean isFeedEnabled() {
        return feedEnabled;
    }

    public boolean isMinecraftChatRelayEnabled() {
        return minecraftChatRelayEnabled;
    }

    public boolean isWebPostToMinecraftEnabled() {
        return webPostToMinecraftEnabled;
    }

    public boolean isDiscordIntegrationEnabled() {
        return discordIntegrationEnabled;
    }

    public boolean isImageUploadEnabled() {
        return imageUploadEnabled;
    }

    public int getFeedRetentionDays() {
        return feedRetentionDays;
    }

    public boolean isWebMapAccessEnabled() {
        return isGloballyEnabled()
                && webMapFeatureEnabled
                && plugin.getToggleModule() != null
                && plugin.getToggleModule().getGlobal("webmap");
    }

    public void setGloballyEnabled(boolean enabled) {
        if (plugin.getToggleModule() != null) {
            plugin.getToggleModule().setGlobal("webservice", enabled);
        }
        restartSharedHttpServer();
    }

    public void toggleGloballyEnabled() {
        setGloballyEnabled(!isGloballyEnabled());
    }

    public void setWebMapFeatureEnabled(boolean enabled) {
        webMapFeatureEnabled = enabled;
        saveSettings();
    }

    public void toggleWebMapFeatureEnabled() {
        setWebMapFeatureEnabled(!webMapFeatureEnabled);
    }

    public void toggleFeedEnabled() {
        feedEnabled = !feedEnabled;
        saveSettings();
    }

    public void toggleMinecraftChatRelayEnabled() {
        minecraftChatRelayEnabled = !minecraftChatRelayEnabled;
        saveSettings();
    }

    public void toggleWebPostToMinecraftEnabled() {
        webPostToMinecraftEnabled = !webPostToMinecraftEnabled;
        saveSettings();
    }

    public void toggleDiscordIntegrationEnabled() {
        discordIntegrationEnabled = !discordIntegrationEnabled;
        saveSettings();
    }

    public void toggleImageUploadEnabled() {
        imageUploadEnabled = !imageUploadEnabled;
        saveSettings();
    }

    public void updateFeedRetentionDays(int days) {
        feedRetentionDays = Math.max(1, Math.min(30, days));
        postStore.prune(feedRetentionDays);
        saveSettings();
    }

    public void restartSharedHttpServer() {
        if (plugin.getWebMapModule() != null) {
            plugin.getWebMapModule().restartServer();
        }
    }

    public String issueHotp(Player player) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        pendingCodes.put(player.getUniqueId().toString(), new PendingHotp(code, System.currentTimeMillis() + HOTP_TTL_MS));
        cleanupExpiredCodes();
        return code;
    }

    public Optional<WebProfile> findProfileBySession(String token) {
        cleanupExpiredSessions();
        WebSession session = sessions.get(token);
        if (session == null || session.isExpired(System.currentTimeMillis())) {
            if (session != null) {
                sessions.remove(token);
            }
            return Optional.empty();
        }
        return profileStore.findByUuid(session.uuid());
    }

    public AuthResult register(String username, String code, String email, String password) {
        String trimmedCode = code == null ? "" : code.trim();
        cleanupExpiredCodes();
        Map.Entry<String, PendingHotp> matchedEntry = pendingCodes.entrySet().stream()
                .filter(entry -> entry.getValue().code().equals(trimmedCode))
                .findFirst()
                .orElse(null);
        if (matchedEntry == null) {
            return AuthResult.error("ワンタイムコードが一致しないか期限切れです。Minecraft 内で /hotp を再実行してください");
        }
        String uuid = matchedEntry.getKey();
        PendingHotp pending = matchedEntry.getValue();
        if (pending == null || pending.isExpired(System.currentTimeMillis())) {
            pendingCodes.remove(uuid);
            return AuthResult.error("ワンタイムコードが期限切れです。Minecraft 内で /hotp を再実行してください");
        }
        Player player;
        try {
            player = plugin.getServer().getPlayer(UUID.fromString(uuid));
        } catch (IllegalArgumentException error) {
            pendingCodes.remove(uuid);
            return AuthResult.error("ワンタイムコードの所有者を確認できませんでした。/hotp を再実行してください");
        }
        if (player == null) {
            pendingCodes.remove(uuid);
            return AuthResult.error("ワンタイムコードを発行したプレイヤーがオフラインです。Minecraft に参加して /hotp を再実行してください");
        }
        if (password == null || password.length() < 8) {
            return AuthResult.error("パスワードは8文字以上で入力してください");
        }
        WebProfile profile = profileStore.findByUuid(uuid).orElseGet(WebProfile::new);
        profile.setUuid(uuid);
        profile.setUsername(player.getName());
        profile.setDisplayName(player.getName());
        profile.setFaceUrl(McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(player)));
        profile.setEmail(email == null ? "" : email.trim());
        PasswordHash passwordHash = hashPassword(password);
        profile.setPasswordSalt(passwordHash.salt());
        profile.setPasswordHash(passwordHash.hash());
        profile.setPasskeyEnabled(false);
        profile.setPasskeyLabel("");
        profileStore.save(profile);
        pendingCodes.remove(uuid);
        return AuthResult.success(profile, createSession(uuid));
    }

    public AuthResult login(String username, String password) {
        Optional<WebProfile> optionalProfile = profileStore.findByUsername(username);
        if (optionalProfile.isEmpty()) {
            return AuthResult.error("ユーザーが登録されていません");
        }
        WebProfile profile = optionalProfile.get();
        if (!verifyPassword(password == null ? "" : password, profile.getPasswordSalt(), profile.getPasswordHash())) {
            return AuthResult.error("ユーザー名またはパスワードが違います");
        }
        return AuthResult.success(profile, createSession(profile.getUuid()));
    }

    public ProfileResult updateProfile(String token, Map<String, Object> request) {
        Optional<WebProfile> optionalProfile = findProfileBySession(token);
        if (optionalProfile.isEmpty()) {
            return ProfileResult.error("ログインが必要です");
        }
        WebProfile profile = optionalProfile.get();
        profile.setNickname(limit(stringValue(request.get("nickname")), 32));
        profile.setBio(limit(stringValue(request.get("bio")), 300));
        profile.setLocation(limit(stringValue(request.get("location")), 80));
        profile.setCountry(limit(stringValue(request.get("country")), 60));
        profile.setRegion(limit(stringValue(request.get("region")), 80));
        profile.setBannerUrl(limit(stringValue(request.get("bannerUrl")), 600));
        profile.setWebsite(limit(stringValue(request.get("website")), 300));
        profile.setXUrl(limit(stringValue(request.get("xUrl")), 300));
        profile.setYoutubeUrl(limit(stringValue(request.get("youtubeUrl")), 300));
        profile.setInstagramUrl(limit(stringValue(request.get("instagramUrl")), 300));
        profileStore.save(profile);
        return ProfileResult.success(profile);
    }

    public PostResult createWebPost(String token, String text, List<WebPost.Attachment> attachments, String replyToId) {
        if (!feedEnabled) {
            return PostResult.error("Web feed is disabled");
        }
        Optional<WebProfile> optionalProfile = findProfileBySession(token);
        if (optionalProfile.isEmpty()) {
            return PostResult.error("ログインが必要です");
        }
        WebProfile profile = optionalProfile.get();
        String normalizedReplyToId = limit(replyToId, 80);
        String replyToUsername = "";
        if (!normalizedReplyToId.isBlank()) {
            Optional<WebPost> parent = postStore.findById(normalizedReplyToId);
            if (parent.isEmpty()) {
                return PostResult.error("返信先の投稿が見つかりません");
            }
            WebPost parentPost = parent.get();
            parentPost.setReplies(parentPost.getReplies() + 1);
            postStore.save(parentPost);
            replyToUsername = parentPost.getUsername();
        }
        WebPost post = buildPost(profile, "web", limit(text, MAX_POST_LENGTH), attachments, normalizedReplyToId, replyToUsername);
        postStore.add(post, feedRetentionDays);
        if (webPostToMinecraftEnabled && !post.getText().isBlank()) {
            plugin.getServer().sendMessage(webPostComponent(post, profile.getUsername()));
        }
        return PostResult.success(post);
    }

    public PostResult createDiscordPost(String authorId, String authorName, String avatarUrl, String channelId, String messageId, String messageUrl, String text, List<WebPost.Attachment> attachments, String replyToMessageId) {
        if (!feedEnabled || !discordIntegrationEnabled) {
            return PostResult.error("Discord integration is disabled");
        }
        String normalizedReplyToId = limit(replyToMessageId, 120);
        String replyToUsername = "";
        if (!normalizedReplyToId.isBlank()) {
            Optional<WebPost> parent = postStore.findByExternalId(normalizedReplyToId);
            if (parent.isPresent()) {
                WebPost parentPost = parent.get();
                parentPost.setReplies(parentPost.getReplies() + 1);
                postStore.save(parentPost);
                replyToUsername = parentPost.getUsername();
            }
        }
        WebProfile profile = new WebProfile();
        profile.setUuid("discord:" + limit(authorId, 80));
        profile.setUsername(limit(authorName, 40));
        profile.setDisplayName(limit(authorName, 40));
        profile.setFaceUrl(limit(avatarUrl, 600));
        WebPost post = buildPost(profile, "discord", limit(text, MAX_POST_LENGTH), attachments, "", replyToUsername);
        post.setExternalId(limit(messageId, 120));
        post.setExternalUrl(limit(messageUrl, 600));
        post.setExternalChannelId(limit(channelId, 120));
        post.setExternalAuthorId(limit(authorId, 120));
        post.setExternalReplyToId(normalizedReplyToId);
        postStore.add(post, feedRetentionDays);
        return PostResult.success(post);
    }

    public PostResult likePost(String token, String postId) {
        Optional<WebProfile> optionalProfile = findProfileBySession(token);
        if (optionalProfile.isEmpty()) {
            return PostResult.error("ログインが必要です");
        }
        Optional<WebPost> optionalPost = postStore.findById(postId);
        if (optionalPost.isEmpty()) {
            return PostResult.error("投稿が見つかりません");
        }
        WebPost post = optionalPost.get();
        post.toggleLike(optionalProfile.get().getUuid());
        postStore.save(post);
        return PostResult.success(post);
    }

    public PostResult repostPost(String token, String postId) {
        Optional<WebProfile> optionalProfile = findProfileBySession(token);
        if (optionalProfile.isEmpty()) {
            return PostResult.error("ログインが必要です");
        }
        Optional<WebPost> optionalPost = postStore.findById(postId);
        if (optionalPost.isEmpty()) {
            return PostResult.error("投稿が見つかりません");
        }
        WebPost post = optionalPost.get();
        post.toggleRepost(optionalProfile.get().getUuid());
        postStore.save(post);
        return PostResult.success(post);
    }

    public boolean deletePost(String token, String postId) {
        Optional<WebProfile> optionalProfile = findProfileBySession(token);
        return optionalProfile.isPresent() && postStore.softDelete(postId, optionalProfile.get().getUuid());
    }

    public Optional<String> sessionUuid(String token) {
        return findProfileBySession(token).map(WebProfile::getUuid);
    }

    public List<WebPost> listPosts(long since, int limit) {
        if (!feedEnabled) {
            return List.of();
        }
        return postStore.listSince(since, limit, feedRetentionDays);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAsyncChat(AsyncChatEvent event) {
        if (!feedEnabled || !minecraftChatRelayEnabled) {
            return;
        }
        String message = PlainTextComponentSerializer.plainText().serialize(event.message()).trim();
        if (message.isBlank()) {
            return;
        }
        Player player = event.getPlayer();
        WebProfile profile = profileStore.findByUuid(player.getUniqueId().toString()).orElseGet(() -> {
            WebProfile fallback = new WebProfile();
            fallback.setUuid(player.getUniqueId().toString());
            fallback.setUsername(player.getName());
            fallback.setDisplayName(player.getName());
            fallback.setFaceUrl(McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(player)));
            return fallback;
        });
        postStore.add(buildPost(profile, "minecraft", limit(message, MAX_POST_LENGTH), List.of(), "", ""), feedRetentionDays);
    }

    public Map<String, Object> profilePayload(WebProfile profile) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("uuid", profile.getUuid());
        payload.put("username", profile.getUsername());
        payload.put("displayName", profile.getDisplayName());
        payload.put("nickname", profile.getNickname());
        payload.put("faceUrl", profile.getFaceUrl());
        payload.put("email", profile.getEmail());
        payload.put("bio", profile.getBio());
        payload.put("location", profile.getLocation());
        payload.put("country", profile.getCountry());
        payload.put("region", profile.getRegion());
        payload.put("bannerUrl", profile.getBannerUrl());
        payload.put("website", profile.getWebsite());
        payload.put("xUrl", profile.getXUrl());
        payload.put("youtubeUrl", profile.getYoutubeUrl());
        payload.put("instagramUrl", profile.getInstagramUrl());
        payload.put("passkeyEnabled", profile.isPasskeyEnabled());
        payload.put("createdAt", profile.getCreatedAt());
        payload.put("updatedAt", profile.getUpdatedAt());
        return payload;
    }

    public Map<String, Object> postPayload(WebPost post) {
        return postPayload(post, "");
    }

    public Map<String, Object> postPayload(WebPost post, String viewerUuid) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", post.getId());
        payload.put("uuid", post.getUuid());
        payload.put("username", post.getUsername());
        payload.put("displayName", post.getDisplayName());
        payload.put("nickname", post.getNickname());
        payload.put("faceUrl", post.getFaceUrl());
        payload.put("source", post.getSource());
        payload.put("text", post.getText());
        payload.put("attachments", post.getAttachments());
        payload.put("createdAt", post.getCreatedAt());
        payload.put("replyToId", post.getReplyToId());
        payload.put("replyToUsername", post.getReplyToUsername());
        payload.put("externalId", post.getExternalId());
        payload.put("externalUrl", post.getExternalUrl());
        payload.put("externalChannelId", post.getExternalChannelId());
        payload.put("externalAuthorId", post.getExternalAuthorId());
        payload.put("externalReplyToId", post.getExternalReplyToId());
        payload.put("likes", post.getLikes());
        payload.put("replies", post.getReplies());
        payload.put("reposts", post.getReposts());
        payload.put("likedByMe", post.isLikedBy(viewerUuid));
        payload.put("repostedByMe", post.isRepostedBy(viewerUuid));
        return payload;
    }

    public void logout(String token) {
        if (token != null) {
            sessions.remove(token);
        }
    }

    public void shutdown() {
        pendingCodes.clear();
        sessions.clear();
    }

    private void loadSettings() {
        PEXConfig config = plugin.getConfigManager().loadConfig(SETTINGS_PATH).orElseGet(PEXConfig::new);
        Object value = config.get("webMapFeatureEnabled");
        webMapFeatureEnabled = value instanceof Boolean ? (Boolean) value : false;
        feedEnabled = booleanSetting(config, "feedEnabled", true);
        minecraftChatRelayEnabled = booleanSetting(config, "minecraftChatRelayEnabled", true);
        webPostToMinecraftEnabled = booleanSetting(config, "webPostToMinecraftEnabled", true);
        discordIntegrationEnabled = booleanSetting(config, "discordIntegrationEnabled", false);
        imageUploadEnabled = booleanSetting(config, "imageUploadEnabled", true);
        feedRetentionDays = intSetting(config, "feedRetentionDays", 7, 1, 30);
        if (!(value instanceof Boolean) || config.get("feedEnabled") == null || config.get("feedRetentionDays") == null || config.get("discordIntegrationEnabled") == null) {
            saveSettings();
        }
    }

    private void saveSettings() {
        PEXConfig config = plugin.getConfigManager().loadConfig(SETTINGS_PATH).orElseGet(PEXConfig::new);
        config.put("webMapFeatureEnabled", webMapFeatureEnabled);
        config.put("feedEnabled", feedEnabled);
        config.put("minecraftChatRelayEnabled", minecraftChatRelayEnabled);
        config.put("webPostToMinecraftEnabled", webPostToMinecraftEnabled);
        config.put("discordIntegrationEnabled", discordIntegrationEnabled);
        config.put("imageUploadEnabled", imageUploadEnabled);
        config.put("feedRetentionDays", feedRetentionDays);
        plugin.getConfigManager().saveConfig(SETTINGS_PATH, config);
    }

    private Component webPostComponent(WebPost post, String fallbackName) {
        Component header = Component.text("[Web] ", NamedTextColor.GREEN)
                .append(Component.text(fallbackName, NamedTextColor.WHITE))
                .append(Component.text(": ", NamedTextColor.DARK_GRAY));
        Component body = Component.text(post.getText(), NamedTextColor.GRAY);
        if (!post.getReplyToId().isBlank()) {
            body = Component.text("↪ @" + post.getReplyToUsername() + " ", NamedTextColor.AQUA).append(body);
        }
        Component actions = Component.empty();
        if (!post.getExternalUrl().isBlank()) {
            actions = actions.append(Component.text(" [Discordで開く]", NamedTextColor.BLUE)
                    .decorate(TextDecoration.UNDERLINED)
                    .clickEvent(ClickEvent.openUrl(post.getExternalUrl())));
        }
        return header.append(body).append(actions);
    }

    private boolean booleanSetting(PEXConfig config, String key, boolean fallback) {
        Object value = config.get(key);
        return value instanceof Boolean bool ? bool : fallback;
    }

    private int intSetting(PEXConfig config, String key, int fallback, int min, int max) {
        Object value = config.get(key);
        if (value instanceof Number number) {
            return Math.max(min, Math.min(max, number.intValue()));
        }
        return fallback;
    }

    private WebPost buildPost(WebProfile profile, String source, String text, List<WebPost.Attachment> attachments, String replyToId, String replyToUsername) {
        WebPost post = new WebPost();
        post.setId(UUID.randomUUID().toString());
        post.setUuid(profile.getUuid());
        post.setUsername(profile.getUsername());
        post.setDisplayName(profile.getDisplayName().isBlank() ? profile.getUsername() : profile.getDisplayName());
        post.setNickname(profile.getNickname());
        post.setFaceUrl(profile.getFaceUrl());
        post.setSource(source);
        post.setText(text);
        post.setAttachments(attachments == null ? List.of() : attachments);
        post.setReplyToId(replyToId);
        post.setReplyToUsername(replyToUsername);
        post.setCreatedAt(System.currentTimeMillis());
        return post;
    }

    private String stringValue(Object value) {
        return value instanceof String string ? string.trim() : "";
    }

    private String limit(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() <= maxLength) {
            return trimmed;
        }
        return trimmed.substring(0, maxLength);
    }

    private SessionPair createSession(String uuid) {
        byte[] tokenBytes = new byte[32];
        RANDOM.nextBytes(tokenBytes);
        byte[] csrfBytes = new byte[32];
        RANDOM.nextBytes(csrfBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        String csrfToken = Base64.getUrlEncoder().withoutPadding().encodeToString(csrfBytes);
        sessions.put(token, new WebSession(token, uuid, csrfToken, System.currentTimeMillis() + SESSION_TTL_MS));
        return new SessionPair(token, csrfToken);
    }

    public boolean verifyCsrfToken(String token, String csrfToken) {
        cleanupExpiredSessions();
        if (token == null || token.isBlank() || csrfToken == null || csrfToken.isBlank()) {
            return false;
        }
        WebSession session = sessions.get(token);
        if (session == null || session.isExpired(System.currentTimeMillis())) {
            if (session != null) {
                sessions.remove(token);
            }
            return false;
        }
        return java.security.MessageDigest.isEqual(
                csrfToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                session.csrfToken().getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    public String csrfTokenForSession(String token) {
        cleanupExpiredSessions();
        WebSession session = sessions.get(token);
        if (session == null || session.isExpired(System.currentTimeMillis())) {
            if (session != null) {
                sessions.remove(token);
            }
            return "";
        }
        return session.csrfToken();
    }

    private PasswordHash hashPassword(String password) {
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return new PasswordHash(
                Base64.getEncoder().encodeToString(salt),
                pbkdf2(password.toCharArray(), salt)
        );
    }

    private boolean verifyPassword(String password, String salt, String expectedHash) {
        if (salt == null || salt.isBlank() || expectedHash == null || expectedHash.isBlank()) {
            return false;
        }
        byte[] saltBytes = Base64.getDecoder().decode(salt);
        String actual = pbkdf2(password.toCharArray(), saltBytes);
        return java.security.MessageDigest.isEqual(actual.getBytes(java.nio.charset.StandardCharsets.UTF_8), expectedHash.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private String pbkdf2(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, 120_000, 256);
            byte[] hash = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception error) {
            throw new IllegalStateException("Password hashing failed", error);
        }
    }

    private void cleanupExpiredCodes() {
        long now = System.currentTimeMillis();
        pendingCodes.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        sessions.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    private record PendingHotp(String code, long expiresAt) {
        boolean isExpired(long now) {
            return expiresAt <= now;
        }
    }

    private record PasswordHash(String salt, String hash) {
    }

    private record SessionPair(String token, String csrfToken) {
    }

    public record AuthResult(boolean success, String message, WebProfile profile, String token, String csrfToken) {
        public static AuthResult success(WebProfile profile, SessionPair session) {
            return new AuthResult(true, "OK", profile, session.token(), session.csrfToken());
        }

        public static AuthResult error(String message) {
            return new AuthResult(false, message, null, "", "");
        }
    }

    public record ProfileResult(boolean success, String message, WebProfile profile) {
        public static ProfileResult success(WebProfile profile) {
            return new ProfileResult(true, "OK", profile);
        }

        public static ProfileResult error(String message) {
            return new ProfileResult(false, message, null);
        }
    }

    public record PostResult(boolean success, String message, WebPost post) {
        public static PostResult success(WebPost post) {
            return new PostResult(true, "OK", post);
        }

        public static PostResult error(String message) {
            return new PostResult(false, message, null);
        }
    }
}


