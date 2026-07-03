package org.pexserver.koukunn.bettersurvival.Commands.tournament;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Tournament.TournamentModule;

import java.util.ArrayList;
import java.util.List;

/**
 * 頂点決定戦コマンド
 * /tournament        : メニューを開く（参加登録・トーナメント表・管理）
 * /tournament join   : 参加登録 / 取消
 * /tournament cancel : 大会を中止（OP専用）
 */
public class TournamentCommand extends BaseCommand {

    private final Loader plugin;

    public TournamentCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "tournament";
    }

    @Override
    public String getDescription() {
        return "頂点決定戦（トーナメント大会）の参加・管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public String getUsage() {
        return "/tournament [join|cancel]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        TournamentModule module = plugin.getTournamentModule();
        if (module == null) {
            sendError(sender, "トーナメント機能が初期化されていません");
            return true;
        }
        if (!module.isFeatureEnabled()) {
            sendError(sender, "トーナメント機能は現在無効です");
            return true;
        }
        String sub = args.length >= 1 ? args[0].toLowerCase() : "";
        switch (sub) {
            case "join" -> module.toggleJoin(player);
            case "cancel" -> {
                if (!player.isOp()) {
                    sendError(player, "大会の中止は OP のみ実行できます");
                    return true;
                }
                if (module.getTournament() == null) {
                    sendError(player, "開催中の大会がありません");
                    return true;
                }
                module.cancelTournament("管理者による中止");
            }
            default -> module.getMenu().openMain(player);
        }
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String candidate : List.of("join", "cancel")) {
                if (candidate.startsWith(prefix)) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }
}
