package org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.filter;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.SmithingTransformRecipe;
import org.bukkit.inventory.StonecuttingRecipe;
import org.bukkit.inventory.TransmuteRecipe;
import org.bukkit.inventory.CookingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.SharedStorage.model.SubFrameFilterMode;

import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@SuppressWarnings("deprecation")
public class SharedStorageFrameFilterRules {

    private static final String CLEAR_FILTER_NAME = "chest-clear";
    private static final Pattern ALL_FILTER_TOKEN = Pattern.compile("(^|[^a-z0-9])all([^a-z0-9]|$)");
    private volatile Map<Material, Set<Material>> recipeIngredientToResultGraph;
    private final Map<Material, Set<Material>> recipeReachabilityCache = new HashMap<>();

    public String resolveCategoryKey(ItemStack filter) {
        if (filter == null || filter.getType() == Material.AIR)
            return "nofilter";
        if (isClearFrameFilter(filter))
            return "clear:" + filter.getType().name();
        String namedCategory = resolveNamedFrameFilterCategory(filter);
        if (namedCategory != null)
            return "category:" + namedCategory;
        if (isAllFrameFilter(filter))
            return "all:" + filter.getType().name();
        return "filter:" + filter.getType().name();
    }

    public boolean matchesCategoryKey(ItemStack item, String categoryKey) {
        if (item == null || item.getType() == Material.AIR)
            return false;
        if (categoryKey == null || categoryKey.equals("nofilter"))
            return true;
        if (categoryKey.startsWith("category:")) {
            String category = categoryKey.substring("category:".length());
            return matchesNamedFrameFilterCategory(item.getType(), category);
        }
        if (categoryKey.startsWith("all:")) {
            Material baseMaterial = Material.matchMaterial(categoryKey.substring("all:".length()));
            if (baseMaterial == null)
                return false;
            return matchesAllMaterialFamily(item.getType(), baseMaterial);
        }
        if (categoryKey.startsWith("filter:") || categoryKey.startsWith("clear:")) {
            int separator = categoryKey.indexOf(':');
            if (separator < 0 || separator >= categoryKey.length() - 1)
                return false;
            Material material = Material.matchMaterial(categoryKey.substring(separator + 1));
            return material != null && item.getType() == material;
        }
        return false;
    }

    public boolean matchesAnyFilter(
            ItemStack item,
            Collection<ItemStack> filters,
            SubFrameFilterMode mode,
            BiPredicate<ItemStack, ItemStack> exactMatcher,
            Predicate<ItemStack> enchantStateChecker) {
        if (filters == null || filters.isEmpty())
            return false;
        for (ItemStack filter : filters) {
            if (matchesFilter(item, filter, mode, exactMatcher, enchantStateChecker))
                return true;
        }
        return false;
    }

    public boolean isClearFrameFilter(ItemStack filter) {
        if (filter == null || filter.getType() == Material.AIR || !filter.hasItemMeta())
            return false;
        ItemMeta meta = filter.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return false;
        String raw = meta.getDisplayName();
        if (raw == null)
            return false;
        String normalized = ChatColor.stripColor(raw).trim().toLowerCase(Locale.ROOT);
        return CLEAR_FILTER_NAME.equals(normalized);
    }

    public int resolveFilterPriority(List<ItemStack> filters) {
        int best = 0;
        for (ItemStack filter : filters) {
            if (filter == null || filter.getType() == Material.AIR)
                continue;
            if (resolveNamedFrameFilterCategory(filter) != null) {
                best = Math.max(best, 1);
                continue;
            }
            if (isAllFrameFilter(filter)) {
                best = Math.max(best, 2);
                continue;
            }
            best = Math.max(best, 3);
        }
        return best;
    }

    private boolean matchesFilter(
            ItemStack item,
            ItemStack filter,
            SubFrameFilterMode mode,
            BiPredicate<ItemStack, ItemStack> exactMatcher,
            Predicate<ItemStack> enchantStateChecker) {
        if (item == null || filter == null || item.getType() == Material.AIR || filter.getType() == Material.AIR)
            return false;
        if (isClearFrameFilter(filter))
            return matchesClearFilter(item, filter, mode, enchantStateChecker);
        String namedCategory = resolveNamedFrameFilterCategory(filter);
        if (namedCategory != null)
            return matchesNamedFrameFilterCategory(item.getType(), namedCategory);
        boolean directMatch = switch (mode) {
            case MATERIAL -> item.getType() == filter.getType();
            case ENCHANT_STATE -> item.getType() == filter.getType()
                    && enchantStateChecker.test(item) == enchantStateChecker.test(filter);
            case EXACT -> exactMatcher.test(item, filter);
        };
        if (directMatch)
            return true;
        if (isAllFrameFilter(filter))
            return matchesAllMaterialFamily(item.getType(), filter.getType());
        return false;
    }

    private boolean matchesClearFilter(ItemStack item, ItemStack filter, SubFrameFilterMode mode, Predicate<ItemStack> enchantStateChecker) {
        if (item.getType() != filter.getType())
            return false;
        if (mode == SubFrameFilterMode.ENCHANT_STATE)
            return enchantStateChecker.test(item) == enchantStateChecker.test(filter);
        return true;
    }

    private String resolveNamedFrameFilterCategory(ItemStack filter) {
        if (filter == null || filter.getType() != Material.NAME_TAG || !filter.hasItemMeta())
            return null;
        ItemMeta meta = filter.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return null;
        String normalized = ChatColor.stripColor(meta.getDisplayName()).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty())
            return null;
        if (containsCategoryKeyword(normalized, "combat", "戦闘"))
            return "combat";
        if (containsCategoryKeyword(normalized, "tools", "tool", "utilities", "utility", "道具と実用品", "道具", "実用品"))
            return "tools_utilities";
        if (containsCategoryKeyword(normalized, "functional", "機能性ブロック", "機能性"))
            return "functional_blocks";
        if (containsCategoryKeyword(normalized, "redstone", "レッドストーン系ブロック", "レッドストーン"))
            return "redstone_blocks";
        if (containsCategoryKeyword(normalized, "pickaxe", "ピッケル", "つるはし", "ツルハシ"))
            return "pickaxe";
        if (containsCategoryKeyword(normalized, "sword", "剣"))
            return "sword";
        if (containsCategoryKeyword(normalized, "shovel", "spade", "シャベル"))
            return "shovel";
        if (containsCategoryKeyword(normalized, "hoe", "クワ", "くわ"))
            return "hoe";
        if (containsCategoryKeyword(normalized, "axe", "斧"))
            return "axe";
        if (containsCategoryKeyword(normalized, "food", "edible", "食べ物", "食料"))
            return "food";
        if (containsCategoryKeyword(normalized, "helmet", "ヘルメット"))
            return "helmet";
        if (containsCategoryKeyword(normalized, "chestplate", "チェストプレート"))
            return "chestplate";
        if (containsCategoryKeyword(normalized, "leggings", "レギンス"))
            return "leggings";
        if (containsCategoryKeyword(normalized, "boots", "ブーツ"))
            return "boots";
        if (containsCategoryKeyword(normalized, "armor", "armour", "防具"))
            return "armor";
        if (containsCategoryKeyword(normalized, "crop", "farm", "作物", "農作物"))
            return "crop";
        if (containsCategoryKeyword(normalized, "ore", "mineral", "鉱物", "鉱石"))
            return "ore";
        if (containsCategoryKeyword(normalized, "ingredients", "ingredient", "materials", "material", "材料", "素材"))
            return "material";
        return null;
    }

    private boolean containsCategoryKeyword(String normalized, String... keywords) {
        if (normalized == null || normalized.isEmpty() || keywords == null)
            return false;
        for (String keyword : keywords) {
            if (keyword != null && !keyword.isEmpty() && normalized.contains(keyword))
                return true;
        }
        return false;
    }

    private boolean isAllFrameFilter(ItemStack filter) {
        if (filter == null || filter.getType() == Material.AIR || !filter.hasItemMeta())
            return false;
        ItemMeta meta = filter.getItemMeta();
        if (meta == null || !meta.hasDisplayName())
            return false;
        String normalized = ChatColor.stripColor(meta.getDisplayName()).trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty())
            return false;
        return ALL_FILTER_TOKEN.matcher(normalized).find();
    }

    private boolean matchesNamedFrameFilterCategory(Material material, String category) {
        if (material == null || category == null || category.isEmpty())
            return false;
        return switch (category) {
            case "food" -> matchesFoodCategory(material);
            case "combat" -> matchesCombatCategory(material);
            case "tools_utilities" -> matchesToolsUtilitiesCategory(material);
            case "functional_blocks" -> matchesFunctionalBlockCategory(material);
            case "redstone_blocks" -> matchesRedstoneBlockCategory(material);
            case "sword" -> isTagged(material, Tag.ITEMS_SWORDS);
            case "axe" -> isTagged(material, Tag.ITEMS_AXES);
            case "pickaxe" -> isTagged(material, Tag.ITEMS_PICKAXES);
            case "shovel" -> isTagged(material, Tag.ITEMS_SHOVELS);
            case "hoe" -> isTagged(material, Tag.ITEMS_HOES);
            case "helmet" -> matchesHelmetCategory(material);
            case "chestplate" -> matchesChestplateCategory(material);
            case "leggings" -> matchesLeggingsCategory(material);
            case "boots" -> matchesBootsCategory(material);
            case "armor" -> matchesArmorCategory(material);
            case "crop" -> matchesCropCategory(material);
            case "ore" -> matchesOreCategory(material);
            case "material" -> matchesMaterialCategory(material);
            default -> false;
        };
    }

    private boolean matchesFoodCategory(Material material) {
        if (material == null)
            return false;
        if (!material.isEdible())
            return false;
        return material != Material.SPIDER_EYE;
    }

    @SafeVarargs
    private final boolean isTaggedAny(Material material, Tag<Material>... tags) {
        if (material == null || tags == null)
            return false;
        for (Tag<Material> tag : tags) {
            if (isTagged(material, tag))
                return true;
        }
        return false;
    }

    private boolean isTagged(Material material, Tag<Material> tag) {
        return material != null && tag != null && tag.isTagged(material);
    }

    private boolean matchesCombatCategory(Material material) {
        if (material == null)
            return false;
        String name = material.name();
        return matchesArmorCategory(material)
                || isTaggedAny(material, Tag.ITEMS_SWORDS, Tag.ITEMS_AXES, Tag.ITEMS_ENCHANTABLE_WEAPON,
                Tag.ITEMS_ENCHANTABLE_BOW, Tag.ITEMS_ENCHANTABLE_CROSSBOW, Tag.ITEMS_ENCHANTABLE_TRIDENT, Tag.ITEMS_ENCHANTABLE_MACE, Tag.ITEMS_ARROWS)
                || name.equals("SHIELD")
                || name.equals("BOW")
                || name.equals("CROSSBOW")
                || name.equals("TRIDENT")
                || name.equals("MACE");
    }

    private boolean matchesToolsUtilitiesCategory(Material material) {
        if (material == null)
            return false;
        String name = material.name();
        return isTaggedAny(material, Tag.ITEMS_PICKAXES, Tag.ITEMS_AXES, Tag.ITEMS_SHOVELS, Tag.ITEMS_HOES, Tag.ITEMS_COMPASSES)
                || name.equals("SHEARS")
                || name.equals("FISHING_ROD")
                || name.equals("FLINT_AND_STEEL")
                || name.equals("CLOCK")
                || name.equals("SPYGLASS")
                || name.equals("BRUSH")
                || name.endsWith("_BUCKET")
                || name.equals("NAME_TAG")
                || name.equals("LEAD")
                || name.equals("ELYTRA")
                || name.equals("SADDLE")
                || name.equals("CARROT_ON_A_STICK")
                || name.equals("WARPED_FUNGUS_ON_A_STICK");
    }

    private boolean matchesFunctionalBlockCategory(Material material) {
        if (material == null || !material.isBlock())
            return false;
        String name = material.name();
        return isTaggedAny(material, Tag.ITEMS_DOORS, Tag.ITEMS_TRAPDOORS, Tag.ITEMS_FENCE_GATES, Tag.ITEMS_BUTTONS, Tag.ITEMS_RAILS,
                Tag.ITEMS_SHULKER_BOXES, Tag.ITEMS_SIGNS, Tag.ITEMS_BANNERS, Tag.ITEMS_BEDS)
                || name.contains("CHEST")
                || name.contains("BARREL")
                || name.contains("CRAFTING_TABLE")
                || name.contains("FURNACE")
                || name.contains("SMOKER")
                || name.contains("BLAST_FURNACE")
                || name.contains("CARTOGRAPHY_TABLE")
                || name.contains("FLETCHING_TABLE")
                || name.contains("SMITHING_TABLE")
                || name.contains("GRINDSTONE")
                || name.contains("STONECUTTER")
                || name.contains("LOOM")
                || name.contains("ANVIL")
                || name.contains("ENCHANTING_TABLE")
                || name.contains("BREWING_STAND")
                || name.contains("CAULDRON")
                || name.contains("JUKEBOX")
                || name.contains("LECTERN")
                || name.contains("BEACON")
                || name.contains("BELL")
                || name.contains("LODESTONE")
                || name.contains("RESPAWN_ANCHOR");
    }

    private boolean matchesRedstoneBlockCategory(Material material) {
        if (material == null || !material.isBlock())
            return false;
        String name = material.name();
        return name.contains("REDSTONE")
                || name.equals("REPEATER")
                || name.equals("COMPARATOR")
                || name.contains("PISTON")
                || name.equals("OBSERVER")
                || name.equals("DISPENSER")
                || name.equals("DROPPER")
                || name.equals("HOPPER")
                || name.equals("TARGET")
                || name.equals("NOTE_BLOCK")
                || name.equals("DAYLIGHT_DETECTOR")
                || name.equals("LEVER")
                || name.endsWith("_BUTTON")
                || name.endsWith("_PRESSURE_PLATE")
                || name.equals("TRIPWIRE_HOOK")
                || name.contains("RAIL")
                || name.equals("SCULK_SENSOR")
                || name.equals("CALIBRATED_SCULK_SENSOR")
                || name.equals("SLIME_BLOCK")
                || name.equals("HONEY_BLOCK");
    }

    private boolean matchesHelmetCategory(Material material) {
        return isTagged(material, Tag.ITEMS_HEAD_ARMOR);
    }

    private boolean matchesChestplateCategory(Material material) {
        return isTagged(material, Tag.ITEMS_CHEST_ARMOR);
    }

    private boolean matchesLeggingsCategory(Material material) {
        return isTagged(material, Tag.ITEMS_LEG_ARMOR);
    }

    private boolean matchesBootsCategory(Material material) {
        return isTagged(material, Tag.ITEMS_FOOT_ARMOR);
    }

    private boolean matchesArmorCategory(Material material) {
        return isTaggedAny(material, Tag.ITEMS_HEAD_ARMOR, Tag.ITEMS_CHEST_ARMOR, Tag.ITEMS_LEG_ARMOR, Tag.ITEMS_FOOT_ARMOR, Tag.ITEMS_TRIMMABLE_ARMOR);
    }

    private boolean matchesAllMaterialFamily(Material targetMaterial, Material baseMaterial) {
        if (targetMaterial == null || baseMaterial == null)
            return false;
        String target = targetMaterial.name();
        String base = baseMaterial.name();
        if (isTagged(baseMaterial, Tag.ITEMS_LEAVES))
            return isTagged(targetMaterial, Tag.ITEMS_LEAVES);
        if (isAnyGlassFamily(baseMaterial))
            return isAnyGlassFamily(targetMaterial);
        String resourceFamily = resolveResourceFamily(baseMaterial);
        if (resourceFamily != null)
            return isResourceFamilyMaterial(targetMaterial, resourceFamily);
        if (target.equals(base) || isDirectAllFamilyVariant(target, base))
            return true;
        if (base.contains("_"))
            return target.endsWith("_" + base) || target.contains("_" + base + "_");
        return matchesSingleTokenAllFamily(target, base);
    }

    private boolean isDirectAllFamilyVariant(String target, String base) {
        if (target == null || target.isEmpty() || base == null || base.isEmpty())
            return false;
        if (!target.startsWith(base + "_"))
            return false;
        String suffix = target.substring(base.length() + 1);
        if (suffix.isEmpty())
            return false;
        return switch (suffix) {
            case "SLAB", "STAIRS", "WALL", "BUTTON", "PRESSURE_PLATE" -> true;
            default -> false;
        };
    }

    private boolean matchesSingleTokenAllFamily(String target, String base) {
        if (target == null || target.isEmpty() || base == null || base.isEmpty())
            return false;
        String infix = "_" + base + "_";
        int infixIndex = target.indexOf(infix);
        if (infixIndex >= 0) {
            String prefix = target.substring(0, infixIndex);
            return isAllowedAllFamilyPrefix(prefix);
        }
        String suffix = "_" + base;
        if (target.endsWith(suffix)) {
            String prefix = target.substring(0, target.length() - suffix.length());
            return isAllowedAllFamilyPrefix(prefix);
        }
        return false;
    }

    private boolean isAllowedAllFamilyPrefix(String prefix) {
        if (prefix == null || prefix.isEmpty())
            return false;
        return switch (prefix) {
            case "SMOOTH", "POLISHED", "CHISELED", "CRACKED", "MOSSY", "INFESTED",
                    "CUT", "DARK", "WAXED", "EXPOSED", "WEATHERED", "OXIDIZED", "COARSE" -> true;
            default -> false;
        };
    }

    private boolean matchesSingleTokenRecipeFamily(Material targetMaterial, Material baseMaterial, String baseToken) {
        if (targetMaterial == null || baseMaterial == null || baseToken == null || baseToken.isEmpty())
            return false;
        String target = targetMaterial.name();
        if (!(target.startsWith(baseToken + "_")
                || target.endsWith("_" + baseToken)
                || target.contains("_" + baseToken + "_")))
            return false;
        Set<Material> reachable = resolveRecipeReachability(baseMaterial);
        return reachable.contains(targetMaterial);
    }

    private Set<Material> resolveRecipeReachability(Material baseMaterial) {
        if (baseMaterial == null)
            return Collections.emptySet();
        Set<Material> cached = recipeReachabilityCache.get(baseMaterial);
        if (cached != null)
            return cached;
        Map<Material, Set<Material>> graph = getRecipeIngredientToResultGraph();
        Set<Material> visited = new HashSet<>();
        ArrayDeque<Material> queue = new ArrayDeque<>();
        queue.add(baseMaterial);
        while (!queue.isEmpty()) {
            Material current = queue.removeFirst();
            Set<Material> next = graph.get(current);
            if (next == null || next.isEmpty())
                continue;
            for (Material material : next) {
                if (material == null || !visited.add(material))
                    continue;
                queue.addLast(material);
            }
        }
        Set<Material> unmodifiable = Collections.unmodifiableSet(visited);
        recipeReachabilityCache.put(baseMaterial, unmodifiable);
        return unmodifiable;
    }

    private synchronized Map<Material, Set<Material>> getRecipeIngredientToResultGraph() {
        if (recipeIngredientToResultGraph != null)
            return recipeIngredientToResultGraph;
        Map<Material, Set<Material>> graph = new HashMap<>();
        var iterator = Bukkit.recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe == null || recipe.getResult() == null)
                continue;
            Material result = recipe.getResult().getType();
            if (result == null || result == Material.AIR)
                continue;
            Set<Material> ingredients = new HashSet<>();
            collectRecipeIngredients(recipe, ingredients);
            for (Material ingredient : ingredients) {
                if (ingredient == null || ingredient == Material.AIR)
                    continue;
                graph.computeIfAbsent(ingredient, key -> new HashSet<>()).add(result);
            }
        }
        Map<Material, Set<Material>> immutableGraph = new HashMap<>();
        for (Map.Entry<Material, Set<Material>> entry : graph.entrySet()) {
            immutableGraph.put(entry.getKey(), Collections.unmodifiableSet(new HashSet<>(entry.getValue())));
        }
        recipeIngredientToResultGraph = Collections.unmodifiableMap(immutableGraph);
        return recipeIngredientToResultGraph;
    }

    private void collectRecipeIngredients(Recipe recipe, Set<Material> ingredients) {
        if (recipe instanceof ShapedRecipe shapedRecipe) {
            for (RecipeChoice choice : shapedRecipe.getChoiceMap().values()) {
                collectChoiceMaterials(choice, ingredients);
            }
            return;
        }
        if (recipe instanceof ShapelessRecipe shapelessRecipe) {
            for (RecipeChoice choice : shapelessRecipe.getChoiceList()) {
                collectChoiceMaterials(choice, ingredients);
            }
            return;
        }
        if (recipe instanceof StonecuttingRecipe stonecuttingRecipe) {
            collectChoiceMaterials(stonecuttingRecipe.getInputChoice(), ingredients);
            return;
        }
        if (recipe instanceof CookingRecipe<?> cookingRecipe) {
            collectChoiceMaterials(cookingRecipe.getInputChoice(), ingredients);
            return;
        }
        if (recipe instanceof SmithingTransformRecipe smithingTransformRecipe) {
            collectChoiceMaterials(smithingTransformRecipe.getBase(), ingredients);
            collectChoiceMaterials(smithingTransformRecipe.getAddition(), ingredients);
            return;
        }
        if (recipe instanceof TransmuteRecipe transmuteRecipe) {
            collectChoiceMaterials(transmuteRecipe.getInput(), ingredients);
            collectChoiceMaterials(transmuteRecipe.getMaterial(), ingredients);
        }
    }

    private void collectChoiceMaterials(RecipeChoice choice, Set<Material> ingredients) {
        if (choice == null || ingredients == null)
            return;
        if (choice instanceof RecipeChoice.MaterialChoice materialChoice) {
            ingredients.addAll(materialChoice.getChoices());
            return;
        }
        if (choice instanceof RecipeChoice.ExactChoice exactChoice) {
            for (ItemStack item : exactChoice.getChoices()) {
                if (item == null)
                    continue;
                ingredients.add(item.getType());
            }
        }
    }

    private boolean isAnyGlassFamily(Material material) {
        if (material == null)
            return false;
        String name = material.name();
        return name.equals("GLASS")
                || name.equals("TINTED_GLASS")
                || name.endsWith("_STAINED_GLASS")
                || name.equals("GLASS_PANE")
                || name.endsWith("_STAINED_GLASS_PANE");
    }

    private String resolveResourceFamily(Material material) {
        if (material == null)
            return null;
        String name = material.name();
        if (name.contains("IRON"))
            return "IRON";
        if (name.contains("GOLD"))
            return "GOLD";
        if (name.contains("COPPER"))
            return "COPPER";
        if (name.contains("DIAMOND"))
            return "DIAMOND";
        if (name.contains("EMERALD"))
            return "EMERALD";
        if (name.contains("REDSTONE"))
            return "REDSTONE";
        if (name.contains("COAL"))
            return "COAL";
        if (name.contains("LAPIS"))
            return "LAPIS";
        if (name.contains("QUARTZ"))
            return "QUARTZ";
        if (name.contains("NETHERITE"))
            return "NETHERITE";
        if (name.contains("AMETHYST"))
            return "AMETHYST";
        return null;
    }

    private boolean isResourceFamilyMaterial(Material material, String family) {
        if (material == null || family == null || family.isEmpty())
            return false;
        String name = material.name();
        if (!name.contains(family))
            return false;
        if (name.endsWith("_ORE") || name.contains("_ORE_"))
            return true;
        if (name.startsWith("RAW_") || name.contains("_RAW_") || name.endsWith("_RAW"))
            return true;
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_BLOCK"))
            return true;
        if (name.endsWith("_SHARD") || name.endsWith("_SCRAP") || name.endsWith("_CLUSTER") || name.endsWith("_GEM"))
            return true;
        return name.equals(family) || name.equals("LAPIS_LAZULI");
    }

    private boolean matchesCropCategory(Material material) {
        if (material == null)
            return false;
        if (isTagged(material, Tag.ITEMS_VILLAGER_PLANTABLE_SEEDS))
            return true;
        String name = material.name();
        return name.contains("WHEAT")
                || name.contains("CARROT")
                || name.contains("POTATO")
                || name.contains("BEETROOT")
                || name.contains("NETHER_WART")
                || name.contains("COCOA")
                || name.contains("MELON")
                || name.contains("PUMPKIN")
                || name.contains("TORCHFLOWER")
                || name.contains("SUGAR_CANE")
                || name.contains("BAMBOO")
                || name.contains("CACTUS")
                || name.contains("KELP")
                || name.contains("SWEET_BERRIES")
                || name.contains("GLOW_BERRIES")
                || name.contains("CHORUS_FRUIT")
                || name.contains("SEEDS");
    }

    private boolean matchesOreCategory(Material material) {
        if (material == null)
            return false;
        if (isTaggedAny(material, Tag.ITEMS_COAL_ORES, Tag.ITEMS_COPPER_ORES, Tag.ITEMS_DIAMOND_ORES, Tag.ITEMS_EMERALD_ORES,
                Tag.ITEMS_GOLD_ORES, Tag.ITEMS_IRON_ORES, Tag.ITEMS_LAPIS_ORES, Tag.ITEMS_REDSTONE_ORES))
            return true;
        if (isTaggedAny(material, Tag.ITEMS_COPPER_TOOL_MATERIALS, Tag.ITEMS_IRON_TOOL_MATERIALS, Tag.ITEMS_GOLD_TOOL_MATERIALS,
                Tag.ITEMS_DIAMOND_TOOL_MATERIALS, Tag.ITEMS_NETHERITE_TOOL_MATERIALS, Tag.ITEMS_COALS, Tag.ITEMS_BEACON_PAYMENT_ITEMS))
            return true;
        String name = material.name();
        return name.endsWith("_INGOT")
                || name.endsWith("_NUGGET")
                || name.endsWith("_RAW")
                || name.contains("RAW_")
                || name.endsWith("_GEM")
                || name.endsWith("_SHARD")
                || name.endsWith("_SCRAP");
    }

    private boolean matchesMaterialCategory(Material material) {
        if (material == null)
            return false;
        if (material.isEdible())
            return false;
        if (matchesCombatCategory(material) || matchesToolsUtilitiesCategory(material) || matchesRedstoneBlockCategory(material))
            return false;
        if (matchesOreCategory(material))
            return true;
        if (isTaggedAny(material, Tag.ITEMS_DYES, Tag.ITEMS_COALS, Tag.ITEMS_TRIM_MATERIALS, Tag.ITEMS_DECORATED_POT_INGREDIENTS,
                Tag.ITEMS_STONE_CRAFTING_MATERIALS, Tag.ITEMS_BREWING_FUEL, Tag.ITEMS_LOGS, Tag.ITEMS_PLANKS, Tag.ITEMS_VILLAGER_PLANTABLE_SEEDS))
            return true;
        String name = material.name();
        if (name.startsWith("RAW_") || name.contains("_RAW_"))
            return true;
        if (name.endsWith("_INGOT") || name.endsWith("_NUGGET") || name.endsWith("_GEM")
                || name.endsWith("_SHARD") || name.endsWith("_SCRAP")
                || name.endsWith("_DUST") || name.endsWith("_POWDER"))
            return true;
        return name.equals("STICK")
                || name.equals("STRING")
                || name.equals("PAPER")
                || name.equals("LEATHER")
                || name.equals("FEATHER")
                || name.equals("FLINT")
                || name.equals("BONE")
                || name.equals("BONE_MEAL")
                || name.equals("SLIME_BALL")
                || name.equals("CLAY_BALL")
                || name.equals("BRICK")
                || name.equals("NETHER_STAR")
                || name.equals("ENDER_PEARL")
                || name.equals("ENDER_EYE")
                || name.equals("BLAZE_ROD")
                || name.equals("BLAZE_POWDER")
                || name.equals("GHAST_TEAR")
                || name.equals("PHANTOM_MEMBRANE")
                || name.equals("RABBIT_HIDE")
                || name.equals("SCUTE")
                || name.equals("PRISMARINE_CRYSTALS")
                || name.equals("PRISMARINE_SHARD")
                || name.endsWith("_HIDE");
    }
}
