package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;

import java.util.function.BiConsumer;

public final class SharedStorageSettingsUi {

    private SharedStorageSettingsUi() {
    }

    public static void openMainMenu(Player player, MainMenuState state, BiConsumer<Player, Integer> onClick) {
        ChestUI.builder()
                .title("共有ストレージ " + state.networkId())
                .size(36)
                .addButtonAt(4, "§b共有ストレージ", Material.CHEST,
                        "§7ID: " + state.networkId() + "\n§7sub数: " + state.subCount())
                .addButtonAt(5, "§esub接続範囲", Material.SPYGLASS,
                        "§7現在: " + state.subRange() + "ブロック\n§7設定可能: 1 〜 " + state.maxSubRange())
                .addButtonAt(0, "§6sub設定", Material.BOOK, "§7直接操作 / ホッパー設定")
                .addButtonAt(18, "§6main設定", Material.CHEST, "§7ホッパー搬入 / 搬出設定")
                .addButtonAt(21, "§6額縁フィルタ", Material.ITEM_FRAME, "§7ON/OFF と一致ルール")
                .addButtonAt(22,
                        state.chestPageEnabled() ? "§aChestPage: ON" : "§cChestPage: OFF",
                        state.chestPageEnabled() ? Material.BOOKSHELF : Material.BARRIER,
                        "§7スニーク左クリックでsub一覧UIを開く")
                .addButtonAt(27, "§6操作 / 演出", Material.BELL, "§7再仕分け / 演出 / 終了")
                .addButtonAt(10,
                        state.allowSubInsert() ? "§asub直接投入: 許可" : "§csub直接投入: 禁止",
                        state.allowSubInsert() ? Material.LIME_DYE : Material.RED_DYE,
                        "§7プレイヤー操作で sub に入れる")
                .addButtonAt(11,
                        state.allowSubExtract() ? "§asub取出し: 許可" : "§csub取出し: 禁止",
                        state.allowSubExtract() ? Material.LIME_CANDLE : Material.RED_CANDLE,
                        "§7プレイヤー操作で sub から出せる")
                .addButtonAt(12,
                        state.allowSubHopperInsert() ? "§asubホッパー搬入: 許可" : "§csubホッパー搬入: 禁止",
                        state.allowSubHopperInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から sub に入れる")
                .addButtonAt(13,
                        state.allowSubHopperExtract() ? "§asubホッパー搬出: 許可" : "§csubホッパー搬出: 禁止",
                        state.allowSubHopperExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で sub から吸い出す")
                .addButtonAt(19,
                        state.allowMainInsert() ? "§amainホッパー搬入: 許可" : "§cmainホッパー搬入: 禁止",
                        state.allowMainInsert() ? Material.HOPPER : Material.BARRIER,
                        "§7ホッパー等から main に入れる")
                .addButtonAt(20,
                        state.allowMainExtract() ? "§amainホッパー搬出: 許可" : "§cmainホッパー搬出: 禁止",
                        state.allowMainExtract() ? Material.DROPPER : Material.BARRIER,
                        "§7ホッパー等で main から吸い出す")
                .addButtonAt(23,
                        state.frameFilterEnabled() ? "§a額縁フィルタ: ON" : "§c額縁フィルタ: OFF",
                        state.frameFilterEnabled() ? Material.ITEM_FRAME : Material.BARRIER,
                        "§7subに額縁のアイテムだけを入れる")
                .addButtonAt(24,
                        "§b額縁一致モード: " + state.frameFilterModeLabel(),
                        state.frameFilterEnabled() ? Material.COMPARATOR : Material.GRAY_DYE,
                        "§7クリックで切り替え\n§7EXACT / MATERIAL / ENCHANT_STATE")
                .addButtonAt(29,
                        state.transferParticleEnabled() ? "§aParticle演出: ON" : "§cParticle演出: OFF",
                        state.transferParticleEnabled() ? Material.BLAZE_POWDER : Material.GUNPOWDER,
                        "§7搬送ライン演出の表示切替")
                .addButtonAt(8, "§b再仕分け実行", Material.HOPPER, "§7main / sub 全体を再仕分けします")
                .addButtonAt(35, "§7閉じる", Material.BARRIER, "§7UIを閉じます")
                .then((result, p) -> {
                    if (!result.success || result.slot == null)
                        return;
                    onClick.accept(p, result.slot);
                })
                .show(player);
    }

    public static void openSubRangeMenu(Player player, String networkId, int subRange, int maxSubRange, BiConsumer<Player, Integer> onClick) {
        String status = "§7現在値: §f" + subRange + " / " + maxSubRange;
        ChestUI.builder()
                .title("sub接続範囲設定: " + networkId)
                .size(27)
                .addButtonAt(10, "§c-10", Material.RED_STAINED_GLASS_PANE, status)
                .addButtonAt(11, "§c-5", Material.ORANGE_STAINED_GLASS_PANE, status)
                .addButtonAt(12, "§c-1", Material.PINK_STAINED_GLASS_PANE, status)
                .addButtonAt(13, "§6現在値", Material.SPYGLASS, status)
                .addButtonAt(14, "§a+1", Material.LIME_STAINED_GLASS_PANE, status)
                .addButtonAt(15, "§a+5", Material.GREEN_STAINED_GLASS_PANE, status)
                .addButtonAt(16, "§a+10", Material.EMERALD_BLOCK, status)
                .addButtonAt(18, "§b10に設定", Material.WHEAT_SEEDS, status)
                .addButtonAt(19, "§b15に設定", Material.CARROT, status)
                .addButtonAt(20, "§b20に設定", Material.POTATO, status)
                .addButtonAt(21, "§b30に設定", Material.BEETROOT_SEEDS, status)
                .addButtonAt(22, "§b50に設定", Material.NETHER_WART, status)
                .addButtonAt(23, "§b最大値に設定", Material.COMPASS, status)
                .addButtonAt(26, "§e戻る", Material.ARROW, "")
                .then((result, p) -> {
                    if (!result.success || result.slot == null)
                        return;
                    onClick.accept(p, result.slot);
                })
                .show(player);
    }

    public record MainMenuState(
            String networkId,
            int subCount,
            int subRange,
            int maxSubRange,
            boolean allowSubInsert,
            boolean allowSubExtract,
            boolean allowSubHopperInsert,
            boolean allowSubHopperExtract,
            boolean allowMainInsert,
            boolean allowMainExtract,
            boolean frameFilterEnabled,
            String frameFilterModeLabel,
            boolean chestPageEnabled,
            boolean transferParticleEnabled) {
    }
}
