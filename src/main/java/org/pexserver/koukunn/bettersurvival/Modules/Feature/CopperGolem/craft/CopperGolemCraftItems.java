package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.craft;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import java.util.Locale;

public class CopperGolemCraftItems {

    private final NamespacedKey summonCoreKey;

    public CopperGolemCraftItems(NamespacedKey summonCoreKey) {
        this.summonCoreKey = summonCoreKey;
    }

    public ItemStack createSummonCoreItem() {
        ItemStack stack = new ItemStack(Material.CARVED_PUMPKIN);
        ItemMeta meta = stack.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§6Copper Golem Core");
        ComponentUtils.setLore(meta,
                "§7銅ブロックとカボチャから作成された召喚コア",
                "§7名札(golem-xxxx)と合成で召喚");
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(summonCoreKey, PersistentDataType.BYTE, (byte) 1);
        stack.setItemMeta(meta);
        return stack;
    }

    public boolean isSummonCore(ItemStack stack) {
        if (stack == null || stack.getType() != Material.CARVED_PUMPKIN || !stack.hasItemMeta()) {
            return false;
        }
        Byte flag = stack.getItemMeta().getPersistentDataContainer().get(summonCoreKey, PersistentDataType.BYTE);
        return flag != null && flag == 1;
    }

    public boolean isGolemIdTag(ItemStack stack) {
        return extractGolemId(stack) != null;
    }

    public String extractGolemId(ItemStack nameTag) {
        if (nameTag == null || nameTag.getType() != Material.NAME_TAG || !nameTag.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = nameTag.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) {
            return null;
        }
        String display = ComponentUtils.getDisplayName(meta);
        if (display == null) {
            return null;
        }
        String plain = display.replace("§", "").trim();
        String normalized = plain.toLowerCase(Locale.ROOT);
        if (!normalized.startsWith("golem-")) {
            return null;
        }
        String id = plain.substring("golem-".length()).trim();
        if (id.isBlank()) {
            return null;
        }
        return id;
    }
}
