package org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Locale;
import java.util.Properties;

/** Otherworldグループ単位のオフラインInvSeeスナップショット。 */
public class InvseeOfflineData {
    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_ARMOR = "armor";
    private static final String KEY_OFFHAND = "offhand";
    private static final String KEY_ENDERCHEST = "enderchest";
    private static final String KEY_DIRTY = "dirty";

    private static File dataFolder;
    private static Loader plugin;

    public static void initialize(Loader loader) {
        plugin = loader;
        dataFolder = new File(loader.getDataFolder(), "InvseeOfflineData");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            loader.getLogger().warning("[InvSee] オフラインデータフォルダを作成できませんでした");
        }
    }

    public static String scope(Player player) {
        if (player == null || plugin == null || plugin.getOtherworldModule() == null) return "default";
        return normalizeScope(plugin.getOtherworldModule().getGroup(player));
    }

    public static ItemStack[] getInventoryContents(OfflinePlayer player) { return getInventoryContents(player, "default"); }
    public static ItemStack[] getInventoryContents(OfflinePlayer player, String scope) { return load(player, scope, KEY_INVENTORY, 36); }
    public static ItemStack[] getArmorContents(OfflinePlayer player) { return getArmorContents(player, "default"); }
    public static ItemStack[] getArmorContents(OfflinePlayer player, String scope) { return load(player, scope, KEY_ARMOR, 4); }
    public static ItemStack getOffhandItem(OfflinePlayer player) { return getOffhandItem(player, "default"); }
    public static ItemStack getOffhandItem(OfflinePlayer player, String scope) {
        ItemStack[] contents = load(player, scope, KEY_OFFHAND, 1);
        return contents.length == 0 ? null : normalize(contents[0]);
    }
    public static ItemStack[] getEnderchestContents(OfflinePlayer player) { return getEnderchestContents(player, "default"); }
    public static ItemStack[] getEnderchestContents(OfflinePlayer player, String scope) { return load(player, scope, KEY_ENDERCHEST, 27); }

    public static void setInventoryContents(OfflinePlayer player, ItemStack[] contents) { setInventoryContents(player, "default", contents); }
    public static void setInventoryContents(OfflinePlayer player, String scope, ItemStack[] contents) { save(player, scope, KEY_INVENTORY, normalize(contents, 36), true); }
    public static void setArmorContents(OfflinePlayer player, ItemStack[] armor) { setArmorContents(player, "default", armor); }
    public static void setArmorContents(OfflinePlayer player, String scope, ItemStack[] armor) { save(player, scope, KEY_ARMOR, normalize(armor, 4), true); }
    public static void setOffhandItem(OfflinePlayer player, ItemStack item) { setOffhandItem(player, "default", item); }
    public static void setOffhandItem(OfflinePlayer player, String scope, ItemStack item) { save(player, scope, KEY_OFFHAND, new ItemStack[]{normalize(item)}, true); }
    public static void setEnderchestContents(OfflinePlayer player, ItemStack[] contents) { setEnderchestContents(player, "default", contents); }
    public static void setEnderchestContents(OfflinePlayer player, String scope, ItemStack[] contents) { save(player, scope, KEY_ENDERCHEST, normalize(contents, 27), true); }

    public static boolean hasData(OfflinePlayer player) { return hasData(player, "default"); }
    public static boolean hasData(OfflinePlayer player, String scope) {
        File file = getReadFile(player, scope);
        if (!file.exists()) return false;
        Properties properties = loadProperties(player, scope);
        return properties.containsKey(KEY_INVENTORY) || properties.containsKey(KEY_ARMOR)
                || properties.containsKey(KEY_OFFHAND) || properties.containsKey(KEY_ENDERCHEST);
    }

    public static void saveSnapshot(Player player) {
        if (player != null) saveSnapshot(player, scope(player));
    }

    public static void saveSnapshot(Player player, String scope) {
        if (player == null) return;
        PlayerInventory inventory = player.getInventory();
        setAll(player, scope, inventory.getStorageContents(), inventory.getArmorContents(),
                inventory.getItemInOffHand(), player.getEnderChest().getContents(), false);
    }

    public static boolean applyPendingEdits(Player player) {
        return player != null && applyPendingEdits(player, scope(player));
    }

    public static boolean applyPendingEdits(Player player, String scope) {
        if (player == null) return false;
        Properties properties = loadProperties(player, scope);
        if (!Boolean.parseBoolean(properties.getProperty(KEY_DIRTY, "false"))) return false;
        PlayerInventory inventory = player.getInventory();
        inventory.setStorageContents(load(player, scope, properties, KEY_INVENTORY, 36));
        inventory.setArmorContents(load(player, scope, properties, KEY_ARMOR, 4));
        ItemStack[] offhand = load(player, scope, properties, KEY_OFFHAND, 1);
        inventory.setItemInOffHand(offhand.length == 0 ? null : offhand[0]);
        player.getEnderChest().setContents(load(player, scope, properties, KEY_ENDERCHEST, 27));
        properties.setProperty(KEY_DIRTY, "false");
        saveProperties(player, scope, properties);
        return true;
    }

    private static ItemStack[] load(OfflinePlayer player, String scope, String key, int size) {
        Properties properties = loadProperties(player, scope);
        return load(player, scope, properties, key, size);
    }

    private static ItemStack[] load(OfflinePlayer player, String scope, Properties properties, String key, int size) {
        String encoded = properties.getProperty(key);
        if (encoded == null || encoded.isBlank()) return new ItemStack[size];
        try {
            return normalize(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded)), size);
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータの読み込みに失敗: " + player.getUniqueId() + " / " + normalizeScope(scope));
            return new ItemStack[size];
        }
    }

    private static void save(OfflinePlayer player, String scope, String key, ItemStack[] contents, boolean dirty) {
        Properties properties = loadProperties(player, scope);
        properties.setProperty(key, encode(contents));
        properties.setProperty(KEY_DIRTY, Boolean.toString(dirty));
        saveProperties(player, scope, properties);
    }

    private static void setAll(OfflinePlayer player, String scope, ItemStack[] inventory, ItemStack[] armor,
                               ItemStack offhand, ItemStack[] enderchest, boolean dirty) {
        Properties properties = loadProperties(player, scope);
        properties.setProperty(KEY_INVENTORY, encode(normalize(inventory, 36)));
        properties.setProperty(KEY_ARMOR, encode(normalize(armor, 4)));
        properties.setProperty(KEY_OFFHAND, encode(new ItemStack[]{normalize(offhand)}));
        properties.setProperty(KEY_ENDERCHEST, encode(normalize(enderchest, 27)));
        properties.setProperty(KEY_DIRTY, Boolean.toString(dirty));
        saveProperties(player, scope, properties);
    }

    private static Properties loadProperties(OfflinePlayer player, String scope) {
        Properties properties = new Properties();
        File file = getReadFile(player, scope);
        if (!file.exists()) return properties;
        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータファイルの読み込みに失敗: " + file.getPath());
        }
        return properties;
    }

    private static void saveProperties(OfflinePlayer player, String scope, Properties properties) {
        File file = getWriteFile(player, scope);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータフォルダを作成できませんでした");
            return;
        }
        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, "InvSee offline data / scope=" + normalizeScope(scope));
        } catch (IOException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータファイルの保存に失敗: " + file.getPath());
        }
    }

    private static File getReadFile(OfflinePlayer player, String scope) {
        File scoped = getWriteFile(player, scope);
        if (scoped.exists() || !"default".equals(normalizeScope(scope))) return scoped;
        File folder = root();
        File legacy = new File(folder, player.getUniqueId() + ".properties");
        return legacy.exists() ? legacy : scoped;
    }

    private static File getWriteFile(OfflinePlayer player, String scope) {
        return new File(new File(root(), safeScope(scope)), player.getUniqueId() + ".properties");
    }

    private static File root() {
        return dataFolder != null ? dataFolder : new File("plugins/Bettersurvival/InvseeOfflineData");
    }

    private static String normalizeScope(String scope) {
        return scope == null || scope.isBlank() ? "default" : scope.toLowerCase(Locale.ROOT);
    }

    private static String safeScope(String scope) {
        return normalizeScope(scope).replaceAll("[^a-z0-9._-]", "_");
    }

    private static String encode(ItemStack[] items) {
        return Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(items));
    }

    private static ItemStack[] normalize(ItemStack[] source, int size) {
        ItemStack[] normalized = new ItemStack[size];
        if (source == null) return normalized;
        for (int i = 0; i < Math.min(source.length, size); i++) normalized[i] = normalize(source[i]);
        return normalized;
    }

    private static ItemStack normalize(ItemStack item) {
        return item == null || item.isEmpty() ? null : item.clone();
    }
}
