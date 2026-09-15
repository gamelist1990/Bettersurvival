package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Loader;

/** projectile.ender_eye_laserの60tick照準、30tick警告、90tick照射、110tick消滅を実装する。 */
public final class TimedLaserSystem {
    private final NamespacedKey laserKey;
    private final NamespacedKey ageKey;
    private final NamespacedKey ownerKey;
    private final BukkitTask task;

    public TimedLaserSystem(Loader plugin) {
        laserKey = new NamespacedKey(plugin, "truecrafter_ender_eye_laser");
        ageKey = new NamespacedKey(plugin, "truecrafter_laser_age");
        ownerKey = new NamespacedKey(plugin, "truecrafter_laser_owner");
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public void spawn(EnderDragon owner, Player target) {
        spawn(owner, owner.getLocation().clone().add(0, 3, 0), target);
    }

    public void spawn(EnderDragon owner, Location location, Player target) {
        ArmorStand marker = owner.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true); stand.setMarker(true); stand.setGravity(false); stand.setInvulnerable(true); stand.setSilent(true);
        });
        marker.getPersistentDataContainer().set(laserKey, PersistentDataType.BYTE, (byte) 1);
        marker.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        aim(marker, target);
        ItemDisplay eye = owner.getWorld().spawn(marker.getLocation(), ItemDisplay.class, display -> {
            display.setItemStack(new org.bukkit.inventory.ItemStack(org.bukkit.Material.ENDER_EYE));
            display.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            display.setTransformation(new org.bukkit.util.Transformation(new org.joml.Vector3f(), new org.joml.Quaternionf(),
                    new org.joml.Vector3f(0.55F, 0.55F, 0.55F), new org.joml.Quaternionf()));
            display.setPersistent(true);
        });
        marker.addPassenger(eye);
        owner.getWorld().spawnParticle(Particle.EXPLOSION, marker.getLocation(), 1);
        owner.getWorld().playSound(marker.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 3.0F, 1.5F);
        owner.getWorld().playSound(marker.getLocation(), Sound.BLOCK_END_PORTAL_FRAME_FILL, 3.0F, 1.5F);
    }

    public void shutdown() { task.cancel(); }
    private void tick() { for (org.bukkit.World world:Bukkit.getWorlds()) for (ArmorStand laser:world.getEntitiesByClass(ArmorStand.class)) { if(!laser.getPersistentDataContainer().has(laserKey,PersistentDataType.BYTE))continue; int age=laser.getPersistentDataContainer().getOrDefault(ageKey,PersistentDataType.INTEGER,0)+1;laser.getPersistentDataContainer().set(ageKey,PersistentDataType.INTEGER,age); Player target=world.getPlayers().stream().filter(p->p.getLocation().distanceSquared(laser.getLocation())<=2304).min(java.util.Comparator.comparingDouble(p->p.getLocation().distanceSquared(laser.getLocation()))).orElse(null); if(age<=60&&target!=null)aim(laser,target); if(age<=60)line(laser,Color.fromRGB(128,128,128),0.5F);else if(age<=90)line(laser,Color.fromRGB(153,0,204),0.8F); if(age==60){world.playSound(laser.getLocation(),Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM,5,1.5F);world.playSound(laser.getLocation(),Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM,5,1.7F);}if(age==90){world.playSound(laser.getLocation(),Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,4,1.5F);world.playSound(laser.getLocation(),Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE,4,1.3F);world.playSound(laser.getLocation(),Sound.ENTITY_SHULKER_SHOOT,4,1.0F);world.playSound(laser.getLocation(),Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE,4,1.0F);damageLine(laser);}if(age>=110){world.spawnParticle(Particle.DUST,laser.getLocation(),30,0.8,0.8,0.8,0,new Particle.DustOptions(Color.fromRGB(204,51,255),2),true);laser.getPassengers().forEach(org.bukkit.entity.Entity::remove);laser.remove();}} }
    private void aim(ArmorStand laser, Player target){Location facing=laser.getLocation();facing.setDirection(target.getEyeLocation().toVector().subtract(facing.toVector()));laser.setRotation(facing.getYaw(),facing.getPitch());}
    private void line(ArmorStand laser, Color color, float size) {
        Location origin = laser.getEyeLocation();
        org.bukkit.util.Vector direction = origin.getDirection().normalize();
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(0.5D);
        for (int index = 0; index < 32; index++) {
            Location point = origin.clone().add(direction.clone().multiply(index));
            if (!point.getBlock().isPassable()) break;
            dust(point, color, size, 1);
            dust(point.clone().add(side), color, size, 1);
            dust(point.clone().subtract(side), color, size, 1);
        }
    }

    private void damageLine(ArmorStand laser) {
        Location origin = laser.getEyeLocation();
        org.bukkit.util.Vector direction = origin.getDirection().normalize();
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-direction.getZ(), 0.0D, direction.getX()).normalize().multiply(0.5D);
        for (int index = 0; index < 32; index++) {
            Location point = origin.clone().add(direction.clone().multiply(index));
            if (!point.getBlock().isPassable()) break;
            for (Location beamPoint : new Location[] {point, point.clone().add(side), point.clone().subtract(side)}) {
                dust(beamPoint, Color.fromRGB(204, 51, 255), 1.5F, 4);
                beamPoint.getWorld().spawnParticle(Particle.END_ROD, beamPoint, 1, 0.0D, 0.0D, 0.0D, 0.05D, null, true);
            }
            for (Player player : point.getWorld().getPlayers()) {
                if (Math.abs(player.getLocation().getX() - point.getX()) <= 1.0D
                        && Math.abs(player.getLocation().getY() - point.getY()) <= 1.0D
                        && Math.abs(player.getLocation().getZ() - point.getZ()) <= 1.0D) player.damage(10.0D, laser);
            }
        }
    }

    private void dust(Location location, Color color, float size, int count) {
        location.getWorld().spawnParticle(Particle.DUST, location, count, 0.0D, 0.0D, 0.0D, 0.0D,
                new Particle.DustOptions(color, size), true);
    }
}
