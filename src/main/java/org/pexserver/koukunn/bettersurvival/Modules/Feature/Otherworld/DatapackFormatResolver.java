package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** Resolves the running server's data-pack format without pinning to a Minecraft version. */
final class DatapackFormatResolver {
    private DatapackFormatResolver() { }

    static PackFormat resolve(BootstrapContext context) {
        PackFormat reflected = resolveFromMinecraftRuntime(context);
        if (reflected != null) return reflected;

        PackFormat versionJson = resolveFromVersionJson(context);
        if (versionJson != null) return versionJson;

        throw new IllegalStateException("Unable to determine the current Minecraft data-pack format dynamically");
    }

    /**
     * Minecraft/Paper keep the authoritative pack version on the current game version object.
     * Reflection keeps this plugin source independent from NMS compile-time names while allowing
     * the running server to remain the source of truth after Minecraft updates.
     */
    private static PackFormat resolveFromMinecraftRuntime(BootstrapContext context) {
        try {
            Class<?> sharedConstants = Class.forName("net.minecraft.SharedConstants");
            Object currentVersion = invokeNoArgStatic(sharedConstants,
                    List.of("getCurrentVersion", "getCurrentVersionData", "getCurrentVersionInfo"));
            if (currentVersion == null) return null;

            // Newer versions may expose a dedicated data-pack format object/value.
            Object direct = invokeNoArg(currentVersion,
                    List.of("dataPackVersion", "dataPackFormat", "getDataPackVersion", "getDataPackFormat"));
            PackFormat parsed = parseUnknownPackFormat(direct);
            if (parsed != null) return parsed;

            // Traditional Minecraft versions expose PackType.SERVER_DATA -> pack version.
            try {
                Class<?> packType = Class.forName("net.minecraft.server.packs.PackType");
                @SuppressWarnings({"rawtypes", "unchecked"})
                Object serverData = Enum.valueOf((Class<? extends Enum>) packType.asSubclass(Enum.class), "SERVER_DATA");
                for (String name : List.of("packVersion", "getPackVersion")) {
                    Method method = findSingleArgMethod(currentVersion.getClass(), name, packType);
                    if (method == null) continue;
                    method.setAccessible(true);
                    parsed = parseUnknownPackFormat(method.invoke(currentVersion, serverData));
                    if (parsed != null) return parsed;
                }
            } catch (ReflectiveOperationException ignored) { }
        } catch (ReflectiveOperationException | LinkageError ex) {
            context.getLogger().debug("Otherworld datapack format: runtime lookup unavailable: {}", ex.toString());
        }
        return null;
    }

    /** Uses Minecraft's bundled version.json as the API-independent fallback. */
    private static PackFormat resolveFromVersionJson(BootstrapContext context) {
        ClassLoader[] loaders = {
                Thread.currentThread().getContextClassLoader(),
                DatapackFormatResolver.class.getClassLoader(),
                ClassLoader.getSystemClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) continue;
            try (InputStream in = loader.getResourceAsStream("version.json")) {
                if (in == null) continue;
                JsonElement parsed = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (!parsed.isJsonObject()) continue;
                JsonObject root = parsed.getAsJsonObject();

                // Common Mojang format: "pack_version": { "resource": n, "data": n }
                if (root.has("pack_version") && root.get("pack_version").isJsonObject()) {
                    JsonObject pack = root.getAsJsonObject("pack_version");
                    PackFormat format = parseJsonFormat(pack.get("data"));
                    if (format != null) return format;
                }

                // Accept newer/alternate metadata spellings without requiring a plugin update.
                for (String key : List.of("data_pack_version", "data_pack_format", "datapack_version", "datapack_format")) {
                    PackFormat format = parseJsonFormat(root.get(key));
                    if (format != null) return format;
                }
            } catch (Exception ex) {
                context.getLogger().debug("Otherworld datapack format: version.json lookup failed: {}", ex.toString());
            }
        }
        return null;
    }

    private static PackFormat parseJsonFormat(JsonElement value) {
        if (value == null || value.isJsonNull()) return null;
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            return PackFormat.whole(value.getAsInt());
        }
        if (value.isJsonArray() && !value.getAsJsonArray().isEmpty()) {
            int major = value.getAsJsonArray().get(0).getAsInt();
            int minor = value.getAsJsonArray().size() > 1 ? value.getAsJsonArray().get(1).getAsInt() : 0;
            return new PackFormat(major, minor);
        }
        if (value.isJsonObject()) {
            JsonObject object = value.getAsJsonObject();
            if (object.has("major")) {
                return new PackFormat(object.get("major").getAsInt(), object.has("minor") ? object.get("minor").getAsInt() : 0);
            }
        }
        return null;
    }

    private static PackFormat parseUnknownPackFormat(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return PackFormat.whole(number.intValue());
        if (value instanceof int[] values && values.length > 0) return new PackFormat(values[0], values.length > 1 ? values[1] : 0);

        // Record-like/version-component objects used by newer Minecraft builds.
        try {
            Method major = findNoArgMethod(value.getClass(), List.of("major", "getMajor"));
            if (major != null) {
                Object majorValue = major.invoke(value);
                Method minor = findNoArgMethod(value.getClass(), List.of("minor", "getMinor"));
                Object minorValue = minor == null ? 0 : minor.invoke(value);
                if (majorValue instanceof Number a && minorValue instanceof Number b) {
                    return new PackFormat(a.intValue(), b.intValue());
                }
            }
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    private static Object invokeNoArgStatic(Class<?> type, List<String> names) throws ReflectiveOperationException {
        Method method = findNoArgMethod(type, names);
        return method == null || !Modifier.isStatic(method.getModifiers()) ? null : method.invoke(null);
    }

    private static Object invokeNoArg(Object target, List<String> names) throws ReflectiveOperationException {
        Method method = findNoArgMethod(target.getClass(), names);
        return method == null ? null : method.invoke(target);
    }

    private static Method findNoArgMethod(Class<?> type, List<String> names) {
        for (String name : names) {
            try {
                Method method = type.getMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) { }
            try {
                Method method = type.getDeclaredMethod(name);
                method.setAccessible(true);
                return method;
            } catch (ReflectiveOperationException ignored) { }
        }
        return null;
    }

    private static Method findSingleArgMethod(Class<?> type, String name, Class<?> argumentType) {
        try {
            return type.getMethod(name, argumentType);
        } catch (ReflectiveOperationException ignored) { }
        try {
            return type.getDeclaredMethod(name, argumentType);
        } catch (ReflectiveOperationException ignored) { }
        return null;
    }

    record PackFormat(int major, int minor) {
        PackFormat {
            if (major < 0 || minor < 0) throw new IllegalArgumentException("Negative pack format");
        }

        static PackFormat whole(int value) {
            return new PackFormat(value, 0);
        }

        String minFormatJson() {
            return minor == 0 ? Integer.toString(major) : "[" + major + ", " + minor + "]";
        }

        String maxFormatJson() {
            return Integer.toString(major);
        }

        @Override
        public String toString() {
            return minor == 0 ? Integer.toString(major) : major + "." + minor;
        }
    }
}
