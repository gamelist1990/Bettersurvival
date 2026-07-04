package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 成長加速 (accelerategrowth) — シルクタッチの派生。
 *
 * 右クリックで隣接する作物ブロックに骨粉効果を与える。
 * 使用するたびにアイテムの耐久値が消費される。
 * Lv1=1段階成長 / Lv2=2段階 / Lv3=最大成長 (最大段階まで一気に)。
 */
public class AccelerateGrowthEnchant extends CustomEnchant {

    public AccelerateGrowthEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "accelerategrowth";
    }

    @Override
    public String displayName() {
        return "成長加速";
    }

    @Override
    public String description() {
        return "\u00a77右クリックで作物に骨粉効果を与える"
                + "\n\u00a77Lv1=1段階 / Lv2=2段階 / Lv3=最大成長"
                + "\n\u00a77使用するたびに耐久値を消費する";
    }

    @Override
    public Material icon() {
        return Material.BONE_MEAL;
    }

    @Override
    public String vanillaParentName() {
        return "シルクタッチ";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_HOE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 16), new ItemStack(Material.BONE_MEAL, 16));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 32), new ItemStack(Material.BONE_MEAL, 32));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 48), new ItemStack(Material.EMERALD, 4));
        };
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null || !(block.getBlockData() instanceof Ageable ageable)) {
            return;
        }
        Player player = event.getPlayer();
        ItemStack tool = player.getInventory().getItemInMainHand();
        int level = levelOf(tool);
        if (level <= 0) {
            return;
        }
        event.setCancelled(true);
        int current = ageable.getAge();
        int max = ageable.getMaximumAge();
        if (current >= max) {
            return;
        }
        int newAge;
        if (level >= 3) {
            newAge = max;
        } else {
            newAge = Math.min(max, current + level);
        }
        ageable.setAge(newAge);
        block.setBlockData(ageable);
        consumeDurability(tool, player, 1);
    }

    private void consumeDurability(ItemStack item, Player player, int amount) {
        if (item.getType().getMaxDurability() <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return;
        }
        int newDamage = damageable.getDamage() + amount;
        if (newDamage >= item.getType().getMaxDurability()) {
            player.getInventory().setItemInMainHand(null);
            player.getWorld().playSound(player.getLocation(),
                    org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
            return;
        }
        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
    }
}
