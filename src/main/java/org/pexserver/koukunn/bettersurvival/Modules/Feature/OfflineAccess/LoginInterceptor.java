package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import com.mojang.authlib.GameProfile;
import io.netty.channel.Channel;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelHandlerContext;
import org.pexserver.koukunn.bettersurvival.Core.Util.OfflineUUIDUtil;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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

    private final OfflineAccessManager manager;

    public LoginInterceptor(OfflineAccessManager manager) {
        this.manager = manager;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (!manager.isEnabled()) {
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
        } catch (Exception e) {
            manager.getPlugin().getLogger().log(Level.WARNING, "[OfflineAccess] HelloPacket の解析に失敗しました", e);
            super.channelRead(ctx, msg);
            return;
        }

        if (!manager.isAllowed(name)) {
            super.channelRead(ctx, msg);
            return;
        }

        Channel channel = ctx.channel();
        Object connection = channel.pipeline().get(PACKET_HANDLER);
        if (connection == null) {
            manager.getPlugin().getLogger().warning("[OfflineAccess] packet_handler が見つかりません: " + name);
            super.channelRead(ctx, msg);
            return;
        }

        Object packetListener = getField(connection, PACKET_LISTENER_FIELD);
        if (packetListener == null || !LOGIN_LISTENER_CLASS.equals(packetListener.getClass().getName())) {
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

            manager.getPlugin().getLogger().info("[OfflineAccess] オフラインログインを処理しました: " + name);
        } catch (Exception e) {
            manager.getPlugin().getLogger().log(Level.SEVERE, "[OfflineAccess] オフラインログイン処理に失敗しました: " + name, e);
            super.channelRead(ctx, msg);
            return;
        }

        // パケットを通常のハンドラに渡さない（こちらで処理済み）
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
}


