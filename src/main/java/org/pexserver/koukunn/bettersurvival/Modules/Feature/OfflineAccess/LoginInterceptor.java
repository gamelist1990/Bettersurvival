package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil;
import org.pexserver.koukunn.bettersurvival.Core.Util.OfflineUUIDUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * ログイン時の {@code ServerboundHelloPacket} をインターセプトし、
 * 許可されたプレイヤーに対してオフライン GameProfile で認証をスキップする。
 */
public class LoginInterceptor extends ChannelDuplexHandler {

    private static final String LOGIN_LISTENER_CLASS = "net.minecraft.server.network.ServerLoginPacketListenerImpl";
    private static final String USERNAME_VALIDATION_FIELD = "iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation";
    private static final String REQUESTED_USERNAME_FIELD = "requestedUsername";
    private static final String REQUESTED_UUID_FIELD = "requestedUuid";
    private static final String AUTHENTICATED_PROFILE_FIELD = "authenticatedProfile";
    private static final String CALL_PLAYER_PRE_LOGIN_EVENTS_METHOD = "callPlayerPreLoginEvents";
    private static final String START_CLIENT_VERIFICATION_METHOD = "startClientVerification";
    private static final String PACKET_HANDLER = "packet_handler";
    private static final String PACKET_LISTENER_FIELD = "packetListener";
    private static final String HANDLER_NAME = "offline_access_handler";

    private static boolean tracePipeline() {
        return false;
    }

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

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            handleChannelRead(ctx, msg);
        } catch (Throwable t) {
            // インターセプト処理中に想定外の例外が発生しても、サーバー全体へ波及させない。
            // この接続だけを通常のログイン処理へフォールバックさせ、それも失敗する場合は接続を閉じる。
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

    /**
     * ログイン段階で発生した例外がサーバー全体へ波及するのを防ぐ。
     * 例外はこの接続に限定し、該当チャンネルのみを閉じる。
     *
     * <p>Netty の {@code DecoderException} のような「不正クライアント / スキャナ / プロキシ forwarding
     * 不整合 / 古いプロトコル」などで日常的に発生する例外は、接続を静かに閉じるだけで実運用は無害。
     * これらを WARN 相当でフルスタックトレース出力するとログがノイズだらけになるため、
     * 既知の無害な例外は {@link OfflineAccessManager#debug} レベルに降格する。</p>
     */
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

    /**
     * よくあるプロトコル系デコード例外かどうかを判定する。
     * これらは接続を閉じるだけで対処すれば十分で、スタックトレース出力は不要。
     */
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
            if (message == null) {
                continue;
            }
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

    /**
     * 例外を 1 行で要約する (ログ用)。ネストは 3 段まで辿る。
     */
    private static String summarizeException(Throwable cause) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable t = cause; t != null && depth < 3; t = t.getCause(), depth++) {
            if (depth > 0) {
                sb.append(" -> ");
            }
            sb.append(t.getClass().getSimpleName());
            String message = t.getMessage();
            if (message != null && !message.isEmpty()) {
                sb.append(": ").append(message);
            }
        }
        return sb.toString();
    }

    private void handleChannelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (relocateBeforePacketHandler(ctx)) {
            super.channelRead(ctx, msg);
            return;
        }

        // msg が null の場合はそのまま通す（後続の getClass() での NPE を防ぐ）。
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
            if ("ServerboundHelloPacket".equals(msg.getClass().getSimpleName())) {
                manager.debug("HelloPacket ignored because OfflineAccess toggle is disabled");
            }
            super.channelRead(ctx, msg);
            return;
        }

        Class<?> packetClass = msg.getClass();
        if (!"ServerboundHelloPacket".equals(packetClass.getSimpleName())) {
            super.channelRead(ctx, msg);
            return;
        }

        String name;
        UUID profileId;
        try {
            name = (String) packetClass.getMethod("name").invoke(msg);
            profileId = (UUID) packetClass.getMethod("profileId").invoke(msg);
            manager.debug("HelloPacket parsed name=" + name + " profileId=" + profileId);
        } catch (Exception e) {
            manager.getPlugin().getLogger().log(Level.WARNING, "[OfflineAccess] HelloPacket の解析に失敗しました", e);
            manager.debug("HelloPacket parse failed packetClass=" + packetClass.getName(), e);
            super.channelRead(ctx, msg);
            return;
        }

        // 統合版(Floodgate/Geyser)ユーザーは OfflineAccess の対象外。
        // 初回参加時の Floodgate 側ログイン処理・チャンク送信を邪魔しないよう、ここで完全に素通しする。
        if (FloodgateUtil.isBedrock(profileId) || FloodgateUtil.isBedrockName(name)) {
            manager.debug("OfflineAccess ignored Floodgate/Bedrock login name=" + name + " profileId=" + profileId);
            super.channelRead(ctx, msg);
            return;
        }

        if (!manager.isAllowed(name)) {
            UUID offlineUuid = OfflineUUIDUtil.getUUID(name);
            if (!offlineUuid.equals(profileId)) {
                manager.debug("OfflineAccess ignored online-auth login name=" + name + " profileId=" + profileId);
                super.channelRead(ctx, msg);
                return;
            }
            manager.debug("Offline login not allowed name=" + name + " enabled=" + manager.isEnabled());
            disconnectDeniedOfflineLogin(ctx, name);
            return;
        }

        Channel channel = ctx.channel();
        Object connection = channel.pipeline().get(PACKET_HANDLER);
        if (connection == null) {
            manager.getPlugin().getLogger().warning("[OfflineAccess] packet_handler が見つかりません: " + name);
            manager.debug("packet_handler missing name=" + name + " pipeline=" + channel.pipeline().names());
            super.channelRead(ctx, msg);
            return;
        }

        Object packetListener = getField(connection, PACKET_LISTENER_FIELD);
        if (packetListener == null || !LOGIN_LISTENER_CLASS.equals(packetListener.getClass().getName())) {
            manager.debug("packetListener not login listener name=" + name + " listener="
                    + (packetListener == null ? "null" : packetListener.getClass().getName()));
            super.channelRead(ctx, msg);
            return;
        }

        try {
            // Paper のユーザー名検証を無効化
            setField(packetListener, USERNAME_VALIDATION_FIELD, true);

            // リクエスト情報をセット
            setField(packetListener, REQUESTED_USERNAME_FIELD, name);
            setField(packetListener, REQUESTED_UUID_FIELD, profileId);

            // オフライン UUID の GameProfile を作成
            UUID offlineUuid = OfflineUUIDUtil.getUUID(name);
            GameProfile profile = new GameProfile(offlineUuid, name);

            // AsyncPlayerPreLoginEvent を発火
            Method callPlayerPreLoginEvents = packetListener.getClass()
                    .getDeclaredMethod(CALL_PLAYER_PRE_LOGIN_EVENTS_METHOD, GameProfile.class);
            callPlayerPreLoginEvents.setAccessible(true);
            GameProfile resultProfile = (GameProfile) callPlayerPreLoginEvents.invoke(packetListener, profile);

            // 認証済みプロフィールをセット
            setField(packetListener, AUTHENTICATED_PROFILE_FIELD, resultProfile);

            // クライアント検証を開始
            Method startClientVerification = packetListener.getClass()
                    .getDeclaredMethod(START_CLIENT_VERIFICATION_METHOD, GameProfile.class);
            startClientVerification.setAccessible(true);
            startClientVerification.invoke(packetListener, resultProfile);
                manager.debug("Offline login accepted name=" + name + " profile=" + resultProfile);

            manager.getPlugin().getLogger().info("[OfflineAccess] オフラインログインを処理しました: " + name);
        } catch (Exception e) {
            manager.getPlugin().getLogger().log(Level.SEVERE, "[OfflineAccess] オフラインログイン処理に失敗しました: " + name, e);
            manager.debug("Offline login process failed name=" + name, e);
            super.channelRead(ctx, msg);
            return;
        }

        // パケットを通常のハンドラに渡さない（こちらで処理済み）
    }

    private void disconnectDeniedOfflineLogin(ChannelHandlerContext ctx, String name) {
        try {
            ChannelHandler packetHandler = ctx.pipeline().get(PACKET_HANDLER);
            if (packetHandler == null) {
                manager.debug("Denied offline login but packet_handler was not found name=" + name
                        + " pipeline=" + ctx.pipeline().names());
                ctx.close();
                return;
            }

            Class<?> componentClass = Class.forName("net.minecraft.network.chat.Component");
            Object component = componentClass.getMethod("literal", String.class)
                    .invoke(null, DENIED_OFFLINE_LOGIN_MESSAGE);

            Object disconnectTarget = packetHandler;
            try {
                Object packetListener = getField(packetHandler, PACKET_LISTENER_FIELD);
                if (packetListener != null) {
                    Method listenerDisconnect = findMethod(packetListener.getClass(), "disconnect", componentClass);
                    if (listenerDisconnect != null) {
                        disconnectTarget = packetListener;
                    }
                }
            } catch (Exception ignored) {
            }

            Method disconnect = findMethod(disconnectTarget.getClass(), "disconnect", componentClass);
            if (disconnect == null) {
                manager.debug("Denied offline login disconnect method not found name=" + name
                        + " target=" + disconnectTarget.getClass().getName());
                ctx.close();
                return;
            }

            disconnect.setAccessible(true);
            disconnect.invoke(disconnectTarget, component);
            manager.debug("Denied offline login disconnected name=" + name
                    + " target=" + disconnectTarget.getClass().getName());
        } catch (Exception e) {
            manager.debug("Failed to disconnect denied offline login name=" + name, e);
            ctx.close();
        }
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
                if (packetHandler == null || currentHandler == null) {
                    return false;
                }

                List<String> names = pipeline.names();
                int offlineIndex = names.indexOf(HANDLER_NAME);
                int packetIndex = names.indexOf(PACKET_HANDLER);
                if (offlineIndex < 0 || packetIndex < 0 || offlineIndex == packetIndex - 1) {
                    return false;
                }

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

    private static Object getField(Object target, String name) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = findField(target.getClass(), name);
        if (field == null) {
            throw new NoSuchFieldException(name);
        }
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Field findField(Class<?> clazz, String name) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }

    private static Method findMethod(Class<?> clazz, String name, Class<?>... parameterTypes) {
        while (clazz != null && clazz != Object.class) {
            try {
                return clazz.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                clazz = clazz.getSuperclass();
            }
        }
        return null;
    }
}


