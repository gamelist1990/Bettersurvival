package org.pexserver.koukunn.bettersurvival.Modules.Feature.Protect;

import org.bukkit.DyeColor;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.TileState;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * BlockData では保持されない BlockEntity の重要データを永続化する。
 *
 * Paper の公開APIだけで安全に扱える Container inventory / TileState PDC /
 * Sign の両面テキスト・色・発光・wax を対象にする。
 */
final class ProtectBlockSnapshot {
    private static final int MAGIC = 0x42535031; // BSP1
    private static final int VERSION = 1;
    private static final int FLAG_INVENTORY = 1;
    private static final int FLAG_PDC = 1 << 1;
    private static final int FLAG_SIGN = 1 << 2;

    private ProtectBlockSnapshot() {
    }

    static byte[] capture(BlockState state) {
        if (state == null) return null;

        boolean hasInventory = state instanceof InventoryHolder;
        boolean hasPdc = state instanceof TileState;
        boolean hasSign = state instanceof Sign;
        if (!hasInventory && !hasPdc && !hasSign) return null;

        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream out = new DataOutputStream(bytes)) {
                out.writeInt(MAGIC);
                out.writeInt(VERSION);
                int flags = (hasInventory ? FLAG_INVENTORY : 0)
                        | (hasPdc ? FLAG_PDC : 0)
                        | (hasSign ? FLAG_SIGN : 0);
                out.writeInt(flags);

                if (hasInventory) {
                    Inventory inventory = ((InventoryHolder) state).getInventory();
                    byte[] inventoryBytes = ItemStack.serializeItemsAsBytes(inventory.getContents());
                    writeBytes(out, inventoryBytes);
                }

                if (hasPdc) {
                    byte[] pdc = ((TileState) state).getPersistentDataContainer().serializeToBytes();
                    writeBytes(out, pdc);
                }

                if (hasSign) {
                    Sign sign = (Sign) state;
                    out.writeBoolean(sign.isWaxed());
                    writeSignSide(out, sign.getSide(Side.FRONT));
                    writeSignSide(out, sign.getSide(Side.BACK));
                }
            }
            return bytes.toByteArray();
        } catch (Throwable ignored) {
            return null;
        }
    }

    static void apply(BlockState state, byte[] data) {
        if (state == null || data == null || data.length == 0) return;

        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            if (in.readInt() != MAGIC) return;
            int version = in.readInt();
            if (version != VERSION) return;
            int flags = in.readInt();

            if ((flags & FLAG_INVENTORY) != 0) {
                byte[] inventoryBytes = readBytes(in);
                if (state instanceof InventoryHolder holder && inventoryBytes != null) {
                    ItemStack[] items = ItemStack.deserializeItemsFromBytes(inventoryBytes);
                    Inventory inventory = holder.getInventory();
                    for (int slot = 0; slot < inventory.getSize(); slot++) {
                        inventory.setItem(slot, slot < items.length ? normalize(items[slot]) : null);
                    }
                }
            }

            if ((flags & FLAG_PDC) != 0) {
                byte[] pdc = readBytes(in);
                if (state instanceof TileState tile && pdc != null) {
                    tile.getPersistentDataContainer().readFromBytes(pdc, true);
                }
            }

            if ((flags & FLAG_SIGN) != 0) {
                boolean waxed = in.readBoolean();
                if (state instanceof Sign sign) {
                    sign.setWaxed(waxed);
                    readSignSide(in, sign.getSide(Side.FRONT));
                    readSignSide(in, sign.getSide(Side.BACK));
                } else {
                    skipSignSide(in);
                    skipSignSide(in);
                }
            }
        } catch (Throwable ignored) {
            // Snapshot破損でrollback全体を止めない。BlockDataの復元は呼び出し側で継続する。
        }
    }

    private static void writeSignSide(DataOutputStream out, SignSide side) throws IOException {
        String[] lines = side.getLines();
        out.writeInt(lines.length);
        for (String line : lines) out.writeUTF(line == null ? "" : line);
        DyeColor color = side.getColor();
        out.writeUTF(color == null ? "" : color.name());
        out.writeBoolean(side.isGlowingText());
    }

    private static void readSignSide(DataInputStream in, SignSide side) throws IOException {
        int count = Math.max(0, Math.min(16, in.readInt()));
        for (int i = 0; i < count; i++) {
            String line = in.readUTF();
            if (i < 4) side.setLine(i, line);
        }
        String colorName = in.readUTF();
        if (!colorName.isBlank()) {
            try {
                side.setColor(DyeColor.valueOf(colorName));
            } catch (IllegalArgumentException ignored) {
            }
        }
        side.setGlowingText(in.readBoolean());
    }

    private static void skipSignSide(DataInputStream in) throws IOException {
        int count = Math.max(0, Math.min(16, in.readInt()));
        for (int i = 0; i < count; i++) in.readUTF();
        in.readUTF();
        in.readBoolean();
    }

    private static void writeBytes(DataOutputStream out, byte[] bytes) throws IOException {
        if (bytes == null) {
            out.writeInt(-1);
            return;
        }
        out.writeInt(bytes.length);
        out.write(bytes);
    }

    private static byte[] readBytes(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0) return null;
        if (length > 64 * 1024 * 1024) throw new IOException("snapshot payload too large");
        return in.readNBytes(length);
    }

    private static ItemStack normalize(ItemStack item) {
        if (item == null || item.getType().isAir() || item.getAmount() <= 0) return null;
        return item;
    }
}
