package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.ContainerTarget;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

public final class CopperGolemBoneMealSourceMenuUI {

    private CopperGolemBoneMealSourceMenuUI() {
    }

    public interface ActionHandler {
        void onBack(Player player);
        void onClear(Player player);
        void onClose(Player player);
        void onSelectSlot(Player player, int slot);
    }

    public static void open(Player player, GolemProfile profile, ActionHandler handler) {
        ChestUI.Builder builder = ChestUI.builder()
                .title("骨粉供給元設定: " + profile.id())
                .size(54)
                .addButtonAt(45, "§e戻る", Material.ARROW, "")
                .addButtonAt(52, "§c全解除", Material.BARRIER, "§7登録済みの供給元を全削除")
                .addButtonAt(53, "§7閉じる", Material.RED_STAINED_GLASS_PANE, "");

        for (int i = 0; i < 45; i++) {
            if (i < profile.boneMealSources().size()) {
                ContainerTarget target = profile.boneMealSources().get(i);
                String worldName = target.anchor().getWorld() == null ? "unknown" : target.anchor().getWorld().getName();
                String lore = "§7" + worldName + " "
                        + target.anchor().getBlockX() + ", "
                        + target.anchor().getBlockY() + ", "
                        + target.anchor().getBlockZ()
                        + "\n§7クリックで再設定";
                builder.addButtonAt(i, "§a供給元 " + (i + 1), Material.BONE_MEAL, lore);
            } else if (i == profile.boneMealSources().size()) {
                builder.addButtonAt(i, "§b供給元を追加", Material.LIME_STAINED_GLASS_PANE,
                        "§7クリック後、対象チェストを左クリック");
            } else {
                builder.addButtonAt(i, " ", Material.GRAY_STAINED_GLASS_PANE, "");
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
}
