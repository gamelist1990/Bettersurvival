FloodGate の GitHub リポジトリから、ログイン時の Netty パイプラインインジェクションと GameProfile 書き換え実装を調査しました。

## 1. Netty パイプラインハンドラー追加クラス

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/inject/spigot/SpigotInjector.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/inject/spigot/SpigotInjector.java)

```java
@Singleton
public final class SpigotInjector extends CommonPlatformInjector {
    @Override
    public void inject() throws Exception {
        if (isInjected()) {
            return;
        }

        Object serverConnection = getServerConnection();
        
        for (Field field : serverConnection.getClass().getDeclaredFields()) {
            if (field.getType() == List.class) {
                field.setAccessible(true);

                ParameterizedType parameterType = ((ParameterizedType) field.getGenericType());
                Type listType = parameterType.getActualTypeArguments()[0];

                // ChannelFuture のリストを探す
                if (listType != ChannelFuture.class) {
                    continue;
                }

                injectedFieldName = field.getName();
                // CustomList でラップして新しい接続をインターセプト
                List<?> newList = new CustomList((List<?>) field.get(serverConnection)) {
                    @Override
                    public void onAdd(Object object) {
                        try {
                            injectClient((ChannelFuture) object);
                        } catch (Exception exception) {
                            exception.printStackTrace();
                        }
                    }
                };
                field.set(serverConnection, newList);
                injected = true;
                return;
            }
        }
    }

    // クライアントごとのハンドラー追加
    public void injectClient(ChannelFuture future) {
        future.channel().pipeline().addFirst("floodgate-init", new ChannelInboundHandlerAdapter() {
            @Override
            public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
                Channel channel = (Channel) msg;
                channel.pipeline().addLast("floodgate-injector", new ChannelInboundHandlerAdapter() {
                    @Override
                    public void channelActive(ChannelHandlerContext childCtx) throws Exception {
                        // アドオンを呼び出す (SpigotDataHandler 等)
                        injectAddonsCall(childCtx.channel(), false);
                        addInjectedClient(childCtx.channel());
                        childCtx.pipeline().remove(this);
                        super.channelActive(childCtx);
                    }
                });
                super.channelRead(ctx, msg);
            }
        });
    }
}
```

## 2. ServerboundHelloPacket インターセプト・GameProfile 書き換え

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/addon/data/SpigotDataHandler.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/addon/data/SpigotDataHandler.java)

```java
public final class SpigotDataHandler extends CommonDataHandler {
    
    @Override
    public boolean channelRead(Object packet) throws Exception {
        // ハンドシェイクパケットを処理
        if (ClassNames.HANDSHAKE_PACKET.isInstance(packet)) {
            ctx.pipeline().addAfter("splitter", "floodgate_packet_blocker", blocker);
            networkManager = ctx.channel().pipeline().get("packet_handler");
            handle(packet, getCastedValue(packet, ClassNames.HANDSHAKE_HOST));
            return false;
        }

        // LoginStartPacket をインターセプト
        return !checkAndHandleLogin(packet);
    }

    private boolean checkAndHandleLogin(Object packet) throws Exception {
        if (ClassNames.LOGIN_START_PACKET.isInstance(packet)) {
            Object packetListener = ClassNames.PACKET_LISTENER.get(networkManager);

            // LoginListener の状態確認
            if (!ClassNames.LOGIN_LISTENER.isInstance(packetListener)) {
                ctx.pipeline().remove(this);
                return true;
            }

            // Paper のユーザー名検証を無効化
            if (ClassNames.PAPER_DISABLE_USERNAME_VALIDATION != null) {
                setValue(packetListener, ClassNames.PAPER_DISABLE_USERNAME_VALIDATION, true);
            }

            // プロキシデータでない場合は GameProfile を作成して設定
            if (!proxyData) {
                // デフォルトテクスチャ付き GameProfile を生成
                GameProfile gameProfile = versionSpecificMethods.createGameProfile(
                    player.getCorrectUniqueId(),
                    player.getCorrectUsername(),
                    DEFAULT_TEXTURE_PROPERTY
                );

                // バージョンに応じた処理
                if (ClassNames.IS_PRE_1_20_2) {
                    // 1.20.1 以下
                    Object loginHandler = ClassNames.LOGIN_HANDLER_CONSTRUCTOR.newInstance(packetListener);
                    setValue(packetListener, ClassNames.LOGIN_PROFILE, gameProfile);
                    ClassNames.INIT_UUID.invoke(packetListener);
                    ClassNames.FIRE_LOGIN_EVENTS.invoke(loginHandler);
                } else if (!ClassNames.IS_POST_LOGIN_HANDLER) {
                    // 1.20.2～1.20.4
                    Object loginHandler = ClassNames.LOGIN_HANDLER_CONSTRUCTOR.newInstance(packetListener);
                    ClassNames.FIRE_LOGIN_EVENTS_GAME_PROFILE.invoke(loginHandler, gameProfile);
                } else {
                    // 1.20.4 以降
                    ClassNames.CALL_PLAYER_PRE_LOGIN_EVENTS.invoke(packetListener, gameProfile);
                    ClassNames.START_CLIENT_VERIFICATION.invoke(packetListener, gameProfile);
                }
            }

            ctx.pipeline().remove(this);
            return true;
        }
        return false;
    }
}
```

## 3. GameProfile 作成・プロパティ設定

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/util/SpigotVersionSpecificMethods.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/util/SpigotVersionSpecificMethods.java)

```java
public GameProfile createGameProfile(UUID uuid, String name, Property texturesProperty) {
    if (RECORD_GAME_PROFILE_CONSTRUCTOR != null && IMMUTABLE_PROPERTY_MAP_CONSTRUCTOR != null) {
        if (texturesProperty != null) {
            Map<String, Property> properties = new HashMap<>();
            properties.put("textures", texturesProperty);
            Object multimap = ReflectionUtils.invoke(null, MULTIMAP_FROM_MAP, properties);
            return ReflectionUtils.newInstanceOrThrow(RECORD_GAME_PROFILE_CONSTRUCTOR, uuid,
                    name,
                    ReflectionUtils.newInstanceOrThrow(IMMUTABLE_PROPERTY_MAP_CONSTRUCTOR, multimap));
        }
    }
    GameProfile profile = new GameProfile(uuid, name);
    if (texturesProperty != null) {
        profile.getProperties().put("textures", texturesProperty);
    }
    return profile;
}
```

## 4. NMS/Reflection パターン（ClassNames）

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/util/ClassNames.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/util/ClassNames.java)

```java
// ログインパケットクラス特定
LOGIN_START_PACKET = getClassOrFallback(
    "net.minecraft.network.protocol.login.ServerboundHelloPacket",  // 1.20.2+
    "net.minecraft.network.protocol.login.PacketLoginInStart",      // 1.19-1.20.1
    nmsPackage + "PacketLoginInStart"                               // Spigot
);

// LoginListener クラス特定
LOGIN_LISTENER = getClassOrFallback(
    "net.minecraft.server.network.ServerLoginPacketListenerImpl",    // 1.20+
    "net.minecraft.server.network.LoginListener",                   // 1.19
    nmsPackage + "LoginListener"                                    // Spigot
);

// GameProfile フィールド取得
LOGIN_PROFILE = getFieldOfType(LOGIN_LISTENER, GameProfile.class);

// Paper の username validation 無効化フィールド
PAPER_DISABLE_USERNAME_VALIDATION = getField(LOGIN_LISTENER,
    "iKnowThisMayNotBeTheBestIdeaButPleaseDisableUsernameValidation");
```

## 5. サーバー起動時の注入トリガー

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/SpigotPlugin.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/SpigotPlugin.java)

```java
@Override
public void onLoad() {
    injector = Guice.createInjector(
        new ServerCommonModule(getDataFolder().toPath()),
        new SpigotPlatformModule(this)
    );
    platform = injector.getInstance(FloodgatePlatform.class);
}

@Override
public void onEnable() {
    // パイプラインインジェクション実行
    platform.enable(
        new SpigotCommandModule(this),
        new SpigotAddonModule(),  // ← SpigotDataAddon がここで登録される
        new PluginMessageModule(),
        new SpigotListenerModule()
    );
}
```

## 6. パイプライン追加時のハンドラー登録

**ファイル**: [spigot/src/main/java/org/geysermc/floodgate/addon/data/SpigotDataAddon.java](https://github.com/GeyserMC/Floodgate/tree/master/spigot/src/main/java/org/geysermc/floodgate/addon/data/SpigotDataAddon.java)

```java
@Override
public void onInject(Channel channel, boolean toServer) {
    channel.pipeline().addBefore(
        packetHandlerName, "floodgate_data_handler",
        new SpigotDataHandler(handshakeHandler, config, kickMessageAttribute, versionSpecificMethods)
    );
}
```

---

**ポイント**:
- **CustomList ラップ**: ServerConnection の ChannelFuture リストを Reflection でインターセプト
- **パケット判定**: Reflection で型を判定して LoginStartPacket のみ処理
- **GameProfile 上書き**: LoginListener の `profile` フィールドに新しい GameProfile をセット
- **バージョン対応**: 1.20.2, 1.20.4 で異なる initUUID/fireEvents の呼び出しパターン