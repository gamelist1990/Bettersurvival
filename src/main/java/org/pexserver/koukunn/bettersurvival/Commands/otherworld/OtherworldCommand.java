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
    @Override public String getUsage() { return "/otherworld <add|delete|regen|lock|unlock|move|setjoin|whitelist|lobby|list|dimensions> ..."; }
    @Override public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sendError(sender, "プレイヤーのみ使用できます"); return true; }
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) { sendInfo(player, "グループ: " + module().getAccessibleGroupNames(player).stream().map(module()::displayGroupName).collect(java.util.stream.Collectors.joining(", "))); return true; }
        if (args[0].equalsIgnoreCase("dimensions")) {
            if (!player.isOp()) {
                sendError(player, "管理者のみ使用できます");
                return true;
            }
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
        if (args[0].equalsIgnoreCase("regen")) {
            if (!player.isOp() || args.length < 3) {
                sendError(player, "使用法: /otherworld regen <group> <overworld|nether|end|custom-dimension>（管理者のみ）");
                return true;
            }
            boolean regenerated = module().regenerateDimension(args[1], args[2]);
            if (regenerated) {
                sendSuccess(player, module().displayGroupName(args[1]) + " の " + args[2] + " を新しいSeedで再生成しました");
            } else {
                sendError(player, "Dimensionを再生成できませんでした。グループ名、Dimension名、アンロード状態を確認してください");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("lock")) {
            if (!player.isOp() || args.length < 4) {
                sendError(player, "使用法: /otherworld lock <group> <yyyy-MM-dd> <HH:mm> [メッセージ]（管理者のみ）");
                return true;
            }
            String message = args.length > 4
                    ? String.join(" ", Arrays.copyOfRange(args, 4, args.length))
                    : "オープンまでお待ちください";
            if (module().setGroupLock(args[1], args[2], args[3], message)) {
                sendSuccess(player, module().displayGroupName(args[1]) + " をロックしました");
                sendInfo(player, module().getLockDisplay(args[1]));
            } else {
                sendError(player, "設定できません。グループ名と日時形式 yyyy-MM-dd HH:mm を確認してください");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("unlock")) {
            if (!player.isOp() || args.length < 2) {
                sendError(player, "使用法: /otherworld unlock <group>（管理者のみ）");
                return true;
            }
            if (module().clearGroupLock(args[1])) {
                sendSuccess(player, module().displayGroupName(args[1]) + " のロックを解除しました");
            } else {
                sendError(player, "存在しないグループです");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("move")) {
            if (args.length < 2) {
                sendError(player, "使用法: /otherworld move <group|lobby>");
                return true;
            }
            boolean lobby = args[1].equalsIgnoreCase("lobby");
            boolean moved = lobby ? module().moveToLobby(player) : module().move(player, args[1]);
            if (!moved) sendError(player, "移動先が存在しないか、許可されていません");
            else sendSuccess(player, (lobby ? "ロビー" : module().displayGroupName(args[1])) + " へ移動しました");
            return true;
        }
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
        if (args[0].equalsIgnoreCase("lobby")) {
            if (!player.isOp() || args.length < 2) {
                sendError(player, "使用法: /otherworld lobby <settings|spawn|route|npc> ...（管理者のみ）");
                return true;
            }
            if (args[1].equalsIgnoreCase("settings")) {
                if (args.length == 2) {
                    sendInfo(player, module().getLobbySettingsDisplay());
                    return true;
                }
                if (args.length < 4 || !(args[3].equalsIgnoreCase("true") || args[3].equalsIgnoreCase("false"))
                        || !module().setLobbySetting(args[2], Boolean.parseBoolean(args[3]))) {
                    sendError(player, "使用法: /otherworld lobby settings <always-spawn|join-spawn|always-respawn|auto-menu|fly> <true|false>");
                    return true;
                }
                sendSuccess(player, "ロビー設定を更新しました: " + module().getLobbySettingsDisplay());
                return true;
            }
            if (args[1].equalsIgnoreCase("spawn") && args.length >= 3 && args[2].equalsIgnoreCase("set")) {
                if (module().setLobbySpawn(player)) sendSuccess(player, "現在地をロビーの通常スポーンに設定しました");
                else sendError(player, "Otherworldロビー内で実行してください");
                return true;
            }
            if (args[1].equalsIgnoreCase("route")) {
                if (args.length >= 3 && args[2].equalsIgnoreCase("list")) {
                    List<String> routes = module().getLobbyRouteDescriptions();
                    sendInfo(player, routes.isEmpty() ? "季節スポーンルートは未設定です" : String.join(", ", routes));
                    return true;
                }
                if (args.length >= 4 && args[2].equalsIgnoreCase("remove")) {
                    if (module().removeLobbyRoute(args[3])) sendSuccess(player, "季節スポーンルートを削除しました");
                    else sendError(player, "存在しないルートです");
                    return true;
                }
                if (args.length >= 6 && args[2].equalsIgnoreCase("set")) {
                    if (module().setLobbyRoute(player, args[3], args[4], args[5])) {
                        sendSuccess(player, "季節スポーンルートを現在地に設定しました");
                    } else {
                        sendError(player, "Otherworldロビー内で MM-dd 形式を使用してください");
                    }
                    return true;
                }
                sendError(player, "使用法: /otherworld lobby route <set <name> <start-MM-dd> <end-MM-dd>|remove <name>|list>");
                return true;
            }
            if (args[1].equalsIgnoreCase("npc") && args.length >= 5 && args[2].equalsIgnoreCase("spawn")) {
                if (module().spawnLobbyNpc(player, args[3], args[4])) {
                    sendSuccess(player, module().displayGroupName(args[3]) + " のアクセスNPCを設置しました");
                } else {
                    sendError(player, "NPCを設置できません。Otherworldロビー内か、グループ名とスキン名を確認してください");
                }
                return true;
            }
            sendError(player, "使用法: /otherworld lobby <settings|spawn|route|npc> ...");
            return true;
        }
        if (args[0].equalsIgnoreCase("whitelist")) {
            if (!player.isOp() || args.length < 3) { sendError(player, "使用法: /otherworld whitelist <name> on|off|list または add|remove <username>"); return true; }
            String action = args[2].toLowerCase(Locale.ROOT); boolean ok;
            if (action.equals("list")) {
                if (!module().getGroupNames().contains(args[1]) && module().getGroupNames().stream()
                        .noneMatch(group -> module().displayGroupName(group).equalsIgnoreCase(args[1]))) {
                    sendError(player, "存在しないグループです");
                    return true;
                }
                if (!module().hasWhitelist(args[1])) {
                    sendInfo(player, module().displayGroupName(args[1]) + " のホワイトリストは無効です");
                    return true;
                }
                List<String> whitelist = module().getWhitelistMembers(args[1]);
                sendInfo(player, "=== " + module().displayGroupName(args[1]) + " Whitelist ===");
                sendInfo(player, whitelist.isEmpty() ? "登録メンバーなし" : String.join(", ", whitelist));
                return true;
            }
            if (action.equals("on") || action.equals("off")) ok = module().setWhitelist(args[1], action.equals("on"));
            else if ((action.equals("add") || action.equals("remove")) && args.length >= 4) ok = action.equals("add") ? module().addMember(args[1], args[3]) : module().removeMember(args[1], args[3]);
            else ok = false;
            sendInfo(player, ok ? "ホワイトリストを更新しました" : "グループまたはユーザーが不正です"); return true;
        }
        sendInfo(player, getUsage()); return true;
    }
    @Override public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) values.addAll(List.of("add", "delete", "regen", "lock", "unlock", "move", "setjoin", "whitelist", "lobby", "list", "dimensions"));
            else if (args.length == 2 && args[0].equalsIgnoreCase("lobby") && sender.isOp()) values.addAll(List.of("settings", "spawn", "route", "npc"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("settings") && sender.isOp()) values.addAll(List.of("always-spawn", "join-spawn", "always-respawn", "auto-menu", "fly"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("settings") && sender.isOp()) values.addAll(List.of("true", "false"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("spawn") && sender.isOp()) values.add("set");
        else if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("route") && sender.isOp()) values.addAll(List.of("set", "remove", "list"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("route") && args[2].equalsIgnoreCase("remove") && sender.isOp()) values.addAll(module().getLobbyRouteNames());
        else if ((args.length == 5 || args.length == 6) && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("route") && args[2].equalsIgnoreCase("set") && sender.isOp()) values.addAll(List.of("01-01", "03-01", "06-01", "09-01", "12-01", "12-31"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc") && sender.isOp()) values.add("spawn");
        else if (args.length == 4 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc") && args[2].equalsIgnoreCase("spawn") && sender.isOp()) values.addAll(module().getGroupNames().stream().map(module()::displayGroupName).toList());
        else if (args.length == 5 && args[0].equalsIgnoreCase("lobby") && args[1].equalsIgnoreCase("npc") && args[2].equalsIgnoreCase("spawn") && sender.isOp()) for (Player online : Bukkit.getOnlinePlayers()) values.add(online.getName());
        else if (args.length == 2 && args[0].equalsIgnoreCase("move")) {
            values.add("lobby");
            if (sender instanceof Player player) values.addAll(module().getAccessibleGroupNames(player).stream().map(module()::displayGroupName).toList());
        }
        else if (args.length == 2 && (args[0].equalsIgnoreCase("delete") || args[0].equalsIgnoreCase("regen") || args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock") || args[0].equalsIgnoreCase("setjoin") || args[0].equalsIgnoreCase("whitelist"))) values.addAll(module().getGroupNames().stream().map(module()::displayGroupName).toList());
        else if (args.length == 3 && args[0].equalsIgnoreCase("lock")) values.add("2026-09-21");
        else if (args.length == 4 && args[0].equalsIgnoreCase("lock")) values.addAll(List.of("00:00", "12:00", "18:00", "20:00", "21:00"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("regen")) values.addAll(module().getDimensionSelectors(args[1]));
        else if (args.length == 3 && args[0].equalsIgnoreCase("whitelist")) values.addAll(List.of("on", "off", "list", "add", "remove"));
        else if (args.length == 4 && args[0].equalsIgnoreCase("whitelist") && (args[2].equalsIgnoreCase("add") || args[2].equalsIgnoreCase("remove"))) for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        return CompletionUtils.filterBySimilarity(args.length == 0 ? "" : args[args.length - 1], values);
    }
}
