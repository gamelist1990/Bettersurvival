package org.pexserver.koukunn.bettersurvival.Modules.Feature.Recycler;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * リサイクラーの UI (54スロット)。
 *
 * <pre>
 * Row0: [情報] [枠] [状態⚙] [枠] [統計] [枠] [還元率] [枠] [使い方]
 * Row1: [≫投入口]      [投入×8 (編集可)]
 * Row2: [≪回収口]      [回収×8 (編集可)]
 * Row3: [≪回収口(続)]  [回収×8 (編集可)]
 * Row4: [≪回収口(続)]  [回収×8 (編集可)]
 * Row5: [≪回収口(続)]  [回収×8 (編集可)]
 * </pre>
 *
 * リサイクラー1台につき共有 {@link Inventory} を1つだけ持ち、複数プレイヤーが
 * 同じインベントリを同時に開く。ボタン処理・編集可否の制御は
 * {@link RecyclerModule} 側のイベントリスナーが行う。
 */
public class RecyclerUI implements InventoryHolder {

    static final int SIZE = 54;

    static final int SLOT_INFO = 0;
    static final int SLOT_STATUS = 2;
    static final int SLOT_STATS = 4;
    static final int SLOT_RATE = 6;
    static final int SLOT_HELP = 8;

    static final int SLOT_INPUT_LABEL = 9;
    static final int INPUT_START = 10;   // 10..17 (8)

    static final int[] OUTPUT_LABELS = {18, 27, 36, 45};
    static final int[] OUTPUT_STARTS = {19, 28, 37, 46};
    static final int OUT_ROW_SLOTS = 8;

    private static final int[] FRAME_SLOTS = {1, 3, 5, 7};
    private static final String[] GEAR_FRAMES = {"§a⚙", "§2⚙", "§e⚙"};
    private static final Set<Integer> EDITABLE_SLOTS = buildEditableSlots();

    private final RecyclerModule module;
    private final RecyclerData data;
    private final String key;
    private final Inventory inventory;

    public RecyclerUI(RecyclerModule module, RecyclerData data, String key) {
        this.module = module;
        this.data = data;
        this.key = key;
        this.inventory = Bukkit.createInventory(this, SIZE, ComponentUtils.legacy("§8♻ §2リサイクラー §8- §7分解装置"));
        renderStatic();
        syncToInventory();
        renderDynamic(0);
    }

    private static Set<Integer> buildEditableSlots() {
        Set<Integer> slots = new HashSet<>();
        for (int i = 0; i < RecyclerData.INPUT_SLOTS; i++) {
            slots.add(INPUT_START + i);
        }
        for (int start : OUTPUT_STARTS) {
            for (int i = 0; i < OUT_ROW_SLOTS; i++) {
                slots.add(start + i);
            }
        }
        return slots;
    }

    public String key() {
        return key;
    }

    public RecyclerData data() {
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
        readRegion(inventory, INPUT_START, data.input(), 0, RecyclerData.INPUT_SLOTS);
        for (int row = 0; row < OUTPUT_STARTS.length; row++) {
            readRegion(inventory, OUTPUT_STARTS[row], data.output(), row * OUT_ROW_SLOTS, OUT_ROW_SLOTS);
        }
    }

    /** 仮想ストレージ → UI の編集可能スロット */
    public void syncToInventory() {
        writeRegion(inventory, INPUT_START, data.input(), 0, RecyclerData.INPUT_SLOTS);
        for (int row = 0; row < OUTPUT_STARTS.length; row++) {
            writeRegion(inventory, OUTPUT_STARTS[row], data.output(), row * OUT_ROW_SLOTS, OUT_ROW_SLOTS);
        }
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

    /**
     * プレイヤーインベントリからのシフトクリック搬入（投入口へ入れる）。
     *
     * @return 実際に移動できた個数
     */
    public int acceptShiftClick(ItemStack source) {
        if (source == null || source.getType().isAir() || source.getAmount() <= 0) {
            return 0;
        }
        ItemStack[] region = new ItemStack[RecyclerData.INPUT_SLOTS];
        readRegion(inventory, INPUT_START, region, 0, RecyclerData.INPUT_SLOTS);
        int leftover = RecyclerData.addToStorage(region, source.clone());
        int moved = source.getAmount() - leftover;
        if (moved > 0) {
            writeRegion(inventory, INPUT_START, region, 0, region.length);
        }
        return moved;
    }

    // ================= 描画 =================

    private void setButton(int slot, String name, Material icon, String lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ComponentUtils.setDisplayName(meta, name);
            List<Component> lines = new ArrayList<>();
            if (lore != null && !lore.isEmpty()) {
                for (String line : lore.split("\\n", -1)) {
                    lines.add(ComponentUtils.legacy(line));
                }
            }
            if (!lines.isEmpty()) {
                ComponentUtils.setLoreComponents(meta, lines);
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    private void renderStatic() {
        for (int slot : FRAME_SLOTS) {
            setButton(slot, "§7 ", Material.LIME_STAINED_GLASS_PANE, "");
        }
        setButton(SLOT_INPUT_LABEL, "§e≫ 投入口", Material.HOPPER,
                "§e§l→ 右の8マスに処分したい物を入れる"
                        + "\n§7クラフトレシピを逆算して素材へ分解します"
                        + "\n§7分解できない物はそのまま処分(消滅)されます"
                        + "\n§8上ホッパーからの自動搬入も可");
        for (int row = 0; row < OUTPUT_LABELS.length; row++) {
            setButton(OUTPUT_LABELS[row], row == 0 ? "§a≪ 回収口" : "§a≪ 回収口 (続き)", Material.CHEST_MINECART,
                    "§a§l← 分解された素材はここに並ぶ"
                            + "\n§7この4段(32マス)から取り出してください"
                            + "\n§8下ホッパーで自動回収も可");
        }
        setButton(SLOT_RATE, "§b還元率: §f50%", Material.COMPARATOR,
                "§7分解時に素材の§f約50%§7が戻ります"
                        + "\n§7端数は確率で1個になることがあります"
                        + "\n§7耐久が減った道具は残り耐久に応じて減額");
        setButton(SLOT_HELP, "§d使い方", Material.KNOWLEDGE_BOOK,
                "§7① 上段の§e投入口§7に不要な物を入れる"
                        + "\n§7② 毎秒自動で分解され、素材が§a回収口§7へ"
                        + "\n§7③ レシピの無い物は消滅し、少量のXPに変換"
                        + "\n§7※ 中身入りシュルカーボックスは分解されず"
                        + "\n§7   そのまま回収口へ移動します");
    }

    /** 動的部分の再描画。frame はアニメーションのコマ番号 */
    public void renderDynamic(int frame) {
        renderInfo();
        renderStatus(frame);
        renderStats();
    }

    private void renderInfo() {
        Location loc = data.location();
        String ownerName = Bukkit.getOfflinePlayer(data.owner()).getName();
        setButton(SLOT_INFO, "§2リサイクラー 情報", Material.BOOK,
                "§7所有者: §f" + (ownerName == null ? "不明" : ownerName)
                        + "\n§7処理速度: §f毎秒最大" + RecyclerModule.ITEMS_PER_CYCLE + "個"
                        + "\n§7座標: §f" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ()
                        + "\n§8複数人で同時に開いても内容は同期されます");
    }

    private void renderStatus(int frame) {
        boolean outputFull = isOutputFull();
        if (outputFull && data.hasPendingInput()) {
            setButton(SLOT_STATUS, "§c● 回収口が満杯", Material.BARRIER,
                    "§7回収口から素材を取り出すと再開します");
        } else if (data.hasPendingInput()) {
            String gear = GEAR_FRAMES[Math.floorMod(frame, GEAR_FRAMES.length)];
            setButton(SLOT_STATUS, gear + " §a稼働中 " + gear,
                    frame % 2 == 0 ? Material.GRINDSTONE : Material.IRON_INGOT,
                    "§7投入された物を分解しています…");
        } else {
            setButton(SLOT_STATUS, "§7● 待機中", Material.GRINDSTONE,
                    "§7上の§e投入口§7に不要な物を入れると分解します");
        }
    }

    private boolean isOutputFull() {
        for (ItemStack stack : data.output()) {
            if (stack == null || stack.getType().isAir()) {
                return false;
            }
        }
        return true;
    }

    private void renderStats() {
        setButton(SLOT_STATS, "§6統計", Material.EXPERIENCE_BOTTLE,
                "§7分解した数: §f" + data.getRecycledCount() + " §7個"
                        + "\n§7処分した数: §f" + data.getDestroyedCount() + " §7個"
                        + "\n§7処分した物は少量のXPオーブになります");
    }
}
