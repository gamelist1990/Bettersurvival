package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.ui.ToolSettingsMenu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「左クリック → 右クリック → 左クリック」ジェスチャでツール設定メニューを開くリスナー。
 *
 * 採掘系の道具を手に持った状態でこの並びをすばやく行うとメニューが開き、
 * 範囲採掘や採掘穴埋めといった効果を ON/OFF できる。
 */
public class ToolSettingsController implements Listener {

    /** ジェスチャの各クリック間の最大猶予 (ms) */
    private static final long GESTURE_WINDOW_MILLIS = 1_000L;

    private final ToolSettingsStore store;
    private final Map<UUID, GestureState> gestureStates = new ConcurrentHashMap<>();

    public ToolSettingsController(ToolSettingsStore store) {
        this.store = store;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // オフハンドの重複発火を無視
        }
        Action action = event.getAction();
        boolean left = action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK;
        boolean right = action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
        if (!left && !right) {
            return;
        }
        Player player = event.getPlayer();
        if (!isMiningTool(player.getInventory().getItemInMainHand())) {
            return; // 採掘系の道具を持っているときだけ
        }
        GestureState gesture = gestureStates.computeIfAbsent(player.getUniqueId(), ignored -> new GestureState());
        if (gesture.advance(left, System.currentTimeMillis())) {
            openMenu(player);
        }
    }

    private void openMenu(Player player) {
        ToolSettingsMenu menu = new ToolSettingsMenu(store, player);
        menu.open(player);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.7F, 1.4F);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ToolSettingsMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < ToolSettingsMenu.SIZE) {
            menu.handleClick(player, rawSlot);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof ToolSettingsMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        gestureStates.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        gestureStates.clear();
    }

    private static boolean isMiningTool(ItemStack item) {
        if (item == null) {
            return false;
        }
        String name = item.getType().name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
    }

    /**
     * 左 → 右 → 左 のクリック順を判定する状態機械。
     * stage は「一致済みクリック数」。期待順は [左, 右, 左]。
     */
    private static final class GestureState {
        private int stage;
        private long lastClickMillis;

        boolean advance(boolean left, long now) {
            if (now - lastClickMillis > GESTURE_WINDOW_MILLIS) {
                stage = 0;
            }
            lastClickMillis = now;
            // 期待するクリック種別: stage 0 = 左, stage 1 = 右, stage 2 = 左
            boolean expectLeft = stage != 1;
            if (left == expectLeft) {
                stage++;
                if (stage >= 3) {
                    stage = 0;
                    return true;
                }
                return false;
            }
            stage = left ? 1 : 0;
            return false;
        }
    }
}
