package org.pexserver.koukunn.bettersurvival.Modules.Feature.Recycler;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * リサイクラー1台分の状態。
 *
 * 投入口8スロット・回収口32スロットの仮想ストレージを持ち、
 * UI を開いている間は UI 側のスロットと同期される。
 */
public class RecyclerData {

    /** 投入口スロット数 */
    public static final int INPUT_SLOTS = 8;
    /** 回収口スロット数 */
    public static final int OUTPUT_SLOTS = 32;

    private final UUID owner;
    private final Location location;

    private long recycledCount;
    private long destroyedCount;
    /** 処分アイテムから貯まる経験値バッファ(1.0で1XPオーブ化) */
    private double xpBank;

    private final ItemStack[] input = new ItemStack[INPUT_SLOTS];
    private final ItemStack[] output = new ItemStack[OUTPUT_SLOTS];

    public RecyclerData(UUID owner, Location location) {
        this.owner = owner;
        this.location = location;
    }

    public UUID owner() {
        return owner;
    }

    public Location location() {
        return location.clone();
    }

    public long getRecycledCount() {
        return recycledCount;
    }

    public void setRecycledCount(long recycledCount) {
        this.recycledCount = Math.max(0L, recycledCount);
    }

    public void addRecycled(long amount) {
        this.recycledCount += amount;
    }

    public long getDestroyedCount() {
        return destroyedCount;
    }

    public void setDestroyedCount(long destroyedCount) {
        this.destroyedCount = Math.max(0L, destroyedCount);
    }

    public void addDestroyed(long amount) {
        this.destroyedCount += amount;
    }

    public double getXpBank() {
        return xpBank;
    }

    public void setXpBank(double xpBank) {
        this.xpBank = Math.max(0.0D, xpBank);
    }

    public ItemStack[] input() {
        return input;
    }

    public ItemStack[] output() {
        return output;
    }

    /** 投入口にアイテムが1つでもあるか */
    public boolean hasPendingInput() {
        for (ItemStack stack : input) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                return true;
            }
        }
        return false;
    }

    /** 内部に保持している全アイテムを列挙する（破壊時ドロップ用） */
    public List<ItemStack> collectAllItems() {
        List<ItemStack> items = new ArrayList<>();
        collectFrom(input, items);
        collectFrom(output, items);
        return items;
    }

    private static void collectFrom(ItemStack[] storage, List<ItemStack> into) {
        for (ItemStack stack : storage) {
            if (stack != null && !stack.getType().isAir() && stack.getAmount() > 0) {
                into.add(stack.clone());
            }
        }
    }

    /**
     * ストレージ配列へスタック規則に従ってアイテムを追加する。
     *
     * @return 入りきらなかった残り個数
     */
    public static int addToStorage(ItemStack[] storage, ItemStack stack) {
        if (stack == null || stack.getType().isAir() || stack.getAmount() <= 0) {
            return 0;
        }
        int remaining = stack.getAmount();
        // 既存スタックへ積み増し
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            ItemStack target = storage[i];
            if (target == null || !target.isSimilar(stack)) {
                continue;
            }
            int max = target.getMaxStackSize();
            if (target.getAmount() >= max) {
                continue;
            }
            int move = Math.min(remaining, max - target.getAmount());
            target.setAmount(target.getAmount() + move);
            remaining -= move;
        }
        // 空きスロットへ配置
        for (int i = 0; i < storage.length && remaining > 0; i++) {
            if (storage[i] != null && !storage[i].getType().isAir()) {
                continue;
            }
            ItemStack placed = stack.clone();
            placed.setAmount(Math.min(remaining, stack.getMaxStackSize()));
            storage[i] = placed;
            remaining -= placed.getAmount();
        }
        return remaining;
    }

    /** 指定アイテムを1個以上受け入れられるか */
    public static boolean hasRoomFor(ItemStack[] storage, ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        for (ItemStack target : storage) {
            if (target == null || target.getType().isAir()) {
                return true;
            }
            if (target.isSimilar(stack) && target.getAmount() < target.getMaxStackSize()) {
                return true;
            }
        }
        return false;
    }

    static ItemStack[] copyOf(ItemStack[] storage) {
        ItemStack[] copy = new ItemStack[storage.length];
        for (int i = 0; i < storage.length; i++) {
            copy[i] = storage[i] == null ? null : storage[i].clone();
        }
        return copy;
    }
}
