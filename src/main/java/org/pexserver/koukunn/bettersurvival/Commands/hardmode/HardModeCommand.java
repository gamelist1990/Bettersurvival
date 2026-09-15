package org.pexserver.koukunn.bettersurvival.Commands.hardmode;

import org.bukkit.command.CommandSender;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode.TrueCrafterModeModule;

import java.util.List;

/** サバイバル高難易度機能を簡単なON/OFF形式で管理する。 */
public final class HardModeCommand extends BaseCommand {
    private final TrueCrafterModeModule trueCrafter;

    public HardModeCommand(TrueCrafterModeModule trueCrafter) {
        this.trueCrafter = trueCrafter;
    }

    @Override public String getName() { return "hardmode"; }
    @Override public String getDescription() { return "サバイバル高難易度機能を管理"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.ADMIN_OR_CONSOLE; }
    @Override public String getUsage() { return "/hardmode <list|truecrafter enabled|disabled|heat 1-5>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§6HardMode機能一覧");
            sender.sendMessage("§e真クラ §7(truecrafter): " + state() + " §7/ 熱量: §6" + trueCrafter.heatLevel());
            return true;
        }
        if (args.length == 3 && (args[0].equalsIgnoreCase("truecrafter") || args[0].equals("真クラ"))
                && args[1].equalsIgnoreCase("heat")) {
            try {
                int level = Integer.parseInt(args[2]);
                if (level < 1 || level > 5) throw new NumberFormatException();
                trueCrafter.setHeatLevel(level);
                sender.sendMessage("§6真クラの熱量を §e" + level + " §6に変更しました");
            } catch (NumberFormatException exception) {
                sendError(sender, "熱量は1から5で指定してください");
            }
            return true;
        }
        if (args.length == 2) {
            String mode = args[0].toLowerCase();
            String state = args[1].toLowerCase();
            if ((mode.equals("truecrafter") || mode.equals("真クラ"))
                    && (state.equals("enabled") || state.equals("disabled"))) {
                boolean enabled = state.equals("enabled");
                String error = trueCrafter.setEnabled(enabled);
                if (error != null) {
                    sendError(sender, error);
                    return true;
                }
                sender.sendMessage("§6真クラを" + (enabled ? "§a有効化" : "§c無効化") + "§6しました");
                return true;
            }
        }
        sender.sendMessage("§e/hardmode list");
        sender.sendMessage("§e/hardmode truecrafter <enabled|disabled>");
        sender.sendMessage("§e/hardmode truecrafter heat <1-5>");
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("list", "truecrafter");
        if (args.length == 2 && args[0].equalsIgnoreCase("truecrafter")) return List.of("enabled", "disabled", "heat");
        if (args.length == 3 && args[0].equalsIgnoreCase("truecrafter") && args[1].equalsIgnoreCase("heat")) return List.of("1", "2", "3", "4", "5");
        return List.of();
    }

    private String state() {
        return trueCrafter.isEnabled() ? "§aenabled" : "§cdisabled";
    }
}
