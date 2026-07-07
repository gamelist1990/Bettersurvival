package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable;

import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantRegistry;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 金床でカスタムエンチャント本を対応装備に適用するリスナー。
 *
 * <ul>
 *   <li>左スロット: 適用対象 (剣・道具・防具など)</li>
 *   <li>右スロット: カスタムエンチャントを持つエンチャント本</li>
 * </ul>
 *
 * 本に付いているカスタムエンチャントのうち、
 * {@link CustomEnchant#supports(Material)} が true を返すものだけを対象装備へ転写する。
 * それ以外は「本には付いているが対象には非対応」なので **自動的に切り捨てられ**、
 * 装備に不正なエンチャントが乗る問題を防ぐ。
 */
public class CustomEnchantAnvilListener implements Listener {

    private final CustomEnchantRegistry registry;

    public CustomEnchantAnvilListener(CustomEnchantRegistry registry) {
        this.registry = registry;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        AnvilInventory inv = event.getInventory();
        ItemStack left = inv.getItem(0);
        ItemStack right = inv.getItem(1);
        if (left == null || left.getType().isAir()) {
            return;
        }
        if (right == null || right.getType() != Material.ENCHANTED_BOOK) {
            return;
        }
        // 本→本 の複製やコンビは対象外 (装備へ転写する用途のみ扱う)
        Material targetType = left.getType();
        if (targetType == Material.BOOK || targetType == Material.ENCHANTED_BOOK) {
            return;
        }

        Map<CustomEnchant, Integer> bookEnchants = extractCustomEnchants(right);
        if (bookEnchants.isEmpty()) {
            return;
        }

        // vanilla の結果があればそれをベースに、なければ左のクローンを土台にする
        ItemStack base = event.getResult();
        if (base == null || base.getType().isAir()) {
            base = left.clone();
        }
        ItemStack result = base.clone();

        int cost = 0;
        int applied = 0;
        int rejected = 0;
        for (Map.Entry<CustomEnchant, Integer> entry : bookEnchants.entrySet()) {
            CustomEnchant ench = entry.getKey();
            int bookLevel = Math.max(1, Math.min(entry.getValue(), ench.maxLevel()));

            // ★ ここが「対象装備に付けられないエンチャントの切り捨て」ロジック ★
            if (!ench.supports(targetType)) {
                rejected++;
                continue;
            }

            int current = ench.levelOf(result);
            int newLevel = current;
            if (bookLevel > current) {
                newLevel = bookLevel;
            } else if (bookLevel == current && current < ench.maxLevel()) {
                // 同レベルなら 1 段階アップ (バニラのエンチャント本と同じ挙動)
                newLevel = current + 1;
            }
            if (newLevel <= current) {
                continue;
            }
            ench.applyLevel(result, newLevel);
            cost += Math.max(2, newLevel * 3);
            applied++;
        }

        if (applied == 0) {
            // 対応可能なエンチャントが 1 つも無ければ結果を出さない (誤って本だけ消費するのを防ぐ)
            if (rejected > 0 && (event.getResult() == null || event.getResult().getType().isAir())) {
                event.setResult(null);
            }
            return;
        }

        event.setResult(result);
        // 修理コスト設定はバージョン依存 (setRepairCost は 1.21 で削除予定) のため設定しない。
        // バニラ既定の経験値コストを利用する。
        int ignoredCost = cost; // 将来 API 対応のため保持
        if (ignoredCost < 0) {
            // no-op
        }
    }

    /** 本の PersistentDataContainer からカスタムエンチャントとレベルを抽出。 */
    private Map<CustomEnchant, Integer> extractCustomEnchants(ItemStack book) {
        Map<CustomEnchant, Integer> map = new LinkedHashMap<>();
        if (book == null || !book.hasItemMeta()) {
            return map;
        }
        PersistentDataContainer pdc = book.getItemMeta().getPersistentDataContainer();
        for (CustomEnchant ench : registry.all()) {
            Integer lv = pdc.get(ench.key(), PersistentDataType.INTEGER);
            if (lv != null && lv > 0) {
                map.put(ench, lv);
            }
        }
        return map;
    }
}
