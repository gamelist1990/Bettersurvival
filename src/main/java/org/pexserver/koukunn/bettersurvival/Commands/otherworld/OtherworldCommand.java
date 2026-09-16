package org.pexserver.koukunn.bettersurvival.Commands.otherworld;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.CompletionUtils;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld.OtherworldModule;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Commands for creating and accessing isolated Otherworld groups. */
public class OtherworldCommand extends BaseCommand {
    private final Loader plugin;

    public OtherworldCommand(Loader plugin) { this.plugin = plugin; }
    private OtherworldModule module() { return plugin.getOtherworldModule(); }

    @Override public String getName() { return "otherworld"; }
    @Override public String getDescription() { return "Otherworldグループを管理します"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.MEMBER; }
    @Override public String getUsage() { return "/otherworld <add|move|whitelist|list|dimensions> ..."; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sendInfo(player, "グループ: " + String.join(", ", module().getGroupNames()));
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) {
            if (!player.isOp() || args.length < 2) {
                sendError(player, "使用法: /otherworld add <name>（管理者のみ）");
                return true;
            }
            sendInfo(player, module().createGroup(args[1])
                    ? "グループを作成し、Vanilla Dimensionと検出済みCustom Dimensionを分離しました"
                    : "作成できません");
            return true;
        }
        if (args[0].equalsIgnoreCase("move")) {
            if (args.length < 2 || !module().move(player, args[1])) {
                sendError(player, "移動先が存在しないか、許可されていません");
            } else {
                sendSuccess(player, args[1] + " へ移動しました");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("dimensions")) {
            if (!player.isOp()) {
                sendError(player, "管理者のみ使用できます");
                return true;
            }
            module().scanAndMirrorCustomDimensions();
            Map<String, String> detected = module().getDetectedCustomDimensions();
            if (detected.isEmpty()) {
                sendInfo(player, "追加Custom Dimensionは現在検出されていません");
                return true;
            }
            sendInfo(player, "検出Custom Dimension: " + detected.size());
            for (var entry : detected.entrySet()) {
                player.sendMessage("§b- " + entry.getKey() + " §7(default: §f" + entry.getValue() + "§7)");
                for (String group : module().getGroupNames()) {
                    if (group.equals("default")) continue;
                    String mirror = module().getCustomDimensions(group).get(entry.getKey());
                    player.sendMessage("  §7" + group + ": §f" + (mirror == null ? "未生成" : mirror));
                }
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("whitelist")) {
            if (!player.isOp() || args.length < 3) {
                sendError(player, "使用法: /otherworld whitelist <name> on|off または add|remove <username>");
                return true;
            }
            String action = args[2].toLowerCase(Locale.ROOT);
            boolean ok;
            if (action.equals("on") || action.equals("off")) {
                ok = module().setWhitelist(args[1], action.equals("on"));
            } else if ((action.equals("add") || action.equals("remove")) && args.length >= 4) {
                ok = action.equals("add") ? module().addMember(args[1], args[3]) : module().removeMember(args[1], args[3]);
            } else {
                ok = false;
            }
            sendInfo(player, ok ? "ホワイトリストを更新しました" : "グループまたはユーザーが不正です");
            return true;
        }
        sendInfo(player, getUsage());
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) values.addAll(List.of("add", "move", "whitelist", "list", "dimensions"));
        else if (args.length == 2 && (args[0].equalsIgnoreCase("move") || args[0].equalsIgnoreCase("whitelist"))) {
            values.addAll(module().getGroupNames());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) {
            values.addAll(List.of("on", "off", "add", "remove"));
        } else if (args.length == 4 && args[0].equalsIgnoreCase("whitelist")
                && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) {
            for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        }
        return CompletionUtils.filterBySimilarity(args.length == 0 ? "" : args[args.length - 1], values);
    }
}
