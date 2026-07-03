package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.List;

/**
 * FloodGate 方式の Netty パイプラインインジェクター。
 * <p>
 * ServerConnection が保持する {@code List<ChannelFuture>} をラップし、
 * 新規接続が追加されたタイミングで {@link LoginInterceptor} をパイプラインに追加する。
 * </p>
 */
public final class NettyInjector {

    private NettyInjector() {
    }

    /**
     * サーバー接続に対して Netty インジェクションを行う。
     *
     * @param manager オフラインアクセスマネージャー
     */
    public static void inject(OfflineAccessManager manager) {
        try {
            Object minecraftServer = getMinecraftServer();
            Object serverConnection = getServerConnection(minecraftServer);
            injectServerConnection(serverConnection, manager);
        } catch (Exception e) {
            throw new RuntimeException("[OfflineAccess] Netty パイプラインのインジェクションに失敗しました", e);
        }
    }

    private static Object getMinecraftServer() throws Exception {
        Object craftServer = Bukkit.getServer();
        return craftServer.getClass().getMethod("getServer").invoke(craftServer);
    }

    private static Object getServerConnection(Object minecraftServer) throws Exception {
        Field connectionField = findField(minecraftServer.getClass(), "connection");
        if (connectionField == null) {
            throw new NoSuchFieldException("MinecraftServer.connection");
        }
        connectionField.setAccessible(true);
        return connectionField.get(minecraftServer);
    }

    private static void injectServerConnection(Object serverConnection, OfflineAccessManager manager) throws Exception {
        for (Field field : serverConnection.getClass().getDeclaredFields()) {
            if (field.getType() != List.class) {
                continue;
            }
            field.setAccessible(true);

            Type genericType = field.getGenericType();
            if (!(genericType instanceof ParameterizedType parameterizedType)) {
                continue;
            }
            Type[] actualTypes = parameterizedType.getActualTypeArguments();
            if (actualTypes.length != 1 || actualTypes[0] != ChannelFuture.class) {
                continue;
            }

            @SuppressWarnings("unchecked")
            List<Object> originalList = (List<Object>) field.get(serverConnection);
            List<Object> wrappedList = new CustomList(originalList, manager);
            field.set(serverConnection, wrappedList);

            // 既存の接続にもハンドラを追加
            for (Object obj : originalList) {
                if (obj instanceof ChannelFuture future) {
                    injectChannelFuture(future, manager);
                }
            }
            return;
        }
        throw new IllegalStateException("[OfflineAccess] ServerConnection 内に ChannelFuture のリストが見つかりません");
    }

    private static void injectChannelFuture(ChannelFuture future, OfflineAccessManager manager) {
        future.channel().pipeline().addFirst("offline_access_init", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof Channel channel) {
                    channel.pipeline().addLast("offline_access_handler", new LoginInterceptor(manager));
                }
                super.channelRead(ctx, msg);
            }
        });
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

    /**
     * 追加された ChannelFuture に自動でハンドラを差し込むリストラッパー。
     */
    private static final class CustomList extends AbstractList<Object> {
        private final List<Object> original;
        private final OfflineAccessManager manager;

        CustomList(List<Object> original, OfflineAccessManager manager) {
            this.original = original;
            this.manager = manager;
        }

        @Override
        public Object get(int index) {
            return original.get(index);
        }

        @Override
        public int size() {
            return original.size();
        }

        @Override
        public boolean add(Object e) {
            if (e instanceof ChannelFuture future) {
                injectChannelFuture(future, manager);
            }
            return original.add(e);
        }

        @Override
        public void add(int index, Object element) {
            if (element instanceof ChannelFuture future) {
                injectChannelFuture(future, manager);
            }
            original.add(index, element);
        }

        @Override
        public Object set(int index, Object element) {
            if (element instanceof ChannelFuture future) {
                injectChannelFuture(future, manager);
            }
            return original.set(index, element);
        }
    }
}

