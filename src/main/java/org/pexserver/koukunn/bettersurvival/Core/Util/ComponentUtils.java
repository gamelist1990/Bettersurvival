package org.pexserver.koukunn.bettersurvival.Core.Util;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public final class ComponentUtils {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private ComponentUtils() {}

    public static Component legacy(String text) {
        return LEGACY.deserialize(text == null ? "" : text);
    }

    public static String legacyText(Component component) {
        return component == null ? null : LEGACY.serialize(component);
    }

    public static List<Component> legacyList(Collection<String> lines) {
        if (lines == null) return null;
        List<Component> components = new ArrayList<>();
        for (String line : lines) {
            components.add(legacy(line));
        }
        return components;
    }

    public static List<String> legacyStrings(Collection<Component> components) {
        if (components == null) return null;
        List<String> lines = new ArrayList<>();
        for (Component component : components) {
            lines.add(legacyText(component));
        }
        return lines;
    }

    public static Inventory createInventory(InventoryHolder holder, int size, String title) {
        return Bukkit.createInventory(holder, size, legacy(title));
    }

    public static void setDisplayName(ItemMeta meta, String name) {
        meta.displayName(name == null ? null : legacy(name));
    }

    public static String getDisplayName(ItemMeta meta) {
        return meta == null ? null : legacyText(meta.displayName());
    }

    public static void setLore(ItemMeta meta, List<String> lore) {
        meta.lore(legacyList(lore));
    }

    public static void setLore(ItemMeta meta, String... lore) {
        setLore(meta, Arrays.asList(lore));
    }

    public static List<String> getLore(ItemMeta meta) {
        return meta == null ? null : legacyStrings(meta.lore());
    }
}
