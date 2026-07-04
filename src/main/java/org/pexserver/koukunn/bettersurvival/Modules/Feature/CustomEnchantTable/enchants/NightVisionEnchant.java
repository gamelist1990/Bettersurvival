package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * ナイトビジョン (nightvision)。
 *
 * ヘルメットに付与するとナイトビジョン効果を持続的に付与する。
 */
public class NightVisionEnchant extends CustomEnchant {

    private static final int REFRESH_TICKS = 40;

    private final BukkitTask task;

    public NightVisionEnchant(Loader plugin) {
        super(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    @Override
    public String id() {
        return "nightvision";
    }

    @Override
    public String displayName() {
        return "ナイトビジョン";
    }

    @Override
    public String description() {
        return "\u00a77ヘルメット装備中、ナイトビジョン効果を付与する";
    }

    @Override
    public Material icon() {
        return Material.DAYLIGHT_DETECTOR;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_HELMET");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return List.of(new ItemStack(Material.LAPIS_LAZULI, 24), new ItemStack(Material.GOLDEN_CARROT, 8));
    }

    @Override
    public void shutdown() {
        task.cancel();
    }

    private void tick() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getGameMode() == GameMode.SPECTATOR || player.isDead()) {
                continue;
            }
            if (levelOf(player.getInventory().getHelmet()) > 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.NIGHT_VISION, REFRESH_TICKS + 20, 0, false, false, true));
            }
        }
    }
}
