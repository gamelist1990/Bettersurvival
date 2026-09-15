package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Wither;
import org.bukkit.entity.EnderDragon;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

/** object/projectile群の残存tick、速度変化、追尾と飛行中パーティクルを統一管理する。 */
public final class ProjectileMotionSystem {
    private final NamespacedKey kindKey;
    private final NamespacedKey ageKey;
    private final NamespacedKey ownerKey;
    private final NamespacedKey damageKey;
    private final BukkitTask task;

    public ProjectileMotionSystem(Loader plugin) {
        kindKey = new NamespacedKey(plugin, "truecrafter_projectile");
        ageKey = new NamespacedKey(plugin, "truecrafter_projectile_age");
        ownerKey = new NamespacedKey(plugin, "truecrafter_projectile_owner");
        damageKey = new NamespacedKey(plugin, "truecrafter_projectile_damage");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() { task.cancel(); }

    private void tick() {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            String kind = entity.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
            if (kind == null) continue;
            if (kind.equals("brute") && entity instanceof Marker marker) {
                tickBruteShockwave(world, marker);
                continue;
            }
            if (!(entity instanceof Projectile projectile)) continue;
            int age = projectile.getPersistentDataContainer().getOrDefault(ageKey, PersistentDataType.INTEGER, 0) + 1;
            projectile.getPersistentDataContainer().set(ageKey, PersistentDataType.INTEGER, age);
            switch (kind) {
                case "web" -> {
                    world.spawnParticle(Particle.SPIT, projectile.getLocation(), 1);
                    if (age >= 100) projectile.remove();
                }
                case "poison" -> {
                    world.spawnParticle(Particle.ITEM_SLIME, projectile.getLocation(), 1);
                    world.spawnParticle(Particle.DUST, projectile.getLocation(), 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(0, 128, 0), 1.0F));
                    if (age >= 50) projectile.remove();
                }
                case "zealot" -> {
                    world.spawnParticle(Particle.ELECTRIC_SPARK, projectile.getLocation().subtract(projectile.getVelocity().normalize().multiply(0.5)), 1);
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, projectile.getLocation().subtract(projectile.getVelocity().normalize().multiply(0.5)), 1,
                            0.1D, 0.1D, 0.1D, 1.0D,
                            new Particle.DustTransition(Color.fromRGB(204, 51, 255), Color.fromRGB(51, 51, 51), 1.0F), true);
                    if (age == 20 || age == 25 || age == 30) accelerateAndAim(projectile, age == 20 ? 1.0D : age == 25 ? 2.0D : 3.0D);
                    else if (age < 35) aim(projectile, 128.0D);
                    if (age >= 400) projectile.remove();
                }
                case "void" -> {
                    world.spawnParticle(Particle.DUST, projectile.getLocation(), 1, 0.1, 0.1, 0.1, 0,
                            new Particle.DustOptions(Color.fromRGB(204, 51, 255), 1.5F));
                    if (age >= 400) projectile.remove();
                }
                case "dragon_homing" -> {
                    world.spawnParticle(Particle.DUST, projectile.getLocation().subtract(projectile.getVelocity().normalize().multiply(0.5D)), 1, 0.3D, 0.3D, 0.3D, 1.0D, new Particle.DustOptions(Color.fromRGB(204, 51, 255), 2.0F));
                    if (age == 20) accelerateAndAim(projectile, 3.0D, 128.0D);
                    else if (age == 25) accelerateAndAim(projectile, 4.0D, 128.0D);
                    else if (age == 30 || age == 35) accelerateAndAim(projectile, 5.0D, 128.0D);
                    else if (age < 35) aim(projectile, 128.0D);
                    if (age >= 400) projectile.remove();
                }
                case "wither_homing" -> {
                    world.spawnParticle(Particle.DUST_COLOR_TRANSITION, projectile.getLocation().subtract(projectile.getVelocity().normalize().multiply(0.5D)), 1, 0, 0, 0, 1.0D,
                            new Particle.DustTransition(Color.fromRGB(224, 247, 147), Color.fromRGB(51, 51, 51), 1.0F), true);
                    if (age == 20) accelerateAndAim(projectile, 3.0D, 48.0D);
                    else if (age == 25) accelerateAndAim(projectile, 4.0D, 48.0D);
                    else if (age == 30 || age == 35) accelerateAndAim(projectile, 5.0D, 48.0D);
                    else if (age < 35) aim(projectile, 48.0D);
                    if (age >= 400) projectile.remove();
                }
                case "elite_arrow" -> {
                    world.spawnParticle(Particle.DUST, projectile.getLocation(), 10, 0.1D, 0.1D, 0.1D, 1.0D, new Particle.DustOptions(Color.fromRGB(255, 77, 77), 0.5F));
                    if (age >= 100) projectile.setGravity(true);
                }
                case "elite_wither_arrow" -> {
                    world.spawnParticle(Particle.DUST, projectile.getLocation(), 10, 0.1D, 0.1D, 0.1D, 1.0D, new Particle.DustOptions(Color.GRAY, 0.5F));
                    if (age >= 100) projectile.setGravity(true);
                }
                default -> { }
            }
        }
    }

    private void tickBruteShockwave(org.bukkit.World world, Marker marker) {
        int age = marker.getPersistentDataContainer().getOrDefault(ageKey, PersistentDataType.INTEGER, 0) + 1;
        marker.getPersistentDataContainer().set(ageKey, PersistentDataType.INTEGER, age);
        if (age > 40) {
            marker.remove();
            return;
        }
        LivingEntity target = world.getEntities().stream()
                .filter(this::isPiglinEnemy)
                .filter(entity -> entity.getLocation().distanceSquared(marker.getLocation()) <= 2304.0D)
                .map(entity -> (LivingEntity) entity)
                .min(java.util.Comparator.comparingDouble(entity -> entity.getLocation().distanceSquared(marker.getLocation())))
                .orElse(null);
        Location location = marker.getLocation();
        if (target != null) {
            Vector direction = target.getEyeLocation().toVector().subtract(location.toVector()).setY(0.0D);
            if (direction.lengthSquared() > 0.0D) {
                direction.normalize();
                location.setDirection(direction);
                marker.setRotation(location.getYaw(), 0.0F);
            }
        }
        Vector forward = marker.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() > 0.0D) {
            forward.normalize().multiply(0.5D);
            location.add(forward);
        }
        if (location.clone().add(0.0D, -1.0D, 0.0D).getBlock().isPassable()) location.subtract(0.0D, 1.0D, 0.0D);
        if (!location.getBlock().isPassable()) location.add(0.0D, 1.0D, 0.0D);
        marker.teleport(location);
        world.spawnParticle(Particle.CRIT, location, 5, 0.2D, 0.1D, 0.2D, 0.05D);
        world.spawnParticle(Particle.ELECTRIC_SPARK, location, 10, 0.2D, 0.1D, 0.2D, 0.05D);
        if (age % 4 != 0) return;
        double damage = marker.getPersistentDataContainer().getOrDefault(damageKey, PersistentDataType.DOUBLE, 12.0D);
        Entity owner = owner(marker);
        for (Entity entity : world.getNearbyEntities(location, 0.75D, 2.0D, 0.75D)) {
            if (!(entity instanceof LivingEntity victim) || !isPiglinEnemy(victim)) continue;
            double y = victim.getLocation().getY() - location.getY();
            if (y < 0.0D || y > 2.0D) continue;
            victim.damage(damage, owner == null ? marker : owner);
        }
        world.spawnParticle(Particle.ENTITY_EFFECT, location.clone().add(0.0D, 0.5D, 0.0D), 35, 0.3D, 1.0D, 0.3D, 0.0D, Color.fromRGB(255, 128, 0), true);
        world.spawnParticle(Particle.DUST, location.clone().add(0.0D, 0.5D, 0.0D), 35, 0.3D, 1.0D, 0.3D, 0.0D, new Particle.DustOptions(Color.fromRGB(255, 128, 0), 1.0F));
        world.spawnParticle(Particle.EXPLOSION, location, 2);
        world.playSound(location, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0F, 2.0F);
        world.playSound(location, org.bukkit.Sound.ENTITY_BLAZE_SHOOT, 1.0F, 1.0F);
        if (age >= 40) marker.remove();
    }

    private boolean isPiglinEnemy(Entity entity) {
        if (!(entity instanceof LivingEntity living) || living.isInvulnerable()) return false;
        return entity instanceof Player player ? !player.getGameMode().isInvulnerable()
                : entity instanceof Monster || entity instanceof Wither || entity instanceof EnderDragon;
    }

    private Entity owner(Marker marker) {
        String ownerId = marker.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (ownerId == null) return null;
        try {
            java.util.UUID uuid = java.util.UUID.fromString(ownerId);
            return marker.getWorld().getEntities().stream().filter(entity -> entity.getUniqueId().equals(uuid)).findFirst().orElse(null);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private void accelerateAndAim(Projectile projectile, double speed) {
        accelerateAndAim(projectile, speed, 48.0D);
    }

    private void accelerateAndAim(Projectile projectile, double speed, double range) {
        aim(projectile, range);
        projectile.setVelocity(projectile.getVelocity().normalize().multiply(speed));
    }

    @SuppressWarnings("unused")
    private void aim(Projectile projectile) {
        aim(projectile, 48.0D);
    }

    private void aim(Projectile projectile, double range) {
        Player target = projectile.getWorld().getPlayers().stream()
                .filter(player -> !player.getGameMode().isInvulnerable())
                .filter(player -> player.getLocation().distanceSquared(projectile.getLocation()) <= range * range)
                .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(projectile.getLocation()))).orElse(null);
        if (target == null) return;
        double speed = projectile.getVelocity().length();
        Vector direction = target.getEyeLocation().toVector().subtract(projectile.getLocation().toVector()).normalize();
        projectile.setVelocity(direction.multiply(speed));
    }
}
