package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

/** object/projectile群の残存tick、速度変化、追尾と飛行中パーティクルを統一管理する。 */
public final class ProjectileMotionSystem {
    private final NamespacedKey kindKey;
    private final NamespacedKey ageKey;
    private final BukkitTask task;

    public ProjectileMotionSystem(Loader plugin) {
        kindKey = new NamespacedKey(plugin, "truecrafter_projectile");
        ageKey = new NamespacedKey(plugin, "truecrafter_projectile_age");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void shutdown() { task.cancel(); }

    private void tick() {
        for (org.bukkit.World world : Bukkit.getWorlds()) for (Entity entity : world.getEntities()) {
            if (!(entity instanceof Projectile projectile)) continue;
            String kind = projectile.getPersistentDataContainer().get(kindKey, PersistentDataType.STRING);
            if (kind == null) continue;
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
                case "brute" -> {
                    if (age >= 40) {
                        projectile.remove();
                        continue;
                    }
                    aim(projectile, 48.0D);
                    Vector direction = projectile.getVelocity();
                    if (direction.lengthSquared() > 0.0D) projectile.setVelocity(direction.normalize().multiply(0.5D));
                    world.spawnParticle(Particle.CRIT, projectile.getLocation(), 5, 0.2D, 0.1D, 0.2D, 0.05D);
                    world.spawnParticle(Particle.ELECTRIC_SPARK, projectile.getLocation(), 10, 0.2D, 0.1D, 0.2D, 0.05D);
                    if (age % 4 == 0) for (Player player : world.getPlayers()) {
                        Location location = player.getLocation();
                        Location wave = projectile.getLocation();
                        if (player.getGameMode().isInvulnerable()
                                || Math.abs(location.getX() - wave.getX()) > 0.75D
                                || Math.abs(location.getZ() - wave.getZ()) > 0.75D
                                || Math.abs(location.getY() - wave.getY()) > 1.0D) continue;
                        player.damage(12.0D, projectile);
                    }
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
