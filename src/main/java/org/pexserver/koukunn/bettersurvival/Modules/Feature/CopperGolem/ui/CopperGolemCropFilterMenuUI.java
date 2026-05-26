package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

import java.util.ArrayList;
import java.util.List;

public final class CopperGolemCropFilterMenuUI {

    private CopperGolemCropFilterMenuUI() {
    }

    public interface ActionHandler {
        void onBack(Player player);
        void onClear(Player player);
        void onClose(Player player);
        void onSelectSlot(Player player, int slot);
    }

    public static void open(Player player, GolemProfile profile, ActionHandler handler) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("作物フィルタ: " + profile.id())
                .size(54)
                .addButtonAt(45, "§e戻る", Material.ARROW, "")
                .addButtonAt(52, "§c全解除", Material.BARRIER, "")
                .addButtonAt(53, "§7閉じる", Material.RED_STAINED_GLASS_PANE, "");

        List<Material> filters = new ArrayList<>(profile.cropFilters());
        for (int i = 0; i < 45; i++) {
            if (i < filters.size()) {
                Material material = filters.get(i);
                builder.addButtonAt(i, "§a" + material.name(), toDisplayItem(material),
                        "§7手持ちアイテムで上書き\n§7手持ちが空なら削除");
            } else {
                builder.addButtonAt(i, "§7空き", Material.BLACK_STAINED_GLASS_PANE,
                        "§7手持ちに作物を持ってクリック");
            }
        }

        builder.then((result, p) -> {
            if (result.slot == null) {
                return;
            }
            int slot = result.slot;
            if (slot == 45) {
                handler.onBack(p);
                return;
            }
            if (slot == 52) {
                handler.onClear(p);
                return;
            }
            if (slot == 53) {
                handler.onClose(p);
                return;
            }
            if (slot < 0 || slot >= 45) {
                return;
            }
            handler.onSelectSlot(p, slot);
        }).show(player);
    }

    private static Material toDisplayItem(Material material) {
        if (material == null) {
            return Material.WHEAT_SEEDS;
        }
        if (material.isItem()) {
            return material;
        }
        return switch (material) {
            case WHEAT -> Material.WHEAT_SEEDS;
            case BEETROOTS -> Material.BEETROOT_SEEDS;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case COCOA -> Material.COCOA_BEANS;
            case TORCHFLOWER_CROP -> Material.TORCHFLOWER_SEEDS;
            case NETHER_WART -> Material.NETHER_WART;
            default -> Material.WHEAT_SEEDS;
        };
    }
}
