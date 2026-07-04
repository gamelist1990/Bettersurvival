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
 * ヘイスト (haste) — 効率強化の派生。
 *
 * ツルハシを手に持っている間、レベルに応じたヘイスト効果を付与する。
 * Lv1=ヘイストI / Lv2=ヘイストII / Lv3=ヘイストIII。
 */
public class HasteEnchant extends CustomEnchant {

    private static final int REFRESH_TICKS = 40;

    private final BukkitTask task;

    public HasteEnchant(Loader plugin) {
        super(plugin);
        this.task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, REFRESH_TICKS, REFRESH_TICKS);
    }

    @Override
    public String id() {
        return "haste";
    }

    @Override
    public String displayName() {
        return "ヘイスト";
    }

    @Override
    public String description() {
        return "\u00a77ツルハシを手に持っている間、ヘイスト効果を付与する"
                + "\n\u00a77Lv1=ヘイストI / Lv2=ヘイストII / Lv3=ヘイストIII";
    }

    @Override
    public Material icon() {
        return Material.IRON_PICKAXE;
    }

    @Override
    public String vanillaParentName() {
        return "効率強化";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_PICKAXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.REDSTONE, 16));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.REDSTONE_BLOCK, 4));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.DIAMOND, 4));
        };
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
            int level = levelOf(player.getInventory().getItemInMainHand());
            if (level > 0) {
                player.addPotionEffect(new PotionEffect(
                        PotionEffectType.HASTE, REFRESH_TICKS + 20, level - 1, false, false, true));
            }
        }
    }
}
