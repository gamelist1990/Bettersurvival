package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

public final class CopperGolemMainMenuUI {

    private CopperGolemMainMenuUI() {
    }

    public interface ActionHandler {
        void onToggleMode(Player player);
        void onEditRange(Player player);
        void onUpgradeHarvest(Player player);
        void onUpgradeRange(Player player);
        void onUpgradeMoveSpeed(Player player);
        void onToggleCropRouteMode(Player player);
        void onConfigureCombatWeapon(Player player);
        void onUnlockReplant(Player player);
        void onUnlockBoneMeal(Player player);
        void onToggleReplant(Player player);
        void onToggleBoneMeal(Player player);
        void onOpenCropFilter(Player player);
        void onOpenTargets(Player player);
        void onOpenBoneMealSources(Player player);
        void onClose(Player player);
    }

    public static void open(
            Player player,
            GolemProfile profile,
            int maxRange,
            boolean replantUnlocked,
            boolean boneMealUnlocked,
            int replantUnlockCost,
            int boneMealUnlockCost,
            ActionHandler handler) {
        switch (profile.mode()) {
            case IDLE -> openIdleMenu(player, profile, maxRange, handler);
            case CROP -> openCropMenu(player, profile, maxRange, replantUnlocked, boneMealUnlocked, replantUnlockCost, boneMealUnlockCost, handler);
            case COMBAT -> openCombatMenu(player, profile, maxRange, handler);
        }
    }

    private static void openIdleMenu(
            Player player,
            GolemProfile profile,
            int maxRange,
            ActionHandler handler) {
        String info = "§7ID: §f" + profile.id()
                + "\n§7Level: §f" + profile.level()
                + "\n§7未使用ポイント: §e" + profile.availablePoints()
                + "\n§7Mode: §f" + profile.mode().getDisplayName()
                + "\n§7状態: §f停止中"
                + "\n§7行動範囲: §f" + profile.range() + " / " + maxRange
                + "\n§7範囲拡張: §f" + profile.rangePoints()
                + "\n§7保管先: §f" + profile.targets().size() + "件";

        ChestUI.builder()
                .title("Copper Golem [Idle]: " + profile.id())
                .size(54)
                .addButtonAt(4, "§6ステータス", Material.COPPER_BLOCK, info)
                .addButtonAt(10, "§bモード切替", Material.COMPARATOR, "§7現在: " + profile.mode().getDisplayName())
                .addButtonAt(11, "§e行動範囲", Material.SPYGLASS, "§71 〜 " + maxRange + " の範囲で設定")
                .addButtonAt(13, "§6範囲拡張 +1", Material.COMPASS,
                        "§7必要ポイント: 1\n§7現在: " + profile.rangePoints() + "\n§7最大行動範囲が増えます")
                .addButtonAt(19, "§7機能スロット", Material.GRAY_STAINED_GLASS_PANE, "§7戦闘モードで利用可能")
                .addButtonAt(20, "§6保管先設定", Material.CHEST, "§7チェスト/ラージチェスト/樽を複数登録")
                .addButtonAt(53, "§c閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> handler.onToggleMode(p);
                        case 11 -> handler.onEditRange(p);
                        case 13 -> handler.onUpgradeRange(p);
                        case 20 -> handler.onOpenTargets(p);
                        case 53 -> handler.onClose(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private static void openCropMenu(
            Player player,
            GolemProfile profile,
            int maxRange,
            boolean replantUnlocked,
            boolean boneMealUnlocked,
            int replantUnlockCost,
            int boneMealUnlockCost,
            ActionHandler handler) {
        String info = "§7ID: §f" + profile.id()
                + "\n§7Level: §f" + profile.level()
                + "\n§7未使用ポイント: §e" + profile.availablePoints()
                + "\n§7Mode: §f" + profile.mode().getDisplayName()
                + "\n§7行動範囲: §f" + profile.range() + " / " + maxRange
                + "\n§7採取速度強化: §f" + profile.harvestPoints()
                + "\n§7移動速度強化: §f" + profile.moveSpeedPoints()
                + "\n§7範囲拡張: §f" + profile.rangePoints()
                + "\n§7行動モード: §f" + profile.cropRouteMode().getDisplayName()
                + "\n§7植え直し解放: " + (replantUnlocked ? "§a済" : "§c未")
                + "\n§7骨粉機能解放: " + (boneMealUnlocked ? "§a済" : "§c未")
                + "\n§7保管先: §f" + profile.targets().size() + "件";

        ChestUI.builder()
                .title("Copper Golem [作物採取]: " + profile.id())
                .size(54)
                .addButtonAt(4, "§6ステータス", Material.COPPER_BLOCK, info)
                .addButtonAt(10, "§bモード切替", Material.COMPARATOR, "§7現在: " + profile.mode().getDisplayName())
                .addButtonAt(11, "§e行動範囲", Material.SPYGLASS, "§71 〜 " + maxRange + " の範囲で設定")
                .addButtonAt(12, "§6採取速度強化 +1", Material.IRON_HOE,
                        "§7必要ポイント: 1\n§7現在: " + profile.harvestPoints() + "\n§71サイクルの採取ブロック数が増えます")
                .addButtonAt(13, "§6範囲拡張 +1", Material.COMPASS,
                        "§7必要ポイント: 1\n§7現在: " + profile.rangePoints() + "\n§7最大行動範囲が増えます")
                .addButtonAt(19, "§6作物フィルタ", Material.WHEAT, "§7採取対象の作物をスロット式で設定")
                .addButtonAt(20, "§6保管先設定", Material.CHEST, "§7チェスト/ラージチェスト/樽を複数登録")
                .addButtonAt(21, "§6骨粉供給元設定", Material.BONE_MEAL, "§7骨粉入りチェストを複数登録")
                .addButtonAt(23, "§a自動植え直し", Material.WHEAT_SEEDS, profile.autoReplant() ? "§aON" : "§cOFF")
                .addButtonAt(24, "§a自動骨粉", Material.BONE_MEAL, profile.autoBoneMeal() ? "§aON" : "§cOFF")
                .addButtonAt(47, "§6機能解放:植え直し", Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE,
                        "§7必要ポイント: " + replantUnlockCost + "\n§7状態: " + (replantUnlocked ? "解放済み" : "未解放"))
                .addButtonAt(48, "§6機能解放:骨粉", Material.BONE_MEAL,
                        "§7必要ポイント: " + boneMealUnlockCost + "\n§7状態: " + (boneMealUnlocked ? "解放済み" : "未解放"))
                .addButtonAt(49, "§6行動アルゴリズム", Material.RECOVERY_COMPASS,
                        "§7現在: " + profile.cropRouteMode().getDisplayName() + "\n§7密集地優先 + 行動モード切替")
                .addButtonAt(50, "§6移動速度強化 +1", Material.SUGAR,
                        "§7必要ポイント: 1\n§7現在: " + profile.moveSpeedPoints() + "\n§7移動速度が上がります")
                .addButtonAt(51, "§7機能スロット", Material.GRAY_STAINED_GLASS_PANE, "§7今後の拡張用")
                .addButtonAt(53, "§c閉じる", Material.BARRIER, "")
                .then((result, p) -> {
                    if (result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 10 -> handler.onToggleMode(p);
                        case 11 -> handler.onEditRange(p);
                        case 12 -> handler.onUpgradeHarvest(p);
                        case 13 -> handler.onUpgradeRange(p);
                        case 19 -> handler.onOpenCropFilter(p);
                        case 20 -> handler.onOpenTargets(p);
                        case 21 -> handler.onOpenBoneMealSources(p);
                        case 23 -> handler.onToggleReplant(p);
                        case 24 -> handler.onToggleBoneMeal(p);
                        case 47 -> handler.onUnlockReplant(p);
                        case 48 -> handler.onUnlockBoneMeal(p);
                        case 49 -> handler.onToggleCropRouteMode(p);
                        case 50 -> handler.onUpgradeMoveSpeed(p);
                        case 53 -> handler.onClose(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private static void openCombatMenu(
            Player player,
            GolemProfile profile,
            int maxRange,
            ActionHandler handler) {
        String info = "§7ID: §f" + profile.id()
                + "\n§7Level: §f" + profile.level()
                + "\n§7未使用ポイント: §e" + profile.availablePoints()
                + "\n§7Mode: §f" + profile.mode().getDisplayName()
                + "\n§7行動範囲: §f" + profile.range() + " / " + maxRange
                + "\n§7範囲拡張: §f" + profile.rangePoints()
                + "\n§7保管先: §f" + profile.targets().size() + "件"
                + "\n§7敵対Mob探索: §f10ブロック以上";

        ChestUI.builder()
                .title("Copper Golem [戦闘]: " + profile.id())
                .size(54)
                .addButtonAt(4, "§6ステータス", Material.COPPER_BLOCK, info)
                .addButtonAt(10, "§bモード切替", Material.COMPARATOR, "§7現在: " + profile.mode().getDisplayName())
                .addButtonAt(11, "§e行動範囲", Material.SPYGLASS, "§710以上で探索")
                .addButtonAt(13, "§6範囲拡張 +1", Material.COMPASS,
                        "§7必要ポイント: 1\n§7現在: " + profile.rangePoints() + "\n§7最大行動範囲が増えます")
                .addButtonAt(19, "§6戦闘装備設定", Material.NETHERITE_CHESTPLATE,
                        "§7専用画面で武器/防具/オフハンドを設定")
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
