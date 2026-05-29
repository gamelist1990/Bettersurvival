package org.pexserver.koukunn.bettersurvival.Core.Util;

import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

public final class EnchantmentBookUtil {

    private EnchantmentBookUtil() {}

    /**
     * 指定したエンチャントを1つだけ格納したエンチャント本を作成します。
     *
     * @param enchantment 格納するエンチャント
     * @param level エンチャントのレベル
     * @return エンチャント本の {@link ItemStack}
     */
    public static ItemStack createBook(Enchantment enchantment, int level) {
        return createBook(enchantment, level, null);
    }

    /**
     * 指定したエンチャントを1つだけ格納したエンチャント本を作成します。
     *
     * @param enchantment 格納するエンチャント
     * @param level エンチャントのレベル
     * @param displayName 本の表示名（{@code null} の場合はデフォルト）
     * @return エンチャント本の {@link ItemStack}
     */
    public static ItemStack createBook(Enchantment enchantment, int level, Component displayName) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);
        if (displayName != null) {
            meta.displayName(displayName);
        }
        book.setItemMeta(meta);
        return book;
    }

    /**
     * 複数のエンチャントを格納したエンチャント本を作成します。
     *
     * @param enchants エンチャントとレベルのマップ
     * @return エンチャント本の {@link ItemStack}
     */
    public static ItemStack createBook(Map<Enchantment, Integer> enchants) {
        return createBook(enchants, null);
    }

    /**
     * 複数のエンチャントを格納したエンチャント本を作成します。
     *
     * @param enchants エンチャントとレベルのマップ
     * @param displayName 本の表示名（{@code null} の場合はデフォルト）
     * @return エンチャント本の {@link ItemStack}
     */
    public static ItemStack createBook(Map<Enchantment, Integer> enchants, Component displayName) {
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        for (Map.Entry<Enchantment, Integer> entry : enchants.entrySet()) {
            meta.addStoredEnchant(entry.getKey(), entry.getValue(), true);
        }
        if (displayName != null) {
            meta.displayName(displayName);
        }
        book.setItemMeta(meta);
        return book;
    }
}
