package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;

import java.util.function.BiConsumer;

/**
 * 共有ストレージ設定 UI。
 *
 * レイアウトは行ごとに役割を分け、同じ種類の設定が同じ行に並ぶようにする:
 * 1行目: 情報表示 / 2行目: 手動操作 / 3行目: ホッパー搬送 / 4行目: 機能 / 5行目: アクション
 * スロット番号はこのクラスの公開定数が唯一の定義。モジュール側もこれを参照する。
 */
public final class SharedStorageSettingsUi {

    public static final int SLOT_INFO = 4;

    public static final int SLOT_SUB_INSERT = 10;
    public static final int SLOT_SUB_EXTRACT = 11;

    public static final int SLOT_SUB_HOPPER_INSERT = 19;
    public static final int SLOT_SUB_HOPPER_EXTRACT = 20;
    public static final int SLOT_MAIN_HOPPER_INSERT = 21;
    public static final int SLOT_MAIN_HOPPER_EXTRACT = 22;

    public static final int SLOT_FRAME_FILTER = 28;
    public static final int SLOT_FRAME_FILTER_MODE = 29;
    public static final int SLOT_CHEST_PAGE = 30;
    public static final int SLOT_PARTICLE = 31;

    public static final int SLOT_RANGE = 38;
    public static final int SLOT_RESORT = 40;
    public static final int SLOT_CLOSE = 44;

    private SharedStorageSettingsUi() {
    }

    public static void openMainMenu(Player player, MainMenuState state, BiConsumer<Player, Integer> onClick) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("共有ストレージ設定 [" + state.networkId() + "]")
                .size(45);

        builder.addButtonAt(SLOT_INFO, "§b共有ストレージ §f" + state.networkId(), Material.ENDER_CHEST,
                "§7ID: §f" + state.networkId()
                        + "\n§7接続中のsub: §f" + state.subCount() + "個"
                        + "\n§7接続範囲: §f" + state.subRange() + "ブロック");

        // ---- 手動操作(プレイヤーがsubチェストを直接開く時) ----
        builder.addButtonAt(9, "§6■ 手動操作", Material.PLAYER_HEAD,
                "§7プレイヤーが sub チェストを\n§7直接開いた時のルール");
        addToggle(builder, SLOT_SUB_INSERT, "subへ入れる", state.allowSubInsert(),
                "§7手でsubチェストにアイテムを入れる");
        addToggle(builder, SLOT_SUB_EXTRACT, "subから取る", state.allowSubExtract(),
                "§7手でsubチェストからアイテムを取り出す");

        // ---- ホッパー搬送 ----
        builder.addButtonAt(18, "§6■ ホッパー搬送", Material.HOPPER,
                "§7ホッパー・ドロッパー等の\n§7自動搬送のルール");
        addToggle(builder, SLOT_SUB_HOPPER_INSERT, "sub搬入", state.allowSubHopperInsert(),
                "§7ホッパーからsubへ入れる");
        addToggle(builder, SLOT_SUB_HOPPER_EXTRACT, "sub搬出", state.allowSubHopperExtract(),
                "§7ホッパーでsubから吸い出す");
        addToggle(builder, SLOT_MAIN_HOPPER_INSERT, "main搬入", state.allowMainInsert(),
                "§7ホッパーからmainへ入れる\n§7(入ったアイテムは自動で仕分け)");
        addToggle(builder, SLOT_MAIN_HOPPER_EXTRACT, "main搬出", state.allowMainExtract(),
                "§7ホッパーでmainから吸い出す");

        // ---- 機能設定 ----
        builder.addButtonAt(27, "§6■ 機能設定", Material.REDSTONE_TORCH,
                "§7仕分け・表示に関する機能");
        addToggle(builder, SLOT_FRAME_FILTER, "額縁フィルタ", state.frameFilterEnabled(),
                "§7subに貼った額縁のアイテムだけを\n§7そのsubへ仕分けする");
        builder.addButtonAt(SLOT_FRAME_FILTER_MODE,
                "§b一致モード: §f" + state.frameFilterModeLabel(),
                state.frameFilterEnabled() ? Material.COMPARATOR : Material.GRAY_DYE,
                "§7額縁フィルタの一致条件"
                        + "\n§7EXACT: 完全一致 / MATERIAL: 種類のみ"
                        + "\n§7ENCHANT_STATE: エンチャ有無"
                        + "\n§eクリックで切り替え");
        addToggle(builder, SLOT_CHEST_PAGE, "ChestPage", state.chestPageEnabled(),
                "§7mainをスニーク左クリックで\n§7sub一覧UIを開けるようにする");
        addToggle(builder, SLOT_PARTICLE, "搬送演出", state.transferParticleEnabled(),
                "§7仕分け時のパーティクル表示");

        // ---- アクション ----
        builder.addButtonAt(SLOT_RANGE, "§esub接続範囲を変更", Material.SPYGLASS,
                "§7現在: §f" + state.subRange() + " §7/ 最大 " + state.maxSubRange() + "ブロック"
                        + "\n§eクリックで設定画面を開く");
        builder.addButtonAt(SLOT_RESORT, "§a今すぐ再仕分け", Material.CHEST_MINECART,
                "§7main / sub の全アイテムを\n§7いま再仕分けします"
                        + "\n§eクリックで実行");
        builder.addButtonAt(SLOT_CLOSE, "§c閉じる", Material.BARRIER, "§7UIを閉じます");

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            onClick.accept(p, result.slot);
        }).show(player);
    }

    private static void addToggle(ChestUI.Builder builder, int slot, String name, boolean enabled, String description) {
        String label = enabled ? "§a✔ " + name + ": 許可" : "§c✘ " + name + ": 禁止";
        Material icon = enabled ? Material.LIME_DYE : Material.GRAY_DYE;
        String lore = description
                + "\n§7現在: " + (enabled ? "§a許可" : "§c禁止")
                + "\n§eクリックで切り替え";
        builder.addButtonAt(slot, label, icon, lore);
    }

    public static void openSubRangeMenu(Player player, String networkId, int subRange, int maxSubRange, BiConsumer<Player, Integer> onClick) {
        String status = "§7現在値: §f" + subRange + " §7/ 最大 " + maxSubRange;
        ChestUI.builder()
                .title("sub接続範囲 [" + networkId + "] 現在: " + subRange)
                .size(27)
                .addButtonAt(10, "§c-10", Material.RED_STAINED_GLASS_PANE, status + "\n§eクリックで -10")
                .addButtonAt(11, "§c-5", Material.RED_STAINED_GLASS_PANE, status + "\n§eクリックで -5")
                .addButtonAt(12, "§c-1", Material.PINK_STAINED_GLASS_PANE, status + "\n§eクリックで -1")
                .addButtonAt(13, "§b現在: " + subRange + "ブロック", Material.SPYGLASS, status)
                .addButtonAt(14, "§a+1", Material.LIME_STAINED_GLASS_PANE, status + "\n§eクリックで +1")
                .addButtonAt(15, "§a+5", Material.GREEN_STAINED_GLASS_PANE, status + "\n§eクリックで +5")
                .addButtonAt(16, "§a+10", Material.GREEN_STAINED_GLASS_PANE, status + "\n§eクリックで +10")
                .addButtonAt(18, "§e10 に設定", Material.PAPER, status)
                .addButtonAt(19, "§e15 に設定", Material.PAPER, status)
                .addButtonAt(20, "§e20 に設定", Material.PAPER, status)
                .addButtonAt(21, "§e30 に設定", Material.PAPER, status)
                .addButtonAt(22, "§e50 に設定", Material.PAPER, status)
                .addButtonAt(23, "§b最大値に設定", Material.COMPASS, status)
                .addButtonAt(26, "§c戻る", Material.ARROW, "§7設定画面へ戻る")
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
