package org.pexserver.koukunn.bettersurvival.Modules.Feature.OfflineAccess;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.bukkit.Bukkit;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.AbstractList;
import java.util.AbstractQueue;
import java.util.Iterator;
import java.util.List;
import java.util.Queue;

/**
 * FloodGate 方式の Netty パイプラインインジェクター。
 * <p>
 * ServerConnection が保持する {@code List<ChannelFuture>} をラップし、
 * 新規接続が追加されたタイミングで {@link LoginInterceptor} をパイプラインに追加する。
 * </p>
 */
public final class NettyInjector {

    private static boolean tracePipeline() {
        return false;
    }

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
            manager.debug("Netty injection initialized");
        } catch (Exception e) {
            manager.debug("inject() failed", e);
            throw new RuntimeException("[OfflineAccess] Netty パイプラインのインジェクションに失敗しました", e);
        }
    }

    private static Object getMinecraftServer() throws Exception {
        Object craftServer = Bukkit.getServer();
        return craftServer.getClass().getMethod("getServer").invoke(craftServer);
    }

    private static Object getServerConnection(Object minecraftServer) throws Exception {
        Field connectionField = findField(minecraftServer.getClass(), "connection");
        if (connectionField != null) {
            connectionField.setAccessible(true);
            Object value = connectionField.get(minecraftServer);
            if (value != null) {
                return value;
            }
        }

        for (Method method : minecraftServer.getClass().getMethods()) {
            if (method.getParameterCount() == 0
                    && "net.minecraft.server.network.ServerConnectionListener".equals(method.getReturnType().getName())) {
                method.setAccessible(true);
                Object value = method.invoke(minecraftServer);
                if (value != null) {
                    return value;
                }
            }
        }

        throw new NoSuchFieldException("MinecraftServer.connection / ServerConnectionListener getter");
    }

    private static void injectServerConnection(Object serverConnection, OfflineAccessManager manager) throws Exception {
        boolean injectedAny = false;
        for (Field field : serverConnection.getClass().getDeclaredFields()) {
            if (List.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);

                Type genericType = field.getGenericType();
                if (!(genericType instanceof ParameterizedType parameterizedType)) {
                    continue;
                }
                Type[] actualTypes = parameterizedType.getActualTypeArguments();
                Object value = field.get(serverConnection);
                if (!(value instanceof List<?>)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                List<Object> originalList = (List<Object>) value;
                if (actualTypes.length == 1 && actualTypes[0] == ChannelFuture.class) {
                    if (originalList instanceof CustomList) {
                        injectedAny = true;
                        continue;
                    }
                    List<Object> wrappedList = new CustomList(originalList, manager);
                    field.set(serverConnection, wrappedList);
                    injectedAny = true;
                    manager.debug("Wrapped ChannelFuture list field=" + field.getName() + " size=" + originalList.size());

                    for (Object obj : originalList) {
                        if (obj instanceof ChannelFuture future) {
                            injectChannelFuture(future, manager);
                        }
                    }
                    continue;
                }

                if (actualTypes.length == 1 && "net.minecraft.network.Connection".equals(actualTypes[0].getTypeName())) {
                    if (originalList instanceof ConnectionList) {
                        injectedAny = true;
                        continue;
                    }
                    List<Object> wrappedList = new ConnectionList(originalList, manager);
                    field.set(serverConnection, wrappedList);
                    injectedAny = true;
                    manager.debug("Wrapped Connection list field=" + field.getName() + " size=" + originalList.size());

                    for (Object obj : originalList) {
                        injectConnection(obj, manager);
                    }
                    continue;
                }

                continue;
            }

            if (Queue.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);

                Type genericType = field.getGenericType();
                if (!(genericType instanceof ParameterizedType parameterizedType)) {
                    continue;
                }
                Type[] actualTypes = parameterizedType.getActualTypeArguments();
                if (actualTypes.length != 1 || !"net.minecraft.network.Connection".equals(actualTypes[0].getTypeName())) {
                    continue;
                }

                Object value = field.get(serverConnection);
                if (!(value instanceof Queue<?>)) {
                    continue;
                }

                @SuppressWarnings("unchecked")
                Queue<Object> originalQueue = (Queue<Object>) value;
                if (originalQueue instanceof ConnectionQueue) {
                    injectedAny = true;
                    continue;
                }
                Queue<Object> wrappedQueue = new ConnectionQueue(originalQueue, manager);
                field.set(serverConnection, wrappedQueue);
                injectedAny = true;
                manager.debug("Wrapped Connection queue field=" + field.getName() + " size=" + originalQueue.size());

                for (Object obj : originalQueue) {
                    injectConnection(obj, manager);
                }
                continue;
            }
        }
        if (!injectedAny) {
            throw new IllegalStateException("[OfflineAccess] ServerConnection 内に注入可能な ChannelFuture/Connection リストが見つかりません");
        }
    }

    private static void injectChannelFuture(ChannelFuture future, OfflineAccessManager manager) {
        if (future.channel().pipeline().get("offline_access_init") != null) {
            return;
        }
        if (tracePipeline()) {
            manager.debug("Injecting listener channel=" + future.channel() + " pipeline=" + pipelineNames(future.channel()));
        }
        future.channel().pipeline().addFirst("offline_access_init", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                if (msg instanceof Channel channel) {
                    injectLoginInterceptor(channel, manager);
                }
                super.channelRead(ctx, msg);
            }
        });
    }

    private static void injectLoginInterceptor(Channel channel, OfflineAccessManager manager) {
        try {
            if (!channel.isRegistered()) {
                ensureLoginInterceptorPosition(channel, manager);
            } else if (channel.eventLoop().inEventLoop()) {
                ensureLoginInterceptorPosition(channel, manager);
            } else {
                channel.eventLoop().execute(() -> ensureLoginInterceptorPosition(channel, manager));
            }
        } catch (Exception e) {
            manager.debug("Failed to add offline_access_handler pipeline=" + pipelineNames(channel), e);
        }
    }

    private static void ensureLoginInterceptorPosition(Channel channel, OfflineAccessManager manager) {
        try {
            synchronized (channel) {
                ChannelHandler existing = channel.pipeline().get("offline_access_handler");
                boolean hasPacketHandler = channel.pipeline().get("packet_handler") != null;

                if (hasPacketHandler) {
                    if (existing != null) {
                        List<String> names = channel.pipeline().names();
                        int offlineIndex = names.indexOf("offline_access_handler");
                        int packetIndex = names.indexOf("packet_handler");
                        if (offlineIndex == packetIndex - 1) {
                            return;
                        }
                        channel.pipeline().remove("offline_access_handler");
                        if (tracePipeline()) {
                            manager.debug("offline_access_handler relocated from index=" + offlineIndex + " to before packet_handler index=" + packetIndex);
                        }
                    }

                    if (channel.pipeline().get("offline_access_handler") != null) {
                        return;
                    }
                    try {
                        channel.pipeline().addBefore("packet_handler", "offline_access_handler", new LoginInterceptor(manager));
                        if (tracePipeline()) {
                            manager.debug("offline_access_handler added before packet_handler pipeline=" + pipelineNames(channel));
                        }
                    } catch (IllegalArgumentException duplicate) {
                        if (channel.pipeline().get("offline_access_handler") != null) {
                            return;
                        }
                        throw duplicate;
                    }
                    return;
                }

                if (existing == null) {
                    try {
                        channel.pipeline().addLast("offline_access_handler", new LoginInterceptor(manager));
                        if (tracePipeline()) {
                            manager.debug("packet_handler not found yet; offline_access_handler added last");
                        }
                    } catch (IllegalArgumentException duplicate) {
                        if (channel.pipeline().get("offline_access_handler") != null) {
                            return;
                        }
                        throw duplicate;
                    }
                }
            }
        } catch (Exception e) {
            manager.debug("Failed to ensure offline_access_handler position pipeline=" + pipelineNames(channel), e);
        }
    }

    private static void injectConnection(Object connection, OfflineAccessManager manager) {
        if (connection == null) {
            return;
        }
        try {
            Field channelField = findField(connection.getClass(), "channel");
            if (channelField == null) {
                return;
            }
            channelField.setAccessible(true);
            Object channelObject = channelField.get(connection);
            if (!(channelObject instanceof Channel channel)) {
                return;
            }
            injectLoginInterceptor(channel, manager);
        } catch (Exception e) {
            manager.debug("Failed to inject from Connection object class=" + connection.getClass().getName(), e);
        }
    }

    private static String pipelineNames(Channel channel) {
        try {
            return channel.pipeline().names().toString();
        } catch (Exception e) {
            return "<pipeline unavailable: " + e.getMessage() + ">";
        }
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

        @Override
        public Object remove(int index) {
            return original.remove(index);
        }

        @Override
        public boolean remove(Object object) {
            return original.remove(object);
        }

        @Override
        public void clear() {
            original.clear();
        }
    }

    private static final class ConnectionList extends AbstractList<Object> {
        private final List<Object> original;
        private final OfflineAccessManager manager;

        ConnectionList(List<Object> original, OfflineAccessManager manager) {
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
            injectConnection(e, manager);
            return original.add(e);
        }

        @Override
        public void add(int index, Object element) {
            injectConnection(element, manager);
            original.add(index, element);
        }

        @Override
        public Object set(int index, Object element) {
            injectConnection(element, manager);
            return original.set(index, element);
        }

        @Override
        public Object remove(int index) {
            return original.remove(index);
        }

        @Override
        public boolean remove(Object object) {
            return original.remove(object);
        }

        @Override
        public void clear() {
            original.clear();
        }
    }

    private static final class ConnectionQueue extends AbstractQueue<Object> {
        private final Queue<Object> original;
        private final OfflineAccessManager manager;

        ConnectionQueue(Queue<Object> original, OfflineAccessManager manager) {
            this.original = original;
            this.manager = manager;
        }

        @Override
        public Iterator<Object> iterator() {
            return original.iterator();
        }

        @Override
        public int size() {
            return original.size();
        }

        @Override
        public boolean offer(Object e) {
            injectConnection(e, manager);
            return original.offer(e);
        }

        @Override
        public Object poll() {
            return original.poll();
        }

        @Override
        public Object peek() {
            return original.peek();
        }
    }
}

