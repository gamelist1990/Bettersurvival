package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.Material;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** enemy.ender_dragonのクリスタル解除、スキル循環、第二形態の使徒召喚を処理する。 */
public final class EnderDragonBossSystem {
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Double> dragonBuffFallDamage = new HashMap<>();
    private final Map<UUID, Integer> dragonBuffExpiry = new HashMap<>();
    private final List<LightPillar> lightPillars = new ArrayList<>();
    private final DragonPlatformSystem platforms;
    private final TimedLaserSystem lasers;

    public EnderDragonBossSystem(Loader plugin) {
        platforms = new DragonPlatformSystem(plugin);
        lasers = new TimedLaserSystem(plugin);
    }

    public boolean tick(EnderDragon dragon, Player target) {
        tickLightPillars(dragon.getWorld());
        refreshCrystalPhase(dragon);
        State state = states.get(dragon.getUniqueId());
        applyDragonFightBuff(dragon);
        if (state.phase >= 2) secondPhase(dragon, target, state);
        else placePlatforms(dragon, state);
        if (state.skill == Skill.NONE && ++state.tick >= 60) select(dragon, state);
        if (state.skill != Skill.NONE) skill(dragon, target, state);
        return true;
    }

    public void prepare(EnderDragon dragon) {
        refreshCrystalPhase(dragon);
    }

    /** 本家と同じく、現在地が読み込まれたドラゴンの半径128ブロック内にクリスタルがない時だけ第二形態へ移行する。 */
    public void refreshCrystalPhase(EnderDragon dragon) {
        State state = states.computeIfAbsent(dragon.getUniqueId(), ignored -> initialize(dragon));
        boolean noNearbyCrystal = dragon.getWorld().getEntitiesByClass(org.bukkit.entity.EnderCrystal.class).stream()
                .noneMatch(crystal -> crystal.getLocation().distanceSquared(dragon.getLocation()) <= 16384.0D);
        if (state.phase == 1 && dragon.getLocation().getChunk().isLoaded() && noNearbyCrystal) {
            state.phase = 2;
        }
        if (state.phase == 1) dragon.setInvulnerable(true);
        else if (!state.landingApproach) dragon.setInvulnerable(false);
    }

    public void remove(UUID id) { states.remove(id); clearDragonFightBuff(); }
    public void clear() { states.clear(); lightPillars.clear(); clearDragonFightBuff(); }
    public void shutdown() { platforms.shutdown(); lasers.shutdown(); }

    private State initialize(EnderDragon dragon) {
        dragon.setInvulnerable(true);
        dragon.getWorld().getEntitiesByClass(Enderman.class).stream()
                .filter(enderman -> enderman.getLocation().distanceSquared(dragon.getLocation()) <= 16384.0D).forEach(Enderman::remove);
        return new State();
    }

    private void secondPhase(EnderDragon dragon, Player target, State state) {
        placePlatforms(dragon, state);
        int originY = dragon.getPodium().getBlockY();
        if (++state.minionTick == 40 || state.minionTick == 80 || state.minionTick == 120 || state.minionTick == 160) {
            long existing = dragon.getNearbyEntities(128, 128, 128).stream().filter(entity -> entity instanceof Slime
                    && entity.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_zealot"), org.bukkit.persistence.PersistentDataType.BYTE)).count();
            if (existing <= 3) {
                Location location = new Location(dragon.getWorld(), ThreadLocalRandom.current().nextDouble(-32, 32), originY + 28 + ThreadLocalRandom.current().nextDouble(-16, 16), ThreadLocalRandom.current().nextDouble(-32, 32));
                Slime zealot = dragon.getWorld().spawn(location, Slime.class);
                zealot.setSize(0);
                zealot.setAI(false);
                zealot.setSilent(true);
                zealot.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                zealot.getAttribute(Attribute.SCALE).setBaseValue(2.0D);
                zealot.getAttribute(Attribute.MAX_HEALTH).setBaseValue(30.0D);
                zealot.setHealth(30.0D);
                zealot.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_zealot"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                zealot.customName(net.kyori.adventure.text.Component.text("エンダージーロット"));
                zealot.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 4, true, false));
                dragon.getWorld().spawnParticle(Particle.EXPLOSION, location, 1);
                dragon.getWorld().playSound(location, Sound.ENTITY_ENDERMAN_TELEPORT, 5.0F, 1.5F);
                dragon.getWorld().playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 5.0F, 1.5F);
            }
        }
        if (state.minionTick >= 240) state.minionTick = 0;
    }

    private void placePlatforms(EnderDragon dragon, State state) {
        state.platformTick++;
        int originY = dragon.getPodium().getBlockY();
        if (state.platformTick == 20 || state.platformTick == 30 || state.platformTick == 40) platforms.summon(dragon.getWorld(), originY + 8, 0);
        if (state.platformTick == 50 || state.platformTick == 60 || state.platformTick == 70) platforms.summon(dragon.getWorld(), originY + 16, 1);
        if (state.platformTick == 80 || state.platformTick == 90 || state.platformTick == 100) platforms.summon(dragon.getWorld(), originY + 32, 2);
        if (state.platformTick == 110 || state.platformTick == 120 || state.platformTick == 130) platforms.summon(dragon.getWorld(), originY + 64, 3);
        if (state.platformTick >= 140) state.platformTick = 0;
    }

    private void applyDragonFightBuff(EnderDragon dragon) {
        Location origin = new Location(dragon.getWorld(), 0.0D, dragon.getWorld().getHighestBlockYAt(0, 0) + 1.0D, 0.0D);
        dragonBuffExpiry.replaceAll((id, expiry) -> expiry - 1);
        for (Player player : dragon.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(origin) > 16384.0D) continue;
            org.bukkit.attribute.AttributeInstance fallDamage = player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
            if (fallDamage != null && !dragonBuffFallDamage.containsKey(player.getUniqueId())) {
                dragonBuffFallDamage.put(player.getUniqueId(), fallDamage.getBaseValue());
                player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 2.0F);
            }
            if (fallDamage != null) fallDamage.setBaseValue(0.0D);
            player.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, 40, 9, false, false));
            dragonBuffExpiry.put(player.getUniqueId(), 20);
        }
        Iterator<Map.Entry<UUID, Integer>> iterator = dragonBuffExpiry.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            if (entry.getValue() > 0) continue;
            restoreDragonFightBuff(entry.getKey());
            iterator.remove();
        }
    }

    private void clearDragonFightBuff() {
        for (UUID id : new ArrayList<>(dragonBuffFallDamage.keySet())) restoreDragonFightBuff(id);
        dragonBuffFallDamage.clear();
        dragonBuffExpiry.clear();
    }

    private void restoreDragonFightBuff(UUID id) {
        Double original = dragonBuffFallDamage.remove(id);
        if (original == null) return;
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        org.bukkit.attribute.AttributeInstance fallDamage = player.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER);
        if (fallDamage != null) fallDamage.setBaseValue(original);
        player.removePotionEffect(PotionEffectType.JUMP_BOOST);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_BREEZE_IDLE_AIR, 1.0F, 0.5F);
    }

    private void select(EnderDragon dragon, State state) {
        if (state.remaining.isEmpty()) {
            state.remaining.addAll(List.of(Skill.CHARGE, Skill.AIMING_EYES, Skill.HOMING));
            state.skill = Skill.LANDING;
        } else {
            Collections.shuffle(state.remaining);
            state.skill = state.remaining.removeFirst();
        }
        state.tick = -20;
        dragon.getWorld().playSound(dragon.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 3.0F, 1.5F);
        dragon.getWorld().playSound(dragon.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 3.0F, 0.5F);
    }

    private void skill(EnderDragon dragon, Player target, State state) {
        state.tick++;
        switch (state.skill) {
            case AIMING_EYES -> {
                if (state.tick >= 0 && state.tick <= 80 && state.tick % 20 == 0) eyeShot(dragon, target);
                if (state.tick >= 160) reset(state);
            }
            case HOMING -> {
                if (state.tick >= 0 && state.tick <= 80 && state.tick % 20 == 0) homingShot(dragon, target);
                if (state.tick >= 100) reset(state);
            }
            case CHARGE -> {
                if (state.tick == 0) startCharge(dragon, state);
                if (state.tick <= 70) charge(dragon, state);
                if (state.tick >= 100) reset(state);
            }
            case LANDING -> {
                if (state.tick == 0) {
                    state.landingApproach = true;
                    dragon.setInvulnerable(true);
                    dragon.setPhase(EnderDragon.Phase.LAND_ON_PORTAL);
                }
                if (state.tick == 2) {
                    dragon.setPhase(EnderDragon.Phase.BREATH_ATTACK);
                    dragon.getWorld().playSound(dragon.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 2.0F, 2.0F);
                    dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0F, 1.0F);
                    state.landingApproach = false;
                    if (state.phase >= 2) dragon.setInvulnerable(false);
                }
                if (state.tick >= 2 && state.tick <= 100 && state.tick % 5 == 0) lightningPillar(dragon, target);
                if (state.tick >= 160) {
                    dragon.setPhase(EnderDragon.Phase.LEAVE_PORTAL);
                    reset(state);
                }
            }
            default -> { }
        }
    }

    private void eyeShot(EnderDragon dragon, Player target) {
        List<Player> candidates = dragon.getWorld().getPlayers().stream()
                .filter(player -> player.getLocation().distanceSquared(dragon.getLocation()) <= 16384.0D)
                .toList();
        if (candidates.isEmpty() && target == null) return;
        List<Player> selected = new ArrayList<>(candidates);
        Collections.shuffle(selected);
        if (selected.isEmpty()) selected.add(target);
        selected.stream().limit(3).forEach(player -> {
            Location location = player.getLocation().clone().add(
                    ThreadLocalRandom.current().nextDouble(-24.0D, 24.0D),
                    ThreadLocalRandom.current().nextDouble(0.0D, 16.0D),
                    ThreadLocalRandom.current().nextDouble(-24.0D, 24.0D));
            Player aim = dragon.getWorld().getPlayers().stream()
                    .min(java.util.Comparator.comparingDouble(candidate -> candidate.getLocation().distanceSquared(location))).orElse(player);
            lasers.spawn(dragon, location, aim);
        });
    }

    private void homingShot(EnderDragon dragon, Player target) {
        if (target == null) return;
        dragon.getWorld().spawnParticle(Particle.PORTAL, dragon.getLocation(), 30, 1, 1, 1, 0.1);
        for (double angle : new double[] {-0.05236D, 0.0D, 0.05236D}) {
            Snowball shot = dragon.getWorld().spawn(dragon.getEyeLocation(), Snowball.class);
            shot.setShooter(dragon);
            shot.setItem(dragonHead());
            shot.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_projectile"), PersistentDataType.STRING, "dragon_homing");
            Vector direction = target.getEyeLocation().toVector().subtract(dragon.getEyeLocation().toVector()).normalize();
            direction.rotateAroundY(angle);
            shot.setVelocity(direction.multiply(1.0D));
        }
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_ENDER_DRAGON_SHOOT, 6.0F, 1.0F);
        dragon.getWorld().playSound(dragon.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 6.0F, 1.0F);
    }

    private void startCharge(EnderDragon dragon, State state) {
        Location location = dragon.getLocation();
        state.chargeYaw = location.getYaw() - 180.0F;
        state.chargePitch = location.getPitch();
        dragon.setPhase(EnderDragon.Phase.CIRCLING);
        dragon.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_FLAP, 10.0F, 0.5F);
        dragon.getWorld().playSound(location, Sound.ENTITY_ENDER_DRAGON_GROWL, 10.0F, 0.8F);
    }

    private void charge(EnderDragon dragon, State state) {
        state.chargePitch += state.tick < 40 ? 1.0F : -1.0F;
        Location rotation = dragon.getLocation().clone();
        rotation.setYaw(state.chargeYaw);
        rotation.setPitch(state.chargePitch);
        Location destination = dragon.getLocation().clone().add(rotation.getDirection().normalize().multiply(0.5D));
        destination.setYaw(state.chargeYaw);
        destination.setPitch(state.chargePitch);
        dragon.teleport(destination);
    }

    private void lightningPillar(EnderDragon dragon, Player target) {
        Location origin = dragon.getLocation();
        Location location = origin.clone().add(ThreadLocalRandom.current().nextDouble(-20.0D, 20.0D), 0.0D,
                ThreadLocalRandom.current().nextDouble(-20.0D, 20.0D));
        lightPillars.add(new LightPillar(location));
        dragon.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_BREAK, 3.0F, 1.0F);
        dragon.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2.0F, 2.0F);
    }

    private ItemStack dragonHead() {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.getServer().createProfile(UUID.fromString("dbdb9027-be2e-4458-b484-38dd2afa2081"));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOTY5ZGE0ZTdiOGQ1NThhZjAyZTgwZTJlMTY2OWNlYjYwODQ4OThkYjU1OTA3ZWYzZTY2ZDlhMzI5MzI5ZTk0MSJ9fX0="));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    /** projectile.dragon_light_pillar: 30tickの警告後に柱を炸裂させる。 */
    private void tickLightPillars(org.bukkit.World world) {
        Iterator<LightPillar> iterator = lightPillars.iterator();
        while (iterator.hasNext()) {
            LightPillar pillar = iterator.next();
            if (!pillar.location.getWorld().equals(world)) continue;
            pillar.age++;
            Location location = pillar.location;
            if (pillar.age < 30) continue;
            world.strikeLightningEffect(location);
            world.spawnParticle(Particle.EXPLOSION, location, 7, 1.5, 0.5, 1.5, 0);
            world.spawnParticle(Particle.END_ROD, location, 10, 0.2, 0.2, 0.2, 0.1);
            world.playSound(location, Sound.ENTITY_BLAZE_SHOOT, 2.0F, 1.0F);
            world.playSound(location, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.5F, 0.5F);
            world.playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0F, 2.0F);
            for (Player player : world.getPlayers()) {
                Location feet = player.getLocation();
                boolean inColumn = Math.abs(feet.getX() - location.getX()) <= 1.0D
                        && Math.abs(feet.getZ() - location.getZ()) <= 1.0D
                        && feet.getY() >= location.getY() - 1.0D && feet.getY() <= location.getY() + 10.0D;
                if (inColumn || feet.distanceSquared(location) <= 9.0D) {
                    player.damage(10.0D);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 5, 30, true, false));
                }
            }
            iterator.remove();
        }
    }

    private static final class LightPillar {
        private final Location location;
        private int age;
        private LightPillar(Location location) { this.location = location; }
    }

    private void reset(State state) {
        state.skill = Skill.NONE;
        state.tick = -10;
        state.landingApproach = false;
    }

    private enum Skill { NONE, CHARGE, AIMING_EYES, HOMING, LANDING }
    private static final class State {
        private int phase = 1;
        private int tick = -10;
        private int minionTick;
        private int platformTick;
        private boolean landingApproach;
        private float chargeYaw;
        private float chargePitch;
        private Skill skill = Skill.NONE;
        private final List<Skill> remaining = new ArrayList<>(List.of(Skill.CHARGE, Skill.AIMING_EYES, Skill.HOMING));
    }
}
