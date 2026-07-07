package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.List;

/**
 * カスタムエンチャントの基底クラス。
 *
 * レベルはアイテムの PersistentDataContainer (cench_&lt;id&gt;) に保存する。
 * 表示は Vanilla のエンチャント欄と同じ見た目 (灰色・非イタリック) の lore 行を
 * lore 先頭に置くことで「エンチャント一覧の続き」として描画され、
 * さらにエンチャントの光沢 (glint) も付与するため本物のエンチャントのように見える。
 * {@link #vanillaParentName()} を返すと「[◯◯系]」と Vanilla エンチャントの
 * 派生であることが表示される。
 *
 * 効果のイベント処理は各サブクラス ({@link Listener}) が担当する —
 * 新しいエンチャントを増やすときは enchants/ 配下にクラスを1つ足して
 * {@link CustomEnchantRegistry#register} するだけでよい。
 */
public abstract class CustomEnchant implements Listener {

    /** 旧形式 lore 行のプレフィックス (移行削除用) */
    private static final String LEGACY_MARK_PLAIN = "✦ ";

    protected final Loader plugin;
    private NamespacedKey key;

    protected CustomEnchant(Loader plugin) {
        this.plugin = plugin;
    }

    /** 内部ID (英小文字) */
    public abstract String id();

    /** 表示名 (例: 採掘加速) */
    public abstract String displayName();

    /** UI に表示する説明 (\n 区切り可) */
    public abstract String description();

    /** UI ボタンのアイコン */
    public abstract Material icon();

    /** 最大レベル */
    public abstract int maxLevel();

    /** この道具/装備に付与できるか */
    public abstract boolean supports(Material type);

    /** 次のレベルへ上げるための素材コスト */
    public abstract List<ItemStack> upgradeCost(int nextLevel);

    /**
     * 派生元の Vanilla エンチャント名 (例: "効率強化")。
     * null を返すと独自エンチャントとして表示される。
     */
    public String vanillaParentName() {
        return null;
    }

    /** モジュール停止時の後始末 (必要なら上書き) */
    public void shutdown() {
    }

    public final NamespacedKey key() {
        if (key == null) {
            key = new NamespacedKey(plugin, "cench_" + id());
        }
        return key;
    }

    private Enchantment registryEnchantment;
    private boolean registryChecked;

    /**
     * bootstrap で登録された「本物のエンチャント」(bettersurvival:&lt;id&gt;)。
     * paper-plugin として起動していない環境 (Spigot 等) では null。
     */
    public final Enchantment registryEnchantment() {
        if (!registryChecked) {
            registryChecked = true;
            try {
                registryEnchantment = RegistryAccess.registryAccess()
                        .getRegistry(RegistryKey.ENCHANTMENT)
                        .get(Key.key(CustomEnchantDefinitions.NAMESPACE, id()));
            } catch (Throwable ignored) {
                registryEnchantment = null;
            }
        }
        return registryEnchantment;
    }

    /** アイテムに付与されているこのエンチャントのレベル (未付与なら 0) */
    public final int levelOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return 0;
        }
        ItemMeta meta = item.getItemMeta();
        Enchantment real = registryEnchantment();
        if (real != null) {
            int level = meta.getEnchantLevel(real);
            if (level > 0) {
                return level;
            }
        }
        // 旧形式 (PDC) のフォールバック
        Integer value = meta.getPersistentDataContainer().get(key(), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    /**
     * レベルを設定する。表示は Registry 登録済みの本物のエンチャントが担い、
     * 効果判定用に PDC にも常に同じレベルを書く (環境差異があっても効果が確実に動く)。
     */
    public final void applyLevel(ItemStack item, int level) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        // 効果判定の信頼できるソースとして PDC は常に更新する
        if (level <= 0) {
            meta.getPersistentDataContainer().remove(key());
        } else {
            meta.getPersistentDataContainer().set(key(), PersistentDataType.INTEGER, level);
        }
        removeLoreLines(meta);
        Enchantment real = registryEnchantment();
        if (real != null) {
            // 本の場合は storedEnchant として付ける (エンチャント本の正しい表示のため)
            if (meta instanceof EnchantmentStorageMeta storageMeta) {
                if (level <= 0) {
                    storageMeta.removeStoredEnchant(real);
                } else {
                    storageMeta.addStoredEnchant(real, level, true);
                }
            } else {
                // 表示: 本物のエンチャントとして (Vanilla のエンチャント欄に並ぶ)
                if (level <= 0) {
                    meta.removeEnchant(real);
                } else {
                    meta.addEnchant(real, level, true);
                }
            }
        } else if (level > 0) {
            // フォールバック (bootstrap が動かない環境): Vanilla 風 lore 表示 + 光沢
            try {
                meta.setEnchantmentGlintOverride(Boolean.TRUE);
            } catch (Throwable ignored) {
            }
            addLoreLine(meta, level);
        }
        item.setItemMeta(meta);
    }

    private void removeLoreLines(ItemMeta meta) {
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        String name = displayName();
        lore.removeIf(component -> {
            if (component == null) {
                return false;
            }
            String plain = PlainTextComponentSerializer.plainText().serialize(component);
            return plain.startsWith(name + " ") || plain.startsWith(LEGACY_MARK_PLAIN + name);
        });
        meta.lore(lore.isEmpty() ? null : lore);
    }

    private void addLoreLine(ItemMeta meta, int level) {
        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        // Vanilla のエンチャント欄と同じ灰色・非イタリック。lore 先頭に置くことで
        // ツールチップ上はエンチャント一覧の続きとして見える
        String parent = vanillaParentName();
        String text = "§7" + displayName() + " " + roman(level) + (parent == null ? "" : " §8[" + parent + "系]");
        lore.add(0, ComponentUtils.legacy(text).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
    }

    /** レベルのローマ数字表記 */
    public static String roman(int level) {
        return switch (level) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(level);
        };
    }

    protected static boolean isMiningTool(Material type) {
        String name = type.name();
        return name.endsWith("_PICKAXE") || name.endsWith("_AXE") || name.endsWith("_SHOVEL") || name.endsWith("_HOE");
    }

    /** 本 (BOOK / ENCHANTED_BOOK) 判定。本には全エンチャントを付与でき、金床で対象装備へ転写する。 */
    public static boolean isBookMaterial(Material type) {
        return type == Material.BOOK || type == Material.ENCHANTED_BOOK;
    }
}
