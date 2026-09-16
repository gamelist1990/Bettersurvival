package org.pexserver.koukunn.bettersurvival.Core.NMS;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Paper 26.2 向け {@link NMSBridge} 実装。
 *
 * paperweight-userdev の Mojang mappings を直接利用し、reflection を一切使わない。
 * NMS シグネチャが変わった場合はコンパイル時に検知される。
 */
final class Paper262NMSBridge implements NMSBridge {

    /** ClientboundContainerSetSlotPacket の container=-2 で使う PlayerInventory 上のオフハンド index */
    private static final int PLAYER_INVENTORY_OFFHAND_SLOT = 40;
    private static final EntityDataAccessor<Byte> LIVING_FLAGS = LivingEntityAccessor.flagsAccessor();

    Paper262NMSBridge() {
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public boolean sendFakeSlot(Player player, int rawSlot, ItemStack item) {
        try {
            ServerPlayer handle = handle(player);
            int stateId = handle.containerMenu.incrementStateId();
            int playerInventorySlot = rawSlot == NMSApi.RAW_SLOT_OFFHAND
                    ? PLAYER_INVENTORY_OFFHAND_SLOT
                    : rawSlot;
            net.minecraft.world.item.ItemStack nmsItem = CraftItemStack.asNMSCopy(item);

            // containerId 0 と player inventory (-2) の両方へ送ることで、
            // Paper/クライアント側の同期後も偽装スロットを安定して維持する。
            send(handle, new ClientboundContainerSetSlotPacket(0, stateId, rawSlot, nmsItem));
            send(handle, new ClientboundContainerSetSlotPacket(-2, stateId, playerInventorySlot, nmsItem));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean sendUsingItemFlags(Player player, boolean using, boolean offhand) {
        try {
            ServerPlayer handle = handle(player);
            byte flags = buildUsingFlags(using, offhand);
            SynchedEntityData.DataValue<Byte> dataValue = SynchedEntityData.DataValue.create(LIVING_FLAGS, flags);
            send(handle, new ClientboundSetEntityDataPacket(handle.getId(), List.of(dataValue)));
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public boolean startUsingItem(Player player, boolean offhand, boolean forceUpdate) {
        try {
            ServerPlayer handle = handle(player);
            InteractionHand hand = offhand ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            handle.startUsingItem(hand, forceUpdate);
            return true;
        } catch (Throwable ignored) {
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
        // fake slot は開始時だけ送信し、維持中は entity metadata の使用中フラグだけ再送する。
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

    private static ServerPlayer handle(Player player) {
        return ((CraftPlayer) player).getHandle();
    }

    private static void send(ServerPlayer player, Packet<?> packet) {
        player.connection.send(packet);
    }

    private static byte buildUsingFlags(boolean using, boolean offhand) {
        if (!using) return 0;
        byte flags = 0x01;
        if (offhand) flags |= 0x02;
        return flags;
    }

    /**
     * LivingEntity の protected metadata accessor を reflection 無しで型安全に参照するための
     * コンパイル時アクセサ。インスタンス化はしない。
     */
    private abstract static class LivingEntityAccessor extends LivingEntity {
        private LivingEntityAccessor(EntityType<? extends LivingEntity> type, Level level) {
            super(type, level);
        }

        private static EntityDataAccessor<Byte> flagsAccessor() {
            return DATA_LIVING_ENTITY_FLAGS;
        }
    }
}
