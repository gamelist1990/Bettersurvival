package org.pexserver.koukunn.bettersurvival.Commands.hardmode;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem.JustLevelingRuntime;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem.LevelingSystemModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem.LevelingTitle;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem.LevelingTitleSystem;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode.TrueCrafterModeModule;

import java.util.List;

/** サバイバル高難易度機能を簡単なON/OFF形式で管理する。 */
public final class HardModeCommand extends BaseCommand {
    private final TrueCrafterModeModule trueCrafter;
    private final LevelingSystemModule levelingSystem;
    @SuppressWarnings("unused")
    private final JustLevelingRuntime levelingRuntime;
    private final LevelingTitleSystem titleSystem;

    public HardModeCommand(TrueCrafterModeModule trueCrafter) {
        this.trueCrafter = trueCrafter;
        Loader plugin = Loader.getPlugin(Loader.class);
        this.levelingSystem = new LevelingSystemModule(plugin);
        this.levelingRuntime = new JustLevelingRuntime(plugin, levelingSystem);
        this.titleSystem = new LevelingTitleSystem(plugin, levelingSystem);
    }

    @Override public String getName() { return "hardmode"; }
    @Override public String getDescription() { return "サバイバル高難易度機能を管理"; }
    @Override public PermissionLevel getPermissionLevel() { return PermissionLevel.ADMIN_OR_CONSOLE; }
    @Override public String getUsage() { return "/hardmode <list|truecrafter enabled|disabled|heat 1-5|leveling enabled|disabled|open|book|titles|title>"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§6HardMode機能一覧");
            sender.sendMessage("§e真クラ §7(truecrafter): " + state() + " §7/ 熱量: §6" + trueCrafter.heatLevel());
            sender.sendMessage("§dJust Leveling §7(leveling): " + (levelingSystem.isEnabled() ? "§aenabled" : "§cdisabled"));
            return true;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("leveling")
                && (args[1].equalsIgnoreCase("enabled") || args[1].equalsIgnoreCase("disabled"))) {
            boolean enabled = args[1].equalsIgnoreCase("enabled");
            levelingSystem.setEnabled(enabled);
            sender.sendMessage("§dJust Leveling §6を" + (enabled ? "§a有効化" : "§c無効化") + "§6しました");
            sender.sendMessage(enabled
                    ? "§7Leveling Book のクラフトレシピを登録しました。"
                    : "§7Leveling Book のクラフトレシピを削除しました。");
            return true;
        }

        if (args.length >= 2 && args[0].equalsIgnoreCase("leveling")) {
            if (!levelingSystem.isEnabled()) {
                sendError(sender, "Just Leveling は無効です。/hardmode leveling enabled で有効化してください");
                return true;
            }
            if (!(sender instanceof Player player)) {
                sendError(sender, "プレイヤーから実行してください");
                return true;
            }
            if (args[1].equalsIgnoreCase("open")) {
                levelingSystem.open(player);
                return true;
            }
            if (args[1].equalsIgnoreCase("book")) {
                player.getInventory().addItem(levelingSystem.createLevelingBook());
                sender.sendMessage("§dLeveling Book §6を付与しました");
                return true;
            }
            if (args[1].equalsIgnoreCase("titles")) {
                sender.sendMessage("§6Unlocked Titles §7(" + titleSystem.unlocked(player).size() + "/" + LevelingTitle.values().length + ")");
                sender.sendMessage("§7Selected: §e" + titleSystem.selected(player).displayName());
                for (LevelingTitle title : titleSystem.unlocked(player)) {
                    sender.sendMessage("§e- " + title.key() + " §7(" + title.displayName() + ")");
                }
                return true;
            }
            if (args[1].equalsIgnoreCase("title") && args.length >= 3) {
                if (titleSystem.select(player, args[2])) {
                    sender.sendMessage("§6Title: §e" + titleSystem.selected(player).displayName());
                } else {
                    sendError(sender, "その称号は未解放か、存在しません");
                }
                return true;
            }
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
        sender.sendMessage("§e/hardmode leveling <enabled|disabled>");
        sender.sendMessage("§e/hardmode leveling open");
        sender.sendMessage("§e/hardmode leveling book");
        sender.sendMessage("§e/hardmode leveling titles");
        sender.sendMessage("§e/hardmode leveling title <key>");
        return true;
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) return List.of("list", "truecrafter", "leveling");
        if (args.length == 2 && args[0].equalsIgnoreCase("truecrafter")) return List.of("enabled", "disabled", "heat");
        if (args.length == 2 && args[0].equalsIgnoreCase("leveling")) return List.of("enabled", "disabled", "open", "book", "titles", "title");
        if (args.length == 3 && args[0].equalsIgnoreCase("truecrafter") && args[1].equalsIgnoreCase("heat")) return List.of("1", "2", "3", "4", "5");
        if (args.length == 3 && args[0].equalsIgnoreCase("leveling") && args[1].equalsIgnoreCase("title")) {
            return sender instanceof Player player ? titleSystem.unlocked(player).stream().map(LevelingTitle::key).toList() : List.of();
        }
        return List.of();
    }

    private String state() {
        return trueCrafter.isEnabled() ? "§aenabled" : "§cdisabled";
    }
}
