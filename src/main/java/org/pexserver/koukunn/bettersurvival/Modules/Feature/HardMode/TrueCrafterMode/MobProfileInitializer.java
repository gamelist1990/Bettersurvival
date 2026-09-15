package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Drowned;
import org.bukkit.entity.PiglinAbstract;
import org.bukkit.entity.Enderman;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.HashMap;
import java.util.Map;

/** 本家mob初期化処理の基礎属性・装備・常時効果をPaper APIで適用する。 */
public final class MobProfileInitializer {
    private final Map<Attribute, NamespacedKey> originalValueKeys = new HashMap<>();
    private final NamespacedKey endermanModeKey;
    private final NamespacedKey heatEffectKey;

    public MobProfileInitializer(Loader plugin) {
        endermanModeKey = new NamespacedKey(plugin, "truecrafter_enderman_mode");
        heatEffectKey = new NamespacedKey(plugin, "truecrafter_heat_effect");
        for (Attribute attribute : new Attribute[] {Attribute.MAX_HEALTH, Attribute.ATTACK_DAMAGE, Attribute.MOVEMENT_SPEED, Attribute.JUMP_STRENGTH, Attribute.STEP_HEIGHT, Attribute.KNOCKBACK_RESISTANCE, Attribute.SCALE, Attribute.FALL_DAMAGE_MULTIPLIER, Attribute.WATER_MOVEMENT_EFFICIENCY}) {
            originalValueKeys.put(attribute, new NamespacedKey(plugin, "truecrafter_original_" + attribute.key().value()));
        }
    }

    public void apply(LivingEntity entity, String variant, int heat) {
        switch (entity.getType()) {
            case ZOMBIE, HUSK, ZOMBIE_VILLAGER -> {
                org.bukkit.entity.Zombie zombie = (org.bukkit.entity.Zombie) entity;
                if (zombie.isAdult()) base(entity, Attribute.MOVEMENT_SPEED, variant.equals("zombie_brute") ? 0.25D : isSpear(zombie) ? 0.28D : 0.32D);
                base(entity, Attribute.STEP_HEIGHT, 1D);
            }
            case DROWNED -> drowned((Drowned) entity);
            case CREEPER -> creeper((Creeper) entity);
            case ENDERMAN -> enderman((Enderman) entity);
            case PILLAGER -> { base(entity, Attribute.MAX_HEALTH, 30D); base(entity, Attribute.KNOCKBACK_RESISTANCE, 0.3D); base(entity, Attribute.STEP_HEIGHT, 1D); }
            case VINDICATOR -> { base(entity, Attribute.MAX_HEALTH, 30D); base(entity, Attribute.MOVEMENT_SPEED, 0.3D); base(entity, Attribute.STEP_HEIGHT, 1D); }
            case EVOKER -> { base(entity, Attribute.MAX_HEALTH, 50D); base(entity, Attribute.KNOCKBACK_RESISTANCE, 1D); base(entity, Attribute.MOVEMENT_SPEED, 0.3D); base(entity, Attribute.STEP_HEIGHT, 1D); }
            case PIGLIN_BRUTE -> { base(entity, Attribute.KNOCKBACK_RESISTANCE, 0.3D); base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D); ((PiglinAbstract) entity).setImmuneToZombification(true); entity.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, Integer.MAX_VALUE, 0, false, false)); }
            case ZOMBIFIED_PIGLIN -> {
                base(entity, Attribute.MOVEMENT_SPEED, 0.23D);
                base(entity, Attribute.MAX_HEALTH, 15D);
                if (heat >= 4) base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
            }
            case PIGLIN -> piglin(entity);
            case SLIME, MAGMA_CUBE -> slime(entity);
            case SKELETON, STRAY, BOGGED, PARCHED -> base(entity, Attribute.STEP_HEIGHT, 1D);
            case WITHER_SKELETON -> witherSkeleton(entity);
            default -> { }
        }
        if (variant.equals("zombie_brute")) brute(entity);
        if (variant.equals("elite")) elite(entity);
        heal(entity);
    }

    public void restore(LivingEntity entity) {
        PersistentDataContainer data = entity.getPersistentDataContainer();
        for (Map.Entry<Attribute, NamespacedKey> entry : originalValueKeys.entrySet()) {
            Double original = data.get(entry.getValue(), PersistentDataType.DOUBLE);
            AttributeInstance instance = entity.getAttribute(entry.getKey());
            if (original != null && instance != null) instance.setBaseValue(original);
            data.remove(entry.getValue());
        }
        data.remove(endermanModeKey);
        String heatEffect = data.get(heatEffectKey, PersistentDataType.STRING);
        if (heatEffect != null) entity.removePotionEffect(effect(heatEffect));
        data.remove(heatEffectKey);
        heal(entity);
    }

    public void applyChaser(LivingEntity entity) {
        base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0.0D);
    }

    public void applyHeat(LivingEntity entity, int heat) {
        if (heat < 4) return;
        base(entity, Attribute.WATER_MOVEMENT_EFFICIENCY, heat == 4 ? 0.35D : 0.5D);
        double probability = heat == 4 ? 0.3D : 0.5D;
        if (java.util.concurrent.ThreadLocalRandom.current().nextDouble() >= probability) return;
        String name = switch (java.util.concurrent.ThreadLocalRandom.current().nextInt(8)) {
            case 0 -> "fire_resistance"; case 1 -> "infested"; case 2 -> "oozing"; case 3 -> "speed";
            case 4 -> "resistance"; case 5 -> "wind_charged"; case 6 -> "strength"; default -> "weaving";
        };
        entity.addPotionEffect(new PotionEffect(effect(name), Integer.MAX_VALUE, 0, false, false));
        entity.getPersistentDataContainer().set(heatEffectKey, PersistentDataType.STRING, name);
    }

    private PotionEffectType effect(String name) {
        return switch (name) {
            case "fire_resistance" -> PotionEffectType.FIRE_RESISTANCE; case "infested" -> PotionEffectType.INFESTED;
            case "oozing" -> PotionEffectType.OOZING; case "speed" -> PotionEffectType.SPEED;
            case "resistance" -> PotionEffectType.RESISTANCE; case "wind_charged" -> PotionEffectType.WIND_CHARGED;
            case "strength" -> PotionEffectType.STRENGTH; default -> PotionEffectType.WEAVING;
        };
    }

    private void brute(LivingEntity entity) {
        base(entity, Attribute.MAX_HEALTH, 30D); base(entity, Attribute.KNOCKBACK_RESISTANCE, 1D); base(entity, Attribute.SCALE, 1.25D); base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        ItemStack chestplate = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chestplate.getItemMeta();
        chestMeta.setColor(org.bukkit.Color.fromRGB(13619154));
        chestplate.setItemMeta(chestMeta);
        equipment.setChestplate(chestplate); equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS)); equipment.setBoots(new ItemStack(Material.IRON_BOOTS)); equipment.setItemInMainHand(new ItemStack(Material.IRON_AXE)); equipment.setHelmetDropChance(0F);
        entity.customName(net.kyori.adventure.text.Component.text("ゾンビブルート"));
    }

    private void elite(LivingEntity entity) {
        base(entity, Attribute.MAX_HEALTH, 24D); base(entity, Attribute.STEP_HEIGHT, 1D); base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
        String name = entity.getType() == org.bukkit.entity.EntityType.STRAY ? "エリートストレイ" : entity.getType() == org.bukkit.entity.EntityType.BOGGED ? "エリートボグド" : "エリートスケルトン";
        entity.customName(net.kyori.adventure.text.Component.text(name));
    }

    private void drowned(Drowned drowned) {
        if (drowned.isAdult()) base(drowned, Attribute.MOVEMENT_SPEED, 0.32D); base(drowned, Attribute.STEP_HEIGHT, 1D);
        EntityEquipment equipment = drowned.getEquipment();
        if (equipment == null) return;
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(7887164));
        helmet.setItemMeta(meta);
        equipment.setHelmet(helmet);
    }

    private void enderman(Enderman enderman) {
        boolean nonOverworld = enderman.getWorld().getEnvironment() != org.bukkit.World.Environment.NORMAL;
        if (nonOverworld && java.util.concurrent.ThreadLocalRandom.current().nextInt(8) >= 6) {
            enderman.getPersistentDataContainer().set(endermanModeKey, PersistentDataType.STRING, "outer_chaser");
            base(enderman, Attribute.MOVEMENT_SPEED, 0.12D); base(enderman, Attribute.STEP_HEIGHT, 1D); base(enderman, Attribute.MAX_HEALTH, 30D);
            return;
        }
        enderman.getPersistentDataContainer().set(endermanModeKey, PersistentDataType.STRING, nonOverworld ? "neutral" : "normal");
        base(enderman, Attribute.MOVEMENT_SPEED, 0.25D); base(enderman, Attribute.STEP_HEIGHT, 1D); base(enderman, Attribute.KNOCKBACK_RESISTANCE, 0.3D);
    }

    private void creeper(Creeper creeper) { base(creeper, Attribute.MOVEMENT_SPEED, 0.3D); base(creeper, Attribute.STEP_HEIGHT, 1D); creeper.setMaxFuseTicks(9999); }

    private void piglin(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        boolean crossbow = equipment != null && equipment.getItemInMainHand().getType() == Material.CROSSBOW;
        boolean spear = isSpear(entity);
        base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
        base(entity, Attribute.MAX_HEALTH, crossbow ? 20D : 24D);
        base(entity, Attribute.KNOCKBACK_RESISTANCE, crossbow ? 0.15D : spear ? 0.2D : 0.3D);
        if (spear) base(entity, Attribute.MOVEMENT_SPEED, 0.28D);
        ((PiglinAbstract) entity).setImmuneToZombification(true);
        if (crossbow) {
            equipment.getItemInMainHand().addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FLAME, 1);
            return;
        }
        if (equipment != null) equipment.getItemInMainHand().addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.FIRE_ASPECT, 1);
    }

    private void slime(LivingEntity entity) {
        int size = ((org.bukkit.entity.Slime) entity).getSize();
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) base(entity, Attribute.MAX_HEALTH, health.getBaseValue());
        base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
        if (size == 0) { base(entity, Attribute.MOVEMENT_SPEED, 0.5D); base(entity, Attribute.JUMP_STRENGTH, 0.5D); }
        if (size == 1) { base(entity, Attribute.MOVEMENT_SPEED, 0.7D); base(entity, Attribute.JUMP_STRENGTH, 0.8D); }
        if (size == 3) { base(entity, Attribute.MOVEMENT_SPEED, 0.9D); base(entity, Attribute.JUMP_STRENGTH, 1D); }
    }

    private void witherSkeleton(LivingEntity entity) {
        base(entity, Attribute.MAX_HEALTH, 24D);
        base(entity, Attribute.STEP_HEIGHT, 1D);
        base(entity, Attribute.FALL_DAMAGE_MULTIPLIER, 0D);
        equipBowIfEmpty(entity);
    }
    private void equipBowIfEmpty(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        // 本家 enemy.wither_skeleton/init は既存の石剣を含め、常に弓へ置換する。
        if (equipment != null) equipment.setItemInMainHand(new ItemStack(Material.BOW));
    }

    private boolean isSpear(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return false;
        return switch (equipment.getItemInMainHand().getType()) {
            case WOODEN_SPEAR, STONE_SPEAR, COPPER_SPEAR, IRON_SPEAR, GOLDEN_SPEAR, DIAMOND_SPEAR, NETHERITE_SPEAR -> true;
            default -> false;
        };
    }

    private void base(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        NamespacedKey key = originalValueKeys.get(attribute);
        PersistentDataContainer data = entity.getPersistentDataContainer();
        if (!data.has(key, PersistentDataType.DOUBLE)) data.set(key, PersistentDataType.DOUBLE, instance.getBaseValue());
        instance.setBaseValue(value);
    }

    private void heal(LivingEntity entity) { AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH); if (health != null) entity.setHealth(health.getValue()); }
}
