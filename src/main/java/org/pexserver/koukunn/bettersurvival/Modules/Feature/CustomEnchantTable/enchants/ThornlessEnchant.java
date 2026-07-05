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
 * 棘無効 (thornless)。
 *
 * レギンスに付与するとサボテン・甘い実の茂み・パウダースノーの凍結など
 * 微小な環境ダメージを無効化する。ぶつかりダメージのみが対象で、
 * 落下ダメージ・溺死・戦闘ダメージには影響しない。
 */
public class ThornlessEnchant extends CustomEnchant {

    private static final Set<EntityDamageEvent.DamageCause> BLOCKED_CAUSES = Set.of(
            EntityDamageEvent.DamageCause.CONTACT,
            EntityDamageEvent.DamageCause.FREEZE,
            EntityDamageEvent.DamageCause.CRAMMING);

    public ThornlessEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "thornless";
    }

    @Override
    public String displayName() {
        return "棘無効";
    }

    @Override
    public String description() {
        return "\u00a77レギンス装備中、以下のダメージを無効化する"
                + "\n\u00a77・サボテン / 甘い実の茂み"
                + "\n\u00a77・パウダースノーの凍結"
                + "\n\u00a77・密集による圧迫ダメージ";
    }

    @Override
    public Material icon() {
        return Material.CACTUS;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_LEGGINGS");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 棘無効: サボテン・甘い実・革・氷・パウダースノー・蜂の巣
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 24),
                new ItemStack(Material.CACTUS, 16),
                new ItemStack(Material.SWEET_BERRIES, 8),
                new ItemStack(Material.LEATHER, 8),
                new ItemStack(Material.POWDER_SNOW_BUCKET, 1),
                new ItemStack(Material.HONEYCOMB, 4));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (!BLOCKED_CAUSES.contains(event.getCause())) {
            return;
        }
        if (levelOf(player.getInventory().getLeggings()) <= 0) {
            return;
        }
        event.setCancelled(true);
    }
}
