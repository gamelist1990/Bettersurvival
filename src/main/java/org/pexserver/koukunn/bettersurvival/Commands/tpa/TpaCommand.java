package org.pexserver.koukunn.bettersurvival.Commands.tpa;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.CompletionUtils;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa.TpaModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tpa.TpaRequest;

import java.util.ArrayList;
import java.util.List;

/**
 * TPA コマンド
 * /tpa -r <ユーザー名> : テレポートリクエストを送信
 * /tpa -a <ユーザー名> : リクエストを承認
 * /tpa -d <ユーザー名> : リクエストを拒否
 * /tpa -l : 受信したリクエスト一覧
 * /tpa ui : UIを表示
 */
public class TpaCommand extends BaseCommand {

    private final Loader plugin;
    private TpaModule tpaModule;

    public TpaCommand(Loader plugin) {
        this.plugin = plugin;
    }

    /**
     * TpaModule を遅延取得（Loaderでの初期化順序の問題を回避）
     */
    private TpaModule getModule() {
        if (tpaModule == null) {
            tpaModule = plugin.getTpaModule();
        }
        return tpaModule;
    }

    @Override
    public String getName() {
        return "tpa";
    }

    @Override
    public String getDescription() {
        return "テレポートリクエストを送信・管理するコマンド";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public String getUsage() {
        return "/tpa <-r|-a|-d|-l|ui> [プレイヤー名]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }

        Player p = (Player) sender;
        TpaModule module = getModule();

        if (module == null) {
            sendError(sender, "TPA機能が初期化されていません");
            return true;
        }

        // 引数なしの場合は使用法を表示
        if (args.length == 0) {
            showUsage(p);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "-r":
            case "request":
                // /tpa -r <ユーザー名>
                if (args.length < 2) {
                    sendError(p, "プレイヤー名を指定してください: /tpa -r <プレイヤー名>");
                    return true;
                }
                String targetName = args[1];
                Player target = findPlayer(targetName);
                if (target == null) {
                    sendError(p, "プレイヤー '" + targetName + "' が見つかりません");
                    return true;
                }
                module.sendRequest(p, target);
                return true;

            case "-a":
            case "accept":
                // /tpa -a <ユーザー名>
                if (args.length < 2) {
                    sendError(p, "承認するプレイヤー名を指定してください: /tpa -a <プレイヤー名>");
                    return true;
                }
                module.acceptRequest(p, args[1]);
                return true;

            case "-d":
            case "deny":
                // /tpa -d <ユーザー名>
                if (args.length < 2) {
                    sendError(p, "拒否するプレイヤー名を指定してください: /tpa -d <プレイヤー名>");
                    return true;
                }
                module.denyRequest(p, args[1]);
                return true;

            case "-l":
            case "list":
                // /tpa -l
                module.listRequests(p);
                return true;

            case "ui":
                // /tpa ui
                module.openUI(p);
                return true;

            default:
                // 引数が1つで -r/-a/-d/-l/ui 以外の場合は、プレイヤー名として扱う（ショートカット）
                Player shortcutTarget = findPlayer(sub);
                if (shortcutTarget != null) {
                    module.sendRequest(p, shortcutTarget);
                    return true;
                }
                showUsage(p);
                return true;
        }
    }

    private void showUsage(Player p) {
        sendInfo(p, "§e--- TPA コマンド ---");
        sendInfo(p, "§a/tpa -r <プレイヤー名> §7- リクエストを送信");
        sendInfo(p, "§a/tpa -a <プレイヤー名> §7- リクエストを承認");
        sendInfo(p, "§c/tpa -d <プレイヤー名> §7- リクエストを拒否");
        sendInfo(p, "§e/tpa -l §7- リクエスト一覧を表示");
        sendInfo(p, "§b/tpa ui §7- UIを開く");
    }

    private Player findPlayer(String name) {
        // 完全一致
        Player exact = Bukkit.getPlayerExact(name);
        if (exact != null) return exact;

        // 部分一致
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().contains(name.toLowerCase())) {
                return p;
            }
        }
        return null;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();

        if (args.length == 1) {
            list.add("-r");
            list.add("-a");
            list.add("-d");
            list.add("-l");
            list.add("ui");
            // オンラインプレイヤー名も追加（ショートカット用）
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getName().equals(sender.getName())) {
                    list.add(p.getName());
                }
            }
            return CompletionUtils.filterBySimilarity(args[0], list);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();

            if ("-r".equals(sub) || "request".equals(sub)) {
                // リクエスト送信: TPA受信可能なプレイヤーのみ
                TpaModule module = getModule();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().equals(sender.getName())) continue;
                    if (module != null && module.canReceiveTpa(p)) {
                        list.add(p.getName());
                    }
                }
                return CompletionUtils.filterBySimilarity(args[1], list);
            }

            if ("-a".equals(sub) || "accept".equals(sub) || "-d".equals(sub) || "deny".equals(sub)) {
                // 承認/拒否: 受信したリクエストの送信者名
                if (sender instanceof Player) {
                    Player p = (Player) sender;
                    TpaModule module = getModule();
                    if (module != null) {
                        List<TpaRequest> requests = module.getStore().getRequestsFor(p.getUniqueId().toString());
                        for (TpaRequest req : requests) {
                            list.add(req.getSenderName());
                        }
                    }
                }
                return CompletionUtils.filterBySimilarity(args[1], list);
            }
        }

        return list;
    }
}
