package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.datapack.Datapack;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Generates a datapack before registry loading that mirrors discovered filesystem dimension JSONs
 * into stable Bettersurvival keys for every configured Otherworld group.
 *
 * The JSON body is copied byte-for-byte. References to dimension_type, noise_settings,
 * biome_source and other registries therefore keep pointing at the exact source definitions.
 */
public final class OtherworldDatapackBootstrap {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String GENERATED_PACK_ID = "otherworld-generated";
    private static final String GENERATED_DIR = "generated-otherworld-datapack";
    private static final SecureRandom RANDOM = new SecureRandom();

    private OtherworldDatapackBootstrap() { }

    public static void register(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(LifecycleEvents.DATAPACK_DISCOVERY.newHandler(event -> {
            try {
                BootstrapConfig config = loadAndEnsureSeeds(context.getDataDirectory());
                if (config.groups().isEmpty()) return;

                Map<String, byte[]> dimensions = discoverFilesystemDimensions(context);
                if (dimensions.isEmpty()) {
                    context.getLogger().info("Otherworld datapack bootstrap: no external dimension JSONs found");
                    return;
                }

                DatapackFormatResolver.PackFormat packFormat = DatapackFormatResolver.resolve(context);
                Path generated = context.getDataDirectory().resolve(GENERATED_DIR);
                rebuildGeneratedPack(generated, config.groups(), dimensions, packFormat);
                event.registrar().discoverPack(generated, GENERATED_PACK_ID, options -> options
                        .autoEnableOnServerStart(true)
                        .position(true, Datapack.Position.TOP));

                context.getLogger().info(
                        "Otherworld datapack bootstrap: generated {} dimension mirrors for {} groups (data-pack format={})",
                        dimensions.size(), config.groups().size(), packFormat);
            } catch (Exception ex) {
                context.getLogger().error("Failed to generate Otherworld dimension datapack", ex);
            }
        }));
    }

    private static BootstrapConfig loadAndEnsureSeeds(Path pluginDataDirectory) throws IOException {
        Path configPath = pluginDataDirectory.resolve("Otherworld").resolve("config.json");
        if (!Files.exists(configPath)) return new BootstrapConfig(Map.of());

        JsonObject root;
        try (var reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
            JsonElement parsed = JsonParser.parseReader(reader);
            root = parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        }
        JsonObject groups = root.has("groups") && root.get("groups").isJsonObject()
                ? root.getAsJsonObject("groups") : new JsonObject();

        Map<String, Long> seeds = new LinkedHashMap<>();
        boolean changed = false;
        for (var entry : groups.entrySet()) {
            String group = entry.getKey().toLowerCase();
            if (group.equals("default") || !entry.getValue().isJsonObject()) continue;
            JsonObject values = entry.getValue().getAsJsonObject();
            long seed;
            if (values.has("seed") && values.get("seed").isJsonPrimitive()
                    && values.get("seed").getAsJsonPrimitive().isNumber()) {
                seed = values.get("seed").getAsLong();
            } else {
                seed = RANDOM.nextLong();
                values.addProperty("seed", seed);
                changed = true;
            }
            seeds.put(group, seed);
        }

        if (changed) {
            Files.createDirectories(configPath.getParent());
            try (var writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
                GSON.toJson(root, writer);
            }
        }
        return new BootstrapConfig(seeds);
    }

    private static Map<String, byte[]> discoverFilesystemDimensions(BootstrapContext context) throws IOException {
        Path root = Path.of("").toAbsolutePath().normalize();
        String levelName = readLevelName(root.resolve("server.properties"));
        Set<Path> roots = new LinkedHashSet<>();
        roots.add(root.resolve(levelName).resolve("datapacks"));
        roots.add(root.resolve("datapacks"));

        Map<String, byte[]> dimensions = new TreeMap<>();
        for (Path datapacksDir : roots) {
            if (!Files.isDirectory(datapacksDir)) continue;
            List<Path> packs;
            try (var stream = Files.list(datapacksDir)) {
                packs = stream.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
            }
            for (Path pack : packs) {
                try {
                    if (Files.isDirectory(pack)) collectDirectoryPack(pack, dimensions);
                    else if (pack.getFileName().toString().toLowerCase().endsWith(".zip")) collectZipPack(pack, dimensions);
                } catch (Exception ex) {
                    context.getLogger().warn("Otherworld datapack bootstrap: cannot inspect {}: {}", pack, ex.getMessage());
                }
            }
        }
        return dimensions;
    }

    private static String readLevelName(Path serverProperties) {
        if (!Files.exists(serverProperties)) return "world";
        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(serverProperties)) {
            properties.load(in);
            String value = properties.getProperty("level-name", "world").trim();
            return value.isBlank() ? "world" : value;
        } catch (IOException ignored) {
            return "world";
        }
    }

    private static void collectDirectoryPack(Path packRoot, Map<String, byte[]> dimensions) throws IOException {
        Path data = packRoot.resolve("data");
        if (!Files.isDirectory(data)) return;
        try (var namespaces = Files.list(data)) {
            for (Path namespace : namespaces.filter(Files::isDirectory).toList()) {
                Path dimensionRoot = namespace.resolve("dimension");
                if (!Files.isDirectory(dimensionRoot)) continue;
                try (var files = Files.walk(dimensionRoot)) {
                    for (Path file : files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                        String relative = dimensionRoot.relativize(file).toString().replace('\\', '/');
                        relative = relative.substring(0, relative.length() - ".json".length());
                        dimensions.put(namespace.getFileName() + ":" + relative, Files.readAllBytes(file));
                    }
                }
            }
        }
    }

    private static void collectZipPack(Path zipPath, Map<String, byte[]> dimensions) throws IOException {
        try (ZipFile zip = new ZipFile(zipPath.toFile())) {
            var entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.isDirectory()) continue;
                String name = entry.getName().replace('\\', '/');
                if (!name.startsWith("data/") || !name.endsWith(".json")) continue;
                String[] parts = name.split("/", 4);
                if (parts.length < 4 || !parts[2].equals("dimension")) continue;
                String path = parts[3].substring(0, parts[3].length() - ".json".length());
                try (InputStream in = zip.getInputStream(entry)) {
                    dimensions.put(parts[1] + ":" + path, in.readAllBytes());
                }
            }
        }
    }

    private static void rebuildGeneratedPack(Path generated, Map<String, Long> groups,
                                             Map<String, byte[]> dimensions,
                                             DatapackFormatResolver.PackFormat packFormat) throws IOException {
        deleteRecursively(generated);
        Files.createDirectories(generated);

        JsonObject pack = new JsonObject();
        pack.addProperty("description", "Bettersurvival generated Otherworld dimensions");
        pack.add("min_format", JsonParser.parseString(packFormat.minFormatJson()));
        pack.add("max_format", JsonParser.parseString(packFormat.maxFormatJson()));
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(generated.resolve("pack.mcmeta"), GSON.toJson(root) + System.lineSeparator(), StandardCharsets.UTF_8);

        for (String group : groups.keySet()) {
            for (var dimension : dimensions.entrySet()) {
                Path target = generated.resolve(OtherworldDimensionKeys.dataPackPath(group, dimension.getKey()));
                Files.createDirectories(target.getParent());
                Files.write(target, dimension.getValue());
            }
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var stream = Files.walk(root)) {
            for (Path path : stream.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(path);
        }
    }

    private record BootstrapConfig(Map<String, Long> groups) { }
}
