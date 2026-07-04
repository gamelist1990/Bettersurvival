package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * マグネット (magnet)。
 *
 * 対応ツールを手に持っている間、周囲のドロップアイテムが
 * 自分に向かって引き寄せられる。範囲はレベル依存 (4/6/8ブロック)。
 * 吸い寄せるだけで拾得は通常どおり接触時なので、バランスは控えめ。
 */
public class MagnetEnchant extends CustomEnchant {

    private static final long PULL_PERIOD_TICKS = 8L;
    private static final double PULL_SPEED = 0.35D;

    private final BukkitTask pullTask;

    public MagnetEnchant(Loader plugin) {
        super(plugin);
        this.pullTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickPull, PULL_PERIOD_TICKS, PULL_PERIOD_TICKS);
    }

    @Override
    public String id() {
        return "magnet";
    }

    @Override
    public String displayName() {
        return "マグネット";
    }

    @Override
    public String description() {
        return "§7手に持っている間、周囲のドロップ品を"
                + "\n§7自分に引き寄せる"
                + "\n§7範囲: Lv1=4 / Lv2=6 / Lv3=8 ブロック";
    }

    @Override
    public Material icon() {
        return Material.LODESTONE;
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return isMiningTool(type);
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.IRON_INGOT, 12));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.REDSTONE_BLOCK, 4));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.DIAMOND, 4));
        };
    }

    private void tickPull() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            int level = levelOf(player.getInventory().getItemInMainHand());
            if (level <= 0) {
                continue;
            }
            double radius = 2 + level * 2; // 4 / 6 / 8
            Location center = player.getLocation().add(0.0D, 0.9D, 0.0D);
            for (Item item : player.getWorld().getNearbyEntitiesByType(Item.class, center, radius)) {
                if (!item.isValid() || item.getPickupDelay() > 40) {
                    continue; // ドロップ直後 (自分が捨てた直後など) は引き寄せない
                }
                Vector pull = center.toVector().subtract(item.getLocation().toVector());
                double distance = pull.length();
                if (distance < 0.8D || distance > radius) {
                    continue;
                }
                item.setVelocity(pull.normalize().multiply(PULL_SPEED));
            }
        }
    }

    @Override
    public void shutdown() {
        pullTask.cancel();
    }
}
