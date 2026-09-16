package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage;

import org.bukkit.Location;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

/**
 * SharedStorage の表示IDを変えずに Otherworld scope を内部IDへ埋め込む。
 * suffix は Unicode tag characters なので Minecraft 上では表示されない。
 */
public final class SharedStorageScopedId {
    private static final char SEPARATOR = '\u2063'; // invisible separator
    private static final int TAG_BASE = 0xE0000;

    private SharedStorageScopedId() { }

    public static String encode(String rawId, String scope) {
        if (rawId == null) return null;
        String raw = raw(rawId);
        String normalizedScope = normalizeScope(scope);
        String base64 = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(normalizedScope.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder(raw).append(SEPARATOR);
        for (int i = 0; i < base64.length(); i++) {
            out.appendCodePoint(TAG_BASE + base64.charAt(i));
        }
        return out.toString();
    }

    public static boolean isScoped(String id) {
        return id != null && id.indexOf(SEPARATOR) >= 0;
    }

    public static String raw(String id) {
        if (id == null) return null;
        int split = id.indexOf(SEPARATOR);
        return split < 0 ? id : id.substring(0, split);
    }

    public static String scope(String id) {
        if (!isScoped(id)) return null;
        int split = id.indexOf(SEPARATOR);
        String suffix = id.substring(split + 1);
        StringBuilder base64 = new StringBuilder();
        for (int offset = 0; offset < suffix.length(); ) {
            int cp = suffix.codePointAt(offset);
            offset += Character.charCount(cp);
            int value = cp - TAG_BASE;
            if (value >= 0 && value <= 127) base64.append((char) value);
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(base64.toString());
            return normalizeScope(new String(decoded, StandardCharsets.UTF_8));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static String scopeFor(Loader plugin, Location location) {
        if (plugin == null || location == null || location.getWorld() == null || plugin.getOtherworldModule() == null) {
            return "default";
        }
        return normalizeScope(plugin.getOtherworldModule().getGroup(location.getWorld()));
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "default" : scope.toLowerCase(Locale.ROOT);
    }
}
