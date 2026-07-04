package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 戦利修復 (battlerepair)。
 *
 * 修繕の下位互換として、敵を倒した時に稀にこのエンチャント付き装備の耐久を回復する。
 * 経験値を消費せず、発動率と回復量はレベルに応じて上がる。
 */
public class BattleRepairEnchant extends CustomEnchant {

    public BattleRepairEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "battlerepair";
    }

    @Override
    public String displayName() {
        return "戦利修復";
    }

    @Override
    public String description() {
        return "§7敵を倒した時に稀に耐久値を修復する"
                + "\n§7修繕より弱いが、経験値を消費しない"
                + "\n§7発動率と回復量はレベル依存";
    }

    @Override
    public Material icon() {
        return Material.ANVIL;
    }

    @Override
    public String vanillaParentName() {
        return "修繕";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_PICKAXE")
                || name.endsWith("_SHOVEL")
                || name.endsWith("_HOE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS")
                || type == Material.BOW
                || type == Material.CROSSBOW
                || type == Material.TRIDENT
                || type == Material.SHEARS
                || type == Material.SHIELD
                || type == Material.FISHING_ROD;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 24), new ItemStack(Material.ROTTEN_FLESH, 32));
            case 2 -> List.of(new ItemStack(Material.LAPIS_LAZULI, 40), new ItemStack(Material.BONE, 32));
            default -> List.of(new ItemStack(Material.LAPIS_LAZULI, 56), new ItemStack(Material.EXPERIENCE_BOTTLE, 8));
        };
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntity() instanceof Player) {
            return;
        }
        List<ItemStack> candidates = repairCandidates(killer);
        if (candidates.isEmpty()) {
            return;
        }
        ItemStack target = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        int level = levelOf(target);
        if (!roll(level)) {
            return;
        }
        int repaired = repair(target, repairAmount(level));
        if (repaired <= 0) {
            return;
        }
        killer.playSound(killer.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 0.35F, 1.8F);
    }

    private List<ItemStack> repairCandidates(Player player) {
        List<ItemStack> items = new ArrayList<>();
        EntityEquipment equipment = player.getEquipment();
        if (equipment == null) {
            return items;
        }
        addCandidate(items, equipment.getItemInMainHand());
        addCandidate(items, equipment.getItemInOffHand());
        for (ItemStack armor : equipment.getArmorContents()) {
            addCandidate(items, armor);
        }
        return items;
    }

    private void addCandidate(List<ItemStack> items, ItemStack item) {
        if (item == null || item.getType().isAir() || levelOf(item) <= 0) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable) || damageable.getDamage() <= 0) {
            return;
        }
        items.add(item);
    }

    private boolean roll(int level) {
        double chance = switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> 0.12D;
            case 2 -> 0.20D;
            default -> 0.30D;
        };
        return ThreadLocalRandom.current().nextDouble() < chance;
    }

    private int repairAmount(int level) {
        return switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> ThreadLocalRandom.current().nextInt(2, 7);
            case 2 -> ThreadLocalRandom.current().nextInt(4, 11);
            default -> ThreadLocalRandom.current().nextInt(6, 16);
        };
    }

    private int repair(ItemStack item, int amount) {
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof Damageable damageable)) {
            return 0;
        }
        int oldDamage = damageable.getDamage();
        if (oldDamage <= 0) {
            return 0;
        }
        int newDamage = Math.max(0, oldDamage - amount);
        damageable.setDamage(newDamage);
        item.setItemMeta(meta);
        return oldDamage - newDamage;
    }
}
