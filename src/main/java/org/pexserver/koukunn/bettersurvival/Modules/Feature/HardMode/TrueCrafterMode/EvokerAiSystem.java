package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** enemy.evokerのteleportと瀕死時final_summonをtick単位で再現する。 */
public final class EvokerAiSystem {
    private final Map<UUID, Integer> ticks = new HashMap<>();
    private final Map<UUID, Integer> summonTicks = new HashMap<>();
    private final Map<UUID, Boolean> summonUsed = new HashMap<>();

    public boolean tick(Evoker evoker, Player target) {
        applyInit(evoker);
        if (summonUsed.getOrDefault(evoker.getUniqueId(), false) && !summonTicks.containsKey(evoker.getUniqueId())) return true;
        if (!summonUsed.getOrDefault(evoker.getUniqueId(), false) && evoker.getHealth() <= 25.0D) {
            startFinalSummon(evoker);
            return true;
        }
        Integer active = summonTicks.get(evoker.getUniqueId());
        if (active != null) {
            tickFinalSummon(evoker, target, active + 1);
            return true;
        }
        if (evoker.getLocation().distanceSquared(target.getLocation()) <= 25.0D && !evoker.isInsideVehicle()) {
            int tick = ticks.merge(evoker.getUniqueId(), 1, Integer::sum);
            if (tick >= 40 && evoker.getLocation().distanceSquared(target.getLocation()) <= 256.0D
                    && teleportAndFangs(evoker, target)) {
                ticks.remove(evoker.getUniqueId());
            }
        }
        return true;
    }

    public void remove(UUID id) {
        ticks.remove(id);
        summonTicks.remove(id);
        summonUsed.remove(id);
    }

    public void clear() {
        ticks.clear();
        summonTicks.clear();
        summonUsed.clear();
    }

    private void applyInit(Evoker evoker) {
        if (evoker.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_evoker_init"), org.bukkit.persistence.PersistentDataType.BYTE)) return;
        evoker.getAttribute(Attribute.MAX_HEALTH).setBaseValue(50.0D);
        evoker.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.3D);
        evoker.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.0D);
        evoker.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
        evoker.setHealth(50.0D);
        evoker.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_evoker_init"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
    }

    private void startFinalSummon(Evoker evoker) {
        summonUsed.put(evoker.getUniqueId(), true);
        summonTicks.put(evoker.getUniqueId(), 0);
        ticks.put(evoker.getUniqueId(), -60);
        evoker.setSpell( org.bukkit.entity.Spellcaster.Spell.SUMMON_VEX );
        evoker.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, false, false));
        evoker.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 40, 4, false, false));
        for (Player player : evoker.getWorld().getPlayers()) if (player.getLocation().distance(evoker.getLocation()) <= 16.0D)
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false));
        evoker.getWorld().playSound(evoker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_AMBIENT, 2.0F, 2.0F);
        evoker.getWorld().playSound(evoker.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 2.0F, 2.0F);
        evoker.getWorld().playSound(evoker.getLocation(), Sound.ENTITY_EVOKER_PREPARE_SUMMON, 2.0F, 2.0F);
        evoker.getWorld().spawnParticle(Particle.EXPLOSION, evoker.getLocation().add(0, 1, 0), 1);
        String[] lines = {"Cover me!", "Distract them!", "Strength for illagers!", "Not yet!", "Come, my friends!", "I'm not alone!"};
        evoker.getWorld().getPlayers().stream().filter(player -> player.getLocation().distance(evoker.getLocation()) <= 16.0D)
                .forEach(player -> player.sendMessage(Component.text("<Evoker> ", NamedTextColor.GRAY)
                        .append(Component.text(lines[ThreadLocalRandom.current().nextInt(lines.length)], NamedTextColor.WHITE))));
    }

    private void tickFinalSummon(Evoker evoker, Player target, int tick) {
        summonTicks.put(evoker.getUniqueId(), tick);
        Location origin = evoker.getLocation();
        evoker.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, origin.clone().add(0, 1, 0), 1, 0.3, 0.5, 0.3, 0);
        evoker.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, origin.clone().add(0, 1, 0), 2, 0.3, 0.5, 0.3, 0);
        evoker.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, origin.clone().add(0, 2, 0), 2, 4, 4, 4, 0);
        evoker.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, origin.clone().add(0, 2, 0), 5, 4, 4, 4, 0);
        if (tick < 40) return;
        summonTicks.remove(evoker.getUniqueId());
        org.bukkit.util.Vector facing = target.getLocation().toVector().subtract(origin.toVector()).setY(0.0D);
        if (facing.lengthSquared() > 0.0D) {
            origin.setDirection(facing);
            evoker.setRotation(origin.getYaw(), 0.0F);
        }
        EntityType type = switch (ThreadLocalRandom.current().nextInt(6)) {
            case 0, 1 -> EntityType.VINDICATOR;
            case 2, 3 -> EntityType.PILLAGER;
            case 4 -> EntityType.RAVAGER;
            default -> EntityType.ILLUSIONER;
        };
        if (type == EntityType.VINDICATOR || type == EntityType.PILLAGER) {
            spawnFinalMob(evoker, target, type, origin);
            evoker.getWorld().spawnParticle(Particle.WITCH, origin.clone().add(0, 1, 0), 50, 0.3, 0.5, 0.3, 0);
            evoker.getWorld().spawnParticle(Particle.DUST, origin.clone().add(0, 1, 0), 50, 0.3, 0.5, 0.3, 1.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 0, 255), 1.0F));
            evoker.getWorld().playSound(origin, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
            return;
        }
        spawnFinalMob(evoker, target, type, origin);
    }

    private boolean teleportAndFangs(Evoker evoker, Player target) {
        Location before = evoker.getLocation();
        org.bukkit.util.Vector towardTarget = target.getLocation().toVector().subtract(before.toVector()).setY(0.0D);
        if (towardTarget.lengthSquared() == 0.0D) return false;
        towardTarget.normalize();
        boolean backwardClear = isTeleportPassable(before, towardTarget.clone().multiply(-1.0D), 1);
        boolean forwardClear = isTeleportPassable(before, towardTarget, 1);
        if (!backwardClear && !forwardClear) return false;
        org.bukkit.util.Vector direction = !backwardClear ? towardTarget
                : before.distanceSquared(target.getLocation()) <= 25.0D ? towardTarget.clone().multiply(-1.0D) : towardTarget;
        if (!isTeleportPassable(before, direction, 1)) return false;
        Location destination = recursiveTeleportDestination(before, direction);
        evoker.getWorld().spawnParticle(Particle.WITCH, before.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0);
        evoker.getWorld().spawnParticle(Particle.DUST, before.clone().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 1.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 0, 255), 1.0F));
        evoker.teleport(destination);
        evoker.getWorld().spawnParticle(Particle.WITCH, evoker.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0);
        evoker.getWorld().spawnParticle(Particle.DUST, evoker.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 1.0, new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 0, 255), 1.0F));
        evoker.getWorld().playSound(evoker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        for (int offset : new int[]{0, 1, -1}) {
            Location fang = evoker.getLocation().add(direction.clone().multiply(2)).add(direction.clone().rotateAroundY(Math.PI / 2).multiply(offset));
            evoker.getWorld().spawnEntity(fang, EntityType.EVOKER_FANGS);
        }
        return true;
    }

    private boolean isTeleportPassable(Location origin, org.bukkit.util.Vector direction, int distance) {
        Location candidate = origin.clone().add(direction.clone().multiply(distance));
        return candidate.getBlock().isPassable() && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable();
    }

    private Location recursiveTeleportDestination(Location origin, org.bukkit.util.Vector direction) {
        Location destination = origin;
        for (int distance = 1; distance <= 6; distance++) {
            if (!isTeleportPassable(origin, direction, distance)) break;
            destination = origin.clone().add(direction.clone().multiply(distance));
        }
        return destination;
    }

    private Entity spawnFinalMob(Evoker evoker, Player target, EntityType type, Location location) {
        Entity entity = evoker.getWorld().spawnEntity(location, type);
        if (entity instanceof Mob mob) mob.setTarget(target);
        if (entity instanceof LivingEntity living && (type == EntityType.VINDICATOR || type == EntityType.PILLAGER || type == EntityType.ILLUSIONER)) {
            living.getEquipment().setItemInMainHand(new ItemStack(type == EntityType.VINDICATOR ? Material.IRON_AXE : type == EntityType.PILLAGER ? Material.CROSSBOW : Material.BOW));
        }
        return entity;
    }
}
