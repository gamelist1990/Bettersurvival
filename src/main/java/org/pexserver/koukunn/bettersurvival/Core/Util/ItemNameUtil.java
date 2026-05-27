package org.pexserver.koukunn.bettersurvival.Core.Util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.translation.GlobalTranslator;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Locale;

public final class ItemNameUtil {

    private ItemNameUtil() {
    }

    /**
     * プレイヤークライアント側で翻訳される表示コンポーネントを返します。
     * ItemMeta にカスタム表示名がある場合はそれを優先し、無い場合は
     * {@link Component#translatable(net.kyori.adventure.translation.Translatable)}
     * を返します。
     */
    public static Component localizedComponent(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return Component.text("UNKNOWN");
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.displayName() != null)
            return meta.displayName();
        return Component.translatable(item);
    }

    /**
     * プレイヤークライアント側で翻訳される Material 表示コンポーネントを返します。
     */
    public static Component localizedComponent(Material material) {
        if (material == null || material == Material.AIR)
            return Component.text("UNKNOWN");
        return Component.translatable(material);
    }

    /**
     * localizedComponent を指定 locale でレンダリングし、プレーン文字列化します。
     */
    public static String localizedPlainText(Component component, Locale locale) {
        if (component == null)
            return "UNKNOWN";
        Locale targetLocale = locale == null ? Locale.US : locale;
        Component rendered = GlobalTranslator.render(component, targetLocale);
        String plain = PlainTextComponentSerializer.plainText().serialize(rendered);
        return plain == null || plain.isBlank() ? "UNKNOWN" : plain;
    }

    /**
     * Material 名を指定 locale でローカライズしたプレーン文字列へ変換します。
     */
    public static String localizedPlainText(Material material, Locale locale) {
        return localizedPlainText(localizedComponent(material), locale);
    }

    /**
     * ItemStack 名を指定 locale でローカライズしたプレーン文字列へ変換します。
     */
    public static String localizedPlainText(ItemStack item, Locale locale) {
        return localizedPlainText(localizedComponent(item), locale);
    }

    /**
     * ItemStack の translation key を返します。
     */
    public static String translationKey(ItemStack item) {
        if (item == null || item.getType() == Material.AIR)
            return null;
        return item.translationKey();
    }

    /**
     * Material の translation key を返します。
     */
    public static String translationKey(Material material) {
        if (material == null || material == Material.AIR)
            return null;
        return material.translationKey();
    }

    /**
     * サーバー側で扱うための可読文字列を返します。
     * この値はクライアント言語に応じて自動翻訳されません。
     * ローカライズ表示が必要な UI には {@link #localizedComponent(ItemStack)} を使用してください。
     */
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

    /**
     * サーバー側で扱うための可読文字列を返します。
     * この値はクライアント言語に応じて自動翻訳されません。
     * ローカライズ表示が必要な UI には {@link #localizedComponent(Material)} を使用してください。
     */
    public static String serverReadableName(Material material) {
        if (material == null || material == Material.AIR)
            return "UNKNOWN";
        return serverReadableName(material.translationKey(), material);
    }

    /**
     * translation key をサーバー可読形式へ整形し、失敗時は Material 名整形へフォールバックします。
     * この値はクライアントローカライズ表示ではありません。
     */
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
