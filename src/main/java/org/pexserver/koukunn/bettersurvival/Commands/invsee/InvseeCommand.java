package org.pexserver.koukunn.bettersurvival.Commands.invsee;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeOfflineData;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI;

import java.util.ArrayList;
import java.util.List;

/** InvSee コマンド - 現在のOtherworldグループ内のインベントリを閲覧・編集する。 */
public class InvseeCommand extends BaseCommand {
    private final Loader plugin;

    public InvseeCommand(Loader plugin) { this.plugin = plugin; }
    @Override public String getName() { return "invsee"; }
    @Override public String getDescription() { return "現在のワールドグループ内のプレイヤーインベントリを閲覧・編集します（OP専用）"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.ADMIN; }
    @Override public String getUsage() { return "/invsee [player]"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        if (args.length == 0) {
            Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openPlayerSelectUI(viewer, plugin, 0));
            return true;
        }

        String targetName = args[0];
        Player onlineTarget = Bukkit.getOnlinePlayers().stream()
                .filter(player -> player.getName().equalsIgnoreCase(targetName))
                .findFirst().orElse(null);
        if (onlineTarget != null) {
            if (plugin.getOtherworldModule() != null && !plugin.getOtherworldModule().sameGroup(viewer, onlineTarget)) {
                sendError(sender, "異なるOtherworldグループのプレイヤーはInvSeeできません");
                return true;
            }
            Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openInventoryUI(viewer, onlineTarget, plugin));
            return true;
        }

        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        if (!offlineTarget.hasPlayedBefore()) {
            sendError(sender, "プレイヤーが見つかりません: " + targetName);
            return true;
        }
        String scope = InvseeUI.resolveScope(viewer, plugin);
        if (!InvseeOfflineData.hasData(offlineTarget, scope)) {
            sendError(sender, "このプレイヤーには現在のワールドグループ (" + scope + ") のInvSeeスナップショットがありません");
            return true;
        }
        Bukkit.getScheduler().runTask(plugin, () -> InvseeUI.openInventoryUI(viewer, offlineTarget, plugin));
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length != 1) return completions;
        String partial = args[0].toLowerCase();
        Player viewer = sender instanceof Player player ? player : null;
        String scope = viewer == null ? "default" : InvseeUI.resolveScope(viewer, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (viewer != null && plugin.getOtherworldModule() != null
                    && !plugin.getOtherworldModule().sameGroup(viewer, player)) continue;
            if (player.getName().toLowerCase().startsWith(partial)) completions.add(player.getName());
        }
        for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
            String name = op.getName();
            if (op.isOnline() || name == null || !name.toLowerCase().startsWith(partial)) continue;
            if (!InvseeOfflineData.hasData(op, scope) || completions.contains(name)) continue;
            completions.add(name);
            if (completions.size() >= 20) break;
        }
        return completions;
    }
}
