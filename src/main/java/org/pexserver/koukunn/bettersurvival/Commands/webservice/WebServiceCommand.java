package org.pexserver.koukunn.bettersurvival.Commands.webservice;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebService.WebServiceModule;

import java.util.ArrayList;
import java.util.List;

public class WebServiceCommand extends BaseCommand {
    private final Loader plugin;

    public WebServiceCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "webservice";
    }

    @Override
    public String getDescription() {
        return "WebService / WebMap の保全管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        WebServiceModule service = plugin.getWebServiceModule();
        if (service == null) {
            sendError(sender, "WebService が初期化されていません");
            return true;
        }

        if (args.length == 0 || "menu".equalsIgnoreCase(args[0]) || "settings".equalsIgnoreCase(args[0])) {
            if (!(sender instanceof Player player)) {
                sendStatus(sender, service);
                return true;
            }
            service.openSettings(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "status" -> {
                sendStatus(sender, service);
                return true;
            }
            case "enable" -> {
                service.setGloballyEnabled(true);
                sendSuccess(sender, "WebService を有効にしました");
                return true;
            }
            case "disable", "stop" -> {
                service.setGloballyEnabled(false);
                sendSuccess(sender, "WebService を停止しました");
                return true;
            }
            case "restart" -> {
                service.restartSharedHttpServer();
                sendSuccess(sender, "WebService HTTP サーバーを再起動しました");
                return true;
            }
            case "webmap" -> {
                if (args.length < 2) {
                    sendInfo(sender, "使用法: /webservice webmap <enable|disable>");
                    return true;
                }
                boolean enable = "enable".equalsIgnoreCase(args[1]) || "on".equalsIgnoreCase(args[1]);
                if (!enable && !("disable".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))) {
                    sendInfo(sender, "使用法: /webservice webmap <enable|disable>");
                    return true;
                }
                service.setWebMapFeatureEnabled(enable);
                sendSuccess(sender, enable ? "WebService 側の WebMap 機能を有効にしました" : "WebService 側の WebMap 機能を無効にしました");
                return true;
            }
            case "port" -> {
                if (args.length < 2 || !args[1].matches("\\d+")) {
                    sendInfo(sender, "使用法: /webservice port <1024-65535>");
                    return true;
                }
                int port = Integer.parseInt(args[1]);
                if (port < 1024 || port > 65535) {
                    sendError(sender, "ポートは 1024-65535 の範囲で指定してください");
                    return true;
                }
                if (plugin.getWebMapModule() != null) {
                    plugin.getWebMapModule().updatePort(port);
                }
                sendSuccess(sender, "WebService HTTP ポートを " + port + " に変更しました");
                return true;
            }
            default -> {
                sendInfo(sender, getUsage());
                return true;
            }
        }
    }

    private void sendStatus(CommandSender sender, WebServiceModule service) {
        sendInfo(sender, "WebService: " + (service.isGloballyEnabled() ? "ENABLED" : "DISABLED"));
        if (plugin.getWebMapModule() != null) {
            sendInfo(sender, "HTTP: " + (plugin.getWebMapModule().isServerRunning() ? "ONLINE" : "OFFLINE")
                    + " / URL: " + plugin.getWebMapModule().getPublicUrl());
            sendInfo(sender, "WebMap Toggle: " + (plugin.getWebMapModule().isGloballyEnabled() ? "ON" : "OFF")
                    + " / WebService Feature: " + (service.isWebMapFeatureEnabled() ? "ON" : "OFF")
                    + " / Access: " + (service.isWebMapAccessEnabled() ? "ON" : "OFF"));
        }
    }

    @Override
    public String getUsage() {
        return "/webservice [menu|status|enable|disable|restart|port|webmap]";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (!sender.isOp()) {
            return values;
        }
        if (args.length == 1) {
            values.add("menu");
            values.add("status");
            values.add("enable");
            values.add("disable");
            values.add("restart");
            values.add("port");
            values.add("webmap");
            return values.stream().filter(value -> value.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && "webmap".equalsIgnoreCase(args[0])) {
            values.add("enable");
            values.add("disable");
            return values.stream().filter(value -> value.startsWith(args[1].toLowerCase())).toList();
        }
        return values;
    }
}
