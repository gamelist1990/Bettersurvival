package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.Gson;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * サーバーの稼働状況 (CPU / メモリ / 電力 / 安定性 / 通信量) を <b>定期的に収集してキャッシュ</b>する。
 * <p>
 * Web からのリクエストは常にこのキャッシュ済みスナップショット (事前シリアライズ済みバイト列) を返すだけなので、
 * 大量アクセス (DDoS 等) を受けても 1 リクエストあたりの処理は「バイト配列を書き出す」だけで済み、
 * サーバー本体 (CPU / メモリ計測・JSON生成) への負荷が増えない。
 * </p>
 * <p>
 * 収集は {@link #REFRESH_TICKS} 間隔でメインスレッド上の軽量タスクとして行う。
 * 通信量カウンタは {@link WebMapHttpServer} から {@link #recordResponseBytes(long)} /
 * {@link #recordRequest(long)} で加算され、{@link #reset()} でゼロに戻せる。
 * </p>
 */
public final class WebMapStatusService {
    /** 収集間隔 (tick)。5 秒。 */
    private static final long REFRESH_TICKS = 100L;
    private static final int REFRESH_SECONDS = (int) (REFRESH_TICKS / 20L);
    private static final Gson GSON = new Gson();

    // 電力推定に使う 1 コアあたりのワット数 (アイドル〜フルロード)。あくまで概算値。
    private static final double IDLE_WATTS_PER_CORE = 1.5D;
    private static final double MAX_WATTS_PER_CORE = 9.0D;

    private final Loader plugin;

    // --- 通信量カウンタ (reset 可能) ---
    private final AtomicLong trafficOut = new AtomicLong();
    private final AtomicLong trafficIn = new AtomicLong();
    private final AtomicLong requestCount = new AtomicLong();

    // --- 安定性の集計 (reset 可能) ---
    private volatile long resetAt = System.currentTimeMillis();
    private volatile long sampleCount = 0L;
    private volatile double tpsSum = 0D;
    private volatile double minTps = 20D;
    private volatile double energyWh = 0D;
    private volatile long lastRefreshAt = System.currentTimeMillis();

    private volatile StatusSnapshot snapshot;
    private BukkitTask refreshTask;

    public WebMapStatusService(Loader plugin) {
        this.plugin = plugin;
        // 起動直後に空アクセスがあってもよいよう、最初のスナップショットを用意しておく。
        this.snapshot = buildSnapshot();
    }

    public void start() {
        if (refreshTask != null) {
            return;
        }
        // メインスレッドで実行。処理はごく軽量 (数値取得 + 小さな JSON 生成) なので tick への影響は無視できる。
        refreshTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::refresh, REFRESH_TICKS, REFRESH_TICKS);
    }

    public void stop() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
    }

    /** Web リクエスト 1 件を記録する (リクエスト数 + 受信バイト数)。 */
    public void recordRequest(long inboundBytes) {
        requestCount.incrementAndGet();
        if (inboundBytes > 0) {
            trafficIn.addAndGet(inboundBytes);
        }
    }

    /** レスポンスボディの送信バイト数を記録する。 */
    public void recordResponseBytes(long outboundBytes) {
        if (outboundBytes > 0) {
            trafficOut.addAndGet(outboundBytes);
        }
    }

    /** すべての集計 (通信量・安定性・積算電力) をリセットする。Minecraft からの {@code /status reset} 用。 */
    public void reset() {
        trafficOut.set(0L);
        trafficIn.set(0L);
        requestCount.set(0L);
        sampleCount = 0L;
        tpsSum = 0D;
        minTps = 20D;
        energyWh = 0D;
        long now = System.currentTimeMillis();
        resetAt = now;
        lastRefreshAt = now;
        // すぐに新しいスナップショットへ反映する (キャッシュ配信を最新化)。
        if (Bukkit.isPrimaryThread()) {
            refresh();
        } else {
            plugin.getServer().getScheduler().runTask(plugin, this::refresh);
        }
    }

    /** キャッシュ済み JSON バイト列を返す (Web 配信用)。 */
    public byte[] cachedJsonBytes() {
        StatusSnapshot current = snapshot;
        return current != null ? current.jsonBytes() : "{}".getBytes(StandardCharsets.UTF_8);
    }

    /** 現在のスナップショットを返す (チャット表示用)。 */
    public StatusSnapshot currentSnapshot() {
        return snapshot;
    }

    public int intervalSeconds() {
        return Math.max(1, REFRESH_SECONDS);
    }

    private void refresh() {
        this.snapshot = buildSnapshot();
    }

    private StatusSnapshot buildSnapshot() {
        long now = System.currentTimeMillis();

        // --- CPU ---
        OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
        int cores = Math.max(1, osBean.getAvailableProcessors());
        double cpuProcess = -1D;
        double cpuSystem = -1D;
        if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            cpuProcess = sunOs.getProcessCpuLoad();
            cpuSystem = sunOs.getCpuLoad();
        }
        double cpuProcessClamped = clamp01(cpuProcess);
        double cpuSystemClamped = clamp01(cpuSystem);
        // 電力推定に使う負荷はシステム全体を優先、無ければプロセス負荷。
        double loadForPower = cpuSystem >= 0D ? cpuSystemClamped : cpuProcessClamped;

        // --- メモリ (JVM に割り当てられた最大ヒープに対する使用率) ---
        Runtime runtime = Runtime.getRuntime();
        long max = runtime.maxMemory();
        long committed = runtime.totalMemory();
        long free = runtime.freeMemory();
        long used = committed - free;
        long allocated = (max == Long.MAX_VALUE) ? committed : max;
        double memPercent = allocated > 0 ? Math.min(100D, (used * 100D) / allocated) : 0D;

        // --- 電力 (概算) ---
        double watts = cores * (IDLE_WATTS_PER_CORE + (MAX_WATTS_PER_CORE - IDLE_WATTS_PER_CORE) * loadForPower);
        // 前回計測からの経過時間で積算エネルギー (Wh) を足し込む。
        long dtMillis = Math.max(0L, now - lastRefreshAt);
        energyWh += watts * (dtMillis / 3_600_000D);
        lastRefreshAt = now;

        // --- 安定性 (TPS / MSPT) ---
        double[] tpsValues = Bukkit.getTPS();
        double tps = tpsValues.length == 0 ? 20D : clamp(tpsValues[0], 0D, 20D);
        double mspt = safeMspt();
        sampleCount++;
        tpsSum += tps;
        if (tps < minTps) {
            minTps = tps;
        }
        double avgTps = sampleCount > 0 ? tpsSum / sampleCount : tps;
        double stabilityPercent = (tps / 20D) * 100D;

        // --- 稼働時間 ---
        RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
        long uptimeMillis = runtimeBean.getUptime();

        // --- 通信量 ---
        long out = trafficOut.get();
        long in = trafficIn.get();
        long requests = requestCount.get();

        // --- サーバー情報 ---
        String serverName = ServerInfoUtil.getServerName();
        String serverVersion = ServerInfoUtil.getServerVersion();
        int playersOnline = Bukkit.getOnlinePlayers().size();
        int playersMax = Bukkit.getMaxPlayers();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("updatedAt", now);
        payload.put("intervalSeconds", intervalSeconds());
        payload.put("resetAt", resetAt);

        Map<String, Object> cpu = new LinkedHashMap<>();
        cpu.put("process", round4(cpuProcessClamped));
        cpu.put("system", round4(cpuSystemClamped));
        cpu.put("processAvailable", cpuProcess >= 0D);
        cpu.put("systemAvailable", cpuSystem >= 0D);
        cpu.put("cores", cores);
        payload.put("cpu", cpu);

        Map<String, Object> memory = new LinkedHashMap<>();
        memory.put("usedBytes", used);
        memory.put("allocatedBytes", allocated);
        memory.put("committedBytes", committed);
        memory.put("usedPercent", round2(memPercent));
        payload.put("memory", memory);

        Map<String, Object> power = new LinkedHashMap<>();
        power.put("watts", round2(watts));
        power.put("energyWh", round4(energyWh));
        power.put("estimate", true);
        payload.put("power", power);

        Map<String, Object> stability = new LinkedHashMap<>();
        stability.put("tps", round2(tps));
        stability.put("mspt", round2(mspt));
        stability.put("percent", round2(stabilityPercent));
        stability.put("avgTps", round2(avgTps));
        stability.put("minTps", round2(minTps));
        payload.put("stability", stability);

        Map<String, Object> uptime = new LinkedHashMap<>();
        uptime.put("millis", uptimeMillis);
        uptime.put("text", formatDuration(uptimeMillis));
        payload.put("uptime", uptime);

        Map<String, Object> traffic = new LinkedHashMap<>();
        traffic.put("bytesOut", out);
        traffic.put("bytesIn", in);
        traffic.put("bytesTotal", out + in);
        traffic.put("requests", requests);
        traffic.put("sinceReset", resetAt);
        payload.put("traffic", traffic);

        Map<String, Object> server = new LinkedHashMap<>();
        server.put("name", serverName);
        server.put("version", serverVersion);
        server.put("playersOnline", playersOnline);
        server.put("playersMax", playersMax);
        payload.put("server", server);

        byte[] jsonBytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

        return new StatusSnapshot(
                now, intervalSeconds(),
                cpuProcessClamped, cpuSystemClamped, cores,
                used, allocated, committed, memPercent,
                watts, energyWh,
                tps, mspt, stabilityPercent, avgTps, minTps,
                uptimeMillis,
                out, in, out + in, requests, resetAt,
                serverName, playersOnline, playersMax,
                jsonBytes
        );
    }

    private double safeMspt() {
        try {
            return clamp(Bukkit.getServer().getAverageTickTime(), 0D, 1000D);
        } catch (Throwable ignored) {
            return 0D;
        }
    }

    private static double clamp01(double value) {
        if (Double.isNaN(value) || value < 0D) {
            return 0D;
        }
        return Math.min(1D, value);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
    }

    private static double round2(double value) {
        return Math.round(value * 100D) / 100D;
    }

    private static double round4(double value) {
        return Math.round(value * 10000D) / 10000D;
    }

    /** ミリ秒を "1d 2h 3m" のような読みやすい文字列にする。 */
    public static String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (days > 0) {
            builder.append(days).append("d ");
        }
        if (days > 0 || hours > 0) {
            builder.append(hours).append("h ");
        }
        if (days > 0 || hours > 0 || minutes > 0) {
            builder.append(minutes).append("m ");
        }
        builder.append(seconds).append("s");
        return builder.toString().trim();
    }

    /**
     * 収集済みのステータス値をまとめたイミュータブルなスナップショット。
     * {@link #jsonBytes()} は Web 配信用に事前シリアライズ済み。
     */
    public record StatusSnapshot(
            long updatedAt,
            int intervalSeconds,
            double cpuProcess,
            double cpuSystem,
            int cores,
            long memUsed,
            long memAllocated,
            long memCommitted,
            double memPercent,
            double watts,
            double energyWh,
            double tps,
            double mspt,
            double stabilityPercent,
            double avgTps,
            double minTps,
            long uptimeMillis,
            long trafficOut,
            long trafficIn,
            long trafficTotal,
            long requests,
            long resetAt,
            String serverName,
            int playersOnline,
            int playersMax,
            byte[] jsonBytes
    ) {
    }
}
