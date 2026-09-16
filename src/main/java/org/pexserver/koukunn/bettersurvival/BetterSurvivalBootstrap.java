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
import org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld.OtherworldDatapackBootstrap;

/**
 * Paper の bootstrap フェーズ。
 *
 * Otherworld の生成DataPackをDataPack discovery中に登録した後、
 * Registry Modification API でカスタムエンチャントを登録する。
 */
public class BetterSurvivalBootstrap implements PluginBootstrap {

    @Override
    public void bootstrap(BootstrapContext context) {
        OtherworldDatapackBootstrap.register(context);

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
