package org.pexserver.koukunn.bettersurvival.Commands.w;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.CompletionUtils;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist.PendingWhitelistModule;

import java.util.ArrayList;
import java.util.List;

public class WhitelistCommand extends BaseCommand {
    private final Loader plugin;
    private PendingWhitelistModule module;

    public WhitelistCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "w";
    }

    @Override
    public String getDescription() {
        return "初回参加前ユーザー向けの接続待機 whitelist を管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN_OR_CONSOLE;
    }

    @Override
    public String getUsage() {
        return "/w <add|remove|list> [username]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        PendingWhitelistModule whitelistModule = getModule();
        if (whitelistModule == null) {
            sendError(sender, "Whitelist機能が初期化されていません");
            return true;
        }

        if (args.length == 0) {
            sendInfo(sender, getUsage());
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add":
                if (args.length < 2) {
                    sendError(sender, "追加するユーザー名を指定してください: /w add <username>");
                    return true;
                }
                switch (whitelistModule.addPending(args[1])) {
                    case PENDING_ADDED:
                        sendSuccess(sender, "接続待機 whitelist に追加しました: " + args[1]);
                        break;
                    case ALREADY_PENDING:
                        sendInfo(sender, "既に接続待機 whitelist に登録されています: " + args[1]);
                        break;
                    default:
                        sendError(sender, "追加できませんでした。ユーザー名を確認してください");
                        break;
                }
                return true;

            case "remove":
                if (args.length < 2) {
                    sendError(sender, "削除するユーザー名を指定してください: /w remove <username>");
                    return true;
                }
                switch (whitelistModule.removePending(args[1])) {
                    case PENDING_ONLY:
                        sendSuccess(sender, "接続待機 whitelist から削除しました: " + args[1]);
                        break;
                    default:
                        sendError(sender, "指定されたユーザーは登録されていません");
                        break;
                }
                return true;

            case "list":
                List<String> pendingNames = whitelistModule.getPendingNames();
                if (pendingNames.isEmpty()) {
                    sendInfo(sender, "接続待機 whitelist は空です");
                    return true;
                }
                sendInfo(sender, "接続待機 whitelist (" + pendingNames.size() + "件): " + String.join(", ", pendingNames));
                return true;

            default:
                sendError(sender, "不明なサブコマンドです: " + sub);
                return true;
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();
        PendingWhitelistModule whitelistModule = getModule();

        if (args.length == 1) {
            list.add("add");
            list.add("remove");
            list.add("list");
            return CompletionUtils.filterBySimilarity(args[0], list);
        }

        if (args.length == 2 && whitelistModule != null) {
            if ("remove".equalsIgnoreCase(args[0])) {
                list.addAll(whitelistModule.getPendingNames());
            }
            if ("add".equalsIgnoreCase(args[0])) {
                for (Player player : plugin.getServer().getOnlinePlayers()) {
                    list.add(player.getName());
                }
            }
            return CompletionUtils.filterBySimilarity(args[1], list);
        }

        return list;
    }

    private PendingWhitelistModule getModule() {
        if (module == null) {
            module = plugin.getPendingWhitelistModule();
        }
        return module;
    }
}
