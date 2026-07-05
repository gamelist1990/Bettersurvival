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
 * 水中呼吸 (aqualung) — 水中呼吸の派生。
 *
 * ヘルメットに付与するとレベルに関わらず水中呼吸効果を持続的に付与する。
 * Vanilla の水中呼吸と違い、装備してさえいれば陸上でも副作用なく効果表示だけ残る
 * (実質水中でのみ意味を持つ) シンプルな便利エンチャント。
 */
public class AqualungEnchant extends CustomEnchant {

    private static final int REFRESH_TICKS = 40;

    private final BukkitTask task;

    public AqualungEnchant(Loader plugin) {
        super(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    @Override
    public String id() {
        return "aqualung";
    }

    @Override
    public String displayName() {
        return "水中呼吸";
    }

    @Override
    public String description() {
        return "\u00a77ヘルメット装備中、水中呼吸効果を付与する";
    }

    @Override
    public Material icon() {
        return Material.TURTLE_HELMET;
    }

    @Override
    public String vanillaParentName() {
        return "水中呼吸";
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
        // 水中呼吸: プリズマリン系・フグ・昆布
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 24),
                new ItemStack(Material.PRISMARINE_SHARD, 8),
                new ItemStack(Material.PRISMARINE_CRYSTALS, 4),
                new ItemStack(Material.PUFFERFISH, 4),
                new ItemStack(Material.KELP, 16));
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
                        PotionEffectType.WATER_BREATHING, REFRESH_TICKS + 20, 0, false, false, true));
            }
        }
    }
}
