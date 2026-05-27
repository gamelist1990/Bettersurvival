package org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.worker;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.CopperGolem;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.LivingEntity;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.ContainerTarget;
import org.pexserver.koukunn.bettersurvival.Modules.Feature.CopperGolem.model.GolemProfile;

import com.google.common.collect.Multimap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class CombatWorker {

    private static final double BASE_SEARCH_RANGE = 10.0D;
    private static final double ATTACK_REACH_DISTANCE = 2.6D;
    private static final double RANGE_RETURN_MARGIN = 2.0D;
    private final Map<UUID, Long> nextAttackTickByGolem = new HashMap<>();

    public int runCombatWorker(GolemProfile profile, CopperGolem golem, int maxRange) {
        ItemStack weapon = golem.getEquipment().getItemInOffHand();
        if (!isCombatWeapon(weapon)) {
            golem.getPathfinder().stopPathfinding();
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            nextAttackTickByGolem.remove(golem.getUniqueId());
            return 0;
        }
        if (profile.targets().isEmpty()) {
            golem.getPathfinder().stopPathfinding();
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            nextAttackTickByGolem.remove(golem.getUniqueId());
            return 0;
        }

        int range = Math.max(1, Math.min(profile.range(), maxRange));
        profile.setRange(range);
        double searchRange = Math.max(BASE_SEARCH_RANGE, range);
        Location searchCenter = resolveSearchCenter(profile.targets(), golem.getLocation());
        if (searchCenter.getWorld() == null || golem.getLocation().getWorld() == null) {
            golem.getPathfinder().stopPathfinding();
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }
        if (!Objects.equals(searchCenter.getWorld(), golem.getLocation().getWorld())) {
            golem.getPathfinder().stopPathfinding();
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }
        double allowedDistance = range + RANGE_RETURN_MARGIN;
        if (golem.getLocation().distanceSquared(searchCenter) > (allowedDistance * allowedDistance)) {
            golem.getPathfinder().moveTo(searchCenter, 1.1D);
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }

        LivingEntity target = findNearestEnemy(golem, searchCenter, searchRange);
        if (target == null) {
            golem.getPathfinder().stopPathfinding();
            golem.setTarget(null);
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }

        golem.setTarget(target);
        golem.lookAt(target);

        double distanceSquared = golem.getLocation().distanceSquared(target.getLocation());
        if (distanceSquared > (ATTACK_REACH_DISTANCE * ATTACK_REACH_DISTANCE)) {
            golem.getPathfinder().moveTo(target, 1.2D);
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }

        long gameTime = golem.getWorld().getGameTime();
        long nextAttackTick = nextAttackTickByGolem.getOrDefault(golem.getUniqueId(), 0L);
        if (gameTime < nextAttackTick) {
            golem.setGolemState(CopperGolem.State.IDLE);
            return 0;
        }

        golem.getPathfinder().stopPathfinding();
        double attackDamage = resolveAttackDamage(weapon, target);
        target.damage(attackDamage, golem);
        applyWeaponCombatEffects(golem, target, attackDamage, weapon);
        playAttackAnimation(golem, target.getLocation());
        golem.swingHand(EquipmentSlot.OFF_HAND);
        golem.swingHand(EquipmentSlot.HAND);
        golem.damageItemStack(EquipmentSlot.OFF_HAND, 1);
        nextAttackTickByGolem.put(golem.getUniqueId(), gameTime + attackCooldownTicks(weapon));
        golem.setGolemState(CopperGolem.State.IDLE);
        return 1;
    }

    public void storeDropsToTargets(GolemProfile profile, CopperGolem golem, List<ItemStack> drops) {
        if (drops == null || drops.isEmpty()) {
            return;
        }
        storeToContainers(profile.targets(), golem.getLocation(), drops);
    }

    public int applyMendingAndReduceExp(CopperGolem golem, int droppedExp) {
        if (droppedExp <= 0) {
            return 0;
        }
        int remained = droppedExp;
        EquipmentSlot[] slots = {
                EquipmentSlot.HAND,
                EquipmentSlot.OFF_HAND,
                EquipmentSlot.HEAD,
                EquipmentSlot.CHEST,
                EquipmentSlot.LEGS,
                EquipmentSlot.FEET
        };
        for (EquipmentSlot slot : slots) {
            if (remained <= 0) {
                break;
            }
            ItemStack equipped = golem.getEquipment().getItem(slot);
            if (equipped == null || equipped.getType().isAir() || equipped.getAmount() <= 0) {
                continue;
            }
            if (!equipped.containsEnchantment(org.bukkit.enchantments.Enchantment.MENDING)) {
                continue;
            }
            if (!(equipped.getItemMeta() instanceof Damageable damageable)) {
                continue;
            }
            int currentDamage = damageable.getDamage();
            if (currentDamage <= 0) {
                continue;
            }
            int repairAmount = Math.min(currentDamage, remained * 2);
            if (repairAmount <= 0) {
                continue;
            }
            damageable.setDamage(currentDamage - repairAmount);
            equipped.setItemMeta(damageable);
            golem.getEquipment().setItem(slot, equipped, true);
            int usedExp = (repairAmount + 1) / 2;
            remained = Math.max(0, remained - usedExp);
        }
        return remained;
    }

    public boolean isCombatWeapon(ItemStack stack) {
        return stack != null
                && !stack.getType().isAir()
                && stack.getAmount() > 0
                && stack.getType().getMaxDurability() > 0;
    }

    public void clearTracking(UUID golemId) {
        if (golemId == null) {
            return;
        }
        nextAttackTickByGolem.remove(golemId);
    }

    private LivingEntity findNearestEnemy(CopperGolem golem, Location center, double range) {
        List<LivingEntity> candidates = new ArrayList<>(center.getNearbyLivingEntities(
                range,
                entity -> entity instanceof Enemy
                        && entity.isValid()
                        && !entity.isDead()
                        && !entity.getUniqueId().equals(golem.getUniqueId())));
        if (candidates.isEmpty()) {
            return null;
        }
        candidates.sort(Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(center)));
        return candidates.get(0);
    }

    private Location resolveSearchCenter(List<ContainerTarget> targets, Location fallback) {
        if (targets == null || targets.isEmpty()) {
            return fallback;
        }
        if (fallback.getWorld() == null) {
            return fallback;
        }

        Location nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (ContainerTarget target : targets) {
            if (target == null || target.anchor() == null || target.anchor().getWorld() == null) {
                continue;
            }
            if (!Objects.equals(target.anchor().getWorld(), fallback.getWorld())) {
                continue;
            }
            double distance = target.anchor().distanceSquared(fallback);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = target.anchor();
            }
        }

        if (nearest == null) {
            return fallback;
        }
        return nearest.clone().add(0.5D, 0.0D, 0.5D);
    }

    private double resolveAttackDamage(ItemStack weapon, LivingEntity target) {
        double baseDamage = 1.0D;
        ItemMeta meta = weapon.getItemMeta();
        if (meta != null) {
            Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers(EquipmentSlot.HAND);
            if (modifiers != null && modifiers.containsKey(Attribute.ATTACK_DAMAGE)) {
                double addNumber = 0.0D;
                double addScalar = 0.0D;
                double multiplyScalar = 1.0D;
                for (AttributeModifier modifier : modifiers.get(Attribute.ATTACK_DAMAGE)) {
                    if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                        addNumber += modifier.getAmount();
                    } else if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
                        addScalar += modifier.getAmount();
                    } else if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
                        multiplyScalar *= (1.0D + modifier.getAmount());
                    }
                }
                baseDamage = Math.max(1.0D, (baseDamage + addNumber) * (1.0D + addScalar) * multiplyScalar);
            }
        }

        double enchantDamage = 0.0D;
        int sharpness = weapon.getEnchantmentLevel(Enchantment.SHARPNESS);
        if (sharpness > 0) {
            enchantDamage += (sharpness * 0.5D) + 0.5D;
        }
        if (Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(target.getType())) {
            enchantDamage += weapon.getEnchantmentLevel(Enchantment.SMITE) * 2.5D;
        } else if (Tag.ENTITY_TYPES_SENSITIVE_TO_BANE_OF_ARTHROPODS.isTagged(target.getType())) {
            enchantDamage += weapon.getEnchantmentLevel(Enchantment.BANE_OF_ARTHROPODS) * 2.5D;
        }
        return Math.max(1.0D, baseDamage + enchantDamage);
    }

    private void applyWeaponCombatEffects(CopperGolem golem, LivingEntity primaryTarget, double attackDamage, ItemStack weapon) {
        int fireAspect = weapon.getEnchantmentLevel(Enchantment.FIRE_ASPECT);
        if (fireAspect > 0) {
            int fireTicks = fireAspect * 80;
            primaryTarget.setFireTicks(Math.max(primaryTarget.getFireTicks(), fireTicks));
        }

        int sweeping = weapon.getEnchantmentLevel(Enchantment.SWEEPING_EDGE);
        if (sweeping <= 0) {
            return;
        }
        double sweepMultiplier = switch (Math.min(3, sweeping)) {
            case 1 -> 0.50D;
            case 2 -> 0.67D;
            default -> 0.75D;
        };
        double sweepDamage = Math.max(1.0D, attackDamage * sweepMultiplier);
        Location center = primaryTarget.getLocation();
        for (LivingEntity nearby : center.getNearbyLivingEntities(
                1.3D,
                entity -> entity instanceof Enemy
                        && entity.isValid()
                        && !entity.isDead()
                        && !entity.getUniqueId().equals(primaryTarget.getUniqueId())
                        && !entity.getUniqueId().equals(golem.getUniqueId()))) {
            nearby.damage(sweepDamage, golem);
            if (fireAspect > 0) {
                int fireTicks = fireAspect * 80;
                nearby.setFireTicks(Math.max(nearby.getFireTicks(), fireTicks));
            }
        }
    }

    private long attackCooldownTicks(ItemStack weapon) {
        double attackSpeed = resolveAttackSpeed(weapon);
        if (attackSpeed <= 0.0D) {
            return 20L;
        }
        return Math.max(1L, Math.round(20.0D / attackSpeed));
    }

    private double resolveAttackSpeed(ItemStack weapon) {
        double baseAttackSpeed = 4.0D;
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) {
            return baseAttackSpeed;
        }
        Multimap<Attribute, AttributeModifier> modifiers = meta.getAttributeModifiers(EquipmentSlot.HAND);
        if (modifiers == null || !modifiers.containsKey(Attribute.ATTACK_SPEED)) {
            return baseAttackSpeed;
        }
        double addNumber = 0.0D;
        double addScalar = 0.0D;
        double multiplyScalar = 1.0D;
        for (AttributeModifier modifier : modifiers.get(Attribute.ATTACK_SPEED)) {
            if (modifier.getOperation() == AttributeModifier.Operation.ADD_NUMBER) {
                addNumber += modifier.getAmount();
            } else if (modifier.getOperation() == AttributeModifier.Operation.ADD_SCALAR) {
                addScalar += modifier.getAmount();
            } else if (modifier.getOperation() == AttributeModifier.Operation.MULTIPLY_SCALAR_1) {
                multiplyScalar *= (1.0D + modifier.getAmount());
            }
        }
        return Math.max(0.1D, (baseAttackSpeed + addNumber) * (1.0D + addScalar) * multiplyScalar);
    }

    private void playAttackAnimation(CopperGolem golem, Location targetLocation) {
        Location effectLocation = targetLocation.clone().add(0.0D, 1.0D, 0.0D);
        golem.getWorld().spawnParticle(Particle.SWEEP_ATTACK, effectLocation, 1, 0.12D, 0.12D, 0.12D, 0.0D);
        golem.getWorld().playSound(golem.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.8F, 1.0F);
    }

    private void storeToContainers(List<ContainerTarget> targets, Location fallback, List<ItemStack> items) {
        if (targets.isEmpty()) {
            for (ItemStack item : items) {
                if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                    continue;
                }
                fallback.getWorld().dropItemNaturally(fallback, item);
            }
            return;
        }

        List<ItemStack> remaining = new ArrayList<>();
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) {
                continue;
            }
            ItemStack working = item.clone();
            for (ContainerTarget target : targets) {
                Inventory inventory = resolveTargetInventory(target);
                if (inventory == null) {
                    continue;
                }
                Map<Integer, ItemStack> leftovers = inventory.addItem(working);
                if (leftovers.isEmpty()) {
                    working = null;
                    break;
                }
                working = leftovers.values().iterator().next();
            }
            if (working != null && !working.getType().isAir() && working.getAmount() > 0) {
                remaining.add(working);
            }
        }

        for (ItemStack leftover : remaining) {
            fallback.getWorld().dropItemNaturally(fallback, leftover);
        }
    }

    private Inventory resolveTargetInventory(ContainerTarget target) {
        if (target == null || target.footprint().isEmpty()) {
            return null;
        }

        for (Location location : target.footprint()) {
            org.bukkit.block.BlockState state = location.getBlock().getState();
            if (!(state instanceof org.bukkit.block.Container container)) {
                continue;
            }
            Inventory inventory = container.getInventory();
            if (inventory != null) {
                return inventory;
            }
        }
        return null;
    }
}
