package org.pexserver.koukunn.bettersurvival.Commands.discord;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.DiscordWebhook.DiscordWebhookModule;

public class DiscordCommand extends BaseCommand {
    private final Loader plugin;

    public DiscordCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "discord";
    }

    @Override
    public String getDescription() {
        return "DiscordWebhook設定UIを開く";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはOPプレイヤーのみ使用できます");
            return true;
        }

        DiscordWebhookModule module = plugin.getDiscordWebhookModule();
        if (module == null) {
            sendError(sender, "DiscordWebhookModuleがまだ初期化されていません");
            return true;
        }

        module.openMenu(player);
        return true;
    }
}
