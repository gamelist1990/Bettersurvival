package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import com.destroystokyo.paper.profile.CraftPlayerProfile;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.papermc.paper.adventure.PaperAdventure;
import io.papermc.paper.configuration.GlobalConfiguration;
import io.papermc.paper.connection.PaperPlayerLoginConnection;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;
import net.minecraft.server.network.ServerLoginPacketListenerImpl;
import org.bukkit.Bukkit;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerPreLoginEvent;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.OfflineUUIDUtil;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * ログイン時の {@link ServerboundHelloPacket} をインターセプトし、
 * 許可されたプレイヤーだけをオフライン GameProfile で認証する。
 *
 * <p>Paper 26.2 + paperweight-userdev の Mojang mappings を直接参照する。
 * reflection は使わず、Paper が公開している login state/profile/connection と
 * Bukkit/Paper の pre-login event API で通常ログインと同じイベント経路を再現する。</p>
 */
public class LoginInterceptor extends ChannelDuplexHandler {

    private static final String PACKET_HANDLER = "packet_handler";
    private static final String HANDLER_NAME = "offline_access_handler";

    private static final String DENIED_OFFLINE_LOGIN_MESSAGE = """
            §cこのオフラインアカウントは許可されていません。
            §7Discord 又は 管理者に申請して下さい。

            §cThis offline account is not allowed.
            §7Please apply via Discord or an administrator.
            """;

    private final OfflineAccessManager manager;
    private boolean loggedFirstRead;

    public LoginInterceptor(OfflineAccessManager manager) {
        this.manager = manager;
    }

    private static boolean tracePipeline() {
        return false;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            handleChannelRead(ctx, msg);
        } catch (Throwable t) {
            manager.getPlugin().getLogger().log(Level.WARNING,
                    "[OfflineAccess] ログイン処理中に想定外の例外が発生したため、この接続のみ通常処理へフォールバックします", t);
            manager.debug("channelRead unexpected error; fallback to super.channelRead", t);
            try {
                super.channelRead(ctx, msg);
            } catch (Throwable inner) {
                manager.debug("fallback super.channelRead also failed; closing channel", inner);
                ctx.close();
            }
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (isBenignProtocolException(cause)) {
            manager.debug("benign protocol exception on login channel; closing quietly ("
                    + summarizeException(cause) + ")");
        } else {
            manager.debug("exceptionCaught on login channel; closing connection only", cause);
        }
        try {
            ctx.close();
        } catch (Throwable ignored) {
        }
    }

    private static boolean isBenignProtocolException(Throwable cause) {
        for (Throwable t = cause; t != null; t = t.getCause()) {
            String className = t.getClass().getName();
            if (className.equals("io.netty.handler.codec.DecoderException")
                    || className.equals("io.netty.handler.codec.CorruptedFrameException")
                    || className.equals("io.netty.handler.codec.PrematureChannelClosureException")
                    || className.equals("io.netty.handler.timeout.ReadTimeoutException")) {
                return true;
            }
            String message = t.getMessage();
            if (message == null) continue;
            if (message.contains("was larger than I expected")
                    || message.contains("Received unknown packet id")
                    || message.contains("VarInt too big")
                    || message.contains("Length was longer than expected")
                    || message.contains("Bad packet id")
                    || message.contains("Connection reset")
                    || message.contains("Broken pipe")
                    || message.contains("forcibly closed")) {
                return true;
            }
        }
        return false;
    }

    private static String summarizeException(Throwable cause) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable t = cause; t != null && depth < 3; t = t.getCause(), depth++) {
            if (depth > 0) sb.append(" -> ");
            sb.append(t.getClass().getSimpleName());
            String message = t.getMessage();
            if (message != null && !message.isEmpty()) sb.append(": ").append(message);
        }
        return sb.toString();
    }

    private void handleChannelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (relocateBeforePacketHandler(ctx)) {
            super.channelRead(ctx, msg);
            return;
        }
        if (msg == null) {
            super.channelRead(ctx, msg);
            return;
        }

        String packetName = msg.getClass().getName();
        if (manager.isDebugEnabled() && tracePipeline()) {
            if (!loggedFirstRead) {
                loggedFirstRead = true;
                manager.debug("channelRead first packet=" + packetName
                        + " simple=" + msg.getClass().getSimpleName()
                        + " pipeline=" + ctx.pipeline().names());
            } else if (shouldLogPacket(packetName)) {
                manager.debug("channelRead packet=" + packetName + " pipeline=" + ctx.pipeline().names());
            }
        }

        if (!manager.isEnabled()) {
            if (msg instanceof ServerboundHelloPacket) {
                manager.debug("HelloPacket ignored because OfflineAccess toggle is disabled");
            }
            super.channelRead(ctx, msg);
            return;
        }

        if (!(msg instanceof ServerboundHelloPacket hello)) {
            super.channelRead(ctx, msg);
            return;
        }

        String name = hello.name();
        UUID profileId = hello.profileId();
        manager.debug("HelloPacket parsed name=" + name + " profileId=" + profileId);

        // Floodgate/Geyser は通常の認証処理へ任せる。
        if (FloodgateUtil.isBedrock(profileId) || FloodgateUtil.isBedrockName(name)) {
            manager.debug("OfflineAccess ignored Floodgate/Bedrock login name=" + name + " profileId=" + profileId);
            super.channelRead(ctx, msg);
            return;
        }

        if (!manager.isAllowed(name)) {
            UUID offlineUuid = OfflineUUIDUtil.getUUID(name);
            if (!offlineUuid.equals(profileId)) {
                // 正規オンライン認証の profileId なら通常処理へ流す。
                manager.debug("OfflineAccess ignored online-auth login name=" + name + " profileId=" + profileId);
                super.channelRead(ctx, msg);
                return;
            }
            manager.debug("Offline login not allowed name=" + name + " enabled=" + manager.isEnabled());
            disconnectDeniedOfflineLogin(ctx, name);
            return;
        }

        Connection connection = nmsConnection(ctx.channel());
        if (connection == null) {
            manager.getPlugin().getLogger().warning("[OfflineAccess] packet_handler が見つかりません: " + name);
            manager.debug("packet_handler missing name=" + name + " pipeline=" + ctx.channel().pipeline().names());
            super.channelRead(ctx, msg);
            return;
        }

        if (!(connection.getPacketListener() instanceof ServerLoginPacketListenerImpl loginListener)) {
            manager.debug("packetListener not login listener name=" + name + " listener="
                    + (connection.getPacketListener() == null ? "null" : connection.getPacketListener().getClass().getName()));
            super.channelRead(ctx, msg);
            return;
        }

        try {
            // Paper が公開している login state を直接操作する。
            loginListener.iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation = true;
            loginListener.requestedUsername = name;
            loginListener.requestedUuid = profileId;

            UUID offlineUuid = OfflineUUIDUtil.getUUID(name);
            GameProfile profile = new GameProfile(offlineUuid, name);
            GameProfile resultProfile = callPlayerPreLoginEvents(loginListener, connection, profile);

            // Vanilla/Paper の private startClientVerification(profile) はこの2代入だけなので、
            // 公開化されている state/authenticatedProfile を直接更新する。
            loginListener.authenticatedProfile = resultProfile;
            loginListener.state = ServerLoginPacketListenerImpl.State.VERIFYING;

            manager.debug("Offline login accepted name=" + name + " profile=" + resultProfile);
            manager.getPlugin().getLogger().info("[OfflineAccess] オフラインログインを処理しました: " + name);
        } catch (Exception e) {
            manager.getPlugin().getLogger().log(Level.SEVERE,
                    "[OfflineAccess] オフラインログイン処理に失敗しました: " + name, e);
            manager.debug("Offline login process failed name=" + name, e);
            super.channelRead(ctx, msg);
        }
        // 成功時は HelloPacket を通常ハンドラへ渡さない（こちらで処理済み）。
    }

    /**
     * Paper の ServerLoginPacketListenerImpl#callPlayerPreLoginEvents と同じ順序で
     * AsyncPlayerPreLoginEvent -> PlayerPreLoginEvent を発火する。
     */
    @SuppressWarnings("deprecation")
    private GameProfile callPlayerPreLoginEvents(ServerLoginPacketListenerImpl loginListener,
                                                  Connection connection,
                                                  GameProfile gameProfile) throws Exception {
        if (GlobalConfiguration.get().proxies.velocity.enabled) {
            loginListener.disconnect(Component.literal("This server requires you to connect with Velocity."));
            return gameProfile;
        }

        String playerName = gameProfile.name();
        UUID uniqueId = gameProfile.id();
        InetAddress address = ((InetSocketAddress) connection.getRemoteAddress()).getAddress();
        InetAddress rawAddress = ((InetSocketAddress) connection.channel.remoteAddress()).getAddress();

        PlayerProfile profile = CraftPlayerProfile.asBukkitCopy(gameProfile);
        PaperPlayerLoginConnection loginConnection = new PaperPlayerLoginConnection(loginListener);
        AsyncPlayerPreLoginEvent asyncEvent = new AsyncPlayerPreLoginEvent(
                playerName,
                address,
                rawAddress,
                uniqueId,
                loginListener.transferred,
                profile,
                connection.hostname,
                loginConnection
        );
        Bukkit.getPluginManager().callEvent(asyncEvent);

        profile = asyncEvent.getPlayerProfile();
        profile.complete(true);
        gameProfile = CraftPlayerProfile.asAuthlibCopy(profile);
        playerName = gameProfile.name();
        uniqueId = gameProfile.id();

        if (PlayerPreLoginEvent.getHandlerList().getRegisteredListeners().length != 0) {
            PlayerPreLoginEvent syncEvent = new PlayerPreLoginEvent(playerName, address, uniqueId);
            if (asyncEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
                syncEvent.disallow(PlayerPreLoginEvent.Result.valueOf(asyncEvent.getLoginResult().name()), asyncEvent.kickMessage());
            }

            PlayerPreLoginEvent completed = callSyncPreLoginEvent(syncEvent);
            if (completed.getResult() != PlayerPreLoginEvent.Result.ALLOWED) {
                loginListener.disconnect(PaperAdventure.asVanilla(completed.kickMessage()));
            }
        } else if (asyncEvent.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            loginListener.disconnect(PaperAdventure.asVanilla(asyncEvent.kickMessage()));
        }

        return gameProfile;
    }

    @SuppressWarnings("deprecation")
    private PlayerPreLoginEvent callSyncPreLoginEvent(PlayerPreLoginEvent event) throws Exception {
        if (Bukkit.isPrimaryThread()) {
            Bukkit.getPluginManager().callEvent(event);
            return event;
        }

        CompletableFuture<PlayerPreLoginEvent> future = new CompletableFuture<>();
        Bukkit.getScheduler().runTask(manager.getPlugin(), () -> {
            try {
                Bukkit.getPluginManager().callEvent(event);
                future.complete(event);
            } catch (Throwable throwable) {
                future.completeExceptionally(throwable);
            }
        });
        return future.get();
    }

    private void disconnectDeniedOfflineLogin(ChannelHandlerContext ctx, String name) {
        try {
            Connection connection = nmsConnection(ctx.channel());
            if (connection == null) {
                manager.debug("Denied offline login but packet_handler was not found name=" + name
                        + " pipeline=" + ctx.pipeline().names());
                ctx.close();
                return;
            }
            if (connection.getPacketListener() instanceof ServerLoginPacketListenerImpl loginListener) {
                loginListener.disconnect(Component.literal(DENIED_OFFLINE_LOGIN_MESSAGE));
            } else {
                connection.disconnect(Component.literal(DENIED_OFFLINE_LOGIN_MESSAGE));
            }
            manager.debug("Denied offline login disconnected name=" + name);
        } catch (Throwable e) {
            manager.debug("Failed to disconnect denied offline login name=" + name, e);
            ctx.close();
        }
    }

    private static Connection nmsConnection(Channel channel) {
        ChannelHandler handler = channel.pipeline().get(PACKET_HANDLER);
        return handler instanceof Connection connection ? connection : null;
    }

    private static boolean shouldLogPacket(String packetClassName) {
        return packetClassName.contains(".handshake.")
                || packetClassName.contains(".login.")
                || packetClassName.contains(".status.")
                || packetClassName.contains(".ping.");
    }

    private boolean relocateBeforePacketHandler(ChannelHandlerContext ctx) {
        Channel channel = ctx.channel();
        synchronized (channel) {
            try {
                ChannelPipeline pipeline = ctx.pipeline();
                ChannelHandler packetHandler = pipeline.get(PACKET_HANDLER);
                ChannelHandler currentHandler = pipeline.get(HANDLER_NAME);
                if (packetHandler == null || currentHandler == null) return false;

                List<String> names = pipeline.names();
                int offlineIndex = names.indexOf(HANDLER_NAME);
                int packetIndex = names.indexOf(PACKET_HANDLER);
                if (offlineIndex < 0 || packetIndex < 0 || offlineIndex == packetIndex - 1) return false;

                pipeline.remove(HANDLER_NAME);
                pipeline.addBefore(PACKET_HANDLER, HANDLER_NAME, new LoginInterceptor(manager));
                if (tracePipeline()) {
                    manager.debug("LoginInterceptor self-relocated from index=" + offlineIndex
                            + " to before packet_handler index=" + packetIndex
                            + " pipeline=" + pipeline.names());
                }
                return true;
            } catch (Exception e) {
                manager.debug("LoginInterceptor self-relocation failed pipeline=" + ctx.pipeline().names(), e);
                return false;
            }
        }
    }
}
