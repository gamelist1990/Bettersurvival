package org.pexserver.koukunn.bettersurvival.Listeners;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;
import org.bukkit.command.CommandSender;
import java.util.Collection;
import org.pexserver.koukunn.bettersurvival.Core.Command.CommandBlockManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

/**
 * イベントリスナー: プレイヤー/コンソールのコマンド発行を監視し、無効化されたコマンドをキャンセルする
 */
public class CommandBlockerListener implements Listener {

    private final CommandBlockManager manager;

    public CommandBlockerListener(CommandBlockManager manager) {
        this.manager = manager;
    }

    @EventHandler
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        String message = event.getMessage();
        if (!message.startsWith("/")) return;
        String[] parts = message.substring(1).split(" ");
        String cmd = parts[0];
        CommandSender sender = event.getPlayer();
        if (manager.matches(sender, cmd)) {
            event.setCancelled(true);
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cこのコマンドは無効化されています"));
        }
    }

    @EventHandler
    public void onServerCommand(ServerCommandEvent event) {
        String message = event.getCommand();
        String[] parts = message.split(" ");
        String cmd = parts[0];
        CommandSender sender = event.getSender();
        if (manager.matches(sender, cmd)) {
            event.setCancelled(true);
            sender.sendMessage(LegacyComponentSerializer.legacySection().deserialize("§cこのコマンドは無効化されています"));
        }
    }

    @EventHandler
    public void onPlayerCommandSend(PlayerCommandSendEvent event) {
        Collection<String> commands = event.getCommands();
        if (commands == null || commands.isEmpty()) return;
        CommandSender sender = event.getPlayer();
        java.util.Iterator<String> it = commands.iterator();
        while (it.hasNext()) {
            String cmd = it.next();
            String base = cmd.contains(":") ? cmd.substring(cmd.indexOf(":") + 1) : cmd;
            if (manager.matches(sender, base)) {
                it.remove();
            }
        }
    }
}
