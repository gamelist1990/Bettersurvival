package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandler;
import io.papermc.paper.network.ChannelInitializeListener;
import io.papermc.paper.network.ChannelInitializeListenerHolder;
import net.kyori.adventure.key.Key;

import java.util.List;

/**
 * Paper の ChannelInitializeListener を使う Netty パイプラインインジェクター。
 *
 * ServerConnectionListener の private field を reflection で走査する旧方式を廃止し、
 * Paper が提供する初期化フックから新規接続へ LoginInterceptor を差し込む。
 */
public final class NettyInjector {

    private static final Key LISTENER_KEY = Key.key("bettersurvival", "offline_access");

    private NettyInjector() {
    }

    public static void inject(OfflineAccessManager manager) {
        try {
            ChannelInitializeListenerHolder.removeListener(LISTENER_KEY);
            ChannelInitializeListener listener = channel -> install(channel, manager);
            ChannelInitializeListenerHolder.addListener(LISTENER_KEY, listener);
            manager.debug("Paper ChannelInitializeListener registered: " + LISTENER_KEY);
        } catch (Throwable e) {
            manager.debug("ChannelInitializeListener registration failed", e);
            throw new RuntimeException("[OfflineAccess] Netty パイプラインのインジェクションに失敗しました", e);
        }
    }

    public static void uninject() {
        ChannelInitializeListenerHolder.removeListener(LISTENER_KEY);
    }

    private static void install(Channel channel, OfflineAccessManager manager) {
        Runnable task = () -> ensureLoginInterceptorPosition(channel, manager);
        if (!channel.isRegistered() || channel.eventLoop().inEventLoop()) {
            task.run();
        } else {
            channel.eventLoop().execute(task);
        }
    }

    private static void ensureLoginInterceptorPosition(Channel channel, OfflineAccessManager manager) {
        try {
            synchronized (channel) {
                ChannelHandler existing = channel.pipeline().get("offline_access_handler");
                ChannelHandler packetHandler = channel.pipeline().get("packet_handler");

                if (packetHandler != null) {
                    if (existing != null) {
                        List<String> names = channel.pipeline().names();
                        int offlineIndex = names.indexOf("offline_access_handler");
                        int packetIndex = names.indexOf("packet_handler");
                        if (offlineIndex == packetIndex - 1) return;
                        channel.pipeline().remove("offline_access_handler");
                    }
                    channel.pipeline().addBefore("packet_handler", "offline_access_handler", new LoginInterceptor(manager));
                    return;
                }

                if (existing == null) {
                    channel.pipeline().addLast("offline_access_handler", new LoginInterceptor(manager));
                }
            }
        } catch (IllegalArgumentException duplicate) {
            if (channel.pipeline().get("offline_access_handler") == null) {
                manager.debug("Failed to install offline_access_handler pipeline=" + pipelineNames(channel), duplicate);
            }
        } catch (Throwable e) {
            manager.debug("Failed to install offline_access_handler pipeline=" + pipelineNames(channel), e);
        }
    }

    private static String pipelineNames(Channel channel) {
        try {
            return channel.pipeline().names().toString();
        } catch (Throwable e) {
            return "<pipeline unavailable: " + e.getMessage() + ">";
        }
    }
}
