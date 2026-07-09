package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.OfflineUUIDUtil;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.UUID;

/**
 * OfflineAccess 機能のエントリーポイント。
 * <p>
 * 有効時に Netty パイプラインへ {@link LoginInterceptor} を注入し、
 * 許可されたオフラインプレイヤーのログインを通過させる。
 * </p>
 */
public class OfflineAccessModule implements Listener {

    public static final boolean Debug = false;

    private final Plugin plugin;
    private final OfflineAccessManager manager;
    private boolean injected;
    private boolean injectionFailed;
    private String failureReason = "未実行";
    private String debugLog = "OfflineAccess はまだ初期化されていません。";
    private Instant lastAttemptAt;

    public OfflineAccessModule(Plugin plugin, ConfigManager configManager, ToggleModule toggleModule) {
        this.plugin = plugin;
        this.manager = new OfflineAccessManager(plugin, configManager, toggleModule);
    }

    /**
     * サーバー接続に Netty ハンドラを注入する。
     * <p>
     * 有効/無効は {@link LoginInterceptor} 実行時に判定されるため、
     * ここでは常にパイプラインを登録する。
     * </p>
     */
    public void inject() {
        lastAttemptAt = Instant.now();
        debugLog = "OfflineAccess debug enabled=" + Debug + " at " + lastAttemptAt + System.lineSeparator();
        appendDebug("Netty injection start");
        try {
            NettyInjector.inject(manager);
            injected = true;
            injectionFailed = false;
            failureReason = "正常";
            appendDebug("Netty injection succeeded");
            plugin.getLogger().info("[OfflineAccess] Netty パイプラインにインジェクションしました（有効/無効は /toggle op で制御）");
        } catch (Exception e) {
            injected = false;
            injectionFailed = true;
            failureReason = e.getMessage() != null ? e.getMessage() : e.getClass().getName();
            appendDebug("Netty injection failed: " + failureReason, e);
            plugin.getLogger().severe("[OfflineAccess] Netty インジェクションに失敗しました。OfflineAccess のみフェールセーフ無効化します: " + failureReason);
            plugin.getLogger().severe(debugLog);
        }
    }

    /**
     * OfflineAccess の許可リストに載っているオフラインアカウントは
     * whitelist 審査を自動で通過させる。
     * （許可リスト入り = Discord 申請などで承認済みのため、
     * 「許可されているのに whitelist で蹴られる」状態を防ぐ）
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProfileWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (!event.isWhitelistEnabled() || event.isWhitelisted()) {
            return;
        }
        String name = event.getPlayerProfile().getName();
        UUID id = event.getPlayerProfile().getId();
        if (name == null || id == null) {
            return;
        }
        // 統合版(Floodgate/Geyser)ユーザーは OfflineAccess の対象外。
        // Bedrock 側は Floodgate の通常処理に任せ、ここでは whitelist 自動通過などを行わない。
        if (FloodgateUtil.isBedrock(id) || FloodgateUtil.isBedrockName(name)) {
            return;
        }
        // オフライン UUID のプロフィールのみ対象（通常の Mojang 認証アカウントは対象外）
        if (!id.equals(OfflineUUIDUtil.getUUID(name))) {
            return;
        }
        if (!manager.isAllowed(name)) {
            return;
        }
        event.setWhitelisted(true);
        manager.debug("Whitelist auto-pass for allowed offline account name=" + name);
    }

    /** 許可済みオフラインアカウントが参加したら本 whitelist にも登録して永続化する。 */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        // 統合版(Floodgate/Geyser)ユーザーは OfflineAccess の対象外。
        if (FloodgateUtil.isBedrock(player)) {
            return;
        }
        if (!player.getUniqueId().equals(OfflineUUIDUtil.getUUID(player.getName()))) {
            return;
        }
        if (!manager.isAllowed(player.getName())) {
            return;
        }
        if (!plugin.getServer().hasWhitelist() || player.isWhitelisted()) {
            return;
        }
        player.setWhitelisted(true);
        plugin.getLogger().info("[OfflineAccess] 許可済みオフラインアカウントを whitelist に追加しました: "
                + player.getName());
    }

    public void appendDebug(String message) {
        if (!Debug) {
            return;
        }
        String line = "[" + Instant.now() + "] " + message;
        debugLog = appendBounded(debugLog, line + System.lineSeparator());
    }

    public void appendDebug(String message, Throwable throwable) {
        if (!Debug) {
            return;
        }
        StringWriter writer = new StringWriter();
        PrintWriter printWriter = new PrintWriter(writer);
        printWriter.println("[" + Instant.now() + "] " + message);
        printWriter.println("Server Bukkit version: " + plugin.getServer().getBukkitVersion());
        printWriter.println("Server Minecraft version: " + plugin.getServer().getMinecraftVersion());
        if (throwable != null) {
            printWriter.println("Exception: " + throwable.getClass().getName() + ": " + throwable.getMessage());
            throwable.printStackTrace(printWriter);
        }
        printWriter.flush();
        debugLog = appendBounded(debugLog, writer.toString());
    }

    private String appendBounded(String base, String addition) {
        String value = (base == null ? "" : base) + addition;
        int maxLength = 60000;
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(value.length() - maxLength);
    }

    public boolean isInjected() {
        return injected;
    }

    public boolean isInjectionFailed() {
        return injectionFailed;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public String getDebugLog() {
        return debugLog;
    }

    public String getLastAttemptAtText() {
        return lastAttemptAt != null ? lastAttemptAt.toString() : "未実行";
    }

    /**
     * マネージャーを取得する。
     *
     * @return OfflineAccessManager
     */
    public OfflineAccessManager getManager() {
        return manager;
    }
}
