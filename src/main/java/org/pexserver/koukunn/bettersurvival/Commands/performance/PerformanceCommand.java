package org.pexserver.koukunn.bettersurvival.Commands.performance;

import org.bukkit.command.CommandSender;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Performance.PerformanceModule;

import java.util.List;
import java.util.stream.Stream;

/**
 * {@code /performance} — 省電力モードの有効/無効を切り替える OP コマンド。
 * <p>
 * true の場合、無人時に無駄な更新スレッドを停止して消費電力を最小限に抑える。デフォルトは false。
 * </p>
 */
public class PerformanceCommand extends BaseCommand {
    private final Loader plugin;

    public PerformanceCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "performance";
    }

    @Override
    public String getDescription() {
        return "省電力モードの有効/無効 (無人時に更新スレッドを停止して消費電力を抑える)";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ADMIN_OR_CONSOLE;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        PerformanceModule module = plugin.getPerformanceModule();
        if (module == null) {
            sendError(sender, "省電力モジュールは現在利用できません");
            return true;
        }

        if (args.length == 0 || "status".equalsIgnoreCase(args[0])) {
            showStatus(sender, module);
            return true;
        }

        Boolean target = parseBoolean(args[0], module);
        if (target == null) {
            sendError(sender, "使用法: /performance <true|false|toggle|status>");
            return true;
        }

        module.setEnabled(target);
        if (target) {
            sendSuccess(sender, "省電力モードを §a有効§r にしました (無人時に更新スレッドを停止します)");
        } else {
            sendSuccess(sender, "省電力モードを §c無効§r にしました (常に通常稼働します)");
        }
        sendInfo(sender, "現在の状態: " + (module.isPowerSaving() ? "§e省電力中 (無人)" : "§a通常稼働"));
        return true;
    }

    private Boolean parseBoolean(String value, PerformanceModule module) {
        return switch (value.toLowerCase()) {
            case "true", "on", "enable" -> true;
            case "false", "off", "disable" -> false;
            case "toggle" -> !module.isEnabled();
            default -> null;
        };
    }

    private void showStatus(CommandSender sender, PerformanceModule module) {
        sendInfo(sender, "§6=== 省電力モード ===");
        sendInfo(sender, "設定: " + (module.isEnabled() ? "§a有効 (true)" : "§c無効 (false)"));
        sendInfo(sender, "現在: " + (module.isPowerSaving() ? "§e省電力中 (無人のため更新停止)" : "§a通常稼働"));
        sendInfo(sender, "§7有効時は無人になると自動で更新スレッドを停止し、接続で復帰します");
        sendInfo(sender, "§7切り替え: /performance <true|false>");
    }

    @Override
    public String getUsage() {
        return "/performance <true|false|toggle|status>";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            return Stream.of("true", "false", "toggle", "status")
                    .filter(value -> value.startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
