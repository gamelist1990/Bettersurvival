package org.pexserver.koukunn.bettersurvival.Commands.webmap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;

import java.util.ArrayList;
import java.util.List;

public class WebMapCommand extends BaseCommand {
    private final Loader plugin;

    public WebMapCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "webmap";
    }

    @Override
    public String getDescription() {
        return "WebMap の状態確認と設定";
    }

    @Override
    public boolean isEnabled() {
        WebMapModule module = plugin.getWebMapModule();
        return module != null && module.isGloballyEnabled();
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        WebMapModule module = plugin.getWebMapModule();
        if (module == null || !module.isGloballyEnabled()) {
            sendError(sender, "WebMap は現在無効です");
            return true;
        }

        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            sendInfo(sender, "WebMap: " + (module.isServerRunning() ? "ONLINE" : "OFFLINE"));
            sendInfo(sender, "URL: " + module.getPublicUrl());
            sendInfo(sender, "探索追従: " + (module.getSettings().isAutoTrackPlayers() ? "ON" : "OFF")
                    + " / 一時停止: " + (module.getSettings().isPaused() ? "ON" : "OFF"));
            sendInfo(sender, "ChunkGen: " + module.getActiveChunkGenCount() + " dimension(s) running");
            return true;
        }

        if ("settings".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sendError(sender, "設定 UI はプレイヤーのみ開けます");
                return true;
            }
            if (!player.isOp()) {
                sendError(sender, "設定画面は OP のみ使用できます");
                return true;
            }
            module.openSettings(player);
            return true;
        }

        if ("restart".equalsIgnoreCase(args[0]) || "pause".equalsIgnoreCase(args[0]) || "resume".equalsIgnoreCase(args[0])) {
            if (!(sender.isOp() || !(sender instanceof Player))) {
                sendError(sender, "この操作は OP またはコンソールのみ使用できます");
                return true;
            }
            if ("restart".equalsIgnoreCase(args[0])) {
                module.restartServer();
                sendSuccess(sender, "WebMap サーバーを再起動しました");
                return true;
            }
            boolean shouldPause = "pause".equalsIgnoreCase(args[0]);
            if (module.getSettings().isPaused() != shouldPause) {
                module.togglePaused();
            }
            sendSuccess(sender, shouldPause ? "WebMap を一時停止しました" : "WebMap を再開しました");
            return true;
        }

        sendInfo(sender, "使用法: /webmap status | /webmap settings");
        return true;
    }

    @Override
    public String getUsage() {
        return "/webmap [status|settings|restart|pause|resume]";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            values.add("status");
            if (sender.isOp()) {
                values.add("settings");
                values.add("restart");
                values.add("pause");
                values.add("resume");
            }
        }
        return values.stream()
                .filter(value -> value.startsWith(args[0].toLowerCase()))
                .toList();
    }
}
