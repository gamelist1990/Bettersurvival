package org.pexserver.koukunn.bettersurvival.Commands.home;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.CompletionUtils;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Home.HomeModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Home.HomePoint;

import java.util.ArrayList;
import java.util.List;

public class HomeCommand extends BaseCommand {
    private final Loader plugin;
    private HomeModule homeModule;

    public HomeCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "home";
    }

    @Override
    public String getDescription() {
        return "Homeの登録・移動・管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.MEMBER;
    }

    @Override
    public String getUsage() {
        return "/home [add|remove|list|ui|名前] [名前]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }

        HomeModule module = getModule();
        if (module == null) {
            sendError(sender, "Home機能が初期化されていません");
            return true;
        }

        if (args.length == 0) {
            module.teleportDefault(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add":
                if (args.length < 2) {
                    sendError(player, "Home名を指定してください: /home add <名前>");
                    return true;
                }
                module.addHome(player, args[1]);
                return true;

            case "remove":
            case "delete":
            case "del":
                if (args.length < 2) {
                    sendError(player, "削除するHome名を指定してください: /home remove <名前>");
                    return true;
                }
                module.removeHome(player, args[1]);
                return true;

            case "list":
                module.listHomes(player);
                return true;

            case "ui":
                module.openUI(player);
                return true;

            default:
                module.teleportNamed(player, args[0]);
                return true;
        }
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> list = new ArrayList<>();
        if (!(sender instanceof Player player)) return list;

        if (args.length == 1) {
            list.add("add");
            list.add("remove");
            list.add("list");
            list.add("ui");
            HomeModule module = getModule();
            if (module != null) {
                for (HomePoint home : module.getStore().getHomes(player)) {
                    list.add(home.getName());
                }
            }
            return CompletionUtils.filterBySimilarity(args[0], list);
        }

        if (args.length == 2) {
            String sub = args[0].toLowerCase();
            if (sub.equals("remove") || sub.equals("delete") || sub.equals("del")) {
                HomeModule module = getModule();
                if (module != null) {
                    for (HomePoint home : module.getStore().getHomes(player)) {
                        list.add(home.getName());
                    }
                }
                return CompletionUtils.filterBySimilarity(args[1], list);
            }
        }

        return list;
    }

    private HomeModule getModule() {
        if (homeModule == null) {
            homeModule = plugin.getHomeModule();
        }
        return homeModule;
    }
}
