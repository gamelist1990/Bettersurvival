package org.pexserver.koukunn.bettersurvival.Commands.otherworld;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.CompletionUtils;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld.OtherworldModule;
import java.util.*;

/** Commands for creating and accessing isolated Otherworld groups. */
public class OtherworldCommand extends BaseCommand {
    private final Loader plugin;
    public OtherworldCommand(Loader plugin) { this.plugin = plugin; }
    private OtherworldModule module() { return plugin.getOtherworldModule(); }
    @Override public String getName() { return "otherworld"; }
    @Override public String getDescription() { return "Otherworldグループを管理します"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.MEMBER; }
    @Override public String getUsage() { return "/otherworld <add|delete|move|setjoin|whitelist|list|dimensions> ..."; }
    @Override public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sendError(sender, "プレイヤーのみ使用できます"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { sendInfo(player, "グループ: " + module().getGroupNames().stream().map(module()::displayGroupName).collect(java.util.stream.Collectors.joining(", "))); return true; }
        if (args[0].equalsIgnoreCase("dimensions")) {
            sendInfo(player, "=== Otherworld Dimension 状態 ===");
            for (String group : module().getGroupNames()) {
                sendInfo(player, "§e" + module().displayGroupName(group) + " §7(seed: §f" + module().getGroupSeed(group) + "§7)");
                Map<String, String> dimensions = module().getCustomDimensions(group);
                if (dimensions.isEmpty()) sendInfo(player, "  §8Custom Dimensionなし");
                else dimensions.forEach((source, target) -> sendInfo(player, "  §b" + source + " §7-> §f" + target));
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("add")) { if (!player.isOp() || args.length < 2) { sendError(player, "使用法: /otherworld add <name>（管理者のみ）"); return true; } sendInfo(player, module().createGroup(args[1]) ? "グループと各Dimensionを作成しました" : "作成できません"); return true; }
        if (args[0].equalsIgnoreCase("delete")) { if (!player.isOp() || args.length < 2) { sendError(player, "使用法: /otherworld delete <name>（管理者のみ）"); return true; } sendInfo(player, module().deleteGroup(args[1]) ? "グループとワールドを削除しました" : "削除できません（default、未ロード、または削除に失敗）"); return true; }
        if (args[0].equalsIgnoreCase("move")) { if (args.length < 2 || !module().move(player, args[1])) sendError(player, "移動先が存在しないか、許可されていません"); else sendSuccess(player, module().displayGroupName(args[1]) + " へ移動しました"); return true; }
        if (args[0].equalsIgnoreCase("setjoin")) {
            if (!player.isOp() || args.length < 2) {
                sendError(player, "使用法: /otherworld setjoin <group>（管理者のみ）");
                return true;
            }
                sendInfo(player, module().setDefaultJoinGroup(args[1])
                    ? "ログイン時の移動先を " + module().displayGroupName(args[1]) + " に設定しました"
                    : "存在しないグループです");
            return true;
        }
        if (args[0].equalsIgnoreCase("whitelist")) {
            if (!player.isOp() || args.length < 3) { sendError(player, "使用法: /otherworld whitelist <name> on|off または add|remove <username>"); return true; }
            String action = args[2].toLowerCase(Locale.ROOT); boolean ok;
            if (action.equals("on") || action.equals("off")) ok = module().setWhitelist(args[1], action.equals("on"));
            else if ((action.equals("add") || action.equals("remove")) && args.length >= 4) ok = action.equals("add") ? module().addMember(args[1], args[3]) : module().removeMember(args[1], args[3]);
            else ok = false;
            sendInfo(player, ok ? "ホワイトリストを更新しました" : "グループまたはユーザーが不正です"); return true;
        }
        sendInfo(player, getUsage()); return true;
    }
    @Override public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) values.addAll(List.of("add", "delete", "move", "setjoin", "whitelist", "list", "dimensions"));
            else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("move") || args[0].equalsIgnoreCase("setjoin") || args[0].equalsIgnoreCase("whitelist"))) values.addAll(module().getGroupNames().stream().map(module()::displayGroupName).toList());
        else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) values.addAll(List.of("on", "off", "add", "remove"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("whitelist") && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        return CompletionUtils.filterBySimilarity(args.length == 0 ? "" : args[args.length - 1], values);
    }
}
