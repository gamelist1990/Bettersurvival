package org.pexserver.koukunn.bettersurvival.Commands.protect;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectMenu;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect.ProtectModule;

import java.util.ArrayList;
import java.util.List;

/**
 * /protect - OP向け監査・検索・ロールバック。
 */
public final class ProtectCommand extends BaseCommand {
    private final ProtectModule module;

    public ProtectCommand(ProtectModule module) {
        this.module = module;
    }

    @Override
    public String getName() {
        return "protect";
    }

    @Override
    public String getDescription() {
        return "ブロック/コンテナ監査ログとロールバックを管理";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN;
    }

    @Override
    public boolean isEnabled() {
        return module.isEnabled();
    }

    @Override
    public String getUsage() {
        return "/protect [inspect|history|rollback|retention]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "このコマンドはOPプレイヤーから実行してください");
            return true;
        }

        if (args.length == 0) {
            ProtectMenu.openMain(player, module);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "inspect" -> {
                boolean enabled = module.toggleInspector(player);
                sendInfo(player, "Inspector: " + (enabled ? "ON" : "OFF"));
            }
            case "history" -> {
                int radius = args.length >= 2 ? parse(args[1], 0, 256, 10) : 10;
                String actor = args.length >= 3 ? args[2] : null;
                ProtectMenu.openHistoryAt(player, module, player.getLocation(), radius, actor, null);
            }
            case "rollback" -> {
                int radius = args.length >= 2 ? parse(args[1], 0, 256, 10) : 10;
                int hours = args.length >= 3 ? parse(args[2], 1, 24 * 365, 24) : 24;
                String actor = args.length >= 4 ? args[3] : null;
                module.rollback(player, radius, hours, actor);
            }
            case "retention" -> {
                if (args.length < 2) {
                    sendInfo(player, "現在の保持期間: " + module.getRetentionDays() + "日");
                    return true;
                }
                int days = parse(args[1], 1, 3650, -1);
                if (days < 1) {
                    sendError(player, "保持日数は1～3650で指定してください");
                    return true;
                }
                module.setRetentionDays(days);
                sendSuccess(player, "保持期間を " + days + " 日に変更しました");
            }
            default -> ProtectMenu.openMain(player, module);
        }
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            result.add("inspect");
            result.add("history");
            result.add("rollback");
            result.add("retention");
        } else if (args.length == 2 && ("history".equalsIgnoreCase(args[0])
                || "rollback".equalsIgnoreCase(args[0]))) {
            result.add("0");
            result.add("10");
            result.add("25");
            result.add("50");
        } else if (args.length == 3 && "rollback".equalsIgnoreCase(args[0])) {
            result.add("1");
            result.add("24");
            result.add("168");
        } else if (args.length == 2 && "retention".equalsIgnoreCase(args[0])) {
            result.add("7");
            result.add("30");
            result.add("90");
        }
        return result;
    }

    private int parse(String raw, int min, int max, int fallback) {
        try {
            int value = Integer.parseInt(raw);
            return value >= min && value <= max ? value : fallback;
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
