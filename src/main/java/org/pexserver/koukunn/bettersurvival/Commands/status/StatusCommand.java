package org.pexserver.koukunn.bettersurvival.Commands.status;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapModule;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap.WebMapStatusService;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /status} — サーバー稼働状況の確認と統計リセット。
 * <ul>
 *     <li>{@code /status} : CPU / メモリ / 電力 / 安定性 / 通信量の概要をチャットに表示</li>
 *     <li>{@code /status reset} : 通信量・安定性・積算電力の統計をリセット (OP / コンソールのみ)</li>
 * </ul>
 */
public class StatusCommand extends BaseCommand {
    private final Loader plugin;

    public StatusCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getName() {
        return "status";
    }

    @Override
    public String getDescription() {
        return "サーバー稼働状況の確認と統計リセット";
    }

    @Override
    public boolean isEnabled() {
        return plugin.getWebMapModule() != null;
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        WebMapModule module = plugin.getWebMapModule();
        if (module == null) {
            sendError(sender, "ステータス機能は現在利用できません");
            return true;
        }

        if (args.length > 0 && "reset".equalsIgnoreCase(args[0])) {
            if (sender instanceof Player player && !player.isOp()) {
                sendError(sender, "統計のリセットは OP またはコンソールのみ実行できます");
                return true;
            }
            module.resetStatus();
            sendSuccess(sender, "サーバー稼働統計 (通信量・安定性・積算電力) をリセットしました");
            return true;
        }

        WebMapStatusService.StatusSnapshot snapshot = module.getStatusService().currentSnapshot();
        if (snapshot == null) {
            sendInfo(sender, "ステータスを収集中です。数秒後に再度お試しください");
            return true;
        }

        sendInfo(sender, "§6=== サーバーステータス ===");
        sendInfo(sender, "§eCPU §7(プロセス): §f" + percent(snapshot.cpuProcess())
                + " §7/ システム: §f" + percent(snapshot.cpuSystem())
                + " §7(" + snapshot.cores() + " コア)");
        sendInfo(sender, "§eメモリ: §f" + formatBytes(snapshot.memUsed())
                + " §7/ " + formatBytes(snapshot.memAllocated())
                + " §7(" + oneDecimal(snapshot.memPercent()) + "%)");
        sendInfo(sender, "§e電力 §7(概算): §f" + oneDecimal(snapshot.watts()) + " W"
                + " §7/ 積算 " + oneDecimal(snapshot.energyWh()) + " Wh");
        sendInfo(sender, "§e安定性: §fTPS " + twoDecimal(snapshot.tps())
                + " §7(" + oneDecimal(snapshot.stabilityPercent()) + "%)"
                + " §7MSPT " + twoDecimal(snapshot.mspt())
                + " §7平均 " + twoDecimal(snapshot.avgTps())
                + " / 最低 " + twoDecimal(snapshot.minTps()));
        sendInfo(sender, "§e稼働時間: §f" + WebMapStatusService.formatDuration(snapshot.uptimeMillis()));
        sendInfo(sender, "§e通信量: §f" + formatBytes(snapshot.trafficTotal())
                + " §7(↑" + formatBytes(snapshot.trafficOut())
                + " ↓" + formatBytes(snapshot.trafficIn())
                + " / " + snapshot.requests() + " req)");
        sendInfo(sender, "§7統計リセット: §f/status reset");
        return true;
    }

    @Override
    public String getUsage() {
        return "/status [reset]";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) {
            if (sender.isOp() || !(sender instanceof Player)) {
                values.add("reset");
            }
        }
        return values.stream()
                .filter(value -> value.startsWith(args[0].toLowerCase()))
                .toList();
    }

    private String percent(double ratio) {
        return oneDecimal(ratio * 100D) + "%";
    }

    private String oneDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private String twoDecimal(double value) {
        return String.format(java.util.Locale.ROOT, "%.2f", value);
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024D;
        if (kb < 1024D) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        }
        double mb = kb / 1024D;
        if (mb < 1024D) {
            return String.format(java.util.Locale.ROOT, "%.1f MB", mb);
        }
        double gb = mb / 1024D;
        return String.format(java.util.Locale.ROOT, "%.2f GB", gb);
    }
}
