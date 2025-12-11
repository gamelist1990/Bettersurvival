package org.pexserver.koukunn.bettersurvival.Commands.command;

import org.bukkit.command.CommandSender;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandBlockManager;

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
            sendInfo(sender, "/command <level?> <add|remove|list|removeAll|search|addplugin> [arg]");
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
