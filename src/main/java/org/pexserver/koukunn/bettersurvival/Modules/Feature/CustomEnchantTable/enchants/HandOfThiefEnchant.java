package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * 泥棒の手 (handofthief) — 略奪の派生。
 *
 * Mob を倒したとき、その装備品をすべて確定でドロップさせる。
 * プレイヤーには効果を与えない。
 * エンチャントレベルに関わらず効果は固定 (Lv1のみ)。
 */
public class HandOfThiefEnchant extends CustomEnchant {

    public HandOfThiefEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "handofthief";
    }

    @Override
    public String displayName() {
        return "泥棒の手";
    }

    @Override
    public String description() {
        return "\u00a77Mobを倒したとき、その装備品をすべてドロップさせる"
                + "\n\u00a77プレイヤーへの効果はない";
    }

    @Override
    public Material icon() {
        return Material.LEATHER_BOOTS;
    }

    @Override
    public String vanillaParentName() {
        return "略奪";
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        String name = type.name();
        return name.endsWith("_SWORD") || name.endsWith("_AXE");
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        // 泥棒の手: 金インゴット・皮革・トリップワイヤーフック・鎖・エメラルド（盗賊テーマ）
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 32),
                new ItemStack(Material.GOLD_INGOT, 16),
                new ItemStack(Material.LEATHER, 8),
                new ItemStack(Material.TRIPWIRE_HOOK, 4),
                new ItemStack(Material.STRING, 16));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Mob)) {
            return;
        }
        Player killer = entity.getKiller();
        if (killer == null) {
            return;
        }
        int level = levelOf(killer.getInventory().getItemInMainHand());
        if (level <= 0) {
            return;
        }
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) {
            return;
        }
        ItemStack[] slots = {
                equipment.getHelmet(),
                equipment.getChestplate(),
                equipment.getLeggings(),
                equipment.getBoots(),
                equipment.getItemInMainHand(),
                equipment.getItemInOffHand()
        };
        for (ItemStack item : slots) {
            if (item != null && !item.getType().isAir()) {
                event.getDrops().removeIf(drop -> drop != null && drop.isSimilar(item));
                event.getDrops().add(item.clone());
            }
        }
    }
}
