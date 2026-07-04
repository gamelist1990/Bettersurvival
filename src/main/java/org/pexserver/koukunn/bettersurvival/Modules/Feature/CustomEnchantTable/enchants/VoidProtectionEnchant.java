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

/**
 * 奈落の保護 (voidprotection) — ダメージ軽減の派生。
 *
 * 装備品のいずれかに付与されていると奈落ダメージを軽減する。
 * 重複する場合は最大レベルのみを使用する。
 * Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減。
 */
public class VoidProtectionEnchant extends CustomEnchant {

    public VoidProtectionEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "voidprotection";
    }

    @Override
    public String displayName() {
        return "奈落の保護";
    }

    @Override
    public String description() {
        return "\u00a77奈落ダメージを軽減する"
                + "\n\u00a77Lv1=25%軽減 / Lv2=50%軽減 / Lv3=75%軽減";
    }

    @Override
    public Material icon() {
        return Material.OBSIDIAN;
    }

    @Override
    public String vanillaParentName() {
        return "ダメージ軽減";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 奈落の保護: 黒曜石・エンダーパール・エンドストーン・クライング黒曜石・エメラルド
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 16),
                    new ItemStack(Material.OBSIDIAN, 8),
                    new ItemStack(Material.ENDER_PEARL, 4),
                    new ItemStack(Material.END_STONE, 8));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.OBSIDIAN, 16),
                    new ItemStack(Material.ENDER_PEARL, 8),
                    new ItemStack(Material.CRYING_OBSIDIAN, 4),
                    new ItemStack(Material.END_STONE_BRICKS, 16));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.CRYING_OBSIDIAN, 8),
                    new ItemStack(Material.ENDER_PEARL, 16),
                    new ItemStack(Material.END_STONE_BRICKS, 32),
                    new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.VOID) {
            return;
        }
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        int maxLevel = getMaxArmorLevel(player);
        if (maxLevel <= 0) {
            return;
        }
        double reduction = maxLevel * 0.25D;
        event.setDamage(event.getDamage() * (1.0D - reduction));
    }

    private int getMaxArmorLevel(Player player) {
        int max = 0;
        ItemStack[] armor = player.getInventory().getArmorContents();
        for (ItemStack item : armor) {
            int l = levelOf(item);
            if (l > max) {
                max = l;
            }
        }
        return max;
    }
}
