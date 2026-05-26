package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

public final class CopperGolemCombatMainMenuUI {

    private CopperGolemCombatMainMenuUI() {
    }

    public interface ActionHandler {
        void onToggleMode(Player player);
        void onEditRange(Player player);
        void onUpgradeRange(Player player);
        void onUpgradeCombatHealth(Player player);
        void onConfigureCombatWeapon(Player player);
        void onOpenTargets(Player player);
        void onClose(Player player);
    }

    public static void open(Player player, GolemProfile profile, int maxRange, double combatMaxHealth, ActionHandler handler) {
        String info = "§7ID: §f" + profile.id()
                + "\n§7Level: §f" + profile.level()
                + "\n§7未使用ポイント: §e" + profile.availablePoints()
                + "\n§7Mode: §f" + profile.mode().getDisplayName()
                + "\n§7行動範囲: §f" + profile.range() + " / " + maxRange
                + "\n§7範囲拡張: §f" + profile.rangePoints()
                + "\n§7戦闘HP: §f" + (int) combatMaxHealth + " / 100"
                + "\n§7HP強化: §f" + profile.combatHealthPoints()
                + "\n§7成果物保管先: §f" + profile.targets().size() + "件"
                + "\n§7敵対Mob探索: §f10ブロック以上";

        ChestUI.builder()
                .title("Copper Golem [戦闘]: " + profile.id())
                .size(54)
                .addButtonAt(4, "§6ステータス", Material.COPPER_BLOCK, info)
                .addButtonAt(10, "§bモード切替", Material.COMPARATOR, "§7現在: " + profile.mode().getDisplayName())
                .addButtonAt(11, "§e行動範囲", Material.SPYGLASS, "§710以上で探索")
                .addButtonAt(13, "§6範囲拡張 +1", Material.COMPASS,
                        "§7必要ポイント: 1\n§7現在: " + profile.rangePoints() + "\n§7最大行動範囲が増えます")
                .addButtonAt(14, "§c戦闘HP強化 +5", Material.GOLDEN_APPLE,
                        "§7必要ポイント: 1\n§7現在HP: " + (int) combatMaxHealth + " / 100\n§7戦闘時の耐久力を上げます")
                .addButtonAt(19, "§6戦闘装備設定", Material.NETHERITE_CHESTPLATE, "§7武器/オフハンド/防具を設定")
                .addButtonAt(20, "§6成果物保管先", Material.CHEST, "§7討伐ドロップの搬送先")
                .addButtonAt(53, "§c閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> handler.onToggleMode(p);
                        case 11 -> handler.onEditRange(p);
                        case 13 -> handler.onUpgradeRange(p);
                        case 14 -> handler.onUpgradeCombatHealth(p);
                        case 19 -> handler.onConfigureCombatWeapon(p);
                        case 20 -> handler.onOpenTargets(p);
                        case 53 -> handler.onClose(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }
}
