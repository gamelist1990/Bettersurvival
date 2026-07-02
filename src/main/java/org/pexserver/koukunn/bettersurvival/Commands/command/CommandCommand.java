package org.pexserver.koukunn.bettersurvival.Commands.command;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandBlockManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.DialogUI;

import java.util.ArrayList;
import java.util.List;

public class CommandCommand extends BaseCommand {

    private final CommandBlockManager manager;

    public CommandCommand(CommandBlockManager manager) {
        this.manager = manager;
    }

    @Override
    public String getName() {
        return "command";
    }

    @Override
    public String getDescription() {
        return "グローバルにコマンドを無効化/管理します";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                if (!PermissionLevel.ADMIN.hasAccess(sender, null)) { sendError(sender, "管理者権限が必要です"); return true; }
                openMainMenu(player, PermissionLevel.MEMBER);
            } else {
                sendInfo(sender, "/command <level?> <add|remove|list|removeAll|search|addplugin> [arg]");
            }
            return true;
        }

        int idx = 0;
        PermissionLevel targetLevel = PermissionLevel.MEMBER; // default
        if (args.length > 1) {
            try {
                int lv = Integer.parseInt(args[0]);
                targetLevel = PermissionLevel.fromLevel(lv);
                idx = 1;
            } catch (NumberFormatException ignored) {
                // no leading level
            }
        }

        if (idx >= args.length) {
            sendError(sender, "引数が不正です");
            return true;
        }

        String action = args[idx].toLowerCase();
        if ("add".equals(action)) {
            if (args.length <= idx + 1) { sendError(sender, "パターンを指定してください"); return true; }
            String pattern = args[idx + 1];
            if (!PermissionLevel.ADMIN.hasAccess(sender, null)) { sendError(sender, "管理者権限が必要です"); return true; }
            boolean ok = manager.add(pattern, targetLevel);
            if (ok) sendSuccess(sender, "追加しました: " + pattern + " (level=" + targetLevel.getLevel() + ")");
            else sendError(sender, "既に存在するか追加に失敗しました");
            return true;
        }

        if ("remove".equals(action)) {
            if (args.length <= idx + 1) { sendError(sender, "パターンを指定してください"); return true; }
            String pattern = args[idx + 1];
            if (!PermissionLevel.ADMIN.hasAccess(sender, null)) { sendError(sender, "管理者権限が必要です"); return true; }
            boolean ok = manager.remove(pattern, targetLevel);
            if (ok) sendSuccess(sender, "削除しました: " + pattern + " (level=" + targetLevel.getLevel() + ")");
            else sendError(sender, "見つかりませんでした");
            return true;
        }

        if ("removeall".equals(action) || "removeAll".equals(action)) {
            if (!PermissionLevel.ADMIN.hasAccess(sender, null)) { sendError(sender, "管理者権限が必要です"); return true; }
            manager.removeAll(targetLevel);
            sendSuccess(sender, "すべて削除しました (level=" + targetLevel.getLevel() + ")");
            return true;
        }

        if ("list".equals(action)) {
            List<String> list = manager.list(targetLevel);
            if (list.isEmpty()) sendInfo(sender, "登録なし");
            else sendInfo(sender, "登録: " + String.join(", ", list));
            return true;
        }

        if ("addplugin".equals(action)) {
            if (args.length <= idx + 1) { sendError(sender, "プラグイン名を指定してください"); return true; }
            String pluginName = args[idx + 1];
            if (!PermissionLevel.ADMIN.hasAccess(sender, null)) { sendError(sender, "管理者権限が必要です"); return true; }
            boolean ok = manager.addPlugin(pluginName, targetLevel);
            if (ok) sendSuccess(sender, "追加しました: " + pluginName + " のすべてのコマンド (level=" + targetLevel.getLevel() + ")");
            else sendError(sender, "見つかりませんでした");
            return true;
        }

        if ("search".equals(action)) {
            if (args.length <= idx + 1) { sendError(sender, "検索文字列を指定してください"); return true; }
            String q = args[idx + 1].toLowerCase();
            
            // プラグインのコマンドも含めて検索
            List<String> match = manager.searchCommands(q);
            if (match.isEmpty()) {
                sendInfo(sender, "見つかりませんでした: " + q);
            } else {
                sendInfo(sender, "見つかったコマンド (" + match.size() + "個):");
                for (String cmd : match) {
                    sendInfo(sender, "  " + cmd);
                }
            }
            return true;
        }

        sendError(sender, "不明なアクション: " + action);
        return true;
    }

    private void openMainMenu(Player player, PermissionLevel level) {
        ChestUI.builder()
                .title("コマンド権限設定")
                .size(45)
                .addButtonAt(10, "§aコマンドを制限に追加", Material.LIME_DYE, "例: bukkit*, pl, reload などを入力")
                .addButtonAt(11, "§cコマンド制限を削除", Material.RED_DYE, "登録済みパターンを入力して解除")
                .addButtonAt(12, "§e登録一覧を見る", Material.BOOK, "現在のレベルに登録された制限を表示")
                .addButtonAt(13, "§bコマンド検索", Material.COMPASS, "使いたい/制限したいコマンドを検索")
                .addButtonAt(14, "§6プラグイン一括制限", Material.CHEST, "指定プラグインの全コマンドを制限")
                .addButtonAt(15, "§4このレベルを全削除", Material.TNT, "選択中レベルの制限をすべて解除")
                .addButtonAt(16, "§dユーザー個別設定", Material.PLAYER_HEAD, "特定プレイヤーだけの制限を設定")
                .addButtonAt(31, "§f対象レベル: §b" + level.getLevel() + " §7" + level.getDescription(), Material.NETHER_STAR, "クリックで対象レベルを変更")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) return;
                    switch (result.slot) {
                        case 10 -> askPattern(p, level, true);
                        case 11 -> askPattern(p, level, false);
                        case 12 -> showGlobalList(p, level);
                        case 13 -> askSearch(p, level);
                        case 14 -> askPlugin(p, level);
                        case 15 -> confirmRemoveAll(p, level);
                        case 16 -> openUserSelectMenu(p, level);
                        case 31 -> openLevelMenu(p, level);
                        default -> openMainMenu(p, level);
                    }
                })
                .show(player);
    }

    private void openLevelMenu(Player player, PermissionLevel current) {
        ChestUI.builder()
                .title("対象レベルを選択")
                .size(27)
                .addButtonAt(10, "§f0: 誰でも", Material.WHITE_WOOL, PermissionLevel.ANY.getDescription())
                .addButtonAt(11, "§a1: メンバー", Material.LIME_WOOL, PermissionLevel.MEMBER.getDescription())
                .addButtonAt(12, "§c2: 管理者", Material.RED_WOOL, PermissionLevel.ADMIN.getDescription())
                .addButtonAt(13, "§7現在: " + current.getLevel(), Material.NETHER_STAR, current.getDescription())
                .addButtonAt(14, "§93: コンソール", Material.BLUE_WOOL, PermissionLevel.CONSOLE.getDescription())
                .addButtonAt(15, "§64: 管理者/コンソール", Material.ORANGE_WOOL, PermissionLevel.ADMIN_OR_CONSOLE.getDescription())
                .addButtonAt(22, "§7戻る", Material.ARROW, "メインメニューへ")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null) { openMainMenu(p, current); return; }
                    PermissionLevel selected = switch (result.slot) {
                        case 10 -> PermissionLevel.ANY;
                        case 11 -> PermissionLevel.MEMBER;
                        case 12 -> PermissionLevel.ADMIN;
                        case 14 -> PermissionLevel.CONSOLE;
                        case 15 -> PermissionLevel.ADMIN_OR_CONSOLE;
                        default -> current;
                    };
                    openMainMenu(p, selected);
                })
                .show(player);
    }

    private void askPattern(Player player, PermissionLevel level, boolean add) {
        DialogUI.builder()
                .title(add ? "コマンド制限を追加" : "コマンド制限を削除")
                .body("例: pl / reload / bukkit* / floodgate*\n* を使うと前方一致のようにまとめて設定できます。")
                .addTextInput("pattern", "コマンド名またはパターン", "", 120, false)
                .confirmation(add ? "追加" : "削除", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) { openMainMenu(p, level); return; }
                    String pattern = result.getText("pattern").trim();
                    if (pattern.startsWith("/")) pattern = pattern.substring(1);
                    if (pattern.isBlank()) { p.sendMessage("§cパターンが空です"); openMainMenu(p, level); return; }
                    boolean ok = add ? manager.add(pattern, level) : manager.remove(pattern, level);
                    p.sendMessage(ok ? "§a保存しました: " + pattern : "§c処理できませんでした: " + pattern);
                    openMainMenu(p, level);
                })
                .show(player);
    }

    private void askPlugin(Player player, PermissionLevel level) {
        DialogUI.builder()
                .title("プラグイン一括制限")
                .body("プラグイン名を入力すると、そのプラグインの登録コマンドをまとめて制限します。")
                .addTextInput("plugin", "プラグイン名", "", 80, false)
                .confirmation("追加", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) { openMainMenu(p, level); return; }
                    String pluginName = result.getText("plugin").trim();
                    boolean ok = !pluginName.isBlank() && manager.addPlugin(pluginName, level);
                    p.sendMessage(ok ? "§a追加しました: " + pluginName : "§c見つかりませんでした: " + pluginName);
                    openMainMenu(p, level);
                })
                .show(player);
    }

    private void askSearch(Player player, PermissionLevel level) {
        DialogUI.builder()
                .title("コマンド検索")
                .body("検索したい文字を入力してください。結果はチャットに表示します。")
                .addTextInput("keyword", "検索キーワード", "", 80, false)
                .confirmation("検索", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) { openMainMenu(p, level); return; }
                    String keyword = result.getText("keyword").trim().toLowerCase();
                    List<String> matches = keyword.isBlank() ? List.of() : manager.searchCommands(keyword);
                    p.sendMessage("§b検索結果: " + matches.size() + "件");
                    matches.stream().limit(30).forEach(cmd -> p.sendMessage("§7- §f" + cmd));
                    if (matches.size() > 30) p.sendMessage("§7他 " + (matches.size() - 30) + " 件");
                    openMainMenu(p, level);
                })
                .show(player);
    }

    private void showGlobalList(Player player, PermissionLevel level) {
        List<String> list = manager.list(level);
        if (list.isEmpty()) player.sendMessage("§b登録なし");
        else {
            player.sendMessage("§b登録一覧 level=" + level.getLevel() + ":");
            list.stream().limit(40).forEach(pattern -> player.sendMessage("§7- §f" + pattern));
            if (list.size() > 40) player.sendMessage("§7他 " + (list.size() - 40) + " 件");
        }
        openMainMenu(player, level);
    }

    private void confirmRemoveAll(Player player, PermissionLevel level) {
        ChestUI.builder()
                .title("全削除の確認")
                .size(27)
                .addButtonAt(11, "§c削除する", Material.TNT, "level=" + level.getLevel() + " の制限を全削除")
                .addButtonAt(15, "§aキャンセル", Material.BARRIER, "戻る")
                .then((result, p) -> {
                    if (!result.cancelled && result.slot != null && result.slot == 11) {
                        manager.removeAll(level);
                        p.sendMessage("§a削除しました level=" + level.getLevel());
                    }
                    openMainMenu(p, level);
                })
                .show(player);
    }

    private void openUserSelectMenu(Player player, PermissionLevel level) {
        ChestUI.Builder builder = ChestUI.builder().title("ユーザー個別設定").size(54)
                .addButtonAt(49, "§7戻る", Material.ARROW, "メインメニューへ");
        int slot = 0;
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (slot >= 45) break;
            builder.addPlayerHeadAt(slot++, "§f" + target.getName(), target, "このユーザーだけのコマンド制限を編集");
        }
        builder.then((result, p) -> {
            if (result.cancelled || result.slot == null || result.slot == 49) { openMainMenu(p, level); return; }
            List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (result.slot >= 0 && result.slot < online.size()) openUserMenu(p, online.get(result.slot), level);
            else openUserSelectMenu(p, level);
        }).show(player);
    }

    private void openUserMenu(Player player, Player target, PermissionLevel level) {
        List<String> personal = manager.listUser(target.getUniqueId());
        ChestUI.builder()
                .title("個別設定: " + target.getName())
                .size(36)
                .addPlayerHeadAt(4, "§f" + target.getName(), target, "個別制限: " + personal.size() + "件")
                .addButtonAt(11, "§a個別制限を追加", Material.LIME_DYE, "このユーザーだけ制限")
                .addButtonAt(13, "§e個別一覧を見る", Material.BOOK, "チャットに一覧表示")
                .addButtonAt(15, "§c個別制限を削除", Material.RED_DYE, "パターンを入力して削除")
                .addButtonAt(22, "§4このユーザーの個別設定を全削除", Material.TNT, "すべて解除")
                .addButtonAt(31, "§7戻る", Material.ARROW, "ユーザー選択へ")
                .then((result, p) -> {
                    if (result.cancelled || result.slot == null || result.slot == 31) { openUserSelectMenu(p, level); return; }
                    switch (result.slot) {
                        case 11 -> askUserPattern(p, target, level, true);
                        case 13 -> { showUserList(p, target, level); }
                        case 15 -> askUserPattern(p, target, level, false);
                        case 22 -> { manager.removeAllUser(target.getUniqueId()); p.sendMessage("§a" + target.getName() + " の個別設定を削除しました"); openUserMenu(p, target, level); }
                        default -> openUserMenu(p, target, level);
                    }
                })
                .show(player);
    }

    private void askUserPattern(Player player, Player target, PermissionLevel level, boolean add) {
        DialogUI.builder()
                .title(add ? "個別制限を追加" : "個別制限を削除")
                .body(target.getName() + " だけに適用します。例: pl / reload / bukkit*")
                .addTextInput("pattern", "コマンド名またはパターン", "", 120, false)
                .confirmation(add ? "追加" : "削除", "戻る")
                .onResponse((result, p) -> {
                    if (!result.isConfirmed()) { openUserMenu(p, target, level); return; }
                    String pattern = result.getText("pattern").trim();
                    if (pattern.startsWith("/")) pattern = pattern.substring(1);
                    boolean ok = add ? manager.addUser(target.getUniqueId(), pattern, level) : manager.removeUser(target.getUniqueId(), pattern);
                    p.sendMessage(ok ? "§a保存しました: " + target.getName() + " / " + pattern : "§c処理できませんでした: " + pattern);
                    openUserMenu(p, target, level);
                })
                .show(player);
    }

    private void showUserList(Player player, Player target, PermissionLevel level) {
        List<String> list = manager.listUser(target.getUniqueId());
        player.sendMessage("§b" + target.getName() + " の個別制限: " + list.size() + "件");
        list.stream().limit(40).forEach(pattern -> player.sendMessage("§7- §f" + pattern));
        if (list.size() > 40) player.sendMessage("§7他 " + (list.size() - 40) + " 件");
        openUserMenu(player, target, level);
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) list.add("1");
        if (args.length <= 2) {
            list.add("add"); list.add("remove"); list.add("list"); list.add("removeAll"); 
            list.add("search"); list.add("addplugin");
        }
        return list;
    }
}
