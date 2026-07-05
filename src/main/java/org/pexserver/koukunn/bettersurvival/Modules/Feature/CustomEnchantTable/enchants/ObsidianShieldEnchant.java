package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.Set;

/**
 * 黒曜石の盾 (obsidianshield) — 爆発耐性の派生。
 *
 * チェストプレートに付与するとクリーパー・TNT・ガスト・ベッド爆発などの
 * 爆発ダメージを軽減する。
 * Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減。
 */
public class ObsidianShieldEnchant extends CustomEnchant {

    private static final Set<EntityDamageEvent.DamageCause> EXPLOSION_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.BLOCK_EXPLOSION,
            EntityDamageEvent.DamageCause.ENTITY_EXPLOSION);

    public ObsidianShieldEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "obsidianshield";
    }

    @Override
    public String displayName() {
        return "黒曜石の盾";
    }

    @Override
    public String description() {
        return "\u00a77チェストプレート装備中、爆発ダメージを軽減する"
                + "\n\u00a77Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減";
    }

    @Override
    public Material icon() {
        return Material.OBSIDIAN;
    }

    @Override
    public String vanillaParentName() {
        return "爆発耐性";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_CHESTPLATE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 黒曜石の盾: 黒曜石・火薬・TNT・クライング黒曜石・盾
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.OBSIDIAN, 8),
                    new ItemStack(Material.GUNPOWDER, 16),
                    new ItemStack(Material.IRON_INGOT, 8));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.OBSIDIAN, 16),
                    new ItemStack(Material.TNT, 4),
                    new ItemStack(Material.SHIELD, 1),
                    new ItemStack(Material.NETHER_BRICK, 16));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.CRYING_OBSIDIAN, 8),
                    new ItemStack(Material.TNT, 8),
                    new ItemStack(Material.NETHERITE_SCRAP, 2),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!EXPLOSION_CAUSES.contains(event.getCause())) {
            return;
        }
        int level = levelOf(player.getInventory().getChestplate());
        if (level <= 0) {
            return;
        }
        double reduction = level * 0.25D;
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }
}
