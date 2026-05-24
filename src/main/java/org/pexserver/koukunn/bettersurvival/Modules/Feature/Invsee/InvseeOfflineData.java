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
import java.util.Properties;

/**
 * オフラインプレイヤーのInvSee編集データをPaper/Bukkit APIで保存するユーティリティ
 */
public class InvseeOfflineData {

    private static final String KEY_INVENTORY = "inventory";
    private static final String KEY_ARMOR = "armor";
    private static final String KEY_OFFHAND = "offhand";
    private static final String KEY_ENDERCHEST = "enderchest";
    private static final String KEY_DIRTY = "dirty";

    private static File dataFolder;

    public static void initialize(Loader plugin) {
        dataFolder = new File(plugin.getDataFolder(), "InvseeOfflineData");
        if (!dataFolder.exists() && !dataFolder.mkdirs()) {
            plugin.getLogger().warning("[InvSee] オフラインデータフォルダを作成できませんでした");
        }
    }

    public static ItemStack[] getInventoryContents(OfflinePlayer player) {
        return load(player, KEY_INVENTORY, 36);
    }

    public static ItemStack[] getArmorContents(OfflinePlayer player) {
        return load(player, KEY_ARMOR, 4);
    }

    public static ItemStack getOffhandItem(OfflinePlayer player) {
        ItemStack[] contents = load(player, KEY_OFFHAND, 1);
        return contents.length == 0 ? null : normalize(contents[0]);
    }

    public static ItemStack[] getEnderchestContents(OfflinePlayer player) {
        return load(player, KEY_ENDERCHEST, 27);
    }

    public static void setInventoryContents(OfflinePlayer player, ItemStack[] contents) {
        save(player, KEY_INVENTORY, normalize(contents, 36), true);
    }

    public static void setArmorContents(OfflinePlayer player, ItemStack[] armor) {
        save(player, KEY_ARMOR, normalize(armor, 4), true);
    }

    public static void setOffhandItem(OfflinePlayer player, ItemStack item) {
        save(player, KEY_OFFHAND, new ItemStack[] { normalize(item) }, true);
    }

    public static void setEnderchestContents(OfflinePlayer player, ItemStack[] contents) {
        save(player, KEY_ENDERCHEST, normalize(contents, 27), true);
    }

    public static boolean hasData(OfflinePlayer player) {
        File file = getDataFile(player);
        if (!file.exists()) {
            return false;
        }
        Properties properties = loadProperties(player);
        return properties.containsKey(KEY_INVENTORY) ||
                properties.containsKey(KEY_ARMOR) ||
                properties.containsKey(KEY_OFFHAND) ||
                properties.containsKey(KEY_ENDERCHEST);
    }

    public static void saveSnapshot(Player player) {
        if (player == null) {
            return;
        }
        PlayerInventory inventory = player.getInventory();
        setAll(player,
                inventory.getStorageContents(),
                inventory.getArmorContents(),
                inventory.getItemInOffHand(),
                player.getEnderChest().getContents(),
                false);
    }

    public static boolean applyPendingEdits(Player player) {
        if (player == null) {
            return false;
        }
        Properties properties = loadProperties(player);
        if (!Boolean.parseBoolean(properties.getProperty(KEY_DIRTY, "false"))) {
            return false;
        }

        ItemStack[] inventory = load(player, properties, KEY_INVENTORY, 36);
        ItemStack[] armor = load(player, properties, KEY_ARMOR, 4);
        ItemStack offhand = load(player, properties, KEY_OFFHAND, 1)[0];
        ItemStack[] enderchest = load(player, properties, KEY_ENDERCHEST, 27);

        PlayerInventory playerInventory = player.getInventory();
        playerInventory.setStorageContents(inventory);
        playerInventory.setArmorContents(armor);
        playerInventory.setItemInOffHand(offhand);
        player.getEnderChest().setContents(enderchest);

        properties.setProperty(KEY_DIRTY, "false");
        saveProperties(player, properties);
        return true;
    }

    private static ItemStack[] load(OfflinePlayer player, String key, int size) {
        Properties properties = loadProperties(player);
        return load(player, properties, key, size);
    }

    private static ItemStack[] load(OfflinePlayer player, Properties properties, String key, int size) {
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

    private static void save(OfflinePlayer player, String key, ItemStack[] contents, boolean dirty) {
        Properties properties = loadProperties(player);
        properties.setProperty(key, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(contents)));
        properties.setProperty(KEY_DIRTY, Boolean.toString(dirty));
        saveProperties(player, properties);
    }

    private static void setAll(OfflinePlayer player, ItemStack[] inventory, ItemStack[] armor, ItemStack offhand, ItemStack[] enderchest, boolean dirty) {
        Properties properties = loadProperties(player);
        properties.setProperty(KEY_INVENTORY, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(normalize(inventory, 36))));
        properties.setProperty(KEY_ARMOR, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(normalize(armor, 4))));
        properties.setProperty(KEY_OFFHAND, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(new ItemStack[] { normalize(offhand) })));
        properties.setProperty(KEY_ENDERCHEST, Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(normalize(enderchest, 27))));
        properties.setProperty(KEY_DIRTY, Boolean.toString(dirty));
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
