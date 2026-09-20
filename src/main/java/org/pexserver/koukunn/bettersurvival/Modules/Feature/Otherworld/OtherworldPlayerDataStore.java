package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

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
    private static final String SELECTION_LOBBY_WORLD = "otherworld_lobby";
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
                apply(player, properties, normalizedScope);
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
        if (location.getWorld() != null && !isSelectionLobby(location.getWorld())) {
            properties.setProperty(KEY_WORLD, location.getWorld().getName());
            properties.setProperty(KEY_X, Double.toString(location.getX()));
            properties.setProperty(KEY_Y, Double.toString(location.getY()));
            properties.setProperty(KEY_Z, Double.toString(location.getZ()));
            properties.setProperty(KEY_YAW, Float.toString(location.getYaw()));
            properties.setProperty(KEY_PITCH, Float.toString(location.getPitch()));
        }
        return properties;
    }

    private void apply(Player player, Properties properties, String scope) {
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
        restoreLocation(player, properties, scope);
        player.updateInventory();
    }

    private void restoreLocation(Player player, Properties properties, String scope) {
        String worldName = properties.getProperty(KEY_WORLD);
        org.bukkit.World world = worldName == null ? null : Bukkit.getWorld(worldName);
        if (world == null || isSelectionLobby(world) || !belongsToScope(world, scope)) {
            return;
        }
        double x = parseDouble(properties.getProperty(KEY_X), world.getSpawnLocation().getX());
        double y = parseDouble(properties.getProperty(KEY_Y), world.getSpawnLocation().getY());
        double z = parseDouble(properties.getProperty(KEY_Z), world.getSpawnLocation().getZ());
        float yaw = parseFloat(properties.getProperty(KEY_YAW), 0.0F);
        float pitch = parseFloat(properties.getProperty(KEY_PITCH), 0.0F);
        player.teleport(new Location(world, x, y, z, yaw, pitch));
    }

    private boolean belongsToScope(org.bukkit.World world, String scope) {
        if (world == null || scope == null || scope.isBlank()) {
            return false;
        }
        var otherworld = plugin.getOtherworldModule();
        if (otherworld == null) {
            return "default".equals(scope) && "world".equals(world.getName());
        }
        return scope.equalsIgnoreCase(otherworld.getGroup(world));
    }

    private static boolean isSelectionLobby(org.bukkit.World world) {
        return world != null && SELECTION_LOBBY_WORLD.equals(world.getName());
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
