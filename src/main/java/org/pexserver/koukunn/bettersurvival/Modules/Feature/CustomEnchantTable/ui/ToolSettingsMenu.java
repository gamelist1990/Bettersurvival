package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.ui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantRegistry;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings.ToolSetting;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.settings.ToolSettingsStore;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ツール設定メニュー (27スロット、プレイヤーごと)。
 *
 * 開いた時点で手に持っていた道具に「実際に付いているエンチャント」に対応する
 * {@link ToolSetting} だけをボタンとして並べ、クリックで ON/OFF を切り替える。
 * エンチャントが付いていない項目は表示されない。設定はプレイヤー単位で保持される。
 */
public class ToolSettingsMenu implements InventoryHolder {

    public static final int SIZE = 27;
    /** 設定ボタンを置くスロット (中央の行) */
    private static final int[] BUTTON_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final ToolSettingsStore store;
    private final CustomEnchantRegistry registry;
    /** メニューを開いた時点の道具 (この道具のエンチャントで項目を絞り込む) */
    private final ItemStack tool;
    private final UUID viewerId;
    private final Inventory inventory;
    /** ボタンスロット → 設定ID */
    private final List<String> slotMapping = new ArrayList<>();

    public ToolSettingsMenu(ToolSettingsStore store, CustomEnchantRegistry registry, ItemStack tool, Player viewer) {
        this.store = store;
        this.registry = registry;
        this.tool = tool;
        this.viewerId = viewer.getUniqueId();
        this.inventory = Bukkit.createInventory(this, SIZE, ComponentUtils.legacy("§8⚙ §bツール設定 §8⚙"));
        render();
    }

    /** その設定に対応するエンチャントが、開いた道具に付いているか */
    private boolean toolHasEnchant(ToolSetting setting) {
        CustomEnchant enchant = registry.byId(setting.id());
        return enchant != null && tool != null && enchant.levelOf(tool) > 0;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void render() {
        for (int slot = 0; slot < SIZE; slot++) {
            setItem(slot, "§7 ", Material.GRAY_STAINED_GLASS_PANE, null);
        }
        setItem(4, "§b⚙ ツール設定", Material.COMPARATOR,
                List.of("§7各エンチャントの効果を ON/OFF できます",
                        "§7設定はプレイヤーごとに保存されます (再起動でリセット)"));

        slotMapping.clear();
        int index = 0;
        for (ToolSetting setting : store.settings()) {
            if (!toolHasEnchant(setting)) {
                continue; // その道具に付いていないエンチャントの設定は出さない
            }
            if (index >= BUTTON_SLOTS.length) {
                break;
            }
            slotMapping.add(setting.id());
            renderSettingButton(BUTTON_SLOTS[index], setting);
            index++;
        }
        if (index == 0) {
            setItem(13, "§7設定できる効果がありません", Material.BARRIER,
                    List.of("§7この道具には ON/OFF できる",
                            "§7カスタムエンチャントが付いていません"));
        }
    }

    private void renderSettingButton(int slot, ToolSetting setting) {
        boolean enabled = store.isEnabled(viewerId, setting.id());
        List<String> lore = new ArrayList<>();
        for (String line : setting.description().split("\\n", -1)) {
            lore.add(line);
        }
        lore.add(" ");
        lore.add(enabled ? "§a● 現在: ON" : "§c○ 現在: OFF");
        lore.add(enabled ? "§7クリックで §cOFF §7にする" : "§7クリックで §aON §7にする");
        String name = (enabled ? "§a" : "§7") + setting.displayName() + (enabled ? " §8[ON]" : " §8[OFF]");
        setItem(slot, name, setting.icon(), lore);
    }

    /** ボタンクリック → 設定を反転 */
    public void handleClick(Player player, int slot) {
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
        String id = slotMapping.get(index);
        ToolSetting setting = store.get(id);
        if (setting == null) {
            return;
        }
        boolean now = store.toggle(player.getUniqueId(), id);
        player.playSound(player.getLocation(),
                now ? Sound.BLOCK_NOTE_BLOCK_PLING : Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, now ? 1.6F : 0.7F);
        player.sendActionBar(ComponentUtils.legacy(
                (now ? "§a" : "§7") + setting.displayName() + ": " + (now ? "ON" : "OFF")));
        render();
    }

    private void setItem(int slot, String name, Material icon, List<String> lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ComponentUtils.setDisplayName(meta, name);
            if (lore != null && !lore.isEmpty()) {
                ComponentUtils.setLore(meta, lore);
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }
}
