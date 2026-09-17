from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected exactly one match, got {count}: {old[:120]!r}")
    write(rel, text.replace(old, new, 1))


def replace_count(rel, old, new, expected):
    text = read(rel)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{rel}: expected {expected} matches, got {count}: {old[:120]!r}")
    write(rel, text.replace(old, new))


player_store = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/Otherworld/OtherworldPlayerDataStore.java"
if "BetterSurvival-Otherworld-IO" in read(player_store):
    print("PR #4 phase 1 already applied")
    sys.exit(0)

# ---------------------------------------------------------------------------
# LandProtection: spatial claim index + Otherworld-scoped ownership limits.
# ---------------------------------------------------------------------------
land = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/LandProtection/LandProtectionModule.java"
replace_once(
    land,
    "import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ui.LandMenu;\n",
    "import org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ui.LandMenu;\n"
    "import org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld.OtherworldModule;\n",
)
replace_once(
    land,
    "    private final ToggleModule toggle;\n    private final PartyModule partyModule;\n",
    "    private final ToggleModule toggle;\n    private final PartyModule partyModule;\n    private final OtherworldModule otherworldModule;\n",
)
replace_once(
    land,
    "    private final Map<String, List<ClaimRegion>> worldIndex = new LinkedHashMap<>();\n",
    "    private final Map<String, List<ClaimRegion>> worldIndex = new LinkedHashMap<>();\n"
    "    /** worldName -> packed chunk coordinate -> claims intersecting that chunk. */\n"
    "    private final Map<String, Map<Long, List<ClaimRegion>>> spatialIndex = new LinkedHashMap<>();\n",
)
replace_once(
    land,
    "    public LandProtectionModule(Loader plugin, ToggleModule toggle,\n                                ItemCombineModule itemCombineModule, PartyModule partyModule) {\n"
    "        this.toggle = toggle;\n        this.partyModule = partyModule;\n",
    "    public LandProtectionModule(Loader plugin, ToggleModule toggle,\n"
    "                                ItemCombineModule itemCombineModule, PartyModule partyModule,\n"
    "                                OtherworldModule otherworldModule) {\n"
    "        this.toggle = toggle;\n        this.partyModule = partyModule;\n        this.otherworldModule = otherworldModule;\n",
)
replace_once(
    land,
    "        for (ClaimRegion claim : getClaimsInWorld(world.getName())) {\n"
    "            if (claim.isActive() && claim.containsHorizontal(location)) {\n"
    "                resolvePartyLazy(claim);\n"
    "                return claim;\n"
    "            }\n"
    "        }\n",
    "        for (ClaimRegion claim : getClaimCandidates(world.getName(),\n"
    "                location.getBlockX(), location.getBlockZ(), 0)) {\n"
    "            if (claim.isActive() && claim.containsHorizontal(location)) {\n"
    "                resolvePartyLazy(claim);\n"
    "                return claim;\n"
    "            }\n"
    "        }\n",
)
replace_once(
    land,
    "    private void rebuildWorldIndex() {\n"
    "        worldIndex.clear();\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            worldIndex.computeIfAbsent(claim.getWorldName(), k -> new ArrayList<>()).add(claim);\n"
    "        }\n"
    "    }\n",
    "    private void rebuildWorldIndex() {\n"
    "        worldIndex.clear();\n"
    "        spatialIndex.clear();\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            worldIndex.computeIfAbsent(claim.getWorldName(), k -> new ArrayList<>()).add(claim);\n"
    "            indexSpatially(claim);\n"
    "        }\n"
    "    }\n\n"
    "    private void indexSpatially(ClaimRegion claim) {\n"
    "        Map<Long, List<ClaimRegion>> worldSpatial = spatialIndex.computeIfAbsent(\n"
    "                claim.getWorldName(), ignored -> new LinkedHashMap<>());\n"
    "        int radius = Math.max(0, claim.getRadius());\n"
    "        int minChunkX = Math.floorDiv(claim.getX() - radius, 16);\n"
    "        int maxChunkX = Math.floorDiv(claim.getX() + radius, 16);\n"
    "        int minChunkZ = Math.floorDiv(claim.getZ() - radius, 16);\n"
    "        int maxChunkZ = Math.floorDiv(claim.getZ() + radius, 16);\n"
    "        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {\n"
    "            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {\n"
    "                worldSpatial.computeIfAbsent(packChunkKey(chunkX, chunkZ), ignored -> new ArrayList<>()).add(claim);\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private List<ClaimRegion> getClaimCandidates(String worldName, int x, int z, int radius) {\n"
    "        Map<Long, List<ClaimRegion>> worldSpatial = spatialIndex.get(worldName);\n"
    "        if (worldSpatial == null || worldSpatial.isEmpty()) {\n"
    "            return List.of();\n"
    "        }\n"
    "        int safeRadius = Math.max(0, radius);\n"
    "        int minChunkX = Math.floorDiv(x - safeRadius, 16);\n"
    "        int maxChunkX = Math.floorDiv(x + safeRadius, 16);\n"
    "        int minChunkZ = Math.floorDiv(z - safeRadius, 16);\n"
    "        int maxChunkZ = Math.floorDiv(z + safeRadius, 16);\n"
    "        if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {\n"
    "            List<ClaimRegion> bucket = worldSpatial.get(packChunkKey(minChunkX, minChunkZ));\n"
    "            return bucket == null ? List.of() : bucket;\n"
    "        }\n"
    "        java.util.LinkedHashSet<ClaimRegion> candidates = new java.util.LinkedHashSet<>();\n"
    "        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {\n"
    "            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {\n"
    "                List<ClaimRegion> bucket = worldSpatial.get(packChunkKey(chunkX, chunkZ));\n"
    "                if (bucket != null) {\n"
    "                    candidates.addAll(bucket);\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "        return candidates.isEmpty() ? List.of() : new ArrayList<>(candidates);\n"
    "    }\n\n"
    "    private static long packChunkKey(int chunkX, int chunkZ) {\n"
    "        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);\n"
    "    }\n",
)
replace_once(
    land,
    "        ClaimRegion existingPersonal = findClaimByOwner(placingOwner);\n",
    "        String scope = scopeForWorldName(worldName);\n"
    "        ClaimRegion existingPersonal = findClaimByOwner(placingOwner, scope);\n",
)
replace_once(
    land,
    "            ClaimRegion existingParty = findClaimByParty(incoming.getPartyId());\n",
    "            ClaimRegion existingParty = findClaimByParty(incoming.getPartyId(), scope);\n",
)
replace_once(
    land,
    "        for (ClaimRegion existing : getClaimsInWorld(worldName)) {\n"
    "            if (!existing.intersects(worldName, placed.getX(), placed.getZ(), incoming.getRadius())) {\n",
    "        for (ClaimRegion existing : getClaimCandidates(worldName, placed.getX(), placed.getZ(), incoming.getRadius())) {\n"
    "            if (!existing.intersects(worldName, placed.getX(), placed.getZ(), incoming.getRadius())) {\n",
)
replace_once(
    land,
    "        for (ClaimRegion existing : getClaimsInWorld(claim.getWorldName())) {\n"
    "            if (existing.key().equals(claim.key())) {\n",
    "        for (ClaimRegion existing : getClaimCandidates(claim.getWorldName(), claim.getX(), claim.getZ(), newRadius)) {\n"
    "            if (existing.key().equals(claim.key())) {\n",
)
replace_once(
    land,
    "        claim.setLevel(nextLevel);\n        saveAll();\n",
    "        claim.setLevel(nextLevel);\n        rebuildWorldIndex();\n        saveAll();\n",
)
replace_once(
    land,
    "        ClaimRegion existingParty = findClaimByParty(party.getId());\n",
    "        ClaimRegion existingParty = findClaimByParty(party.getId(), scopeForWorldName(claim.getWorldName()));\n",
)
replace_once(
    land,
    "    public ClaimRegion findClaimByOwner(UUID owner) {\n"
    "        if (owner == null) {\n"
    "            return null;\n"
    "        }\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            if (owner.equals(claim.getOwner())) {\n"
    "                return claim;\n"
    "            }\n"
    "        }\n"
    "        return null;\n"
    "    }\n\n"
    "    public ClaimRegion findClaimByParty(UUID partyId) {\n"
    "        if (partyId == null) {\n"
    "            return null;\n"
    "        }\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            if (partyId.equals(claim.getPartyId())) {\n"
    "                return claim;\n"
    "            }\n"
    "        }\n"
    "        return null;\n"
    "    }\n",
    "    public ClaimRegion findClaimByOwner(UUID owner) {\n"
    "        return findClaimByOwner(owner, null);\n"
    "    }\n\n"
    "    private ClaimRegion findClaimByOwner(UUID owner, String scope) {\n"
    "        if (owner == null) {\n"
    "            return null;\n"
    "        }\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            if (owner.equals(claim.getOwner())\n"
    "                    && (scope == null || scope.equals(scopeForWorldName(claim.getWorldName())))) {\n"
    "                return claim;\n"
    "            }\n"
    "        }\n"
    "        return null;\n"
    "    }\n\n"
    "    public ClaimRegion findClaimByParty(UUID partyId) {\n"
    "        return findClaimByParty(partyId, null);\n"
    "    }\n\n"
    "    private ClaimRegion findClaimByParty(UUID partyId, String scope) {\n"
    "        if (partyId == null) {\n"
    "            return null;\n"
    "        }\n"
    "        for (ClaimRegion claim : claims.values()) {\n"
    "            if (partyId.equals(claim.getPartyId())\n"
    "                    && (scope == null || scope.equals(scopeForWorldName(claim.getWorldName())))) {\n"
    "                return claim;\n"
    "            }\n"
    "        }\n"
    "        return null;\n"
    "    }\n\n"
    "    private String scopeForWorldName(String worldName) {\n"
    "        return otherworldModule == null ? \"default\" : otherworldModule.getGroupByWorldId(worldName);\n"
    "    }\n",
)

# ---------------------------------------------------------------------------
# Otherworld: non-blocking playerdata disk IO with ordered single-thread IO.
# ---------------------------------------------------------------------------
otherworld = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/Otherworld/OtherworldModule.java"
replace_once(
    otherworld,
    "    public synchronized String getGroup(World world) {\n"
    "        if (world == null) return \"default\";\n"
    "        for (Group group : groups.values()) {\n"
    "            if (group.worlds.values().stream().anyMatch(id -> matchesWorldId(world, id))\n"
    "                    || group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id))) {\n"
    "                return group.name;\n"
    "            }\n"
    "        }\n"
    "        return \"default\";\n"
    "    }\n",
    "    public synchronized String getGroup(World world) {\n"
    "        if (world == null) return \"default\";\n"
    "        for (Group group : groups.values()) {\n"
    "            if (group.worlds.values().stream().anyMatch(id -> matchesWorldId(world, id))\n"
    "                    || group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id))) {\n"
    "                return group.name;\n"
    "            }\n"
    "        }\n"
    "        return \"default\";\n"
    "    }\n\n"
    "    /** Resolve an Otherworld scope from a persisted physical world id/name without requiring a loaded World. */\n"
    "    public synchronized String getGroupByWorldId(String worldId) {\n"
    "        if (worldId == null || worldId.isBlank()) return \"default\";\n"
    "        for (Group group : groups.values()) {\n"
    "            if (group.worlds.values().stream().anyMatch(id -> worldId.equalsIgnoreCase(id))\n"
    "                    || group.customWorlds.values().stream().anyMatch(id -> worldId.equalsIgnoreCase(id))) {\n"
    "                return group.name;\n"
    "            }\n"
    "        }\n"
    "        World loaded = resolveWorldId(worldId);\n"
    "        return loaded == null ? \"default\" : getGroup(loaded);\n"
    "    }\n",
)
# Insert shutdown before the final Group record/class area using a stable method anchor.
replace_once(
    otherworld,
    "    private static String normalize(String value) {\n",
    "    public void shutdown() {\n"
    "        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {\n"
    "            playerDataStore.save(player, getGroup(player));\n"
    "        }\n"
    "        playerDataStore.shutdown();\n"
    "    }\n\n"
    "    private static String normalize(String value) {\n",
)

new_player_store = r'''package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Otherworld グループ単位で Vanilla inventory / XP / EnderChest を保存する。
 * Bukkit の Player/ItemStack 参照はメインスレッドで snapshot/apply し、
 * ディスク I/O だけを専用の単一スレッドへ逃がす。
 */
public final class OtherworldPlayerDataStore {
    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_ARMOR = "armor";
    private static final String KEY_OFFHAND = "offhand";
    private static final String KEY_ENDERCHEST = "enderchest";
    private static final String KEY_LEVEL = "level";
    private static final String KEY_EXP = "exp";
    private static final String KEY_TOTAL_EXP = "totalExp";
    private static final String KEY_WORLD = "world";
    private static final String KEY_X = "x";
    private static final String KEY_Y = "y";
    private static final String KEY_Z = "z";
    private static final String KEY_YAW = "yaw";
    private static final String KEY_PITCH = "pitch";

    private final Loader plugin;
    private final File root;
    private final ExecutorService ioExecutor;
    private final AtomicLong loadSequence = new AtomicLong();
    private final Map<UUID, Long> activeLoads = new ConcurrentHashMap<>();

    public OtherworldPlayerDataStore(Loader plugin) {
        this.plugin = plugin;
        this.root = new File(plugin.getDataFolder(), "Otherworld/playerdata");
        if (!root.exists() && !root.mkdirs()) {
            plugin.getLogger().warning("[Otherworld] playerdata フォルダを作成できませんでした");
        }
        this.ioExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "BetterSurvival-Otherworld-IO");
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean hasData(UUID playerId, String scope) {
        return dataFile(playerId, scope).isFile();
    }

    /** 旧仕様の共有 Vanilla データを default 側へ一度だけ非同期退避する。 */
    public void ensureDefaultMigration(Player player) {
        if (player == null || ioExecutor.isShutdown()) return;
        Properties snapshot = capture(player);
        UUID playerId = player.getUniqueId();
        submitIo(() -> savePropertiesIfAbsent(playerId, "default", snapshot));
    }

    /** Player の状態をメインスレッド上で snapshot し、ファイル書き込みだけを非同期化する。 */
    public void save(Player player, String scope) {
        if (player == null || ioExecutor.isShutdown()) return;
        Properties snapshot = capture(player);
        UUID playerId = player.getUniqueId();
        String normalizedScope = normalizeScope(scope);
        submitIo(() -> saveProperties(playerId, normalizedScope, snapshot));
    }

    /**
     * scope のファイル読み込みを非同期化し、Bukkit inventory/location への反映はメインスレッドへ戻す。
     * 同一プレイヤーで load が連続した場合は最後の要求だけを適用し、古い read の stale apply を防ぐ。
     */
    public void load(Player player, String scope) {
        if (player == null || ioExecutor.isShutdown()) return;
        UUID playerId = player.getUniqueId();
        String normalizedScope = normalizeScope(scope);
        long requestId = loadSequence.incrementAndGet();
        activeLoads.put(playerId, requestId);
        submitIo(() -> {
            File file = dataFile(playerId, normalizedScope);
            boolean exists = file.isFile();
            Properties properties = exists ? loadProperties(playerId, normalizedScope) : null;
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                Long current = activeLoads.get(playerId);
                if (!Objects.equals(current, requestId) || !player.isOnline()) return;
                activeLoads.remove(playerId, requestId);
                if (exists && properties == null) {
                    // 読み込みエラー時は現在の状態を壊さない。
                    return;
                }
                if (!exists) {
                    if ("default".equals(normalizedScope)) {
                        save(player, normalizedScope);
                        return;
                    }
                    clear(player);
                    save(player, normalizedScope);
                    return;
                }
                apply(player, properties);
            });
        });
    }

    private Properties capture(Player player) {
        Properties properties = new Properties();
        PlayerInventory inventory = player.getInventory();
        properties.setProperty(KEY_INVENTORY, encode(normalize(inventory.getStorageContents(), 36)));
        properties.setProperty(KEY_ARMOR, encode(normalize(inventory.getArmorContents(), 4)));
        properties.setProperty(KEY_OFFHAND, encode(new ItemStack[]{normalize(inventory.getItemInOffHand())}));
        properties.setProperty(KEY_ENDERCHEST, encode(normalize(player.getEnderChest().getContents(), 27)));
        properties.setProperty(KEY_LEVEL, Integer.toString(player.getLevel()));
        properties.setProperty(KEY_EXP, Float.toString(player.getExp()));
        properties.setProperty(KEY_TOTAL_EXP, Integer.toString(player.getTotalExperience()));
        Location location = player.getLocation();
        if (location.getWorld() != null) {
            properties.setProperty(KEY_WORLD, location.getWorld().getName());
            properties.setProperty(KEY_X, Double.toString(location.getX()));
            properties.setProperty(KEY_Y, Double.toString(location.getY()));
            properties.setProperty(KEY_Z, Double.toString(location.getZ()));
            properties.setProperty(KEY_YAW, Float.toString(location.getYaw()));
            properties.setProperty(KEY_PITCH, Float.toString(location.getPitch()));
        }
        return properties;
    }

    private void apply(Player player, Properties properties) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(decode(properties.getProperty(KEY_INVENTORY), 36));
        inventory.setArmorContents(decode(properties.getProperty(KEY_ARMOR), 4));
        ItemStack[] offhand = decode(properties.getProperty(KEY_OFFHAND), 1);
        inventory.setItemInOffHand(offhand.length == 0 ? null : offhand[0]);
        player.getEnderChest().setContents(decode(properties.getProperty(KEY_ENDERCHEST), 27));

        int level = parseInt(properties.getProperty(KEY_LEVEL), 0);
        float exp = parseFloat(properties.getProperty(KEY_EXP), 0.0F);
        int totalExp = parseInt(properties.getProperty(KEY_TOTAL_EXP), 0);
        player.setLevel(Math.max(0, level));
        player.setExp(Math.max(0.0F, Math.min(0.999999F, exp)));
        player.setTotalExperience(Math.max(0, totalExp));
        restoreLocation(player, properties);
        player.updateInventory();
    }

    private void restoreLocation(Player player, Properties properties) {
        String worldName = properties.getProperty(KEY_WORLD);
        org.bukkit.World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null) return;
        double x = parseDouble(properties.getProperty(KEY_X), world.getSpawnLocation().getX());
        double y = parseDouble(properties.getProperty(KEY_Y), world.getSpawnLocation().getY());
        double z = parseDouble(properties.getProperty(KEY_Z), world.getSpawnLocation().getZ());
        float yaw = parseFloat(properties.getProperty(KEY_YAW), 0.0F);
        float pitch = parseFloat(properties.getProperty(KEY_PITCH), 0.0F);
        player.teleport(new Location(world, x, y, z, yaw, pitch));
    }

    private void clear(Player player) {
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(new ItemStack[36]);
        inventory.setArmorContents(new ItemStack[4]);
        inventory.setItemInOffHand(null);
        player.getEnderChest().setContents(new ItemStack[27]);
        player.setLevel(0);
        player.setExp(0.0F);
        player.setTotalExperience(0);
        player.updateInventory();
    }

    private Properties loadProperties(UUID playerId, String scope) {
        Properties properties = new Properties();
        File file = dataFile(playerId, scope);
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
            return properties;
        } catch (IOException exception) {
            plugin.getLogger().warning("[Otherworld] playerdata 読み込み失敗: " + file.getPath());
            return null;
        }
    }

    private void savePropertiesIfAbsent(UUID playerId, String scope, Properties properties) {
        File file = dataFile(playerId, scope);
        if (file.isFile()) return;
        saveProperties(playerId, scope, properties);
    }

    private void saveProperties(UUID playerId, String scope, Properties properties) {
        File file = dataFile(playerId, scope);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("[Otherworld] playerdata フォルダを作成できませんでした: " + parent.getPath());
            return;
        }
        File temporary = new File(parent, file.getName() + ".tmp");
        try (var writer = Files.newBufferedWriter(temporary.toPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, "BetterSurvival Otherworld player data");
        } catch (IOException exception) {
            plugin.getLogger().warning("[Otherworld] playerdata 保存失敗: " + file.getPath());
            return;
        }
        try {
            Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException ignored) {
            try {
                Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException exception) {
                plugin.getLogger().warning("[Otherworld] playerdata 保存失敗: " + file.getPath());
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("[Otherworld] playerdata 保存失敗: " + file.getPath());
        }
    }

    private void submitIo(Runnable task) {
        try {
            ioExecutor.execute(task);
        } catch (RejectedExecutionException ignored) {
            // Plugin shutdown 中の遅延イベントは無視する。
        }
    }

    public void shutdown() {
        activeLoads.clear();
        ioExecutor.shutdown();
        try {
            if (!ioExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                plugin.getLogger().warning("[Otherworld] playerdata I/O の終了待機がタイムアウトしました");
                ioExecutor.shutdownNow();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            ioExecutor.shutdownNow();
        }
    }

    private File dataFile(UUID playerId, String scope) {
        return new File(new File(root, safeScope(scope)), playerId + ".properties");
    }

    private static String safeScope(String scope) {
        return normalizeScope(scope).replaceAll("[^a-z0-9._-]", "_");
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "default" : scope.toLowerCase(Locale.ROOT);
    }

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] decode(String encoded, int size) {
        if (encoded == null || encoded.isBlank()) return new ItemStack[size];
        try {
            ItemStack[] decoded = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
            return normalize(decoded, size);
        } catch (IllegalArgumentException exception) {
            Bukkit.getLogger().warning("[Otherworld] ItemStack playerdata の復元に失敗しました");
            return new ItemStack[size];
        }
    }

    private static ItemStack[] normalize(ItemStack[] source, int size) {
        ItemStack[] normalized = new ItemStack[size];
        if (source == null) return normalized;
        for (int i = 0; i < Math.min(size, source.length); i++) normalized[i] = normalize(source[i]);
        return normalized;
    }

    private static ItemStack normalize(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ignored) { return fallback; }
    }

    private static float parseFloat(String value, float fallback) {
        try { return Float.parseFloat(value); } catch (Exception ignored) { return fallback; }
    }

    private static double parseDouble(String value, double fallback) {
        try { return Double.parseDouble(value); } catch (Exception ignored) { return fallback; }
    }
}
'''
write(player_store, new_player_store)

# Loader wiring + orderly Otherworld IO shutdown.
loader = "src/main/java/org/pexserver/koukunn/bettersurvival/Loader.java"
replace_once(
    loader,
    "landProtectionModule = new LandProtectionModule(this, toggleModule, itemCombineModule, partyModule);",
    "landProtectionModule = new LandProtectionModule(this, toggleModule, itemCombineModule, partyModule, otherworldModule);",
)
replace_once(
    loader,
    "        for (Player onlinePlayer : getServer().getOnlinePlayers()) {\n"
    "            InvseeOfflineData.saveSnapshot(onlinePlayer);\n"
    "        }\n"
    "        if (webMapModule != null) {\n",
    "        for (Player onlinePlayer : getServer().getOnlinePlayers()) {\n"
    "            InvseeOfflineData.saveSnapshot(onlinePlayer);\n"
    "        }\n"
    "        if (otherworldModule != null) {\n"
    "            otherworldModule.shutdown();\n"
    "        }\n"
    "        if (webMapModule != null) {\n",
)

# ---------------------------------------------------------------------------
# Pet: stagger startup entity discovery and process pets with a bounded budget.
# ---------------------------------------------------------------------------
pet = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/Pet/PetModule.java"
replace_once(pet, "import org.bukkit.Bukkit;\n", "import org.bukkit.Bukkit;\nimport org.bukkit.Chunk;\n")
replace_once(
    pet,
    "import java.util.LinkedHashMap;\n",
    "import java.util.ArrayDeque;\nimport java.util.LinkedHashMap;\nimport java.util.Queue;\n",
)
replace_once(
    pet,
    "    private final Set<UUID> petIds = ConcurrentHashMap.newKeySet();\n    private final BukkitTask task;\n",
    "    private final Set<UUID> petIds = ConcurrentHashMap.newKeySet();\n"
    "    private final Queue<UUID> petQueue = new ArrayDeque<>();\n"
    "    private final Queue<Chunk> initialScan = new ArrayDeque<>();\n"
    "    private final BukkitTask task;\n",
)
replace_once(
    pet,
    "        registerRecipes(itemCombineModule);\n"
    "        Bukkit.getWorlds().forEach(world -> world.getEntitiesByClass(Mob.class).stream()\n"
    "                .filter(this::isPet).forEach(mob -> petIds.add(mob.getUniqueId())));\n"
    "        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);\n",
    "        registerRecipes(itemCombineModule);\n"
    "        Bukkit.getWorlds().forEach(world -> java.util.Collections.addAll(initialScan, world.getLoadedChunks()));\n"
    "        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);\n",
)
replace_once(pet, "            petIds.add(mob.getUniqueId());\n            setMode(mob, Mode.FOLLOW);\n", "            trackPet(mob);\n            setMode(mob, Mode.FOLLOW);\n")
replace_once(
    pet,
    "    public void onEntitiesLoad(EntitiesLoadEvent event) {\n"
    "        event.getEntities().stream().filter(Mob.class::isInstance).map(Mob.class::cast)\n"
    "                .filter(this::isPet).forEach(mob -> petIds.add(mob.getUniqueId()));\n"
    "    }\n",
    "    public void onEntitiesLoad(EntitiesLoadEvent event) {\n"
    "        event.getEntities().stream().filter(Mob.class::isInstance).map(Mob.class::cast)\n"
    "                .filter(this::isPet).forEach(this::trackPet);\n"
    "    }\n",
)
replace_once(
    pet,
    "    private void tick() {\n"
    "        for (UUID petId : java.util.List.copyOf(petIds)) {\n"
    "            if (!(Bukkit.getEntity(petId) instanceof Mob mob) || !mob.isValid()) {\n"
    "                continue;\n"
    "            }\n"
    "                String owner = mob.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);\n"
    "                if (owner == null) {\n"
    "                    petIds.remove(petId);\n"
    "                    continue;\n"
    "                }\n"
    "                Player player;\n"
    "                try {\n"
    "                    player = Bukkit.getPlayer(UUID.fromString(owner));\n"
    "                } catch (IllegalArgumentException ignored) {\n"
    "                    continue;\n"
    "                }\n"
    "                Mode mode = getMode(mob);\n"
    "                boolean staying = mode == Mode.STAY;\n"
    "                mob.setAware(!staying);\n"
    "                if (mob instanceof Sittable sittable) {\n"
    "                    sittable.setSitting(staying);\n"
    "                }\n"
    "                if (staying || mode == Mode.ROAM || player == null || player.isDead() || mob.isLeashed()) {\n"
    "                    if (staying) mob.getPathfinder().stopPathfinding();\n"
    "                    continue;\n"
    "                }\n"
    "                if (!mob.getWorld().equals(player.getWorld())) {\n"
    "                    // Entity.teleport does not fire PlayerTeleportEvent, so Otherworld's\n"
    "                    // player boundary cannot protect pets automatically.\n"
    "                    if (plugin.getOtherworldModule() != null\n"
    "                            && !plugin.getOtherworldModule().getGroup(mob.getWorld())\n"
    "                                    .equals(plugin.getOtherworldModule().getGroup(player.getWorld()))) {\n"
    "                        continue;\n"
    "                    }\n"
    "                    mob.teleport(player.getLocation());\n"
    "                } else if (mob.getLocation().distanceSquared(player.getLocation()) > 256) {\n"
    "                    mob.teleport(player.getLocation());\n"
    "                } else if (mob.getLocation().distanceSquared(player.getLocation()) > 9) {\n"
    "                    mob.getPathfinder().moveTo(player, 1.2);\n"
    "                }\n"
    "        }\n"
    "    }\n",
    "    private void tick() {\n"
    "        scanInitialChunks(2);\n"
    "        int budget = Math.min(128, petIds.size());\n"
    "        for (int i = 0; i < budget; i++) {\n"
    "            UUID petId = petQueue.poll();\n"
    "            if (petId == null) {\n"
    "                break;\n"
    "            }\n"
    "            if (!petIds.contains(petId)) {\n"
    "                continue;\n"
    "            }\n"
    "            if (!(Bukkit.getEntity(petId) instanceof Mob mob) || !mob.isValid()) {\n"
    "                petIds.remove(petId);\n"
    "                continue;\n"
    "            }\n"
    "            updatePet(petId, mob);\n"
    "            if (petIds.contains(petId)) {\n"
    "                petQueue.offer(petId);\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private void scanInitialChunks(int budget) {\n"
    "        for (int i = 0; i < budget; i++) {\n"
    "            Chunk chunk = initialScan.poll();\n"
    "            if (chunk == null) {\n"
    "                return;\n"
    "            }\n"
    "            if (!chunk.isLoaded()) {\n"
    "                continue;\n"
    "            }\n"
    "            for (org.bukkit.entity.Entity entity : chunk.getEntities()) {\n"
    "                if (entity instanceof Mob mob && isPet(mob)) {\n"
    "                    trackPet(mob);\n"
    "                }\n"
    "            }\n"
    "        }\n"
    "    }\n\n"
    "    private void trackPet(Mob mob) {\n"
    "        UUID petId = mob.getUniqueId();\n"
    "        if (petIds.add(petId)) {\n"
    "            petQueue.offer(petId);\n"
    "        }\n"
    "    }\n\n"
    "    private void updatePet(UUID petId, Mob mob) {\n"
    "        String owner = mob.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);\n"
    "        if (owner == null) {\n"
    "            petIds.remove(petId);\n"
    "            return;\n"
    "        }\n"
    "        Player player;\n"
    "        try {\n"
    "            player = Bukkit.getPlayer(UUID.fromString(owner));\n"
    "        } catch (IllegalArgumentException ignored) {\n"
    "            petIds.remove(petId);\n"
    "            return;\n"
    "        }\n"
    "        Mode mode = getMode(mob);\n"
    "        boolean staying = mode == Mode.STAY;\n"
    "        mob.setAware(!staying);\n"
    "        if (mob instanceof Sittable sittable) {\n"
    "            sittable.setSitting(staying);\n"
    "        }\n"
    "        if (staying || mode == Mode.ROAM || player == null || player.isDead() || mob.isLeashed()) {\n"
    "            if (staying) mob.getPathfinder().stopPathfinding();\n"
    "            return;\n"
    "        }\n"
    "        if (!mob.getWorld().equals(player.getWorld())) {\n"
    "            if (plugin.getOtherworldModule() != null\n"
    "                    && !plugin.getOtherworldModule().getGroup(mob.getWorld())\n"
    "                            .equals(plugin.getOtherworldModule().getGroup(player.getWorld()))) {\n"
    "                return;\n"
    "            }\n"
    "            mob.teleport(player.getLocation());\n"
    "        } else {\n"
    "            double distanceSquared = mob.getLocation().distanceSquared(player.getLocation());\n"
    "            if (distanceSquared > 256) {\n"
    "                mob.teleport(player.getLocation());\n"
    "            } else if (distanceSquared > 9) {\n"
    "                mob.getPathfinder().moveTo(player, 1.2);\n"
    "            }\n"
    "        }\n"
    "    }\n",
)
replace_once(
    pet,
    "    public void shutdown() {\n        task.cancel();\n    }\n",
    "    public void shutdown() {\n"
    "        task.cancel();\n"
    "        initialScan.clear();\n"
    "        petQueue.clear();\n"
    "        petIds.clear();\n"
    "    }\n",
)

# ---------------------------------------------------------------------------
# SharedStorage: spread initial loaded-chunk namespace scan over ticks.
# ---------------------------------------------------------------------------
namespace_listener = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/SharedStorage/SharedStorageNamespaceListener.java"
replace_once(
    namespace_listener,
    "import org.bukkit.persistence.PersistentDataType;\n",
    "import org.bukkit.persistence.PersistentDataType;\nimport org.bukkit.scheduler.BukkitTask;\n",
)
replace_once(
    namespace_listener,
    "import java.util.Locale;\n",
    "import java.util.ArrayDeque;\nimport java.util.Locale;\nimport java.util.Queue;\n",
)
replace_once(
    namespace_listener,
    "    private final NamespacedKey roleKey;\n    private final NamespacedKey idKey;\n",
    "    private final NamespacedKey roleKey;\n"
    "    private final NamespacedKey idKey;\n"
    "    private final Queue<Chunk> initialScanQueue = new ArrayDeque<>();\n"
    "    private BukkitTask initialScanTask;\n",
)
replace_once(
    namespace_listener,
    "        Bukkit.getPluginManager().registerEvents(listener, plugin);\n"
    "        Bukkit.getScheduler().runTask(plugin, listener::scanLoadedChunks);\n",
    "        Bukkit.getPluginManager().registerEvents(listener, plugin);\n"
    "        Bukkit.getScheduler().runTask(plugin, listener::beginInitialScan);\n",
)
replace_once(
    namespace_listener,
    "    private void scanLoadedChunks() {\n"
    "        for (var world : Bukkit.getWorlds()) for (Chunk chunk : world.getLoadedChunks()) scanChunk(chunk);\n"
    "    }\n",
    "    private void beginInitialScan() {\n"
    "        for (var world : Bukkit.getWorlds()) {\n"
    "            java.util.Collections.addAll(initialScanQueue, world.getLoadedChunks());\n"
    "        }\n"
    "        if (!initialScanQueue.isEmpty()) {\n"
    "            initialScanTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drainInitialScan, 1L, 1L);\n"
    "        }\n"
    "    }\n\n"
    "    private void drainInitialScan() {\n"
    "        for (int i = 0; i < 4; i++) {\n"
    "            Chunk chunk = initialScanQueue.poll();\n"
    "            if (chunk == null) {\n"
    "                break;\n"
    "            }\n"
    "            if (chunk.isLoaded()) {\n"
    "                scanChunk(chunk);\n"
    "            }\n"
    "        }\n"
    "        if (initialScanQueue.isEmpty() && initialScanTask != null) {\n"
    "            initialScanTask.cancel();\n"
    "            initialScanTask = null;\n"
    "        }\n"
    "    }\n",
)

# Avoid global network reindex on every single storage redistribution/placement.
shared = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/SharedStorage/SharedStorageModule.java"
# registerPlacement has two target-network reindexes, and redistribute has one; keep startup/destruction global calls intact.
text = read(shared)
needle = "            network.setMain(anchor);\n            reindexAllPlacements();\n            return PlacementResult.SUCCESS;"
if text.count(needle) != 1:
    raise RuntimeError("SharedStorage main placement anchor changed")
text = text.replace(needle, "            network.setMain(anchor);\n            reindexNetwork(network);\n            return PlacementResult.SUCCESS;", 1)
needle2 = "        network.addSub(anchor);\n        reindexAllPlacements();\n        return PlacementResult.SUCCESS;"
if text.count(needle2) != 1:
    raise RuntimeError("SharedStorage sub placement anchor changed")
text = text.replace(needle2, "        network.addSub(anchor);\n        reindexNetwork(network);\n        return PlacementResult.SUCCESS;", 1)
needle3 = "    private void redistributeNetwork(SharedNetwork network, Player actor, boolean announce, List<ItemStack> trackedMainItems,\n                                     List<ItemStack> injectedItems) {\n        reindexAllPlacements();"
if text.count(needle3) != 1:
    raise RuntimeError("SharedStorage redistribute anchor changed")
text = text.replace(needle3, "    private void redistributeNetwork(SharedNetwork network, Player actor, boolean announce, List<ItemStack> trackedMainItems,\n                                     List<ItemStack> injectedItems) {\n        reindexNetwork(network);", 1)
anchor = "    private void indexPlacement(String id, String role, Location anchor, List<Location> footprint) {\n"
if text.count(anchor) != 1:
    raise RuntimeError("SharedStorage indexPlacement anchor changed")
helper = """    private void reindexNetwork(SharedNetwork network) {
        if (network == null) {
            return;
        }
        placements.entrySet().removeIf(entry -> entry.getValue().id().equals(network.id()));
        if (network.main() != null) {
            List<Location> mainFootprint = resolveContainerLocations(network.main());
            if (mainFootprint.isEmpty()) {
                network.setMain(null);
            } else {
                Location canonicalMain = canonicalAnchor(mainFootprint);
                network.setMain(canonicalMain);
                indexPlacement(network.id(), ROLE_MAIN, canonicalMain, mainFootprint);
            }
        }
        List<Location> rewrittenSubs = new ArrayList<>();
        for (Location subAnchor : new ArrayList<>(network.subs())) {
            List<Location> subFootprint = resolveContainerLocations(subAnchor);
            if (subFootprint.isEmpty()) {
                continue;
            }
            Location canonicalSub = canonicalAnchor(subFootprint);
            if (!containsBlock(rewrittenSubs, canonicalSub)) {
                rewrittenSubs.add(canonicalSub);
            }
            indexPlacement(network.id(), ROLE_SUB, canonicalSub, subFootprint);
        }
        network.replaceSubs(rewrittenSubs);
    }

"""
text = text.replace(anchor, helper + anchor, 1)
write(shared, text)

# Tiny hot-path guard: when no AirDash player is armed, PlayerMove becomes a single empty-set check.
airdash = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/AirDash/AirDashModule.java"
replace_once(
    airdash,
    "    public void onMove(PlayerMoveEvent event) {\n        Player player = event.getPlayer();\n",
    "    public void onMove(PlayerMoveEvent event) {\n"
    "        if (armed.isEmpty()) {\n"
    "            return;\n"
    "        }\n"
    "        Player player = event.getPlayer();\n",
)

print("PR #4 phase 1 patch applied successfully")
