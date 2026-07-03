package org.pexserver.koukunn.bettersurvival.Commands.offline;

import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess.OfflineAccessManager;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess.OfflineAccessModule;

import java.util.Set;

/**
 * /offline コマンド: オフラインアカウントログイン許可リストを管理する。
 */
public class OfflineCommand extends BaseCommand {

    private static final String INPUT_KEY_NAME = "name";

    private final OfflineAccessModule module;
    private final OfflineAccessManager manager;

    public OfflineCommand(OfflineAccessModule module) {
        this.module = module;
        this.manager = module.getManager();
    }

    public OfflineCommand(OfflineAccessManager manager) {
        this.module = null;
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "offline";
    }

    @Override
    public String getDescription() {
        return "オフラインアカウントログイン許可リストの管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはプレイヤーから実行してください");
            return true;
        }

        if (module != null && module.isInjectionFailed()) {
            openFailureMenu(player);
            return true;
        }

        if (!manager.isEnabled()) {
            sendError(sender, "OfflineAccess は現在無効です");
            return true;
        }

        openMenu(player);
        return true;
    }

    private void openMenu(Player player) {
        ChestUI.builder()
                .title("§6OfflineAccess Menu")
                .size(27)
                .defaultIcon(Material.PAPER)
                .type("offline_access_menu")
                .addButtonAt(11, "§aプレイヤー追加", Material.LIME_WOOL, "クリックして許可するプレイヤー名を入力")
                .addButtonAt(13, "§e一覧表示", Material.BOOK, "許可されているプレイヤー名を表示")
                .addButtonAt(15, "§cプレイヤー削除", Material.RED_WOOL, "クリックして削除するプレイヤー名を入力")
                .then((result, p) -> {
                    if (result.cancelled || !result.success || result.slot == null) {
                        return;
                    }
                    switch (result.slot) {
                        case 11 -> openAddDialog(p);
                        case 13 -> listPlayers(p);
                        case 15 -> openRemoveDialog(p);
                        default -> {
                        }
                    }
                })
                .show(player);
    }

    private void openFailureMenu(Player player) {
        String reason = module.getFailureReason();
        ChestUI.builder()
                .title("§cOfflineAccess Failed")
                .size(27)
                .defaultIcon(Material.BARRIER)
                .type("offline_access_failed_menu")
                .addButtonAt(11, "§cNMS 初期化失敗", Material.BARRIER, "OfflineAccess はフェールセーフで無効化されています")
                .addButtonAt(13, "§e失敗原因", Material.PAPER, trim(reason, 120))
                .addButtonAt(15, "§6デバッグログ", Material.WRITABLE_BOOK, "クリックして詳細 Dialog を表示")
                .then((result, p) -> {
                    if (result.cancelled || !result.success || result.slot == null) {
                        return;
                    }
                    if (result.slot == 13 || result.slot == 15) {
                        openFailureDialog(p);
                    }
                })
                .show(player);
    }

    private void openFailureDialog(Player player) {
        DialogUI.builder()
                .title("OfflineAccess NMS 失敗")
                .body("OfflineAccess の Netty/NMS 初期化に失敗したため、この機能だけを無効化しています。")
                .body("発生時刻: " + module.getLastAttemptAtText())
                .body("失敗原因: " + module.getFailureReason())
                .addTextInput("debug", "デバッグログ", trim(module.getDebugLog(), 7900), 8192, true)
                .confirmation("閉じる", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        openFailureMenu(p);
                    }
                })
                .show(player);
    }

    private String trim(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, Math.max(0, maxLength - 3)) + "...";
    }

    private void openAddDialog(Player player) {
        DialogUI.builder()
                .title("オフラインプレイヤー追加")
                .body("許可するプレイヤー名を入力してください")
                .addTextInput(INPUT_KEY_NAME, "プレイヤー名", "", 16, false)
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        return;
                    }
                    String name = result.getText(INPUT_KEY_NAME);
                    if (name == null || name.isBlank()) {
                        sendError(p, "無効なプレイヤー名です");
                        return;
                    }
                    if (manager.add(name)) {
                        sendSuccess(p, "追加しました: " + name);
                    } else {
                        sendError(p, "追加に失敗しました（既に登録済みまたは無効な名前）: " + name);
                    }
                })
                .show(player);
    }

    private void openRemoveDialog(Player player) {
        DialogUI.builder()
                .title("オフラインプレイヤー削除")
                .body("削除するプレイヤー名を入力してください")
                .addTextInput(INPUT_KEY_NAME, "プレイヤー名", "", 16, false)
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) {
                        return;
                    }
                    String name = result.getText(INPUT_KEY_NAME);
                    if (name == null || name.isBlank()) {
                        sendError(p, "無効なプレイヤー名です");
                        return;
                    }
                    if (manager.remove(name)) {
                        sendSuccess(p, "削除しました: " + name);
                    } else {
                        sendError(p, "削除に失敗しました（未登録）: " + name);
                    }
                })
                .show(player);
    }

    private void listPlayers(Player player) {
        Set<String> names = manager.getAll();
        if (names.isEmpty()) {
            sendInfo(player, "許可されているプレイヤーはいません");
            return;
        }
        sendInfo(player, "許可されているプレイヤー: " + String.join(", ", names));
    }
}


