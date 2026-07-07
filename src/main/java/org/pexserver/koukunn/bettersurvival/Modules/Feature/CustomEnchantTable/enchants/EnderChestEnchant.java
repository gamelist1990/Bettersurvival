package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;

import java.util.List;

/**
 * エンダーチェスト (enderchest)。
 *
 * エンダーアイを右クリックするとエンダーチェストを開く。
 * アイをワールドに投げるデフォルト動作はキャンセルされる。
 */
public class EnderChestEnchant extends CustomEnchant {

    public EnderChestEnchant(Loader plugin) {
        super(plugin);
    }

    @Override
    public String id() {
        return "enderchest";
    }

    @Override
    public String displayName() {
        return "エンダーチェスト";
    }

    @Override
    public String description() {
        return "\u00a77エンダーアイを右クリックすると"
                + "\n\u00a77エンダーチェストを開く";
    }

    @Override
    public Material icon() {
        return Material.ENDER_CHEST;
    }

    @Override
    public int maxLevel() {
        return 1;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.ENDER_EYE;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return List.of(
                new ItemStack(Material.LAPIS_LAZULI, 16),
                new ItemStack(Material.ENDER_PEARL, 8),
                new ItemStack(Material.OBSIDIAN, 4));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        switch (event.getAction()) {
            case RIGHT_CLICK_AIR, RIGHT_CLICK_BLOCK -> {
            }
            default -> {
                return;
            }
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.ENDER_EYE) {
            return;
        }
        if (levelOf(item) <= 0) {
            return;
        }
        // エンダーアイのデフォルト使用（投げ動作）を確実に止める
        event.setUseItemInHand(Event.Result.DENY);
        event.setUseInteractedBlock(Event.Result.DENY);
        event.setCancelled(true);
        Player player = event.getPlayer();
        player.openInventory(player.getEnderChest());
        player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0F, 1.0F);
    }
}
