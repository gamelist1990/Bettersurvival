package org.pexserver.koukunn.bettersurvival.Core.NMS;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Paper 26.2 (Minecraft 1.21.x) 向け {@link NMSBridge} 実装。
 *
 * Mojang マッピングされた NMS クラスへリフレクションでアクセスする。
 * 初期化は初回呼び出し時に遅延実行され、失敗しても例外を外に出さない。
 * 対応する Paper バージョンが変わった場合は、このクラスを複製して
 * クラス名を変更し、{@link NMSApi} の {@code BRIDGE} フィールドを差し替える。
 */
final class Paper262NMSBridge implements NMSBridge {

    /** ClientboundContainerSetSlotPacket の container=-2 で使う PlayerInventory 上のオフハンド index */
    private static final int PLAYER_INVENTORY_OFFHAND_SLOT = 40;

    // ---- 初期化状態 ----
    private volatile boolean initialized;
    private boolean unavailable;

    // ---- リフレクション キャッシュ ----
    private Method getHandle;           // CraftPlayer#getHandle
    private Method sendPacket;          // ServerGamePacketListenerImpl#send(Packet)
    private Method asNmsCopy;           // CraftItemStack.asNMSCopy(ItemStack)
    private Method dataValueCreate;     // SynchedEntityData$DataValue.create(accessor, value)
    private Method getEntityId;         // Entity#getId
    private Method incrementStateId;    // AbstractContainerMenu#incrementStateId
    private Field connectionField;      // ServerPlayer#connection
    private Field containerMenuField;   // ServerPlayer#containerMenu
    private Object livingFlagsAccessor; // LivingEntity.DATA_LIVING_ENTITY_FLAGS
    private Constructor<?> setSlotCtor;       // ClientboundContainerSetSlotPacket
    private Constructor<?> setEntityDataCtor; // ClientboundSetEntityDataPacket
    private Method startUsingItemMethod;      // LivingEntity#startUsingItem(InteractionHand[, boolean])
    private boolean startUsingItemHasForce;
    private Object mainHandEnum;              // InteractionHand.MAIN_HAND
    private Object offHandEnum;               // InteractionHand.OFF_HAND

    Paper262NMSBridge() {
    }

    // ========================= NMSBridge 実装 =========================

    @Override
    public boolean isAvailable() {
        ensureInit();
        return !unavailable;
    }

    @Override
    public boolean sendFakeSlot(Player player, int rawSlot, ItemStack item) {
        if (!isAvailable()) return false;
        try {
            Object handle = getHandle.invoke(player);
            int stateId = resolveStateId(handle);
                int playerInventorySlot = rawSlot == NMSApi.RAW_SLOT_OFFHAND ? PLAYER_INVENTORY_OFFHAND_SLOT : rawSlot;
            Object nmsItem = asNmsCopy.invoke(null, item);
            // containerId 0 だけだと、Paper/クライアント側の同期で player inventory 側に偽スロットが残ることがある。
                // -2 はプレイヤーインベントリの任意スロット更新だが、オフハンドは container raw slot 45 ではなく
                // PlayerInventory index 40 として送る必要がある。
            sendToPlayer(handle, setSlotCtor.newInstance(0, stateId, rawSlot, nmsItem));
                sendToPlayer(handle, setSlotCtor.newInstance(-2, stateId, playerInventorySlot, nmsItem));
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean sendUsingItemFlags(Player player, boolean using, boolean offhand) {
        if (!isAvailable()) return false;
        try {
            Object handle = getHandle.invoke(player);
            byte flags = buildUsingFlags(using, offhand);
            Object dataValue = dataValueCreate.invoke(null, livingFlagsAccessor, Byte.valueOf(flags));
            int entityId = (int) getEntityId.invoke(handle);
            Object packet = setEntityDataCtor.newInstance(entityId, List.of(dataValue));
            sendToPlayer(handle, packet);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean startUsingItem(Player player, boolean offhand, boolean forceUpdate) {
        if (!isAvailable() || startUsingItemMethod == null) return false;
        try {
            Object handle = getHandle.invoke(player);
            Object hand = offhand ? offHandEnum : mainHandEnum;
            if (startUsingItemHasForce) {
                startUsingItemMethod.invoke(handle, hand, forceUpdate);
            } else {
                startUsingItemMethod.invoke(handle, hand);
            }
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    @Override
    public boolean startSpyglassScope(Player player) {
        if (!sendFakeSlot(player, NMSApi.RAW_SLOT_OFFHAND, new ItemStack(Material.SPYGLASS))) {
            return false;
        }
        return sendUsingItemFlags(player, true, true);
    }

    @Override
    public boolean refreshSpyglassScope(Player player) {
        // 維持中に fake slot を再送すると、クライアント側で望遠鏡使用状態が一瞬リセットされて点滅する。
        // slot 偽装は開始時だけ行い、維持は using item flags の再送だけにする。
        return sendUsingItemFlags(player, true, true);
    }

    @Override
    public void restoreSpyglassSlot(Player player) {
        ItemStack actualOffhand = player.getInventory().getItemInOffHand();
        if (actualOffhand == null || actualOffhand.getType().isAir()) {
            actualOffhand = new ItemStack(Material.AIR);
        }
        sendFakeSlot(player, NMSApi.RAW_SLOT_OFFHAND, actualOffhand);
        player.updateInventory();
    }

    @Override
    public void stopSpyglassScope(Player player) {
        sendUsingItemFlags(player, false, false);
        restoreSpyglassSlot(player);
    }

    // ========================= 内部ヘルパー =========================

    private synchronized void ensureInit() {
        if (initialized) return;
        initialized = true;
        try {
            String obcPackage = Bukkit.getServer().getClass().getPackageName();

            // CraftPlayer / ServerPlayer
            Class<?> craftPlayer = Class.forName(obcPackage + ".entity.CraftPlayer");
            getHandle = craftPlayer.getMethod("getHandle");
            Class<?> serverPlayer = getHandle.getReturnType();

            // 接続フィールドとパケット送信メソッド
            connectionField = findField(serverPlayer, "connection");
            Class<?> packetClass = Class.forName("net.minecraft.network.protocol.Packet");
            sendPacket = findMethod(connectionField.getType(), "send", packetClass);

            // CraftItemStack → NMS ItemStack 変換
            Class<?> craftItemStack = Class.forName(obcPackage + ".inventory.CraftItemStack");
            asNmsCopy = craftItemStack.getMethod("asNMSCopy", ItemStack.class);
            Class<?> nmsItemStack = Class.forName("net.minecraft.world.item.ItemStack");

            // ClientboundContainerSetSlotPacket(int containerId, int stateId, int slot, ItemStack)
            setSlotCtor = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket")
                    .getConstructor(int.class, int.class, int.class, nmsItemStack);

            // SynchedEntityData$DataValue.create(EntityDataAccessor, Object)
            Class<?> accessorClass = Class.forName("net.minecraft.network.syncher.EntityDataAccessor");
            Class<?> dataValueClass = Class.forName("net.minecraft.network.syncher.SynchedEntityData$DataValue");
            dataValueCreate = dataValueClass.getMethod("create", accessorClass, Object.class);

            // LivingEntity.DATA_LIVING_ENTITY_FLAGS (static)
            Field flagsField = Class.forName("net.minecraft.world.entity.LivingEntity")
                    .getDeclaredField("DATA_LIVING_ENTITY_FLAGS");
            flagsField.setAccessible(true);
            livingFlagsAccessor = flagsField.get(null);

            // ClientboundSetEntityDataPacket(int entityId, List<DataValue<?>>)
            setEntityDataCtor = Class.forName(
                    "net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket")
                    .getConstructor(int.class, List.class);

            // ServerPlayer#getId / containerMenu / AbstractContainerMenu#incrementStateId
            getEntityId = findMethod(serverPlayer, "getId");
            containerMenuField = findField(serverPlayer, "containerMenu");
            incrementStateId = findMethod(containerMenuField.getType(), "incrementStateId");

            // LivingEntity#startUsingItem(InteractionHand[, forceUpdate]) — 本物の使用開始
            Class<?> interactionHand = Class.forName("net.minecraft.world.InteractionHand");
            mainHandEnum = enumValue(interactionHand, "MAIN_HAND");
            offHandEnum = enumValue(interactionHand, "OFF_HAND");
            try {
                // Paper 拡張: forceUpdate 付きオーバーロード
                startUsingItemMethod = serverPlayer.getMethod("startUsingItem", interactionHand, boolean.class);
                startUsingItemHasForce = true;
            } catch (NoSuchMethodException e) {
                startUsingItemMethod = serverPlayer.getMethod("startUsingItem", interactionHand);
                startUsingItemHasForce = false;
            }

        } catch (Throwable t) {
            unavailable = true;
            Bukkit.getLogger().warning(
                    "[Bettersurvival/NMS] Paper262NMSBridge の初期化に失敗しました。"
                            + "NMS 依存機能 (スコープUI 等) は無効になります: " + t);
        }
    }

    private int resolveStateId(Object serverPlayerHandle) {
        try {
            Object containerMenu = containerMenuField.get(serverPlayerHandle);
            return (int) incrementStateId.invoke(containerMenu);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private static byte buildUsingFlags(boolean using, boolean offhand) {
        if (!using) return 0;
        byte flags = 0x01;
        if (offhand) flags |= 0x02;
        return flags;
    }

    private static Object enumValue(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        throw new IllegalArgumentException(enumClass.getName() + "." + name);
    }

    private void sendToPlayer(Object serverPlayer, Object packet) throws Exception {
        Object connection = connectionField.get(serverPlayer);
        sendPacket.invoke(connection, packet);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
            }
        }
        throw new NoSuchFieldException(type.getName() + "#" + name);
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... params)
            throws NoSuchMethodException {
        try {
            Method method = type.getMethod(name, params);
            method.setAccessible(true);
            return method;
        } catch (NoSuchMethodException ignored) {
        }
        for (Method method : type.getMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != params.length) continue;
            boolean matches = true;
            Class<?>[] actual = method.getParameterTypes();
            for (int i = 0; i < params.length; i++) {
                if (!actual[i].isAssignableFrom(params[i]) && !params[i].isAssignableFrom(actual[i])) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        throw new NoSuchMethodException(type.getName() + "#" + name);
    }
}
