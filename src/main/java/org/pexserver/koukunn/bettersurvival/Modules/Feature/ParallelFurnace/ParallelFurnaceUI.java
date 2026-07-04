package org.pexserver.koukunn.bettersurvival.Modules.Feature.ParallelFurnace;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 並列かまどの作業コア UI (54スロット)。
 *
 * <pre>
 * Row0: [情報] [枠] [状態✦] [枠] [－コア] [コア数] [＋コア] [枠] [搬出チェスト]
 * Row1: [≫素材投入口]  [素材×8 (編集可)]
 * Row2: [≫焼成ライン]  [ライン進捗×7 (アニメーション)] [燃料ゲージ]
 * Row3: [≫燃料投入口]  [燃料×8 (編集可)]
 * Row4: [≪回収口]      [完成品×8 (編集可)]
 * Row5: [≪回収口(続)]  [完成品×8 (編集可)]
 * </pre>
 *
 * かまど1台につき共有 {@link Inventory} を1つだけ持ち、複数プレイヤーが
 * 同じインベントリを同時に開く（= アイテム操作はサーバー標準の仕組みで
 * 全員へリアルタイム同期される）。ボタン処理・編集可否の制御は
 * {@link ParallelFurnaceModule} 側のイベントリスナーが行う。
 */
public class ParallelFurnaceUI implements InventoryHolder {

    static final int SIZE = 54;

    static final int SLOT_INFO = 0;
    static final int SLOT_STATUS = 2;
    static final int SLOT_CORE_REMOVE = 4;
    static final int SLOT_CORE_COUNT = 5;
    static final int SLOT_CORE_ADD = 6;
    static final int SLOT_OVERFLOW = 8;

    static final int SLOT_INPUT_LABEL = 9;
    static final int INPUT_START = 10;   // 10..17 (8)

    static final int SLOT_PROG_LABEL = 18;
    static final int PROG_START = 19;    // 19..25 (7)
    static final int PROG_SLOTS = 7;
    static final int SLOT_FUEL_GAUGE = 26;

    static final int SLOT_FUEL_LABEL = 27;
    static final int FUEL_START = 28;    // 28..35 (8)

    static final int SLOT_OUTPUT_LABEL = 36;
    static final int OUT_START = 37;     // 37..44 (8) -> output[0..7]

    static final int SLOT_OUTPUT_LABEL2 = 45;
    static final int OUT2_START = 46;    // 46..53 (8) -> output[8..15]

    private static final int OUT_ROW_SLOTS = 8;
    private static final int[] FRAME_SLOTS = {1, 3, 7};
    private static final String[] FLAME_FRAMES = {"§6✦", "§c✦", "§e✦"};
    private static final Set<Integer> EDITABLE_SLOTS = buildEditableSlots();

    private final ParallelFurnaceModule module;
    private final ParallelFurnaceData data;
    private final String key;
    private final Inventory inventory;

    public ParallelFurnaceUI(ParallelFurnaceModule module, ParallelFurnaceData data, String key) {
        this.module = module;
        this.data = data;
        this.key = key;
        this.inventory = Bukkit.createInventory(this, SIZE, ComponentUtils.legacy("§8⚒ §6並列かまど §8- §7作業コア"));
        renderStatic();
        syncToInventory();
        renderDynamic(0);
    }

    private static Set<Integer> buildEditableSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < ParallelFurnaceData.INPUT_SLOTS; i++) {
            slots.add(INPUT_START + i);
        }
        for (int i = 0; i < ParallelFurnaceData.FUEL_SLOTS; i++) {
            slots.add(FUEL_START + i);
        }
        for (int i = 0; i < OUT_ROW_SLOTS; i++) {
            slots.add(OUT_START + i);
            slots.add(OUT2_START + i);
        }
        return slots;
    }

    public String key() {
        return key;
    }

    public ParallelFurnaceData data() {
        return data;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public boolean isEditableSlot(int slot) {
        return EDITABLE_SLOTS.contains(slot);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public boolean hasViewers() {
        return !inventory.getViewers().isEmpty();
    }

    /** 全ビューアーを閉じる（撤去・停止時） */
    public void closeAll() {
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) {
            viewer.closeInventory();
        }
    }

    // ================= 同期 =================

    /** UI の編集可能スロット → 仮想ストレージ */
    public void syncFromInventory() {
        readRegion(inventory, INPUT_START, data.input(), 0, ParallelFurnaceData.INPUT_SLOTS);
        readRegion(inventory, FUEL_START, data.fuel(), 0, ParallelFurnaceData.FUEL_SLOTS);
        readRegion(inventory, OUT_START, data.output(), 0, OUT_ROW_SLOTS);
        readRegion(inventory, OUT2_START, data.output(), OUT_ROW_SLOTS, OUT_ROW_SLOTS);
    }

    /** 仮想ストレージ → UI の編集可能スロット */
    public void syncToInventory() {
        writeRegion(inventory, INPUT_START, data.input(), 0, ParallelFurnaceData.INPUT_SLOTS);
        writeRegion(inventory, FUEL_START, data.fuel(), 0, ParallelFurnaceData.FUEL_SLOTS);
        writeRegion(inventory, OUT_START, data.output(), 0, OUT_ROW_SLOTS);
        writeRegion(inventory, OUT2_START, data.output(), OUT_ROW_SLOTS, OUT_ROW_SLOTS);
    }

    private static void readRegion(Inventory inv, int invStart, ItemStack[] storage, int storageOffset, int count) {
        for (int i = 0; i < count; i++) {
            int invSlot = invStart + i;
            int storageSlot = storageOffset + i;
            if (invSlot >= SIZE || storageSlot >= storage.length) {
                return;
            }
            ItemStack stack = inv.getItem(invSlot);
            storage[storageSlot] =
                    stack == null || stack.getType().isAir() || stack.getAmount() <= 0 ? null : stack.clone();
        }
    }

    private static void writeRegion(Inventory inv, int invStart, ItemStack[] storage, int storageOffset, int count) {
        for (int i = 0; i < count; i++) {
            int invSlot = invStart + i;
            int storageSlot = storageOffset + i;
            if (invSlot >= SIZE || storageSlot >= storage.length) {
                return;
            }
            ItemStack stack = storage[storageSlot];
            inv.setItem(invSlot, stack == null ? null : stack.clone());
        }
    }

    /** ホッパー搬入用: UI 上の該当領域に受け入れ余地があるか */
    public boolean hasRoomLive(boolean intoFuel, ItemStack single) {
        return ParallelFurnaceData.hasRoomFor(snapshotRegion(intoFuel), single);
    }

    /** ホッパー搬入用: セッションが生きている間はライブインベントリへ直接入れる */
    public boolean insertLive(boolean intoFuel, ItemStack single) {
        ItemStack[] region = snapshotRegion(intoFuel);
        if (ParallelFurnaceData.addToStorage(region, single.clone()) > 0) {
            return false;
        }
        writeRegion(inventory, intoFuel ? FUEL_START : INPUT_START, region, 0, region.length);
        return true;
    }

    private ItemStack[] snapshotRegion(boolean intoFuel) {
        int count = intoFuel ? ParallelFurnaceData.FUEL_SLOTS : ParallelFurnaceData.INPUT_SLOTS;
        ItemStack[] region = new ItemStack[count];
        readRegion(inventory, intoFuel ? FUEL_START : INPUT_START, region, 0, count);
        return region;
    }

    /**
     * プレイヤーインベントリからのシフトクリック搬入。
     * 焼ける物と焼けない物は素材投入口へ、燃料にしかならない物は燃料投入口へ振り分ける。
     *
     * @return 実際に移動できた個数
     */
    public int acceptShiftClick(ItemStack source) {
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return 0;
        }
        boolean smeltable = module.lookupRecipe(source) != null;
        boolean intoFuel = !smeltable && module.burnTicksOf(source.getType()) > 0;
        ItemStack[] region = snapshotRegion(intoFuel);
        int leftover = ParallelFurnaceData.addToStorage(region, source.clone());
        int moved = source.getAmount() - leftover;
        if (moved > 0) {
            writeRegion(inventory, intoFuel ? FUEL_START : INPUT_START, region, 0, region.length);
        }
        return moved;
    }

    // ================= 描画 =================

    private void setButton(int slot, String name, Material icon, String lore) {
        setButton(slot, name, icon, legacyLore(lore));
    }

    private void setButton(int slot, String name, Material icon, List<Component> lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ComponentUtils.setDisplayName(meta, name);
            if (lore != null && !lore.isEmpty()) {
                ComponentUtils.setLoreComponents(meta, lore);
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private static List<Component> legacyLore(String lore) {
        List<Component> lines = new ArrayList<>();
        if (lore == null || lore.isEmpty()) {
            return lines;
        }
        for (String line : lore.split("\\n", -1)) {
            lines.add(ComponentUtils.legacy(line));
        }
        return lines;
    }

    private void renderStatic() {
        for (int slot : FRAME_SLOTS) {
            setButton(slot, "§7 ", Material.ORANGE_STAINED_GLASS_PANE, "");
        }
        setButton(SLOT_INPUT_LABEL, "§e≫ 素材投入口", Material.HOPPER,
                "§e§l→ 右の8マスに焼きたい物を入れる"
                        + "\n§7焼けるかは自動判定され、焼けない物は"
                        + "\n§7搬出チェスト(右上で設定)へ送られます"
                        + "\n§8上ホッパーからの自動搬入も可");
        setButton(SLOT_PROG_LABEL, "§b≫ 焼成ライン", Material.CLOCK,
                "§7各ラインの焼成状況がここに並びます");
        setButton(SLOT_FUEL_LABEL, "§6≫ 燃料投入口", Material.LAVA_BUCKET,
                "§6§l→ 右の8マスに燃料を入れる"
                        + "\n§7石炭 / 木材 / 溶岩バケツ など通常かまどと同じ"
                        + "\n§7全ライン共通の燃料タンクに補給されます"
                        + "\n§8横ホッパーからの自動搬入も可");
        setButton(SLOT_OUTPUT_LABEL, "§a≪ 回収口", Material.CHEST_MINECART,
                "§a§l← 焼き上がった完成品はここに並ぶ"
                        + "\n§7この2段(16マス)から取り出してください"
                        + "\n§8下ホッパーで自動回収も可");
        setButton(SLOT_OUTPUT_LABEL2, "§a≪ 回収口 (続き)", Material.CHEST_MINECART,
                "§a§l← 完成品の続きがここに並ぶ"
                        + "\n§8下ホッパーで自動回収も可");
    }

    /** 動的部分の再描画。frame はアニメーションのコマ番号 */
    public void renderDynamic(int frame) {
        renderInfo();
        renderStatus(frame);
        renderOverflowButton();
        renderCores();
        renderProgress(frame);
        renderFuelGauge();
    }

    private void renderInfo() {
        Location loc = data.location();
        Location overflow = data.getOverflowChest();
        String overflowText = overflow == null || overflow.getWorld() == null
                ? "§c未設定"
                : "§a" + overflow.getBlockX() + ", " + overflow.getBlockY() + ", " + overflow.getBlockZ();
        String ownerName = Bukkit.getOfflinePlayer(data.owner()).getName();
        setButton(SLOT_INFO, "§6並列かまど 情報", Material.BOOK,
                "§7所有者: §f" + (ownerName == null ? "不明" : ownerName)
                        + "\n§7並列数: §f" + data.lines() + " §7ライン (最大" + ParallelFurnaceData.MAX_LINES + ")"
                        + "\n§7稼働中: §f" + data.activeJobCount() + " §7ライン"
                        + "\n§7搬出チェスト: " + overflowText
                        + "\n§7座標: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                        + "\n§8複数人で同時に開いても内容は同期されます");
    }

    private void renderStatus(int frame) {
        int active = data.activeJobCount();
        if (active > 0) {
            String flame = FLAME_FRAMES[Math.floorMod(frame, FLAME_FRAMES.length)];
            Material icon = frame % 2 == 0 ? Material.FIRE_CHARGE : Material.BLAZE_POWDER;
            setButton(SLOT_STATUS, flame + " §c稼働中 §7- §f" + active + "§7/§f" + data.lines() + " §7ライン " + flame,
                    icon, "§7並列で焼成しています…");
        } else if (data.getFuelBankTicks() <= 0 && !hasAnyFuel()) {
            setButton(SLOT_STATUS, "§8● 燃料切れ", Material.CAMPFIRE, "§7下の§6燃料投入口§7に燃料を入れてください");
        } else {
            setButton(SLOT_STATUS, "§7● 待機中", Material.FURNACE, "§7上の§e素材投入口§7に素材を入れると焼き始めます");
        }
    }

    private boolean hasAnyFuel() {
        for (ItemStack stack : data.fuel()) {
            if (stack != null && module.burnTicksOf(stack.getType()) > 0) {
                return true;
            }
        }
        return false;
    }

    private void renderOverflowButton() {
        Location overflow = data.getOverflowChest();
        if (overflow != null && overflow.getWorld() != null) {
            setButton(SLOT_OVERFLOW, "§a搬出チェスト: 設定済み", Material.ENDER_CHEST,
                    "§7焼けない素材の送り先:\n§f" + overflow.getWorld().getName() + " §7("
                            + overflow.getBlockX() + ", " + overflow.getBlockY() + ", " + overflow.getBlockZ() + ")"
                            + "\n§cクリックで解除 §7(解除後にもう一度クリックで再設定)");
        } else {
            setButton(SLOT_OVERFLOW, "§e搬出チェストを設定", Material.ENDER_EYE,
                    "§7焼けない素材を自動で送るチェストを指定します"
                            + "\n§7クリック後、§f" + ParallelFurnaceModule.OVERFLOW_MAX_DISTANCE + "ブロック以内§7の"
                            + "\n§7チェストを右クリックしてください"
                            + "\n§c他人の土地保護内 / 他人のロック済みチェストは不可");
        }
    }

    private void renderCores() {
        setButton(SLOT_CORE_REMOVE, "§c－ コア取り外し", Material.RED_STAINED_GLASS_PANE,
                data.getExtraCores() > 0
                        ? "§7クリックでかまどを1つ取り外して返却\n§7(並列数 -1)"
                        : "§8取り外せる追加コアがありません");
        setButton(SLOT_CORE_ADD, "§a＋ コア追加", Material.LIME_STAINED_GLASS_PANE,
                data.getExtraCores() < ParallelFurnaceData.MAX_EXTRA_CORES
                        ? "§7かまどを持ってクリック、または手持ちの\n§7かまどを1つ消費して並列数 +1\n§7(最大" + ParallelFurnaceData.MAX_LINES + "並列)"
                        : "§8これ以上コアを追加できません");
        // コア数はかまどアイテムのスタック数で直感的に表示 (表示専用)
        ItemStack counter = new ItemStack(Material.FURNACE, Math.max(1, Math.min(64, data.lines())));
        ItemMeta meta = counter.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§6作業コア: §f" + data.lines() + " §7並列");
        ComponentUtils.setLore(meta,
                "§7基本1 + 追加コア" + data.getExtraCores() + "個",
                "§7左右のボタンで増減できます");
        counter.setItemMeta(meta);
        inventory.setItem(SLOT_CORE_COUNT, counter);
    }

    private void renderProgress(int frame) {
        ParallelFurnaceData.SmeltJob[] jobs = data.jobs();
        int lines = data.lines();
        List<Integer> activeLines = new ArrayList<>();
        for (int i = 0; i < lines; i++) {
            if (jobs[i] != null) {
                activeLines.add(i);
            }
        }
        boolean overflowSummary = activeLines.size() > PROG_SLOTS;
        int jobSlots = overflowSummary ? PROG_SLOTS - 1 : Math.min(activeLines.size(), PROG_SLOTS);

        setButton(SLOT_PROG_LABEL, "§b≫ 焼成ライン §7(" + activeLines.size() + "/" + lines + " 稼働中)", Material.CLOCK,
                "§7各ラインの焼成状況がここに並びます");

        int slot = PROG_START;
        for (int k = 0; k < jobSlots; k++, slot++) {
            int lineIndex = activeLines.get(k);
            ParallelFurnaceData.SmeltJob job = jobs[lineIndex];
            String flame = FLAME_FRAMES[Math.floorMod(frame + k, FLAME_FRAMES.length)];
            int percent = (int) Math.round(job.progressRatio() * 100.0D);
            String name = job.isDone()
                    ? "§aライン" + (lineIndex + 1) + ": 完成待ち (回収口が満杯)"
                    : flame + " §fライン" + (lineIndex + 1) + ": §e焼成中 §f" + percent + "%";
                List<Component> lore = new ArrayList<>();
                lore.add(ComponentUtils.legacy("§7" + progressBar(job.progressRatio())));
                lore.add(ComponentUtils.legacy("§7焼成中: §f").append(materialNameComponent(job.source)));
                lore.add(ComponentUtils.legacy("§7完成品: §f").append(materialNameComponent(job.result)));
            setButton(slot, name, job.source == null ? Material.FIRE_CHARGE : job.source.getType(),
                    lore);
        }
        if (overflowSummary) {
            int remaining = activeLines.size() - jobSlots;
            double totalRatio = 0.0D;
            for (int k = jobSlots; k < activeLines.size(); k++) {
                totalRatio += jobs[activeLines.get(k)].progressRatio();
            }
            String flame = FLAME_FRAMES[Math.floorMod(frame, FLAME_FRAMES.length)];
            setButton(slot, flame + " §f他 §e" + remaining + " §fライン稼働中", Material.BLAST_FURNACE,
                    "§7平均進捗: " + progressBar(totalRatio / remaining)
                            + "\n§7表示しきれないラインの合計です");
            slot++;
        }
        int idleLines = lines - activeLines.size();
        for (; slot < PROG_START + PROG_SLOTS; slot++) {
            if (idleLines > 0) {
                setButton(slot, "§7ライン待機中", Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                        "§7素材待ちのラインがあります");
                idleLines--;
            } else {
                setButton(slot, "§8未解放", Material.GRAY_STAINED_GLASS_PANE,
                        "§7コアを追加すると並列数が増えます");
            }
        }
    }

    private void renderFuelGauge() {
        long max = Math.max(1L, data.getFuelBankMaxTicks());
        double ratio = Math.max(0.0D, Math.min(1.0D, (double) data.getFuelBankTicks() / (double) max));
        boolean hasFuel = data.getFuelBankTicks() > 0;
        setButton(SLOT_FUEL_GAUGE,
                hasFuel ? "§6燃料タンク: §f残り約" + Math.max(1L, data.getFuelBankTicks() / 20L) + "秒" : "§8燃料タンク: 空",
                hasFuel ? Material.LAVA_BUCKET : Material.BUCKET,
                "§7" + progressBar(ratio) + "\n§7下の§6燃料投入口§7から自動補給されます");
    }

    private static String progressBar(double ratio) {
        int filled = (int) Math.round(Math.max(0.0D, Math.min(1.0D, ratio)) * 10.0D);
        StringBuilder bar = new StringBuilder();
        bar.append(ratio >= 0.5D ? "§a" : ratio >= 0.2D ? "§e" : "§c");
        bar.append("▰".repeat(filled));
        bar.append("§8");
        bar.append("▱".repeat(10 - filled));
        return bar.toString();
    }

    private static Component materialNameComponent(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return ComponentUtils.legacy("不明");
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null && meta.displayName() != null) {
            return meta.displayName();
        }
        return ItemNameUtil.localizedComponent(stack);
    }

    // ================= クリック処理 (module のリスナーから呼ばれる) =================

    void handleButtonClick(Player player, int slot) {
        switch (slot) {
            case SLOT_OVERFLOW -> handleOverflowClick(player);
            case SLOT_CORE_ADD -> handleCoreAdd(player);
            case SLOT_CORE_REMOVE -> handleCoreRemove(player);
            default -> {
            }
        }
    }

    private void handleOverflowClick(Player player) {
        if (data.getOverflowChest() != null) {
            data.setOverflowChest(null);
            module.markDirty();
            player.sendMessage("§6[並列かまど]§r §e搬出チェストを解除しました。もう一度クリックすると再設定できます");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.0F);
            renderOverflowButton();
            renderInfo();
        } else {
            module.beginOverflowSelection(player, key);
        }
    }

    private void handleCoreAdd(Player player) {
        if (data.getExtraCores() >= ParallelFurnaceData.MAX_EXTRA_CORES) {
            player.sendMessage("§c[並列かまど] これ以上コアを追加できません (最大" + ParallelFurnaceData.MAX_LINES + "並列)");
            return;
        }
        if (!consumePlainFurnace(player)) {
            player.sendMessage("§c[並列かまど] かまどを持ってクリックするか、手持ちにかまどが必要です");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.7F);
            return;
        }
        syncFromInventory();
        data.setExtraCores(data.getExtraCores() + 1);
        module.markDirty();
        player.sendMessage("§a[並列かまど] 作業コアを追加しました §7(現在 " + data.lines() + " 並列)");
        player.playSound(player.getLocation(), Sound.BLOCK_FURNACE_FIRE_CRACKLE, 0.8F, 1.2F);
        renderCores();
        renderProgress(0);
        renderInfo();
    }

    private void handleCoreRemove(Player player) {
        if (data.getExtraCores() <= 0) {
            player.sendMessage("§c[並列かまど] 取り外せる追加コアがありません");
            return;
        }
        syncFromInventory();
        data.setExtraCores(data.getExtraCores() - 1);
        giveOrDrop(player, new ItemStack(Material.FURNACE));
        module.markDirty();
        player.sendMessage("§e[並列かまど] 作業コアを取り外しました §7(現在 " + data.lines() + " 並列)");
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4F, 1.4F);
        renderCores();
        renderProgress(0);
        renderInfo();
    }

    /** カーソル → 手持ちインベントリの順で通常のかまどを1個消費する */
    private boolean consumePlainFurnace(Player player) {
        ItemStack cursor = player.getItemOnCursor();
        if (module.isPlainFurnace(cursor)) {
            if (cursor.getAmount() <= 1) {
                player.setItemOnCursor(null);
            } else {
                cursor.setAmount(cursor.getAmount() - 1);
                player.setItemOnCursor(cursor);
            }
            return true;
        }
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack stack = contents[i];
            if (!module.isPlainFurnace(stack)) {
                continue;
            }
            if (stack.getAmount() <= 1) {
                player.getInventory().setItem(i, null);
            } else {
                stack.setAmount(stack.getAmount() - 1);
                player.getInventory().setItem(i, stack);
            }
            return true;
        }
        return false;
    }

    private static void giveOrDrop(Player player, ItemStack stack) {
        for (ItemStack leftover : player.getInventory().addItem(stack).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }
}
