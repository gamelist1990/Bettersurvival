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
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantRegistry;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.ui.ToolSettingsMenu;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 「左 → 右 → 左 → 右 → 右 → シフト」ジェスチャでツール設定メニューを開くリスナー。
 *
 * 対応エンチャントが付いた道具を手に持った状態でこの並びをすばやく行うとメニューが開き、
 * 範囲採掘や採掘穴埋めといった効果を ON/OFF できる。
 * 手順を長め＋最後をシフトにしているのは、通常操作での誤爆を防ぐため。
 */
public class ToolSettingsController implements Listener {

    /** ジェスチャの各入力間の最大猶予 (ms) */
    private static final long GESTURE_WINDOW_MILLIS = 1_000L;

    /** 起動手順: 左 → 右 → 左 → 右 → 右 → シフト */
    private static final Input[] SEQUENCE = {
            Input.LEFT, Input.RIGHT, Input.LEFT, Input.RIGHT, Input.RIGHT, Input.SNEAK
    };

    private enum Input {
        LEFT, RIGHT, SNEAK
    }

    private final ToolSettingsStore store;
    private final CustomEnchantRegistry registry;
    private final Map<UUID, GestureState> gestureStates = new ConcurrentHashMap<>();

    public ToolSettingsController(ToolSettingsStore store, CustomEnchantRegistry registry) {
        this.store = store;
        this.registry = registry;
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
        feedInput(event.getPlayer(), left ? Input.LEFT : Input.RIGHT);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (!event.isSneaking()) {
            return; // シフトを「押し始めた」瞬間だけ手順に数える
        }
        feedInput(event.getPlayer(), Input.SNEAK);
    }

    private void feedInput(Player player, Input input) {
        if (!hasApplicableSetting(player.getInventory().getItemInMainHand())) {
            return; // 対応する効果のエンチャントが付いた道具を持っているときだけ
        }
        GestureState gesture = gestureStates.computeIfAbsent(player.getUniqueId(), ignored -> new GestureState());
        if (gesture.advance(input, System.currentTimeMillis())) {
            openMenu(player);
        }
    }

    private void openMenu(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        ToolSettingsMenu menu = new ToolSettingsMenu(store, registry, tool, player);
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

    /** 道具に、ツール設定で切り替えられるエンチャントが1つでも付いているか */
    private boolean hasApplicableSetting(ItemStack tool) {
        if (tool == null || tool.getType().isAir()) {
            return false;
        }
        for (ToolSetting setting : store.settings()) {
            CustomEnchant enchant = registry.byId(setting.id());
            if (enchant != null && enchant.levelOf(tool) > 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * {@link #SEQUENCE} (左 → 右 → 左 → 右 → 右 → シフト) の入力順を判定する状態機械。
     * stage は「一致済み入力数」。一定時間途切れると先頭からやり直す。
     */
    private static final class GestureState {
        private int stage;
        private long lastInputMillis;

        boolean advance(Input input, long now) {
            if (now - lastInputMillis > GESTURE_WINDOW_MILLIS) {
                stage = 0;
            }
            lastInputMillis = now;
            if (input == SEQUENCE[stage]) {
                stage++;
                if (stage >= SEQUENCE.length) {
                    stage = 0;
                    return true;
                }
                return false;
            }
            // 不一致。この入力が手順の先頭と一致するなら、そこからやり直す
            stage = input == SEQUENCE[0] ? 1 : 0;
            return false;
        }
    }
}
