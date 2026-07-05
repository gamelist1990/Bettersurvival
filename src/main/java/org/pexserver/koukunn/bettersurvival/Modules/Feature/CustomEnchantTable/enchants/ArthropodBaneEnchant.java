package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Bee;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Endermite;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Spider;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 虫殺し (arthropodbane) — 虫特効の派生。
 *
 * 剣・斧に付与すると節足動物Mobへのダメージを増加させる。
 * Lv1=+15% / Lv2=+30% / Lv3=+45%。
 */
public class ArthropodBaneEnchant extends CustomEnchant {

    public ArthropodBaneEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "arthropodbane";
    }

    @Override
    public String displayName() {
        return "虫殺し";
    }

    @Override
    public String description() {
        return "\u00a77節足動物Mobへのダメージを増加させる"
                + "\n\u00a77Lv1=+15% / Lv2=+30% / Lv3=+45%"
                + "\n\u00a77対象: クモ / シルバーフィッシュ / エンダーマイト / 蜂";
    }

    @Override
    public Material icon() {
        return Material.SPIDER_EYE;
    }

    @Override
    public String vanillaParentName() {
        return "虫特効";
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
        // 虫殺し: クモの目・糸・発酵蜘蛛の目・シルバーフィッシュがドロップするタイル
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.SPIDER_EYE, 4),
                    new ItemStack(Material.STRING, 16),
                    new ItemStack(Material.IRON_INGOT, 4));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.SPIDER_EYE, 8),
                    new ItemStack(Material.FERMENTED_SPIDER_EYE, 2),
                    new ItemStack(Material.COBWEB, 4),
                    new ItemStack(Material.GOLD_INGOT, 4));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.FERMENTED_SPIDER_EYE, 4),
                    new ItemStack(Material.COBWEB, 8),
                    new ItemStack(Material.INFESTED_STONE, 8),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    private boolean isArthropod(Entity entity) {
        return entity instanceof Spider
                || entity instanceof CaveSpider
                || entity instanceof Silverfish
                || entity instanceof Endermite
                || entity instanceof Bee;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }
        if (!isArthropod(event.getEntity())) {
            return;
        }
        int level = levelOf(attacker.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        double bonus = 1.0D + (level * 0.15D);
        event.setDamage(event.getDamage() * bonus);
    }
}
