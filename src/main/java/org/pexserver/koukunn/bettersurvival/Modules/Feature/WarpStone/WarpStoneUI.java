package org.pexserver.koukunn.bettersurvival.Modules.Feature.WarpStone;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * ワープ先選択 UI (54スロット、プレイヤーごと)。
 *
 * Row0: [情報(4)] [GTAアニメ切替(8)]、Row1以降: 発見済みワープストーン一覧。
 * クリックでワープ。現在いるワープストーンは一覧から除外される。
 */
public class WarpStoneUI implements InventoryHolder {

    public static final int SIZE = 54;
    public static final int SLOT_RENAME = 0;
    public static final int SLOT_INFO = 4;
    public static final int SLOT_GTA_TOGGLE = 8;
    public static final int LIST_START = 9;

    private final WarpStoneModule module;
    private final UUID viewerId;
    private final String currentKey;
    private final Inventory inventory;
    /** スロット → ワープ先 locationKey */
    private final List<String> slotKeys = new ArrayList<>();

    public WarpStoneUI(WarpStoneModule module, Player viewer, String currentKey) {
        this.module = module;
        this.viewerId = viewer.getUniqueId();
        this.currentKey = currentKey;
        this.inventory = Bukkit.createInventory(this, SIZE, ComponentUtils.legacy("§8◈ §bワープストーン §8◈"));
        render();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public UUID viewerId() {
        return viewerId;
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    private void setButton(int slot, String name, Material icon, String lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            ComponentUtils.setDisplayName(meta, name);
            if (lore != null && !lore.isEmpty()) {
                ComponentUtils.setLore(meta, lore.split("\\n", -1));
            }
            item.setItemMeta(meta);
        }
        inventory.setItem(slot, item);
    }

    public void render() {
        Player viewer = Bukkit.getPlayer(viewerId);
        inventory.clear();
        slotKeys.clear();
        WarpStoneStore.StoneData currentData = module.getStoneData(currentKey);
        boolean isOwner = currentData != null
                && currentData.owner() != null
                && currentData.owner().equals(viewerId);
        for (int slot = 0; slot < LIST_START; slot++) {
            if (slot == SLOT_INFO || slot == SLOT_GTA_TOGGLE) {
                continue;
            }
            if (slot == SLOT_RENAME && isOwner) {
                continue;
            }
            setButton(slot, "§7 ", Material.LIGHT_BLUE_STAINED_GLASS_PANE, "");
        }
        if (isOwner) {
            setButton(SLOT_RENAME, "§e✎ 名前を変更", Material.WRITABLE_BOOK,
                    "§7このワープストーンの名前を変更します"
                            + "\n§7現在: §b" + currentData.name()
                            + "\n\n§eクリックで名前入力を開く");
        }
        List<WarpStoneModule.StoneView> stones = module.discoveredStones(viewerId, currentKey);
        setButton(SLOT_INFO, "§b◈ ワープストーン ◈", Material.LODESTONE,
                "§7発見済み: §f" + stones.size() + " §7箇所"
                        + "\n§7他のワープストーンを右クリックすると"
                        + "\n§7ここの一覧に追加されます");
        boolean gta = module.isGtaEnabled(viewerId);
        setButton(SLOT_GTA_TOGGLE,
                gta ? "§a✈ GTA Animation: ON" : "§7✈ GTA Animation: OFF",
                gta ? Material.ELYTRA : Material.GRAY_DYE,
                "§7ワープ時に上空へ舞い上がり、"
                        + "\n§7GTA風のカメラ移動で目的地へ飛びます"
                        + "\n§eクリックで切り替え");

        int slot = LIST_START;
        Location viewerLoc = viewer == null ? null : viewer.getLocation();
        for (WarpStoneModule.StoneView stone : stones) {
            if (slot >= SIZE) {
                break;
            }
            Location loc = stone.location();
            String distance = "";
            if (viewerLoc != null && loc.getWorld() != null && loc.getWorld().equals(viewerLoc.getWorld())) {
                distance = "\n§7距離: §f" + (int) viewerLoc.distance(loc) + "m";
            }
            setButton(slot, "§b◈ " + stone.name(), Material.LODESTONE,
                    "§7" + loc.getWorld().getName() + " §8(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + ")"
                            + distance
                            + "\n\n§e▶ クリックでワープ");
            slotKeys.add(stone.key());
            slot++;
        }
        if (stones.isEmpty()) {
            setButton(22, "§7まだワープ先がありません", Material.BARRIER,
                    "§7他の場所のワープストーンを右クリックして\n§7発見するとここに表示されます");
        }
    }

    public void handleClick(Player player, int slot) {
        if (slot == SLOT_RENAME) {
            module.requestRename(player, currentKey);
            return;
        }
        if (slot == SLOT_GTA_TOGGLE) {
            boolean now = module.toggleGta(player.getUniqueId());
            player.sendMessage("§b[ワープストーン]§r GTA Animation: " + (now ? "§aON" : "§7OFF"));
            render();
            return;
        }
        int index = slot - LIST_START;
        if (index < 0 || index >= slotKeys.size()) {
            return;
        }
        String destKey = slotKeys.get(index);
        player.closeInventory();
        module.requestTeleport(player, destKey);
    }
}
