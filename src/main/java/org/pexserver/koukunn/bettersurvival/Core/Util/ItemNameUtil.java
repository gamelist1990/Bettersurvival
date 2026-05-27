package org.pexserver.koukunn.bettersurvival.Core.Util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class ItemNameUtil {

    private ItemNameUtil() {
    }

    public static Component localizedComponent(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return Component.text("UNKNOWN");
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null)
            return meta.displayName();
        return Component.translatable(item);
    }

    public static Component localizedComponent(Material material) {
        if (material == null || material == Material.AIR)
            return Component.text("UNKNOWN");
        return Component.translatable(material);
    }

    public static String translationKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return null;
        return item.translationKey();
    }

    public static String translationKey(Material material) {
        if (material == null || material == Material.AIR)
            return null;
        return material.translationKey();
    }

    public static String serverReadableName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return "UNKNOWN";
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            String displayName = PlainTextComponentSerializer.plainText().serialize(meta.displayName());
            if (displayName != null && !displayName.isBlank())
                return displayName;
        }
        return serverReadableName(item.translationKey(), item.getType());
    }

    public static String serverReadableName(Material material) {
        if (material == null || material == Material.AIR)
            return "UNKNOWN";
        return serverReadableName(material.translationKey(), material);
    }

    public static String serverReadableName(String translationKey, Material fallbackMaterial) {
        String formattedFromKey = formatTranslationKeyAsName(translationKey);
        if (formattedFromKey != null && !formattedFromKey.isBlank())
            return formattedFromKey;
        return formatMaterialEnumName(fallbackMaterial);
    }

    public static String formatTranslationKeyAsName(String translationKey) {
        if (translationKey == null)
            return null;
        String normalized = translationKey.trim();
        if (normalized.isEmpty())
            return null;
        int lastDot = normalized.lastIndexOf('.');
        String token = lastDot >= 0 ? normalized.substring(lastDot + 1) : normalized;
        if (token.isEmpty())
            return null;
        String[] parts = token.toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (!builder.isEmpty())
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1)
                builder.append(part.substring(1));
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    public static String formatMaterialEnumName(Material material) {
        if (material == null)
            return "UNKNOWN";
        String[] parts = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder builder = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty())
                continue;
            if (!builder.isEmpty())
                builder.append(' ');
            builder.append(Character.toUpperCase(part.charAt(0)));
            if (part.length() > 1)
                builder.append(part.substring(1));
        }
        return builder.isEmpty() ? material.name() : builder.toString();
    }
}
