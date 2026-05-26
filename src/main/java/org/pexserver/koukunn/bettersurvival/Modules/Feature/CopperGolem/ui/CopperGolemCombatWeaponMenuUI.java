package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

public final class CopperGolemCombatWeaponMenuUI {

    public static final String MENU_TYPE = "coppergolem_combat_weapon";
    public static final int CURRENT_WEAPON_SLOT = 1;
    public static final int CURRENT_DISABLED_SLOT = 2;
    public static final int CURRENT_HELMET_SLOT = 3;
    public static final int CURRENT_CHESTPLATE_SLOT = 4;
    public static final int CURRENT_LEGGINGS_SLOT = 5;
    public static final int CURRENT_BOOTS_SLOT = 6;
    public static final int INPUT_MAIN_HAND = 10;
    public static final int INPUT_OFF_HAND = 11;
    public static final int INPUT_HELMET = 12;
    public static final int INPUT_CHESTPLATE = 13;
    public static final int INPUT_LEGGINGS = 14;
    public static final int INPUT_BOOTS = 15;
    public static final int CLEAR_INPUTS_SLOT = 18;
    public static final int BACK_SLOT = 25;
    public static final int CLOSE_SLOT = 26;
    private static final int[] FILLER_SLOTS = {0, 7, 8, 9, 16, 17, 19, 20, 21, 22, 23, 24};

    private CopperGolemCombatWeaponMenuUI() {
    }

    public interface ActionHandler {
        void onBack(Player player);
        void onClose(Player player);
        void onClearInputs(Player player);
        void onTakeCurrent(Player player, int slot);
    }

    public static void open(
            Player player,
            GolemProfile profile,
            ItemStack equippedMainHand,
            ItemStack equippedOffHand,
            ItemStack equippedHelmet,
            ItemStack equippedChestplate,
            ItemStack equippedLeggings,
            ItemStack equippedBoots,
            ActionHandler handler) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("戦闘装備設定: " + profile.id())
                .size(27)
                .type(MENU_TYPE)
                .editableSlots(INPUT_MAIN_HAND, INPUT_HELMET, INPUT_CHESTPLATE, INPUT_LEGGINGS, INPUT_BOOTS)
                .addButtonAt(CLEAR_INPUTS_SLOT, "§c入力をクリア", Material.BARRIER, "§7中段の入力中アイテムを戻します")
                .addButtonAt(BACK_SLOT, "§e戻る", Material.ARROW, "")
                .addButtonAt(CLOSE_SLOT, "§7閉じる", Material.RED_STAINED_GLASS_PANE, "");

        for (int slot : FILLER_SLOTS) {
            builder.addButtonAt(slot, " ", Material.GRAY_STAINED_GLASS_PANE, "");
        }

        addCurrentDisplay(builder, CURRENT_WEAPON_SLOT, equippedMainHand, Material.IRON_SWORD, "§e現在: 武器(オフハンド)");
        builder.addButtonAt(CURRENT_DISABLED_SLOT, "§8現在: 使用不可", Material.BARRIER, "§7オフハンド固定");
        addCurrentDisplay(builder, CURRENT_HELMET_SLOT, equippedHelmet, Material.IRON_HELMET, "§e現在: ヘルメット");
        addCurrentDisplay(builder, CURRENT_CHESTPLATE_SLOT, equippedChestplate, Material.IRON_CHESTPLATE, "§e現在: 胸当て");
        addCurrentDisplay(builder, CURRENT_LEGGINGS_SLOT, equippedLeggings, Material.IRON_LEGGINGS, "§e現在: レギンス");
        addCurrentDisplay(builder, CURRENT_BOOTS_SLOT, equippedBoots, Material.IRON_BOOTS, "§e現在: ブーツ");

        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            switch (result.slot) {
                case CURRENT_WEAPON_SLOT, CURRENT_HELMET_SLOT, CURRENT_CHESTPLATE_SLOT, CURRENT_LEGGINGS_SLOT, CURRENT_BOOTS_SLOT ->
                        handler.onTakeCurrent(p, result.slot);
                case CLEAR_INPUTS_SLOT -> handler.onClearInputs(p);
                case BACK_SLOT -> handler.onBack(p);
                case CLOSE_SLOT -> handler.onClose(p);
                default -> {
                }
            }
        }).show(player);
    }

    private static void addCurrentDisplay(ChestUI.Builder builder, int slot, ItemStack current, Material fallback, String label) {
        if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
            builder.addButtonAt(slot, "§c未装備", fallback, "§7現在は未装備");
            return;
        }
        ItemStack display = current.clone();
        display.setAmount(1);
        builder.addCustomItemAt(slot, label, display, "§7クリックで取り外し");
    }
}
