package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.NamespacedKey;

import java.util.Locale;

/** Shared naming rules for generated Otherworld dimension definitions and runtime worlds. */
public final class OtherworldDimensionKeys {
    public static final String NAMESPACE = "bettersurvival";
    private static final String ROOT = "otherworld";

    private OtherworldDimensionKeys() { }

    public static NamespacedKey mirrorKey(String group, String sourceKey) {
        SourceKey source = parse(sourceKey);
        return new NamespacedKey(NAMESPACE,
                ROOT + "/" + safe(group) + "/" + safe(source.namespace()) + "/" + safePath(source.path()));
    }

    public static String mirrorKeyString(String group, String sourceKey) {
        return mirrorKey(group, sourceKey).toString();
    }

    public static boolean isGenerated(String key) {
        if (key == null) return false;
        return key.toLowerCase(Locale.ROOT).startsWith(NAMESPACE + ":" + ROOT + "/");
    }

    public static String dataPackPath(String group, String sourceKey) {
        NamespacedKey key = mirrorKey(group, sourceKey);
        return "data/" + key.getNamespace() + "/dimension/" + key.getKey() + ".json";
    }

    public static SourceKey parse(String value) {
        if (value == null || value.isBlank()) return new SourceKey("minecraft", "unknown");
        int colon = value.indexOf(':');
        if (colon < 0) return new SourceKey("minecraft", value);
        return new SourceKey(value.substring(0, colon), value.substring(colon + 1));
    }

    public static String safe(String value) {
        String result = value == null ? "unknown" : value.toLowerCase(Locale.ROOT);
        result = result.replaceAll("[^a-z0-9._-]", "_");
        if (result.isBlank()) result = "unknown";
        return result.length() > 64 ? result.substring(0, 64) : result;
    }

    private static String safePath(String value) {
        if (value == null || value.isBlank()) return "unknown";
        String[] parts = value.toLowerCase(Locale.ROOT).split("/");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append('/');
            out.append(safe(part));
        }
        return out.isEmpty() ? "unknown" : out.toString();
    }

    public record SourceKey(String namespace, String path) { }
}
