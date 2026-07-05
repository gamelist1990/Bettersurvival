package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 連射 (volleyshot)。
 *
 * 弓またはクロスボウで矢を射た際、追加の矢をN本放つ。
 * 追加矢は元の矢の方向から少しランダムにずれた角度へ飛ぶ。
 * Lv1: +1本 / Lv2: +2本 / Lv3: +3本
 */
public class VolleyShotEnchant extends CustomEnchant {

    public VolleyShotEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "volleyshot";
    }

    @Override
    public String displayName() {
        return "連射";
    }

    @Override
    public String description() {
        return "§7弓/クロスボウ発射時に追加の矢をN本放つ"
                + "\n§7Lv1: +1本 / Lv2: +2本 / Lv3: +3本"
                + "\n§7追加矢は元の矢から少しズレた方向へ飛ぶ";
    }

    @Override
    public Material icon() {
        return Material.ARROW;
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.BOW || type == Material.CROSSBOW;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 20), new ItemStack(Material.ARROW, 32));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 36), new ItemStack(Material.ARROW, 64));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 52), new ItemStack(Material.SPECTRAL_ARROW, 16));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        ItemStack bow = event.getBow();
        if (bow == null) {
            return;
        }
        int level = levelOf(bow);
        if (level <= 0) {
            return;
        }
        int extra = level;
        Vector baseVel = event.getProjectile().getVelocity().clone();
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (int i = 0; i < extra; i++) {
                Vector vel = addSpread(baseVel);
                Arrow arrow = player.launchProjectile(Arrow.class, vel);
                arrow.setPickupStatus(Arrow.PickupStatus.CREATIVE_ONLY);
            }
        });
    }

    private Vector addSpread(Vector base) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        double speed = base.length();
        double jitter = speed * 0.10;
        return base.clone().add(new Vector(
                rng.nextDouble(-jitter, jitter),
                rng.nextDouble(-jitter, jitter),
                rng.nextDouble(-jitter, jitter)
        ));
    }
}
