package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.ui;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;

public final class SharedStorageCategoryDisplayHelper {

    private SharedStorageCategoryDisplayHelper() {
    }

    public static String resolveSubEntryLabel(boolean noFilter, boolean useDefaultItemLabel, String displayName, int subNumber) {
        if (noFilter)
            return "§bsub #" + subNumber;
        if (useDefaultItemLabel)
            return null;
        return displayName + " §7#" + subNumber;
    }

    public static String formatTranslationKeyAsName(String translationKey) {
        return ItemNameUtil.formatTranslationKeyAsName(translationKey);
    }

    public static boolean isDefaultItemLabelCategory(String key) {
        if (key == null)
            return false;
        return key.startsWith("all:") || key.startsWith("filter:");
    }

    public static ItemStack stripCustomDisplayName(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta())
            return item;
        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return item;
        meta.displayName(null);
        item.setItemMeta(meta);
        return item;
    }
}
