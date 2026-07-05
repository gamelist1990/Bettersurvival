package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.ui;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.ItemNameUtil;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * カスタムエンチャントテーブルの UI (54スロット、プレイヤーごと)。
 *
 * <pre>
 * Row0: [枠×4] [✦タイトル] [枠×4]
 * Row1: [道具案内] [道具スロット] [枠×6]
 * Row2-4: [枠] [エンチャント一覧 7×3] [枠]
 * Row5: [前ページ] [枠×2] [使い方] [ページ] [枠×2] [次ページ]
 * </pre>
 *
 * 道具スロットに対象を入れると、対応エンチャントのボタンが点灯し、
 * 素材コストを払ってレベルを上げられる。素材はプレイヤーの手持ちから消費。
 */
public class CustomEnchantTableUI implements InventoryHolder {

        public static final int SIZE = 54;
    public static final int SLOT_TITLE = 4;
    public static final int SLOT_TOOL_LABEL = 9;
    public static final int SLOT_TOOL = 10;
        public static final int SLOT_PREV_PAGE = 45;
        public static final int SLOT_INFO = 49;
        public static final int SLOT_PAGE_INFO = 50;
        public static final int SLOT_NEXT_PAGE = 53;
        /** エンチャントボタンのスロット (7列×3段 / 1ページ最大21個) */
        public static final int[] BUTTON_SLOTS = {
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
        };

    private final CustomEnchantRegistry registry;
    private final UUID viewerId;
    private final Inventory inventory;
    /** ボタンスロット → エンチャント */
    private final List<CustomEnchant> slotMapping = new ArrayList<>();
    private int page;

    public CustomEnchantTableUI(CustomEnchantRegistry registry, Player viewer) {
        this.registry = registry;
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, SIZE, ComponentUtils.legacy("§8✦ §dカスタムエンチャント管理 §8✦"));
        renderStatic();
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public boolean isToolSlot(int slot) {
        return slot == SLOT_TOOL;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public ItemStack toolItem() {
        ItemStack stack = inventory.getItem(SLOT_TOOL);
        return stack == null || stack.getType().isAir() ? null : stack;
    }

    /** UI を閉じたとき道具スロットの中身を返却する */
    public void returnTool(Player player) {
        ItemStack tool = toolItem();
        if (tool == null) {
            return;
        }
        inventory.setItem(SLOT_TOOL, null);
        for (ItemStack leftover : player.getInventory().addItem(tool).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

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

    private static boolean isButtonSlot(int slot) {
        for (int buttonSlot : BUTTON_SLOTS) {
            if (buttonSlot == slot) {
                return true;
            }
        }
        return false;
    }

    private void renderStatic() {
        for (int slot = 0; slot < SIZE; slot++) {
            if (slot == SLOT_TOOL || slot == SLOT_TITLE || slot == SLOT_TOOL_LABEL || slot == SLOT_INFO
                    || slot == SLOT_PREV_PAGE || slot == SLOT_PAGE_INFO || slot == SLOT_NEXT_PAGE || isButtonSlot(slot)) {
                continue;
            }
            setButton(slot, "§7 ", Material.PURPLE_STAINED_GLASS_PANE, "");
        }
        setButton(SLOT_TITLE, "§d✦ カスタムエンチャント管理 ✦", Material.ENCHANTING_TABLE,
                "§7今後エンチャントが増えてもページで管理できます");
        setButton(SLOT_TOOL_LABEL, "§e→ 道具をここへ", Material.ITEM_FRAME,
                "§7右のスロットにエンチャントしたい\n§7道具や装備を入れてください");
        setButton(SLOT_INFO, "§b使い方", Material.WRITABLE_BOOK,
                "§7① 道具をスロットに入れる"
                        + "\n§7② 付けたい効果をクリック"
                        + "\n§7③ 素材は手持ちから消費されます"
                        + "\n§7④ 下の矢印でページを切り替え"
                        + "\n§7効果はレベル制で、繰り返し強化できます");
    }

    /** 道具スロットの状態に応じてエンチャントボタンを再描画 */
    public void render() {
        Player viewer = Bukkit.getPlayer(viewerId);
        ItemStack tool = toolItem();
        slotMapping.clear();
        List<CustomEnchant> enchants = new ArrayList<>();
        if (tool != null) {
            for (CustomEnchant enchant : registry.all()) {
                if (enchant.supports(tool.getType())) {
                    enchants.add(enchant);
                }
            }
        }
        page = Math.max(0, Math.min(page, maxPage(enchants.size())));
        int start = page * BUTTON_SLOTS.length;
        int index = 0;
        for (; index < BUTTON_SLOTS.length; index++) {
            int enchantIndex = start + index;
            if (enchantIndex >= enchants.size()) {
                break;
            }
            CustomEnchant enchant = enchants.get(enchantIndex);
            slotMapping.add(enchant);
            renderEnchantButton(BUTTON_SLOTS[index], enchant, tool, viewer);
        }
        for (; index < BUTTON_SLOTS.length; index++) {
            setButton(BUTTON_SLOTS[index], "§8－", Material.GRAY_STAINED_GLASS_PANE,
                    tool == null ? "§7先に道具を入れてください" : "§7この道具に付けられるエンチャントはここまでです");
        }
        renderPageControls(enchants.size());
    }

    private int maxPage(int enchantCount) {
        if (enchantCount <= 0) {
            return 0;
        }
        return (enchantCount - 1) / BUTTON_SLOTS.length;
    }

    private void renderPageControls(int enchantCount) {
        int maxPage = maxPage(enchantCount);
        if (page > 0) {
            setButton(SLOT_PREV_PAGE, "§e← 前ページ", Material.ARROW,
                    "§7前のエンチャント一覧へ戻ります");
        } else {
            setButton(SLOT_PREV_PAGE, "§8← 前ページ", Material.GRAY_STAINED_GLASS_PANE,
                    "§7これ以上前のページはありません");
        }
        setButton(SLOT_PAGE_INFO, "§dページ §f" + (page + 1) + "§7/§f" + (maxPage + 1), Material.PAPER,
            "§7表示中の付与可能エンチャント: §f" + enchantCount
                        + "\n§71ページ表示数: §f" + BUTTON_SLOTS.length);
        if (page < maxPage) {
            setButton(SLOT_NEXT_PAGE, "§e次ページ →", Material.ARROW,
                    "§7次のエンチャント一覧へ進みます");
        } else {
            setButton(SLOT_NEXT_PAGE, "§8次ページ →", Material.GRAY_STAINED_GLASS_PANE,
                    "§7これ以上次のページはありません");
        }
    }

    private void renderEnchantButton(int slot, CustomEnchant enchant, ItemStack tool, Player viewer) {
        StringBuilder lore = new StringBuilder(enchant.description());
        if (tool == null) {
            lore.append("\n\n§8道具を入れると付与できます");
            setButton(slot, "§7" + enchant.displayName(), enchant.icon(), lore.toString());
            return;
        }
        if (!enchant.supports(tool.getType())) {
            lore.append("\n\n§cこの道具には付けられません");
            setButton(slot, "§8" + enchant.displayName() + " §c✘", enchant.icon(), lore.toString());
            return;
        }
        int current = enchant.levelOf(tool);
        if (current >= enchant.maxLevel()) {
            lore.append("\n\n§6★ 最大レベルです");
            setButton(slot, "§d" + enchant.displayName() + " §6" + CustomEnchant.roman(current) + " (MAX)",
                    enchant.icon(), lore.toString());
            return;
        }
        int next = current + 1;
        lore.append("\n\n§7現在: ").append(current <= 0 ? "§8なし" : "§f" + CustomEnchant.roman(current));
        lore.append("\n§7強化後: §d").append(CustomEnchant.roman(next));
        lore.append("\n§7必要素材:");
        boolean affordable = true;
        boolean creative = viewer != null && viewer.getGameMode() == GameMode.CREATIVE;
        List<Component> loreLines = legacyLore(lore.toString());
        for (ItemStack cost : enchant.upgradeCost(next)) {
            boolean has = creative || (viewer != null && viewer.getInventory().containsAtLeast(cost, cost.getAmount()));
            if (!has) {
                affordable = false;
            }
            loreLines.add(ComponentUtils.legacy(has ? "§a ✔ " : "§c ✘ ")
                .append(ItemNameUtil.localizedComponent(cost.getType()))
                .append(ComponentUtils.legacy(" §7×" + cost.getAmount())));
        }
        loreLines.add(Component.empty());
        loreLines.add(ComponentUtils.legacy(affordable ? "§e▶ クリックで付与/強化" : "§c素材が足りません"));
        setButton(slot, "§d" + enchant.displayName() + " §f" + CustomEnchant.roman(next) + " §7へ強化",
            enchant.icon(), loreLines);
    }

    /** エンチャントボタンのクリック処理 */
    public void handleClick(Player player, int slot) {
        if (slot == SLOT_PREV_PAGE) {
            if (page > 0) {
                page--;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 0.8F);
                render();
            }
            return;
        }
        if (slot == SLOT_NEXT_PAGE) {
            int maxPage = maxPage(registry.all().size());
            if (page < maxPage) {
                page++;
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6F, 1.2F);
                render();
            }
            return;
        }
        int index = -1;
        for (int i = 0; i < BUTTON_SLOTS.length; i++) {
            if (BUTTON_SLOTS[i] == slot) {
                index = i;
                break;
            }
        }
        if (index < 0 || index >= slotMapping.size()) {
            return;
        }
        CustomEnchant enchant = slotMapping.get(index);
        ItemStack tool = toolItem();
        if (tool == null) {
            player.sendMessage("§d[エンチャント]§r §c先に道具をスロットに入れてください");
            return;
        }
        if (!enchant.supports(tool.getType())) {
            player.sendMessage("§d[エンチャント]§r §cこの道具には " + enchant.displayName() + " を付けられません");
            return;
        }
        int current = enchant.levelOf(tool);
        if (current >= enchant.maxLevel()) {
            player.sendMessage("§d[エンチャント]§r §e" + enchant.displayName() + " は最大レベルです");
            return;
        }
        int next = current + 1;
        List<ItemStack> costs = enchant.upgradeCost(next);
        boolean isCreative = player.getGameMode() == GameMode.CREATIVE;
        if (!isCreative) {
            for (ItemStack cost : costs) {
                if (!player.getInventory().containsAtLeast(cost, cost.getAmount())) {
                    player.sendMessage(ComponentUtils.legacy("§d[エンチャント]§r §c素材が足りません: ")
                            .append(ItemNameUtil.localizedComponent(cost.getType()))
                            .append(ComponentUtils.legacy(" ×" + cost.getAmount())));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.6F, 0.7F);
                    return;
                }
            }
            for (ItemStack cost : costs) {
                player.getInventory().removeItem(cost.clone());
            }
        }
        enchant.applyLevel(tool, next);
        inventory.setItem(SLOT_TOOL, tool);
        player.sendMessage("§d[エンチャント]§r §a" + enchant.displayName() + " §f"
                + CustomEnchant.roman(next) + " §aを付与しました！");
        player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0F, 1.0F);
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6F, 1.4F);
        player.getWorld().spawnParticle(org.bukkit.Particle.ENCHANT,
                player.getLocation().add(0.0D, 1.5D, 0.0D), 40, 0.5D, 0.5D, 0.5D, 0.5D);
        render();
    }

}
