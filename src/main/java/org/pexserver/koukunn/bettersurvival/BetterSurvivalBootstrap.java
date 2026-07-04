package org.pexserver.koukunn.bettersurvival;

import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.registry.data.EnchantmentRegistryEntry;
import io.papermc.paper.registry.event.RegistryEvents;
import io.papermc.paper.registry.keys.EnchantmentKeys;
import io.papermc.paper.registry.keys.tags.ItemTypeTagKeys;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchantDefinitions;

/**
 * Paper の bootstrap フェーズ。
 *
 * Registry Modification API を使い、カスタムエンチャントを
 * 「本物のエンチャント」としてエンチャントレジストリに登録する。
 * ここで登録したエンチャントは実行時に
 * RegistryAccess から bettersurvival:&lt;id&gt; で取得でき、
 * ツールチップにも Vanilla エンチャントと同じ形式で表示される。
 *
 * IN_ENCHANTING_TABLE タグには追加しないため、通常のエンチャントテーブルには
 * 出現せず、カスタムエンチャントテーブル経由でのみ付与される。
 */
public class BetterSurvivalBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        context.getLifecycleManager().registerEventHandler(RegistryEvents.ENCHANTMENT.compose().newHandler(event -> {
            for (CustomEnchantDefinitions.Definition def : CustomEnchantDefinitions.all()) {
                event.registry().register(
                        EnchantmentKeys.create(Key.key(CustomEnchantDefinitions.NAMESPACE, def.id())),
                        builder -> builder
                                .description(Component.text(def.displayName()))
                                .supportedItems(event.getOrCreateTag(ItemTypeTagKeys.ENCHANTABLE_MINING))
                                .anvilCost(4)
                                .maxLevel(def.maxLevel())
                                .weight(1)
                                .minimumCost(EnchantmentRegistryEntry.EnchantmentCost.of(10, 10))
                                .maximumCost(EnchantmentRegistryEntry.EnchantmentCost.of(50, 10))
                                .activeSlots(EquipmentSlotGroup.MAINHAND));
            }
        }));
    }
}
