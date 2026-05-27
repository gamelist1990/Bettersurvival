package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.ui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class SharedStorageChestPageUi {

    private static final int PAGE_SIZE = 45;

    private SharedStorageChestPageUi() {
    }

    public static void openPagedMenu(
            Player player,
            String title,
            int page,
            List<GridEntry> entries,
            String backLabel,
            String backLore,
            BiConsumer<Player, Integer> onPageChange,
            BiConsumer<Player, Integer> onEntrySelect,
            Consumer<Player> onBack) {
        if (player == null || entries == null || entries.isEmpty())
            return;
        int maxPage = Math.max(0, (entries.size() - 1) / PAGE_SIZE);
        int currentPage = Math.max(0, Math.min(maxPage, page));
        int start = currentPage * PAGE_SIZE;

        ChestUI.Builder builder = ChestUI.builder()
                .title(title + " §7(" + (currentPage + 1) + "/" + (maxPage + 1) + ")")
                .size(54);

        for (int slot = 0; slot < PAGE_SIZE; slot++) {
            int index = start + slot;
            if (index >= entries.size())
                break;
            GridEntry entry = entries.get(index);
            builder.addCustomItemAt(slot, entry.label(), entry.icon(), entry.lore());
        }

        if (currentPage > 0)
            builder.addButtonAt(45, "§e前のページ", Material.ARROW, "§7一覧を戻る");
        builder.addButtonAt(49, backLabel, Material.BARRIER, backLore);
        if (currentPage < maxPage)
            builder.addButtonAt(53, "§e次のページ", Material.ARROW, "§7一覧を進む");

        builder.then((result, p) -> {
            if (!result.success || result.slot == null)
                return;
            if (result.slot == 45) {
                onPageChange.accept(p, currentPage - 1);
                return;
            }
            if (result.slot == 49) {
                onBack.accept(p);
                return;
            }
            if (result.slot == 53) {
                onPageChange.accept(p, currentPage + 1);
                return;
            }
            if (result.slot < 0 || result.slot >= PAGE_SIZE)
                return;
            int index = start + result.slot;
            if (index < 0 || index >= entries.size())
                return;
            onEntrySelect.accept(p, index);
        }).show(player);
    }

    public static void openCategoryMenu(
            Player player,
            String networkId,
            int page,
            List<CategoryPageEntry> categories,
            BiConsumer<Player, Integer> onPageChange,
            Consumer<Player> onBack,
            BiConsumer<Player, Integer> onSelect) {
        List<GridEntry> entries = new ArrayList<>(categories.size());
        for (CategoryPageEntry category : categories) {
            String lore = "§7カテゴリ内sub: §f" + category.subCount()
                    + "\n§7カテゴリ合計: §f" + category.totalAmountText() + "個"
                    + "\n§7クリック: " + (category.subCount() == 1 ? "直接開く" : "カテゴリを開く");
            entries.add(new GridEntry(category.menuLabel(), category.icon(), lore));
        }
        openPagedMenu(
                player,
                "ChestPage: " + networkId,
                page,
                entries,
                "§c戻る",
                "§7共有ストレージ設定へ戻る",
                onPageChange,
                onSelect,
                onBack);
    }

    public static void openSubMenu(
            Player player,
            String categoryTitle,
            int subPage,
            List<SubPageEntry> subs,
            BiConsumer<Player, Integer> onPageChange,
            Consumer<Player> onBack,
            BiConsumer<Player, Integer> onSelect) {
        List<GridEntry> entries = new ArrayList<>(subs.size());
        for (int index = 0; index < subs.size(); index++) {
            SubPageEntry sub = subs.get(index);
            String lore = "§7sub: §f#" + (index + 1)
                    + "\n§7位置: " + sub.locationText()
                    + "\n§7このチェスト内: §f" + sub.subAmountText() + "個"
                    + "\n§7カテゴリ合計: §f" + sub.categoryTotalAmountText() + "個"
                    + "\n§7クリックで開く";
            entries.add(new GridEntry(sub.label(), sub.icon(), lore));
        }
        openPagedMenu(
                player,
                "ChestPage: " + categoryTitle,
                subPage,
                entries,
                "§c戻る",
                "§7カテゴリ一覧へ戻る",
                onPageChange,
                onSelect,
                onBack);
    }

    public record GridEntry(String label, ItemStack icon, String lore) {
    }

    public record CategoryPageEntry(String key, String menuLabel, ItemStack icon, int subCount, String totalAmountText) {
    }

    public record SubPageEntry(String label, ItemStack icon, String locationText, String subAmountText, String categoryTotalAmountText) {
    }
}
