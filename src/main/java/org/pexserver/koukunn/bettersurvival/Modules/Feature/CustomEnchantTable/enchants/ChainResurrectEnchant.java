package org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.enchants;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CustomEnchantTable.api.CustomEnchant;
import org.pexserver.koukunn.bettersurvival.Modules.ItemCombineModule;

import java.util.List;

/**
 * 連鎖蘇生 (chainresurrect)。
 *
 * エンチャント済みトーテムに素のトーテムを合成 (ドロップして隣接) することで
 * 使用回数を増やせるカスタムトーテム。死亡時に1回分消費して蘇生する。
 *
 * 最大使用回数はエンチャントレベルで制限される。
 * Lv1: 最大3回 / Lv2: 最大5回 / Lv3: 最大10回
 */
public class ChainResurrectEnchant extends CustomEnchant {

    private final NamespacedKey chargeKey;
    private final ItemCombineModule itemCombineModule;

    public ChainResurrectEnchant(Loader plugin, ItemCombineModule itemCombineModule) {
        super(plugin);
        this.chargeKey = new NamespacedKey(plugin, "chainresurrect_charges");
        this.itemCombineModule = itemCombineModule;
        registerRecipe();
    }

    @Override
    public String id() {
        return "chainresurrect";
    }

    @Override
    public String displayName() {
        return "連鎖蘇生";
    }

    @Override
    public String description() {
        return "§7トーテムを複数回使えるようにする"
                + "\n§7素のトーテムをドロップ合成で使用回数を追加"
                + "\n§7Lv1: 最大3回 / Lv2: 最大5回 / Lv3: 最大10回";
    }

    @Override
    public Material icon() {
        return Material.TOTEM_OF_UNDYING;
    }

    @Override
    public String vanillaParentName() {
        return "不死のトーテム";
    }

    @Override
    public int maxLevel() {
        return 3;
    }

    @Override
    public boolean supports(Material type) {
        return type == Material.TOTEM_OF_UNDYING;
    }

    @Override
    public List<ItemStack> upgradeCost(int nextLevel) {
        return switch (nextLevel) {
            case 1 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 32),
                    new ItemStack(Material.TOTEM_OF_UNDYING, 1),
                    new ItemStack(Material.EMERALD, 8));
            case 2 -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 48),
                    new ItemStack(Material.TOTEM_OF_UNDYING, 2),
                    new ItemStack(Material.EMERALD, 16));
            default -> List.of(
                    new ItemStack(Material.LAPIS_LAZULI, 64),
                    new ItemStack(Material.TOTEM_OF_UNDYING, 4),
                    new ItemStack(Material.EMERALD, 32));
        };
    }

    private int maxCharges(int level) {
        return switch (Math.max(1, Math.min(maxLevel(), level))) {
            case 1 -> 3;
            case 2 -> 5;
            default -> 10;
        };
    }

    private int getCharges(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return 0;
        }
        Integer val = item.getItemMeta().getPersistentDataContainer().get(chargeKey, PersistentDataType.INTEGER);
        return val == null ? 0 : val;
    }

    private void setCharges(ItemStack item, int charges) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(chargeKey, PersistentDataType.INTEGER, charges);
        updateLoreCharges(meta, charges);
        item.setItemMeta(meta);
    }

    private void updateLoreCharges(ItemMeta meta, int charges) {
        List<net.kyori.adventure.text.Component> lore = meta.lore() == null
                ? new java.util.ArrayList<>()
                : new java.util.ArrayList<>(meta.lore());
        lore.removeIf(c -> {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(c);
            return plain.startsWith("残り使用回数:");
        });
        lore.add(ComponentUtils.legacy("§e残り使用回数: §f" + charges + "回")
                .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore);
    }

    private boolean isEnchantedTotem(ItemStack item) {
        return item != null
                && item.getType() == Material.TOTEM_OF_UNDYING
                && levelOf(item) > 0;
    }

    private boolean isPlainTotem(ItemStack item) {
        return item != null
                && item.getType() == Material.TOTEM_OF_UNDYING
                && levelOf(item) <= 0
                && getCharges(item) <= 0;
    }

    private void registerRecipe() {
        itemCombineModule.recipe("chainresurrect_charge")
                .first(this::isEnchantedTotem)
                .second(this::isPlainTotem)
                .groundRadius(0.8D)
                .airRadius(1.5D)
                .verticalRadius(1.5D)
                .allowAirCombine(false)
                .then(match -> {
                    ItemStack enchanted = match.first().getItemStack().clone();
                    int level = levelOf(enchanted);
                    int current = getCharges(enchanted);
                    int cap = maxCharges(level);
                    if (current >= cap) {
                        return;
                    }
                    match.consumeSecond(1);
                    setCharges(enchanted, current + 1);
                    match.first().setItemStack(enchanted);
                    Location center = match.center();
                    center.getWorld().playSound(center, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.2f);
                    center.getWorld().spawnParticle(
                            org.bukkit.Particle.ENCHANT, center, 30, 0.3, 0.3, 0.3, 0.5);
                });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onLethalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (event.getFinalDamage() < player.getHealth()) {
            return;
        }

        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        ItemStack totem = null;
        boolean isMain = false;

        if (isEnchantedTotem(main) && getCharges(main) > 0) {
            totem = main;
            isMain = true;
        } else if (isEnchantedTotem(off) && getCharges(off) > 0) {
            totem = off;
        }

        if (totem == null) {
            return;
        }

        int charges = getCharges(totem);
        if (charges <= 0) {
            return;
        }

        event.setCancelled(true);

        int newCharges = charges - 1;
        setCharges(totem, newCharges);
        if (isMain) {
            player.getInventory().setItemInMainHand(totem);
        } else {
            player.getInventory().setItemInOffHand(totem);
        }

        player.playEffect(org.bukkit.EntityEffect.PROTECTED_FROM_DEATH);
        player.setHealth(1.0);
        player.setFoodLevel(20);
        player.setSaturation(5.0f);
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.REGENERATION, 900, 1));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.ABSORPTION, 100, 1));
        player.addPotionEffect(new org.bukkit.potion.PotionEffect(
                org.bukkit.potion.PotionEffectType.FIRE_RESISTANCE, 800, 0));
        Location loc = player.getLocation();
        loc.getWorld().playSound(loc, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
        loc.getWorld().spawnParticle(org.bukkit.Particle.TOTEM_OF_UNDYING, loc.add(0, 1, 0), 60, 0.5, 0.5, 0.5, 0.3);
        String msg = newCharges > 0
                ? "§6✦ 連鎖蘇生発動! §7残り §e" + newCharges + "§7回"
                : "§6✦ 連鎖蘇生発動! §7(残り回数なし)";
        player.sendActionBar(ComponentUtils.legacy(msg));
    }

    @Override
    public void shutdown() {
        itemCombineModule.unregister("chainresurrect_charge");
    }
}
