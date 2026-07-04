package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * オートスメルト (autosmelt) — 火属性の派生。
 *
 * 掘ったブロックのドロップ品が、かまどで焼いた状態で手に入る
 * (鉄鉱石→鉄インゴット、丸石→石 など)。焼けない物は通常どおり。
 * シルクタッチとは競合しないが、シルクタッチのドロップ (原石ブロック等) が
 * かまどレシピを持つ場合はそれも焼かれる。
 *
 * 自動回収と併用すると焼き上がりが直接インベントリへ入る。
 */
public class AutoSmeltEnchant extends CustomEnchant {

    private final AutoCollectEnchant autoCollect;
    private final Map<Material, ItemStack> smeltCache = new HashMap<>();
    private final Set<Material> nonSmeltable = new HashSet<>();

    public AutoSmeltEnchant(Loader plugin, AutoCollectEnchant autoCollect) {
        super(plugin);
        this.autoCollect = autoCollect;
    }

    @Override
    public String id() {
        return "autosmelt";
    }

    @Override
    public String displayName() {
        return "オートスメルト";
    }

    @Override
    public String description() {
        return "§7掘ったブロックのドロップ品が"
                + "\n§7かまどで焼いた状態で手に入る"
                + "\n§7(鉄鉱石→鉄インゴット など)"
                + "\n§7自動回収と併用可";
    }

    @Override
    public Material icon() {
        return Material.CAMPFIRE;
    }

    @Override
    public String vanillaParentName() {
        return "火属性";
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_SHOVEL") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 32),
                new ItemStack(Material.BLAZE_POWDER, 8),
                new ItemStack(Material.IRON_INGOT, 16));
    }

    /**
     * AutoCollect (HIGHEST) より先の HIGH で走らせ、焼いた結果を自前で配る。
     * dropItems を false にするため AutoCollect 側は二重処理しない。
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!event.isDropItems()) {
            return;
        }
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (levelOf(tool) <= 0) {
            return;
        }
        Collection<ItemStack> drops = event.getBlock().getDrops(tool, player);
        if (drops.isEmpty()) {
            return;
        }
        boolean anySmelted = false;
        List<ItemStack> results = new ArrayList<>();
        for (ItemStack drop : drops) {
            ItemStack smelted = lookupSmelted(drop.getType());
            if (smelted == null) {
                results.add(drop.clone());
                continue;
            }
            anySmelted = true;
            ItemStack result = smelted.clone();
            result.setAmount(result.getAmount() * drop.getAmount());
            results.add(result);
        }
        if (!anySmelted) {
            return; // 焼ける物が無ければ通常ドロップに任せる
        }
        event.setDropItems(false);
        boolean collect = autoCollect != null && autoCollect.levelOf(tool) > 0;
        Location dropAt = event.getBlock().getLocation().add(0.5D, 0.3D, 0.5D);
        World world = event.getBlock().getWorld();
        for (ItemStack result : results) {
            if (collect) {
                for (ItemStack leftover : player.getInventory().addItem(result).values()) {
                    world.dropItemNaturally(dropAt, leftover);
                }
            } else {
                world.dropItemNaturally(dropAt, result);
            }
        }
        world.spawnParticle(Particle.FLAME, dropAt, 4, 0.15D, 0.15D, 0.15D, 0.01D);
        world.playSound(dropAt, Sound.BLOCK_FIRE_EXTINGUISH, 0.25F, 1.6F);
    }

    /** かまどレシピを動的照会 (結果はキャッシュ) */
    private ItemStack lookupSmelted(Material type) {
        if (type == null || type.isAir()) {
            return null;
        }
        ItemStack cached = smeltCache.get(type);
        if (cached != null) {
            return cached;
        }
        if (nonSmeltable.contains(type)) {
            return null;
        }
        ItemStack probe = new ItemStack(type);
        Iterator<Recipe> iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe;
            try {
                recipe = iterator.next();
            } catch (Throwable ignored) {
                continue;
            }
            if (!(recipe instanceof FurnaceRecipe furnaceRecipe)) {
                continue;
            }
            try {
                if (furnaceRecipe.getInputChoice().test(probe)) {
                    ItemStack result = furnaceRecipe.getResult().clone();
                    smeltCache.put(type, result);
                    return result;
                }
            } catch (Throwable ignored) {
            }
        }
        nonSmeltable.add(type);
        return null;
    }
}
