package org.pexserver.koukunn.bettersurvival.Commands.invsee;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Invsee.InvseeUI;

import java.util.ArrayList;
import java.util.List;

/**
 * InvSee コマンド - プレイヤーのインベントリを閲覧・編集する
 * 
 * /invsee - プレイヤー選択UIを表示
 * /invsee <username> - 指定プレイヤーのインベントリを直接表示
 */
public class InvseeCommand extends BaseCommand {

    private final Loader plugin;

    public InvseeCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "invsee";
    }

    @Override
    public String getDescription() {
        return "プレイヤーのインベントリを閲覧・編集します（OP専用）";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public String getUsage() {
        return "/invsee [player]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }

        Player p = (Player) sender;

        if (args.length == 0) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InvseeUI.openPlayerSelectUI(p, plugin, 0);
            });
            return true;
        }

        String targetName = args[0];
        
        Player onlineTarget = null;
        for (Player pl : Bukkit.getOnlinePlayers()) {
            if (pl.getName().equalsIgnoreCase(targetName)) {
                onlineTarget = pl;
                break;
            }
        }

        if (onlineTarget != null) {
            final Player target = onlineTarget;
            Bukkit.getScheduler().runTask(plugin, () -> {
                InvseeUI.openInventoryUI(p, target, plugin);
            });
            return true;
        }

        @SuppressWarnings("deprecation")
        OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
        
        if (offlineTarget.hasPlayedBefore() || offlineTarget.isOnline()) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                InvseeUI.openInventoryUI(p, offlineTarget, plugin);
            });
            return true;
        }

        sendError(sender, "プレイヤーが見つかりません: " + targetName);
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> completions = new ArrayList<>();

        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(partial)) {
                    completions.add(p.getName());
                }
            }
            
            for (OfflinePlayer op : Bukkit.getOfflinePlayers()) {
                if (op.getName() != null && op.getName().toLowerCase().startsWith(partial)) {
                    if (!completions.contains(op.getName())) {
                        completions.add(op.getName());
                    }
                }
                if (completions.size() >= 20) break;
            }
        }

        return completions;
    }
}
