package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * 自動再植 (autoreplant)。
 *
 * クワを持って完全成熟した作物を右クリックすると、
 * 作物を破壊してドロップを出し、その場に種を植え直す。
 * 種はプレイヤーのインベントリから1個消費する (無ければ再植だけスキップ)。
 *
 * 対応作物: 小麦・ニンジン・ジャガイモ・ビートルート。
 */
public class AutoReplantEnchant extends CustomEnchant {

    /** 作物→種のマッピング */
    private static final Map<Material, Material> CROP_TO_SEED = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT,
            Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS);

    public AutoReplantEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "autoreplant";
    }

    @Override
    public String displayName() {
        return "自動再植";
    }

    @Override
    public String description() {
        return "\u00a77クワを持って成熟作物を右クリックすると"
                + "\n\u00a77収穫と同時に種を植え直す"
                + "\n\u00a77対応: 小麦 / ニンジン / ジャガイモ / ビートルート";
    }

    @Override
    public Material icon() {
        return Material.WHEAT_SEEDS;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return type.name().endsWith("_HOE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 自動再植: 種・作物・骨粉・堆肥
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 24),
                new ItemStack(Material.WHEAT_SEEDS, 16),
                new ItemStack(Material.CARROT, 8),
                new ItemStack(Material.POTATO, 8),
                new ItemStack(Material.BONE_MEAL, 16),
                new ItemStack(Material.COMPOSTER, 1));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        Material seed = CROP_TO_SEED.get(block.getType());
        if (seed == null) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (levelOf(hand) <= 0) {
            return;
        }
        BlockData data = block.getBlockData();
        if (!(data instanceof Ageable ageable) || ageable.getAge() < ageable.getMaximumAge()) {
            return;
        }
        event.setCancelled(true);
        // 収穫: 作物のドロップを取得 (Fortune 等は考慮しないシンプル版)
        Collection<ItemStack> drops = block.getDrops(hand);
        // 種を1個消費して再植 (無ければ更地のまま)
        boolean replanted = consumeSeed(player.getInventory(), seed);
        if (replanted) {
            Ageable resetData = (Ageable) block.getBlockData();
            resetData.setAge(0);
            block.setBlockData(resetData);
        } else {
            block.setType(Material.AIR);
        }
        // ドロップは足元にスポーン
        for (ItemStack drop : drops) {
            // 植え直した種の1個ぶんはドロップから差し引く
            if (replanted && drop.getType() == seed && drop.getAmount() > 0) {
                drop.setAmount(drop.getAmount() - 1);
                if (drop.getAmount() <= 0) {
                    continue;
                }
            }
            block.getWorld().dropItemNaturally(block.getLocation().add(0.5D, 0.1D, 0.5D), drop);
        }
        block.getWorld().playSound(block.getLocation(), Sound.ITEM_CROP_PLANT, 0.7F, 1.1F);
    }

    /** インベントリから指定種を1個消費する。消費できたら true。 */
    private boolean consumeSeed(PlayerInventory inventory, Material seed) {
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item != null && item.getType() == seed && item.getAmount() > 0) {
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    inventory.setItem(i, null);
                }
                return true;
            }
        }
        return false;
    }
}
