package org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Base64;
import java.util.Properties;

/**
 * オフラインプレイヤーのInvSee編集データをPaper/Bukkit APIで保存するユーティリティ
 */
public class InvseeOfflineData {

    private static File dataFolder;

    public static void initialize(Loader plugin) {
        dataFolder = new File(plugin.getDataFolder(), "InvseeOfflineData");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("[InvSee] オフラインデータフォルダを作成できませんでした");
        }
    }

    public static ItemStack[] getInventoryContents(OfflinePlayer player) {
        return load(player, "inventory", 36);
    }

    public static ItemStack[] getArmorContents(OfflinePlayer player) {
        return load(player, "armor", 4);
    }

    public static ItemStack getOffhandItem(OfflinePlayer player) {
        ItemStack[] contents = load(player, "offhand", 1);
        return contents.length == 0 ? null : normalize(contents[0]);
    }

    public static ItemStack[] getEnderchestContents(OfflinePlayer player) {
        return load(player, "enderchest", 27);
    }

    public static void setInventoryContents(OfflinePlayer player, ItemStack[] contents) {
        save(player, "inventory", normalize(contents, 36));
    }

    public static void setArmorContents(OfflinePlayer player, ItemStack[] armor) {
        save(player, "armor", normalize(armor, 4));
    }

    public static void setOffhandItem(OfflinePlayer player, ItemStack item) {
        save(player, "offhand", new ItemStack[] { normalize(item) });
    }

    public static void setEnderchestContents(OfflinePlayer player, ItemStack[] contents) {
        save(player, "enderchest", normalize(contents, 27));
    }

    private static ItemStack[] load(OfflinePlayer player, String key, int size) {
        Properties properties = loadProperties(player);
        String encoded = properties.getProperty(key);
        if (encoded == null || encoded.isBlank()) {
            return new ItemStack[size];
        }

        try {
            ItemStack[] loaded = ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(encoded));
            ItemStack[] contents = new ItemStack[size];
            for (int i = 0; i < Math.min(size, loaded.length); i++) {
                contents[i] = normalize(loaded[i]);
            }
            return contents;
        } catch (IllegalArgumentException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータの読み込みに失敗: " + player.getUniqueId());
            return new ItemStack[size];
        }
    }

    private static void save(OfflinePlayer player, String key, ItemStack[] contents) {
        Properties properties = loadProperties(player);
        properties.setProperty(key, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        saveProperties(player, properties);
    }

    private static Properties loadProperties(OfflinePlayer player) {
        Properties properties = new Properties();
        File file = getDataFile(player);
        if (!file.exists()) {
            return properties;
        }

        try (var reader = Files.newBufferedReader(file.toPath(), StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータファイルの読み込みに失敗: " + file.getName());
        }
        return properties;
    }

    private static void saveProperties(OfflinePlayer player, Properties properties) {
        File file = getDataFile(player);
        File parent = file.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータフォルダを作成できませんでした");
            return;
        }

        try (var writer = Files.newBufferedWriter(file.toPath(), StandardCharsets.UTF_8)) {
            properties.store(writer, "InvSee offline data");
        } catch (IOException e) {
            Bukkit.getLogger().warning("[InvSee] オフラインデータファイルの保存に失敗: " + file.getName());
        }
    }

    private static File getDataFile(OfflinePlayer player) {
        File folder = dataFolder != null ? dataFolder : new File("plugins/Bettersurvival/InvseeOfflineData");
        return new File(folder, player.getUniqueId() + ".properties");
    }

    private static ItemStack[] normalize(ItemStack[] source, int size) {
        ItemStack[] normalized = new ItemStack[size];
        if (source == null) {
            return normalized;
        }

        for (int i = 0; i < Math.min(source.length, size); i++) {
            normalized[i] = normalize(source[i]);
        }
        return normalized;
    }

    private static ItemStack normalize(ItemStack item) {
        if (item == null || item.isEmpty()) {
            return null;
        }
        return item.clone();
    }
}
