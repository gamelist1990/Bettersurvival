package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 知恵 (wisdom) — 略奪の派生。
 *
 * 敵を倒すたびにドロップ経験値にボーナスを加算する。
 * Lv1=+50% / Lv2=+100% / Lv3=+150%。
 */
public class WisdomEnchant extends CustomEnchant {

    public WisdomEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "wisdom";
    }

    @Override
    public String displayName() {
        return "知恵";
    }

    @Override
    public String description() {
        return "\u00a77敵を倒すたびに追加の経験値を得る"
                + "\n\u00a77Lv1=+50% / Lv2=+100% / Lv3=+150%";
    }

    @Override
    public Material icon() {
        return Material.BOOK;
    }

    @Override
    public String vanillaParentName() {
        return "略奪";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.BOOK, 4));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.BOOK, 8));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.ENCHANTED_BOOK, 2));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        int level = levelOf(killer.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        int base = event.getDroppedExp();
        if (base <= 0) {
            return;
        }
        event.setDroppedExp((int) (base * (1.0D + level * 0.5D)));
    }
}
