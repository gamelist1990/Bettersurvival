package org.pexserver.koukunn.bettersurvival.Commands.party;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.Party;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.PartyRank;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Party.ui.PartyMenu;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * パーティーコマンド
 * /party (/p)          : パーティーメニューを開く
 * /party info          : 所属パーティーの情報を表示
 * /party list          : 全パーティーの一覧を表示
 */
public class PartyCommand extends BaseCommand {

    private final Loader plugin;
    private final String name;

    public PartyCommand(Loader plugin, String name) {
        this.plugin = plugin;
        this.name = name;
    }

    private PartyModule getModule() {
        return plugin.getPartyModule();
    }

    private PartyMenu getMenu() {
        return plugin.getPartyMenu();
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return "パーティー（ギルド）を作成・管理するコマンド";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public String getUsage() {
        return "/" + name + " [info|list]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        PartyModule module = getModule();
        if (module == null || getMenu() == null) {
            sendError(sender, "パーティー機能が初期化されていません");
            return true;
        }
        if (!module.isFeatureEnabled()) {
            sendError(sender, "パーティー機能は現在無効です");
            return true;
        }

        if (args.length >= 1) {
            switch (args[0].toLowerCase()) {
                case "info" -> {
                    showInfo(player, module);
                    return true;
                }
                case "list" -> {
                    showList(player, module);
                    return true;
                }
                default -> {
                }
            }
        }
        getMenu().openRoot(player);
        return true;
    }

    private void showInfo(Player player, PartyModule module) {
        Party party = module.getPartyOf(player.getUniqueId());
        if (party == null) {
            sendInfo(player, "パーティーに所属していません。/" + name + " から作成できます");
            return;
        }
        player.sendMessage("§d====== パーティー情報 ======");
        player.sendMessage("§7名前: " + party.getColoredName());
        player.sendMessage("§7カラー: " + party.getColor().getLegacyCode() + party.getColor().getDisplayName());
        if (!party.getDescription().isEmpty()) {
            player.sendMessage("§7説明: §f" + party.getDescription());
        }
        player.sendMessage("§7リーダー: §e" + party.nameOf(party.getLeader()));
        StringBuilder coLeaders = new StringBuilder();
        for (UUID uuid : party.getCoLeaders()) {
            if (coLeaders.length() > 0) {
                coLeaders.append("§7, §e");
            }
            coLeaders.append(party.nameOf(uuid));
        }
        if (coLeaders.length() > 0) {
            player.sendMessage("§7サブリーダー: §e" + coLeaders);
        }
        player.sendMessage("§7メンバー数: §e" + party.getAllMembers().size() + "人");
        PartyRank rank = party.rankOf(player.getUniqueId());
        player.sendMessage("§7あなたの階級: " + (rank == null ? "§8-" : rank.getDisplayName()));
    }

    private void showList(Player player, PartyModule module) {
        List<Party> parties = module.getParties();
        if (parties.isEmpty()) {
            sendInfo(player, "パーティーはまだ存在しません");
            return;
        }
        player.sendMessage("§d====== パーティー一覧 (" + parties.size() + ") ======");
        for (Party party : parties) {
            player.sendMessage("§7- " + party.getColoredName() + " §7(" + party.getAllMembers().size() + "人)"
                    + (party.getDescription().isEmpty() ? "" : " §8" + party.getDescription()));
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase();
            for (String candidate : List.of("info", "list")) {
                if (candidate.startsWith(prefix)) {
                    out.add(candidate);
                }
            }
        }
        return out;
    }
}
