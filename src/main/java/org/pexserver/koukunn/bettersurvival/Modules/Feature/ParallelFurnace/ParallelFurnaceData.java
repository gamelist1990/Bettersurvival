package org.pexserver.koukunn.bettersurvival.Modules.Feature.ParallelFurnace;

import org.bukkit.Location;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 並列かまど1台分の状態。
 *
 * 素材・燃料・完成品はそれぞれ 8 スロットの仮想ストレージで、
 * ChestUI を開いている間は UI 側のスロットと同期される。
 * 焼成ライン(ジョブ)は「基本1 + 追加コア数」だけ並列で動く。
 */
public class ParallelFurnaceData {

    /** 素材スロット数 */
    public static final int INPUT_SLOTS = 8;
    /** 燃料スロット数 */
    public static final int FUEL_SLOTS = 8;
    /** UI 上で編集する各ストレージ領域のスロット数 */
    public static final int STORAGE_SLOTS = 8;
    /** 完成品(回収口)スロット数 */
    public static final int OUTPUT_SLOTS = 16;
    /** 追加できる作業コア(かまど)の最大数 */
    public static final int MAX_EXTRA_CORES = 31;
    /** 最大並列ライン数 (基本1 + 追加コア) */
    public static final int MAX_LINES = 1 + MAX_EXTRA_CORES;

    private final UUID owner;
    private final Location location;
    private int extraCores;
    private long fuelBankTicks;
    private long fuelBankMaxTicks;
    private Location overflowChest;

    private final ItemStack[] input = new ItemStack[INPUT_SLOTS];
    private final ItemStack[] fuel = new ItemStack[FUEL_SLOTS];
    private final ItemStack[] output = new ItemStack[OUTPUT_SLOTS];
    private final SmeltJob[] jobs = new SmeltJob[MAX_LINES];

    public ParallelFurnaceData(UUID owner, Location location) {
        this.owner = owner;
        this.location = location;
    }

    public UUID owner() {
        return owner;
    }

    public Location location() {
        return location.clone();
    }

    public int getExtraCores() {
        return extraCores;
    }

    public void setExtraCores(int extraCores) {
        this.extraCores = Math.max(0, Math.min(MAX_EXTRA_CORES, extraCores));
    }

    /** 稼働可能な並列ライン数 */
    public int lines() {
        return 1 + extraCores;
    }

    public long getFuelBankTicks() {
        return fuelBankTicks;
    }

    public void setFuelBankTicks(long fuelBankTicks) {
        this.fuelBankTicks = fuelBankTicks;
    }

    public long getFuelBankMaxTicks() {
        return fuelBankMaxTicks;
    }

    public void setFuelBankMaxTicks(long fuelBankMaxTicks) {
        this.fuelBankMaxTicks = fuelBankMaxTicks;
    }

    public Location getOverflowChest() {
        return overflowChest == null ? null : overflowChest.clone();
    }

    public void setOverflowChest(Location overflowChest) {
        this.overflowChest = overflowChest == null ? null : overflowChest.clone();
    }

    public ItemStack[] input() {
        return input;
    }

    public ItemStack[] fuel() {
        return fuel;
    }

    public ItemStack[] output() {
        return output;
    }

    public SmeltJob[] jobs() {
        return jobs;
    }

    public int activeJobCount() {
        int count = 0;
        for (SmeltJob job : jobs) {
            if (job != null) {
                count++;
            }
        }
        return count;
    }

    /**
     * 内部に保持している全アイテムを列挙する（破壊時ドロップ用）。
     * 素材・燃料・完成品・焼成中の素材に加え、追加した作業コア(かまど)も返却する。
     */
    public List<ItemStack> collectAllItems() {
        List<ItemStack> items = new ArrayList<>();
        collectFrom(input, items);
        collectFrom(fuel, items);
        collectFrom(output, items);
        for (SmeltJob job : jobs) {
            if (job != null && job.source != null) {
                items.add(job.source.clone());
            }
        }
        int cores = extraCores;
        while (cores > 0) {
            int amount = Math.min(64, cores);
            items.add(new ItemStack(org.bukkit.Material.FURNACE, amount));
            cores -= amount;
        }
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

    /** 1ライン分の焼成ジョブ */
    public static final class SmeltJob {
        public ItemStack source;
        public ItemStack result;
        public int cookTimeTicks;
        public int progressTicks;

        public SmeltJob(ItemStack source, ItemStack result, int cookTimeTicks) {
            this.source = source;
            this.result = result;
            this.cookTimeTicks = Math.max(1, cookTimeTicks);
            this.progressTicks = 0;
        }

        public double progressRatio() {
            return Math.min(1.0D, (double) progressTicks / (double) cookTimeTicks);
        }

        public boolean isDone() {
            return progressTicks >= cookTimeTicks;
        }
    }
}
