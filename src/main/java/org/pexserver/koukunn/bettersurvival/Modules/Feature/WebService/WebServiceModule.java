package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService;

import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Discord.Module.Api.McApiClient;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class WebServiceModule implements Listener {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final long HOTP_TTL_MS = 5 * 60 * 1000L;
    private static final long SESSION_TTL_MS = 7 * 24 * 60 * 60 * 1000L;

    private final Loader plugin;
    private final WebProfileStore profileStore;
    private final Map<String, PendingHotp> pendingCodes = new ConcurrentHashMap<>();
    private final Map<String, WebSession> sessions = new ConcurrentHashMap<>();

    public WebServiceModule(Loader plugin) {
        this.plugin = plugin;
        this.profileStore = new WebProfileStore(plugin.getConfigManager());
    }

    public Loader getPlugin() {
        return plugin;
    }

    public boolean isGloballyEnabled() {
        return plugin.getToggleModule() != null && plugin.getToggleModule().getGlobal("webservice");
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

    public AuthResult register(String username, String code, String email, String password, boolean passkeyRequested) {
        Player player = plugin.getServer().getPlayerExact(username == null ? "" : username.trim());
        if (player == null) {
            return AuthResult.error("Minecraft にオンラインのユーザー名を入力してください");
        }
        String uuid = player.getUniqueId().toString();
        PendingHotp pending = pendingCodes.get(uuid);
        if (pending == null || pending.isExpired(System.currentTimeMillis())) {
            pendingCodes.remove(uuid);
            return AuthResult.error("ワンタイムコードが期限切れです。Minecraft 内で /hotp を再実行してください");
        }
        if (!pending.code().equals(code == null ? "" : code.trim())) {
            return AuthResult.error("ワンタイムコードが一致しません");
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
        profile.setPasskeyEnabled(passkeyRequested);
        profile.setPasskeyLabel(passkeyRequested ? "WebAuthn passkey ready" : "");
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

    public Map<String, Object> profilePayload(WebProfile profile) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("uuid", profile.getUuid());
        payload.put("username", profile.getUsername());
        payload.put("displayName", profile.getDisplayName());
        payload.put("faceUrl", profile.getFaceUrl());
        payload.put("email", profile.getEmail());
        payload.put("bio", profile.getBio());
        payload.put("location", profile.getLocation());
        payload.put("website", profile.getWebsite());
        payload.put("passkeyEnabled", profile.isPasskeyEnabled());
        payload.put("createdAt", profile.getCreatedAt());
        payload.put("updatedAt", profile.getUpdatedAt());
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

    private String createSession(String uuid) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        sessions.put(token, new WebSession(token, uuid, System.currentTimeMillis() + SESSION_TTL_MS));
        return token;
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

    public record AuthResult(boolean success, String message, WebProfile profile, String token) {
        public static AuthResult success(WebProfile profile, String token) {
            return new AuthResult(true, "OK", profile, token);
        }

        public static AuthResult error(String message) {
            return new AuthResult(false, message, null, "");
        }
    }
}
