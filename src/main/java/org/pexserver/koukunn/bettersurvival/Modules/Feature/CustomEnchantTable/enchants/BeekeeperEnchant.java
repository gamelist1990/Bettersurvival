package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Bee;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 養蜂家 (beekeeper)。
 *
 * チェストプレートに付与すると蜂に敵視されなくなる。
 * 蜂の巣を素手で叩いても襲われず、養蜂作業がしやすくなる。
 * ダメージ軽減ではなくヘイト解除なので、他エンティティには一切影響しない。
 */
public class BeekeeperEnchant extends CustomEnchant {

    public BeekeeperEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "beekeeper";
    }

    @Override
    public String displayName() {
        return "養蜂家";
    }

    @Override
    public String description() {
        return "\u00a77チェストプレート装備中、蜂に敵視されない"
                + "\n\u00a77巣を叩いても襲われず養蜂が楽になる";
    }

    @Override
    public Material icon() {
        return Material.HONEYCOMB;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_CHESTPLATE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 養蜂家: 蜂の巣・ハニカム・花・蜂の巣ブロック
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 24),
                new ItemStack(Material.HONEYCOMB, 8),
                new ItemStack(Material.HONEY_BOTTLE, 4),
                new ItemStack(Material.BEEHIVE, 1),
                new ItemStack(Material.DANDELION, 8),
                new ItemStack(Material.POPPY, 8));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Bee)) {
            return;
        }
        if (!(event.getTarget() instanceof Player player)) {
            return;
        }
        if (levelOf(player.getInventory().getChestplate()) <= 0) {
            return;
        }
        event.setCancelled(true);
        event.setTarget(null);
    }
}
