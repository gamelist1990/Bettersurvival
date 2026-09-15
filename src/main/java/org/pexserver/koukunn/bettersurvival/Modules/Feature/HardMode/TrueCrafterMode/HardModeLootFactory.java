package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.concurrent.ThreadLocalRandom;

/** asset:enhance/heat_4・heat_5の武器ルートテーブルをItemStack APIで生成する。 */
public final class HardModeLootFactory {
    public void applySpawnEquipment(LivingEntity entity, int heat) {
        if (heat < 4 || entity.getEquipment() == null || !entity.getEquipment().getItemInMainHand().getType().isAir()) return;
        int denominator = chanceDenominator(entity.getType(), heat);
        if (denominator == 0 || ThreadLocalRandom.current().nextInt(denominator) != 0) return;
        ItemStack weapon = weapon(entity, heat);
        if (weapon == null) return;
        EntityEquipment equipment = entity.getEquipment();
        equipment.setItemInMainHand(weapon);
        equipment.setItemInMainHandDropChance(0.085F);
    }

    public ItemStack eliteHatchet(boolean shieldCracker, int heat) {
        ItemStack item = new ItemStack(shieldCracker ? Material.IRON_AXE : Material.STONE_AXE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(net.kyori.adventure.text.Component.text(shieldCracker ? "盾砕の手斧" : "ぼろい手斧"));
        meta.addAttributeModifier(Attribute.ATTACK_DAMAGE, new AttributeModifier(new NamespacedKey("bettersurvival", shieldCracker ? "shield_crasher_damage" : "shabby_damage"), shieldCracker ? 6.0D : 3.0D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        meta.addAttributeModifier(Attribute.ATTACK_SPEED, new AttributeModifier(new NamespacedKey("bettersurvival", shieldCracker ? "shield_crasher_speed" : "shabby_speed"), -2.8D, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
        item.setItemMeta(meta);
        if (heat == 4) {
            if (ThreadLocalRandom.current().nextInt(6) < 2) enchant(item, Enchantment.SHARPNESS, 1);
            else if (ThreadLocalRandom.current().nextInt(6) < 2) enchant(item, Enchantment.KNOCKBACK, 1);
            else if (ThreadLocalRandom.current().nextInt(6) == 0) enchant(item, Enchantment.SHARPNESS, 2);
        } else if (heat >= 5) {
            enchant(item, Enchantment.SHARPNESS, random(0, shieldCracker ? 3 : 2));
            enchant(item, Enchantment.KNOCKBACK, random(0, shieldCracker ? 3 : 2));
            vanish(item);
        }
        return item;
    }

    private int chanceDenominator(EntityType type, int heat) {
        if (heat == 4) return switch (type) {
            case ZOMBIE -> 50;
            case PIGLIN_BRUTE -> 30;
            case SKELETON, STRAY, BOGGED, PARCHED, WITHER_SKELETON, PILLAGER, VINDICATOR, PIGLIN, ZOMBIFIED_PIGLIN -> 40;
            default -> 0;
        };
        return switch (type) {
            case ZOMBIE -> 50;
            case SKELETON, STRAY, BOGGED, PARCHED, WITHER_SKELETON, PILLAGER, VINDICATOR, PIGLIN, PIGLIN_BRUTE, ZOMBIFIED_PIGLIN -> 60;
            default -> 0;
        };
    }

    private ItemStack weapon(LivingEntity entity, int heat) {
        EntityType type = entity.getType();
        return switch (type) {
            case SKELETON, STRAY, BOGGED, PARCHED, WITHER_SKELETON -> heat == 4 ? weightedBow4() : bow5();
            case PILLAGER -> heat == 4 ? crossbow4() : crossbow5();
            case VINDICATOR -> heat == 4 ? axe4() : axe5();
            case PIGLIN -> entity.getEquipment().getItemInMainHand().getType() == Material.CROSSBOW
                    ? (heat == 4 ? crossbow4() : crossbow5()) : (heat == 4 ? golden4() : golden5());
            case ZOMBIFIED_PIGLIN -> heat == 4 ? golden4() : golden5();
            case PIGLIN_BRUTE -> heat == 4 ? golden4() : brute5();
            case ZOMBIE -> heat == 4 ? melee4() : melee5();
            default -> null;
        };
    }

    private ItemStack weightedBow4() {
        int roll = ThreadLocalRandom.current().nextInt(15);
        return roll < 3 ? enchanted(Material.BOW, Enchantment.POWER, 1)
                : roll < 6 ? enchanted(Material.BOW, Enchantment.FLAME, 1)
                : roll < 9 ? enchanted(Material.BOW, Enchantment.PUNCH, 1)
                : roll < 11 ? enchanted(Material.BOW, Enchantment.POWER, 2)
                : roll < 13 ? enchanted(Material.BOW, Enchantment.FLAME, 2)
                : enchanted(Material.BOW, Enchantment.PUNCH, 2);
    }

    private ItemStack bow5() {
        ItemStack item = new ItemStack(Material.BOW);
        enchant(item, Enchantment.POWER, random(0, 4));
        enchant(item, Enchantment.FLAME, random(0, 2));
        enchant(item, Enchantment.PUNCH, random(0, 2));
        vanish(item);
        return item;
    }

    private ItemStack crossbow4() {
        int roll = ThreadLocalRandom.current().nextInt(5);
        return roll < 2 ? enchanted(Material.CROSSBOW, Enchantment.QUICK_CHARGE, 2)
                : roll < 4 ? enchanted(Material.CROSSBOW, Enchantment.PIERCING, 2)
                : enchanted(Material.CROSSBOW, Enchantment.MULTISHOT, 1);
    }

    private ItemStack crossbow5() {
        ItemStack item = new ItemStack(Material.CROSSBOW);
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) {
            enchant(item, Enchantment.QUICK_CHARGE, random(1, 3)); enchant(item, Enchantment.PIERCING, random(0, 1)); enchant(item, Enchantment.POWER, random(1, 4));
        } else if (roll == 1) {
            enchant(item, Enchantment.MULTISHOT, 3); enchant(item, Enchantment.PUNCH, random(0, 1)); enchant(item, Enchantment.POWER, random(2, 3));
        } else {
            enchant(item, Enchantment.QUICK_CHARGE, 5); enchant(item, Enchantment.PIERCING, random(1, 5)); enchant(item, Enchantment.POWER, random(1, 3));
        }
        vanish(item); return item;
    }

    private ItemStack axe4() {
        return switch (ThreadLocalRandom.current().nextInt(3)) {
            case 0 -> enchanted(Material.DIAMOND_AXE, Enchantment.SHARPNESS, 1);
            case 1 -> enchanted(Material.DIAMOND_AXE, Enchantment.KNOCKBACK, 1);
            default -> enchanted(Material.DIAMOND_AXE, Enchantment.FIRE_ASPECT, 1);
        };
    }

    private ItemStack axe5() {
        ItemStack item = new ItemStack(Material.DIAMOND_AXE);
        enchant(item, Enchantment.SHARPNESS, random(1, 3)); enchant(item, Enchantment.FIRE_ASPECT, random(0, 2)); enchant(item, Enchantment.KNOCKBACK, random(0, 3)); vanish(item); return item;
    }

    private ItemStack golden4() {
        int roll = ThreadLocalRandom.current().nextInt(9);
        return roll < 4 ? enchanted(Material.GOLDEN_SWORD, Enchantment.SHARPNESS, 1)
                : roll < 7 ? enchanted(Material.GOLDEN_SWORD, Enchantment.SHARPNESS, 3)
                : enchanted(Material.GOLDEN_AXE, Enchantment.FIRE_ASPECT, 1);
    }

    private ItemStack golden5() {
        ItemStack item = ThreadLocalRandom.current().nextInt(3) == 2 ? new ItemStack(Material.GOLDEN_AXE) : new ItemStack(Material.GOLDEN_SWORD);
        if (item.getType() == Material.GOLDEN_AXE) enchant(item, Enchantment.FIRE_ASPECT, 1);
        else if (ThreadLocalRandom.current().nextBoolean()) enchant(item, Enchantment.SHARPNESS, random(2, 4));
        else enchant(item, Enchantment.KNOCKBACK, 3);
        vanish(item); return item;
    }

    private ItemStack brute5() { ItemStack item = new ItemStack(Material.NETHERITE_AXE); enchant(item, Enchantment.KNOCKBACK, 3); enchant(item, Enchantment.FIRE_ASPECT, 1); vanish(item); return item; }
    private ItemStack melee4() { Material[] choices = {Material.WOODEN_SWORD, Material.WOODEN_SWORD, Material.WOODEN_SWORD, Material.WOODEN_SWORD, Material.IRON_SWORD, Material.IRON_SWORD, Material.IRON_SWORD, Material.STONE_AXE, Material.STONE_AXE, Material.IRON_AXE, Material.IRON_AXE}; return new ItemStack(choices[ThreadLocalRandom.current().nextInt(choices.length)]); }
    private ItemStack melee5() { int roll = ThreadLocalRandom.current().nextInt(10); if (roll < 4) return new ItemStack(Material.IRON_SWORD); if (roll < 6) return new ItemStack(Material.IRON_AXE); if (roll < 9) { ItemStack item = new ItemStack(Material.DIAMOND_SWORD); enchant(item, Enchantment.SHARPNESS, random(0, 5)); vanish(item); return item; } ItemStack item = new ItemStack(Material.DIAMOND_AXE); enchant(item, Enchantment.KNOCKBACK, random(0, 2)); vanish(item); return item; }
    private ItemStack enchanted(Material material, Enchantment enchantment, int level) { ItemStack item = new ItemStack(material); enchant(item, enchantment, level); return item; }
    private void enchant(ItemStack item, Enchantment enchantment, int level) { if (level > 0) item.addUnsafeEnchantment(enchantment, level); }
    private void vanish(ItemStack item) { item.addUnsafeEnchantment(Enchantment.VANISHING_CURSE, 1); }
    private int random(int min, int max) { return ThreadLocalRandom.current().nextInt(min, max + 1); }
}
