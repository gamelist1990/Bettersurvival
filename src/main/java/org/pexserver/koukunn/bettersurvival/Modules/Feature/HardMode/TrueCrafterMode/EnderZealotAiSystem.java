package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Bukkit;
import org.bukkit.entity.Slime;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.destroystokyo.paper.profile.ProfileProperty;

/** enemy.ender_zealotの40tick予備動作、60～100tickの20tick間隔弾幕を処理する。 */
public final class EnderZealotAiSystem {
    private final NamespacedKey zealotKey = new NamespacedKey("bettersurvival", "truecrafter_zealot");
    private final NamespacedKey projectileKey = new NamespacedKey("bettersurvival", "truecrafter_projectile");
    private final Map<UUID, Integer> ticks = new HashMap<>();

    public boolean tick(Slime zealot, Player target) {
        if (!zealot.getPersistentDataContainer().has(zealotKey, PersistentDataType.BYTE)) return false;
        ensureEyeDisplay(zealot);
        clearEndStone(zealot);
        drawDangerRing(zealot, ticks.getOrDefault(zealot.getUniqueId(), 0) >= 40 && ticks.getOrDefault(zealot.getUniqueId(), 0) < 60);
        if (zealot.getWorld() != target.getWorld()
                || zealot.getLocation().distanceSquared(target.getLocation()) > 2304.0D) return true;
        rotateTowardTarget(zealot, target);
        int tick = ticks.merge(zealot.getUniqueId(), 1, Integer::sum);
        if (tick == 40) {
            zealot.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, zealot.getLocation(), 35, 0.5, 0.5, 0.5, 0);
            zealot.getWorld().spawnParticle(Particle.DUST, zealot.getLocation(), 35, 0.5D, 0.5D, 0.5D, 0.0D,
                    new Particle.DustOptions(org.bukkit.Color.AQUA, 1.0F), true);
            zealot.getWorld().playSound(zealot.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 2.0F, 2.0F);
            zealot.getWorld().playSound(zealot.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.0F, 0.5F);
        }
        if (tick >= 60 && tick <= 100 && tick % 20 == 0) shoot(zealot, target);
        if (tick >= 140) ticks.put(zealot.getUniqueId(), -10);
        return true;
    }

    public void remove(UUID id) { ticks.remove(id); }
    public void clear() { ticks.clear(); }

    private void rotateTowardTarget(Slime zealot, Player target) {
        org.bukkit.util.Vector direction = target.getEyeLocation().toVector().subtract(zealot.getEyeLocation().toVector());
        if (direction.lengthSquared() == 0.0D) return;
        float desiredYaw = (float) Math.toDegrees(Math.atan2(-direction.getX(), direction.getZ()));
        float delta = ((desiredYaw - zealot.getYaw() + 540.0F) % 360.0F) - 180.0F;
        zealot.setRotation(zealot.getYaw() + Math.clamp(delta, -9.0F, 9.0F), 0.0F);
    }

    private void shoot(Slime zealot, Player target) {
        Snowball bullet = zealot.getWorld().spawn(zealot.getEyeLocation(), Snowball.class);
        bullet.setShooter(zealot);
        bullet.setItem(new ItemStack(Material.ENDER_PEARL));
        bullet.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, "zealot");
        org.bukkit.util.Vector velocity = target.getEyeLocation().toVector().subtract(zealot.getEyeLocation().toVector()).normalize();
        velocity.rotateAroundY(java.util.concurrent.ThreadLocalRandom.current().nextDouble(-0.07854D, 0.07854D));
        bullet.setVelocity(velocity.multiply(2.0D));
        zealot.getWorld().playSound(zealot.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.5F, 0.5F);
        zealot.getWorld().playSound(zealot.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0F, 2.0F);
    }

    /** 本家の enemy_part.ender_zealot_eye に相当する視覚オブジェクトを常時追従させる。 */
    private void ensureEyeDisplay(Slime zealot) {
        ItemDisplay existing = zealot.getPassengers().stream().filter(ItemDisplay.class::isInstance)
                .map(ItemDisplay.class::cast).findFirst().orElse(null);
        if (existing != null) {
            configureEyeDisplay(existing);
            existing.setRotation(zealot.getBodyYaw(), 0.0F);
            return;
        }
        ItemDisplay eye = zealot.getWorld().spawn(zealot.getLocation(), ItemDisplay.class);
        ItemStack eyeHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) eyeHead.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.getServer().createProfile(UUID.fromString("b3eb0720-8e8b-412c-a4b3-eb7b74c1cb3e"));
        profile.setProperty(new ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTk4YTQ5Y2E1NGMzZWE2N2E4NmVjOGI5ZjE2YmRmNDZhYTVlZmM1YWVlZmI3YTE5Y2NjYzc5NjJlODIxYTU5OSJ9fX0="));
        skullMeta.setPlayerProfile(profile);
        eyeHead.setItemMeta(skullMeta);
        eye.setItemStack(eyeHead);
        configureEyeDisplay(eye);
        eye.setRotation(zealot.getBodyYaw(), 0.0F);
        zealot.addPassenger(eye);
    }

    private void configureEyeDisplay(ItemDisplay eye) {
        eye.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        eye.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15));
        eye.setGlowColorOverride(org.bukkit.Color.fromRGB(137, 50, 184));
        eye.setGlowing(true);
        eye.setTransformation(new org.bukkit.util.Transformation(
                new org.joml.Vector3f(0.0F, 0.5F, 0.0F), new org.joml.Quaternionf(),
                new org.joml.Vector3f(0.55F, 0.55F, 0.55F), new org.joml.Quaternionf()));
    }

    /** 予備動作中は赤、それ以外は紫の半径1円を描き、元データパックの危険表示を再現する。 */
    private void drawDangerRing(Slime zealot, boolean danger) {
        org.bukkit.Color color = danger ? org.bukkit.Color.fromRGB(204, 0, 51) : org.bukkit.Color.fromRGB(204, 51, 255);
        Particle.DustOptions dust = new Particle.DustOptions(color, 0.5F);
        org.bukkit.Location center = zealot.getLocation().add(0.0D, 0.5D, 0.0D);
        for (int i = 0; i < 36; i++) {
            double angle = Math.PI * 2.0D * i / 36.0D;
            zealot.getWorld().spawnParticle(Particle.DUST,
                    center.getX() + Math.cos(angle), center.getY(), center.getZ() + Math.sin(angle),
                    1, 0.0D, 0.0D, 0.0D, 0.0D, dust, true);
        }
    }

    /** 本家の毎tick end_stone除去。ジーロットが地形に埋まった場合だけ周囲を開ける。 */
    private void clearEndStone(Slime zealot) {
        if (zealot.getWorld().getEnvironment() != org.bukkit.World.Environment.THE_END) return;
        org.bukkit.Location origin = zealot.getLocation().getBlock().getLocation();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            org.bukkit.block.Block block = origin.clone().add(x, y, z).getBlock();
            if (block.getType() == Material.END_STONE) block.setType(Material.AIR, false);
        }
    }
}
