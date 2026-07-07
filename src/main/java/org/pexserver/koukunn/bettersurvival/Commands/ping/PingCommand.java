package org.pexserver.koukunn.bettersurvival.Commands.ping;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Core.Command.PermissionLevel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * ping コマンド
 *
 * <p>サーバーとの通信にかかる時間 (Round-Trip Time) を計測して返します。</p>
 *
 * <ul>
 *   <li>引数なし: 自分自身の ping を表示 (プレイヤーのみ)</li>
 *   <li>引数あり: 指定プレイヤーの ping を表示 (OP 専用)</li>
 * </ul>
 *
 * <p>計測ソース:</p>
 * <ul>
 *   <li>Paper API の {@link Player#getPing()} … クライアントとの Keep-Alive RTT</li>
 *   <li>加えて、コマンド受信から次 tick までの遅延を計測し、サーバー側応答遅延の目安として表示</li>
 * </ul>
 */
public class PingCommand extends BaseCommand {

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "サーバーとの通信遅延 (ping) を計測して表示";
    }

    @Override
    public PermissionLevel getPermissionLevel() {
        return PermissionLevel.ANY;
    }

    @Override
    public String getUsage() {
        return "/ping [プレイヤー名]";
    }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        // 計測開始時刻 (サーバー側応答遅延の目安)
        final long startNanos = System.nanoTime();

        // 対象プレイヤーの決定
        final Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sendError(sender, "コンソールから実行する場合はプレイヤー名を指定してください: /ping <プレイヤー名>");
                return true;
            }
            target = p;
        } else {
            // 他人の ping を見るのは OP のみに制限
            if (sender instanceof Player p && !p.isOp()) {
                sendError(sender, "他プレイヤーの ping を確認するには OP 権限が必要です");
                return true;
            }
            Player found = Bukkit.getPlayerExact(args[0]);
            if (found == null) {
                sendError(sender, "プレイヤーが見つかりません: " + args[0]);
                return true;
            }
            target = found;
        }

        // ネットワーク RTT (Paper: Player#getPing はミリ秒)
        int tmpPing;
        try {
            tmpPing = target.getPing();
        } catch (Throwable ignored) {
            tmpPing = -1;
        }
        final int networkPingMs = tmpPing;

        // 次 tick までの遅延をサーバー応答遅延の目安として表示
        Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("BetterSurvival"),
                () -> sendPingResult(sender, target, networkPingMs, startNanos)
        );
        return true;
    }

    private void sendPingResult(CommandSender sender, Player target, int networkPingMs, long startNanos) {
        double serverLatencyMs = (System.nanoTime() - startNanos) / 1_000_000.0;

        String networkText = networkPingMs < 0 ? "取得不可" : networkPingMs + " ms";
        NamedTextColor networkColor = pingColor(networkPingMs);

        Component msg = Component.text()
                .append(Component.text("===== ", NamedTextColor.DARK_GRAY))
                .append(Component.text("Ping 計測結果", NamedTextColor.AQUA))
                .append(Component.text(" =====\n", NamedTextColor.DARK_GRAY))
                .append(Component.text("対象: ", NamedTextColor.GRAY))
                .append(Component.text(target.getName(), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("通信遅延 (RTT): ", NamedTextColor.GRAY))
                .append(Component.text(networkText, networkColor))
                .append(Component.newline())
                .append(Component.text("サーバー応答: ", NamedTextColor.GRAY))
                .append(Component.text(String.format("%.2f ms", serverLatencyMs), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("判定: ", NamedTextColor.GRAY))
                .append(Component.text(pingLabel(networkPingMs), networkColor))
                .build();

        sender.sendMessage(msg);
    }

    /** ping (ms) を色分けする。 */
    private NamedTextColor pingColor(int ms) {
        if (ms < 0) return NamedTextColor.DARK_GRAY;
        if (ms < 60) return NamedTextColor.GREEN;
        if (ms < 120) return NamedTextColor.YELLOW;
        if (ms < 200) return NamedTextColor.GOLD;
        return NamedTextColor.RED;
    }

    /** ping の目安ラベル。 */
    private String pingLabel(int ms) {
        if (ms < 0) return "計測不可";
        if (ms < 60) return "非常に良好";
        if (ms < 120) return "良好";
        if (ms < 200) return "やや遅延";
        return "要注意 (高遅延)";
    }

    @Override
    public List<String> getTabCompletions(CommandSender sender, String[] args) {
        if (args.length == 1) {
            // OP のみ他人の名前補完
            boolean canQueryOthers = !(sender instanceof Player p) || p.isOp();
            if (!canQueryOthers) {
                return new ArrayList<>();
            }
            String prefix = args[0].toLowerCase();
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase().startsWith(prefix))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
