package org.pexserver.koukunn.bettersurvival.Commands.toggle;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;

/**
 * /toggle コマンド - Chest UI を開く
 */
public class ToggleCommand extends BaseCommand {

    private final Loader plugin;

    public ToggleCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "toggle";
    }

    @Override
    public String getDescription() {
        return "機能の有効/無効を切り替えるUIを開く";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player)) {
            sendError(sender, "このコマンドはプレイヤーのみ使用できます");
            return true;
        }

        Player player = (Player) sender;

        boolean adminMode = false;
        if (args.length > 0 && "op".equalsIgnoreCase(args[0])) {
            if (!player.isOp()) {
                sendError(sender, "管理者権限が必要です");
                return true;
            }
            adminMode = true;
        }

        plugin.getToggleModule().openToggleUI(player, adminMode);
        return true;
    }

    @Override
    public java.util.List<String> getTabCompletions(CommandSender sender, String[] args) {
        java.util.List<String> list = new java.util.ArrayList<>();
        if (args.length == 1) list.add("op");
        return list;
    }
}
