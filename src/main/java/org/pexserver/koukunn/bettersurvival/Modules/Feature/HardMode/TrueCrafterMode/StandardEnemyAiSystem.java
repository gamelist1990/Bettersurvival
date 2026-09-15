package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.MagmaCube;
import org.bukkit.entity.Marker;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PiglinBrute;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.Spider;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Vindicator;
import org.bukkit.entity.Witch;
import org.bukkit.entity.Wither;
import org.bukkit.entity.Zombie;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** 通常敵のmcfunctionに記録されたtick時刻と攻撃手順をPaper APIで実行する。 */
public final class StandardEnemyAiSystem {
    private final NamespacedKey projectileKey;
    private final NamespacedKey projectileOwnerKey;
    private final NamespacedKey projectileDamageKey;
    private final NamespacedKey creeperAttackCountKey;
    private final Map<UUID, Integer> ticks = new HashMap<>();
    private final Map<UUID, Integer> piglinHealTicks = new HashMap<>();
    private final Map<UUID, ItemStack> piglinWeapons = new HashMap<>();
    private final Map<UUID, Boolean> piglinHealUsed = new HashMap<>();
    private final Map<UUID, Integer> piglinFireTicks = new HashMap<>();
    private final Map<UUID, Integer> piglinCrossbowTicks = new HashMap<>();
    private final Map<UUID, Boolean> creeperExplosionRecovery = new HashMap<>();
    private final Map<UUID, Boolean> zombieLeaping = new HashMap<>();
    private final Map<UUID, Boolean> zombieLeapAirborne = new HashMap<>();
    private final Map<UUID, Boolean> zombieBruteLeaping = new HashMap<>();
    private final NamespacedKey variantKey = new NamespacedKey("bettersurvival", "truecrafter_variant");

    public StandardEnemyAiSystem(Loader plugin) {
        projectileKey = new NamespacedKey(plugin, "truecrafter_projectile");
        projectileOwnerKey = new NamespacedKey(plugin, "truecrafter_projectile_owner");
        projectileDamageKey = new NamespacedKey(plugin, "truecrafter_projectile_damage");
        creeperAttackCountKey = new NamespacedKey(plugin, "truecrafter_creeper_attack_count");
    }

    public void tickAmbient(LivingEntity enemy) {
        if (!(enemy instanceof Piglin piglin) || piglin.getEquipment() == null) return;
        Player target = piglin.getTarget() instanceof Player player && !player.getGameMode().isInvulnerable() ? player : null;
        tickPiglinFireResist(piglin, target);
    }
    public boolean tick(LivingEntity enemy, Player target) {
        if (enemy instanceof CaveSpider caveSpider) {
            spider(caveSpider, target, true);
            return true;
        }
        if (enemy instanceof Slime slime) {
            slime(slime);
            return true;
        }
        if (enemy instanceof Spider spider) {
            spider(spider, target, false);
            return true;
        }
        if (enemy instanceof Zombie zombie) {
            zombie(zombie, target);
            return true;
        }
        if (enemy instanceof Witch witch) {
            witch(witch, target);
            return true;
        }
        if (enemy instanceof Vindicator vindicator) {
            vindicator(vindicator, target);
            return true;
        }
        if (enemy instanceof PiglinBrute brute) {
            brute(brute, target);
            return true;
        }
        if (enemy instanceof Piglin piglin) {
            piglin(piglin, target);
            return true;
        }
        if (enemy instanceof Creeper creeper) {
            creeper(creeper, target);
            return true;
        }
        return false;
    }

    public void remove(UUID id) {
        ticks.remove(id);
        piglinHealTicks.remove(id);
        piglinWeapons.remove(id);
        piglinHealUsed.remove(id);
        piglinFireTicks.remove(id);
        piglinCrossbowTicks.remove(id);
        creeperExplosionRecovery.remove(id);
        zombieLeaping.remove(id);
        zombieLeapAirborne.remove(id);
        zombieBruteLeaping.remove(id);
    }

    public void clear() {
        ticks.clear();
        piglinHealTicks.clear();
        piglinWeapons.clear();
        piglinHealUsed.clear();
        piglinFireTicks.clear();
        piglinCrossbowTicks.clear();
        creeperExplosionRecovery.clear();
        zombieLeaping.clear();
        zombieLeapAirborne.clear();
        zombieBruteLeaping.clear();
    }

    private void zombie(Zombie zombie, Player target) {
        if ("zombie_brute".equals(zombie.getPersistentDataContainer().get(variantKey, PersistentDataType.STRING))) {
            zombieBrute(zombie, target);
            return;
        }
        if (isSpear(zombie)) {
            ticks.remove(zombie.getUniqueId());
            return;
        }
        UUID id = zombie.getUniqueId();
        boolean leaping = zombieLeaping.containsKey(id);
        updateZombieLeap(zombie, target, leaping);
        int current = ticks.getOrDefault(id, 0);
        boolean standstill = zombie.isOnGround() && zombie.getVelocity().getX() * zombie.getVelocity().getX()
                + zombie.getVelocity().getZ() * zombie.getVelocity().getZ() < 1.0E-6D;
        if (current < 30 && (leaping || standstill || zombie.getLocation().distanceSquared(target.getLocation()) > 100.0D)) return;
        int tick = increase(zombie);
        if (tick == 30) {
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 2.0F);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
            zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, zombie.getEyeLocation(), 1);
            tick = increase(zombie);
        }
        if (!bruteLeapPathClear(zombie)) {
            ticks.remove(id);
            return;
        }
        if (tick == 40 && !leaping) {
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0F, 1.2F);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_WITCH_THROW, 1.0F, 0.5F);
            Vector motion = target.getLocation().toVector().subtract(zombie.getLocation().toVector()).normalize().multiply(1.0D).setY(0.4D);
            zombie.setVelocity(motion);
            setBase(zombie, Attribute.ATTACK_KNOCKBACK, 1.5D);
            zombieLeaping.put(id, true);
        }
        if (tick >= 80) ticks.remove(id);
    }

    private void updateZombieLeap(Zombie zombie, Player target, boolean leaping) {
        if (!leaping) return;
        UUID id = zombie.getUniqueId();
        Vector direction = target.getLocation().toVector().subtract(zombie.getLocation().toVector()).setY(0.0D);
        if (direction.lengthSquared() > 0.0D) {
            Location facing = zombie.getLocation();
            facing.setDirection(direction);
            zombie.setRotation(facing.getYaw(), zombie.getLocation().getPitch());
        }
        if (!zombie.isOnGround()) zombieLeapAirborne.put(id, true);
        if (!zombieLeapAirborne.containsKey(id) || (!zombie.isOnGround() && !zombie.isInWater())) return;
        zombieLeaping.remove(id);
        zombieLeapAirborne.remove(id);
        setBase(zombie, Attribute.ATTACK_KNOCKBACK, 0.0D);
    }

    private void zombieBrute(Zombie zombie, Player target) {
        UUID id = zombie.getUniqueId();
        boolean leaping = zombieBruteLeaping.containsKey(id);
        int current = ticks.getOrDefault(id, 0);
        if (!leaping && current < 30 && zombie.getLocation().distanceSquared(target.getLocation()) > 256.0D) return;
        int tick = increase(zombie);
        if (!leaping && tick == 30) {
            bruteWindup(zombie);
            tick = increase(zombie);
        }
        if (!bruteLeapPathClear(zombie)) {
            ticks.remove(id);
            return;
        }
        if (leaping) {
            if (tick >= 50 && zombie.isOnGround()) {
                zombieBruteLeaping.remove(id);
                bruteLanding(zombie);
            }
        } else if (tick == 40) {
            Vector direction = target.getLocation().toVector().subtract(zombie.getLocation().toVector()).setY(0.0D);
            if (direction.lengthSquared() > 0.0D) zombie.setVelocity(direction.normalize().multiply(1.0D).setY(0.7D));
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.5F, 0.7F);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_IRON_GOLEM_ATTACK, 1.5F, 0.7F);
            zombieBruteLeaping.put(id, true);
            return;
        }
        if (tick == 70) bruteWindup(zombie);
        if (tick == 80) {
            setBase(zombie, Attribute.MOVEMENT_SPEED, 0.35D);
            zombie.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 1, false, false));
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.0F, 2.0F);
        }
        if (tick >= 80 && tick <= 160) {
            zombie.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, zombie.getLocation().add(0, 1, 0), 1, 0.3D, 0.5D, 0.3D, 0.0D);
            breakBrutePath(zombie, target);
        }
        if (tick >= 160) {
            setBase(zombie, Attribute.MOVEMENT_SPEED, 0.25D);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BREEZE_DEATH, 1.0F, 1.0F);
            zombie.getWorld().playSound(zombie.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.0F, 2.0F);
            ticks.remove(id);
        }
    }

    private void bruteWindup(Zombie zombie) {
        zombie.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false));
        zombie.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, zombie.getEyeLocation(), 1);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 2.0F);
    }

    private void bruteLanding(Zombie zombie) {
        for (Entity entity : zombie.getWorld().getNearbyEntities(zombie.getLocation(), 4.0D, 4.0D, 4.0D)) {
            if (!(entity instanceof LivingEntity victim) || victim == zombie || victim.isInvulnerable()) continue;
            if (!(victim instanceof Player)) continue;
            if (victim.getLocation().distanceSquared(zombie.getLocation()) > 16.0D) continue;
            victim.damage(7.0D, zombie);
        }
        zombie.getWorld().spawnParticle(Particle.EXPLOSION, zombie.getLocation(), 5, 2.0D, 0.0D, 2.0D, 0.5D);
        zombie.getWorld().spawnParticle(Particle.CRIT, zombie.getLocation(), 50, 2.0D, 0.5D, 2.0D, 0.5D);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.5F, 1.5F);
        zombie.getWorld().playSound(zombie.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.5F);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) for (int y = 0; y <= 2; y++) breakBruteBlock(zombie.getLocation().clone().add(x, y, z).getBlock());
    }

    private void breakBrutePath(Zombie zombie, Player target) {
        Vector forward = zombie.getLocation().getDirection().setY(0.0D);
        if (forward.lengthSquared() == 0.0D) return;
        forward.normalize();
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        Location base = zombie.getLocation().add(forward);
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) breakBruteBlock(base.clone().add(side.clone().multiply(x)).add(0.0D, y, 0.0D).getBlock());
    }

    private boolean bruteLeapPathClear(Zombie zombie) {
        Location origin = zombie.getLocation();
        Vector forward = origin.getDirection().setY(0.0D);
        if (forward.lengthSquared() == 0.0D) return false;
        forward.normalize();
        Location lower = origin.clone().add(forward);
        Location upper = lower.clone().add(0.0D, 1.0D, 0.0D);
        return lower.getBlock().isPassable() && upper.getBlock().isPassable();
    }

    private void breakBruteBlock(Block block) {
        Material type = block.getType();
        if (type.isAir() || type.getHardness() < 0.0F || Set.of(
                Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHER_PORTAL,
                Material.END_PORTAL_FRAME, Material.ENCHANTING_TABLE, Material.BARRIER,
                Material.BEDROCK, Material.END_GATEWAY, Material.COMMAND_BLOCK,
                Material.REPEATING_COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
                Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.MOVING_PISTON).contains(type)) return;
        block.breakNaturally();
    }

    private void spider(Spider spider, Player target, boolean poisonous) {
        if (spider.getLocation().distanceSquared(target.getLocation()) > 1024.0D) return;
        if (increase(spider) < 60) return;
        ticks.remove(spider.getUniqueId());
        double distance = spider.getLocation().distance(target.getLocation());
        if (poisonous) {
            if (distance >= 5.0D && distance <= 14.0D) {
                spider.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false));
                spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SPIDER_DEATH, 2.0F, 2.0F);
                spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SLIME_ATTACK, 2.0F, 2.0F);
                launch(spider, caveSpiderAim(spider, target), "poison", Material.SPIDER_EYE, 1.5D);
            }
            return;
        }
        spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SPIDER_DEATH, 2.0F, 2.0F);
        spider.getWorld().playSound(spider.getLocation(), Sound.ENTITY_SLIME_ATTACK, 2.0F, 2.0F);
        if (distance >= 7.0D && distance <= 14.0D) {
            launch(spider, target.getEyeLocation().add(0.0D, 1.0D, 0.0D), "web", Material.COBWEB, 1.5D);
        } else if (distance < 7.0D) {
            launchSpiderSpread(spider);
        }
    }

    private Location caveSpiderAim(Spider spider, Player target) {
        Location aim = spider.getLocation();
        aim.setDirection(target.getEyeLocation().toVector().subtract(aim.toVector()));
        aim.setPitch(aim.getPitch() - 10.0F);
        return spider.getLocation().clone().add(aim.getDirection().multiply(5.0D));
    }

    private void launchSpiderSpread(Spider spider) {
        Location origin = spider.getLocation();
        Vector forward = origin.getDirection().setY(0.0D);
        if (forward.lengthSquared() == 0.0D) return;
        forward.normalize();
        Vector side = new Vector(-forward.getZ(), 0.0D, forward.getX());
        launch(spider, origin.clone().add(side).add(0.0D, 1.0D, 0.0D), "web", Material.COBWEB, 1.0D);
        launch(spider, origin.clone().subtract(side).add(0.0D, 1.0D, 0.0D), "web", Material.COBWEB, 1.0D);
        launch(spider, origin.clone().add(side.clone().multiply(0.5D)).add(forward).add(0.0D, 1.0D, 0.0D), "web", Material.COBWEB, 1.0D);
        launch(spider, origin.clone().subtract(side.clone().multiply(0.5D)).add(forward).add(0.0D, 1.0D, 0.0D), "web", Material.COBWEB, 1.0D);
    }

    private void witch(Witch witch, Player target) {
        witch.removePotionEffect(PotionEffectType.POISON);
        witch.removePotionEffect(PotionEffectType.SLOWNESS);
        for (Entity nearby : witch.getNearbyEntities(16, 16, 16)) {
            if (nearby instanceof Creeper creeper && !creeper.isPowered()) {
                creeper.setPowered(true);
                creeper.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, creeper.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0);
                creeper.getWorld().playSound(creeper.getLocation(), Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0F, 2.0F);
                creeper.getWorld().playSound(creeper.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.0F, 2.0F);
            }
            if (nearby instanceof LivingEntity living && !(nearby instanceof Witch)
                    && (nearby instanceof Monster || nearby instanceof Wither || nearby instanceof EnderDragon)) {
                LivingEntity monster = living;
                monster.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 20, 0, false, false));
                monster.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 20, 0, false, false));
                monster.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 20, 0, false, false));
                monster.removePotionEffect(PotionEffectType.POISON);
                monster.removePotionEffect(PotionEffectType.SLOWNESS);
                monster.removePotionEffect(PotionEffectType.WEAKNESS);
                monster.removePotionEffect(PotionEffectType.WITHER);
                monster.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, monster.getLocation().add(0, 1, 0), 1, 0.3D, 0.5D, 0.3D, 0.0D);
                monster.getWorld().spawnParticle(Particle.WITCH, monster.getLocation().add(0, 1, 0), 1, 0.2, 0.5, 0.2, 0);
            }
        }
        if (witch.getLocation().distanceSquared(target.getLocation()) > 256.0D || increase(witch) < 60) return;
        if (teleportRelative(witch, target)) ticks.remove(witch.getUniqueId());
    }

    private void vindicator(Vindicator vindicator, Player target) {
        int current = ticks.getOrDefault(vindicator.getUniqueId(), 0);
        if (current < 40 && vindicator.getLocation().distanceSquared(target.getLocation()) > 100.0D) return;
        int tick = increase(vindicator);
        if (tick == 40) {
            vindicator.getWorld().playSound(vindicator.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 2.0F);
            vindicator.getWorld().spawnParticle(Particle.ANGRY_VILLAGER, vindicator.getEyeLocation(), 1);
            tick = increase(vindicator);
        } else if (tick == 60) {
            vindicator.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 40, 0, false, false));
            setBase(vindicator, Attribute.KNOCKBACK_RESISTANCE, 1.0D);
            setBase(vindicator, Attribute.MOVEMENT_SPEED, 0.25D);
            vindicator.getWorld().playSound(vindicator.getLocation(), Sound.ENTITY_VINDICATOR_CELEBRATE, 1.0F, 1.0F);
            vindicator.getWorld().playSound(vindicator.getLocation(), Sound.ENTITY_BLAZE_AMBIENT, 1.0F, 2.0F);
            vindicator.getWorld().spawnParticle(Particle.ENTITY_EFFECT, vindicator.getLocation().add(0, 1, 0), 25,
                    0.3D, 0.5D, 0.3D, 0.0D, org.bukkit.Color.fromRGB(255, 128, 0), true);
        } else if (tick >= 100) {
            setBase(vindicator, Attribute.KNOCKBACK_RESISTANCE, 0.0D);
            setBase(vindicator, Attribute.MOVEMENT_SPEED, 0.3D);
            ticks.remove(vindicator.getUniqueId());
        }
        if (tick >= 60 && tick < 100) {
            vindicator.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION,
                    vindicator.getLocation().add(0, 1, 0), 1,
                    0.3D, 0.5D, 0.3D, 0.0D);
        }
    }

    private void brute(PiglinBrute brute, Player target) {
        int current = ticks.getOrDefault(brute.getUniqueId(), 0);
        if (current < 40 && brute.getLocation().distanceSquared(target.getLocation()) > 256.0D) return;
        int tick = increase(brute);
        if (tick == 40) {
            brute.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false));
            brute.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION, brute.getLocation().add(0, 1, 0), 35, 0.3, 1, 0.3, 0);
            brute.getWorld().playSound(brute.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 2.0F, 1.2F);
            brute.getWorld().playSound(brute.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_OMINOUS_ACTIVATE, 2.0F, 0.5F);
            brute.getWorld().playSound(brute.getLocation(), Sound.ENTITY_PIGLIN_BRUTE_ANGRY, 1.0F, 0.7F);
            tick = increase(brute);
        } else if (tick == 80) {
            shockwave(brute, target);
        } else if (tick >= 120) {
            ticks.remove(brute.getUniqueId());
        }
    }

    private void tickPiglinFireResist(Piglin piglin, Player target) {
        UUID id = piglin.getUniqueId();
        Integer fireActive = piglinFireTicks.get(id);
        if (fireActive != null) {
            int tick = fireActive + 1;
            piglinFireTicks.put(id, tick);
            if (tick == 10 || tick == 15 || tick == 20) piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1.0F, 1.0F);
            if (tick < 30) return;
            piglinFireTicks.remove(id);
            ItemStack weapon = piglinWeapons.remove(id);
            if (weapon != null) piglin.getEquipment().setItemInMainHand(weapon);
            piglin.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 120, 0, false, false));
            piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0F, 1.0F);
            return;
        }
        if (piglin.getFireTicks() <= 0 || piglin.hasPotionEffect(PotionEffectType.FIRE_RESISTANCE)) return;
        piglinWeapons.put(id, piglin.getEquipment().getItemInMainHand().clone());
        ItemStack potion = new ItemStack(Material.POTION);
        PotionMeta potionMeta = (PotionMeta) potion.getItemMeta();
        potionMeta.setBasePotionType(PotionType.FIRE_RESISTANCE);
        potion.setItemMeta(potionMeta);
        piglin.getEquipment().setItemInMainHand(potion);
        piglin.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
        if (target != null && piglin.getLocation().distance(target.getLocation()) <= 7.0D) {
            Vector away = piglin.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0D);
            if (away.lengthSquared() > 0.0D) {
                piglin.setVelocity(away.normalize().multiply(0.7D).setY(0.4D));
                piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_PIGLIN_JEALOUS, 1.0F, 1.5F);
                piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0F, 1.2F);
            }
        }
        piglinFireTicks.put(id, 0);
    }
    private void piglin(Piglin piglin, Player target) {
        if (piglin.getEquipment() == null) return;
        if (piglin.getEquipment().getItemInMainHand().getType() == Material.CROSSBOW) {
            piglinCrossbow(piglin, target);
            return;
        }
        UUID id = piglin.getUniqueId();
        Integer active = piglinHealTicks.get(id);
        if (active != null) {
            int tick = active + 1;
            piglinHealTicks.put(id, tick);
            if (tick == 10 || tick == 15 || tick == 20) piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_GENERIC_EAT, 1.0F, 1.0F);
            if (tick < 30) return;
            piglinHealTicks.remove(id);
            ItemStack weapon = piglinWeapons.remove(id);
            if (weapon != null) piglin.getEquipment().setItemInMainHand(weapon);
            // Instant Health II (amplifier 1) は通常体力を8回復する。
            piglin.setHealth(Math.min(piglin.getAttribute(Attribute.MAX_HEALTH).getValue(), piglin.getHealth() + 8.0D));
            piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_PLAYER_BURP, 1.0F, 1.0F);
            piglin.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, piglin.getLocation().add(0, 1, 0), 10, 0.25D, 0.5D, 0.25D, 0);
            piglinHealUsed.put(id, true);
            return;
        }
        if (piglinHealUsed.getOrDefault(id, false) || piglin.getHealth() > 10.0D || piglin.getLocation().distance(target.getLocation()) <= 8.0D) return;
        piglinWeapons.put(id, piglin.getEquipment().getItemInMainHand().clone());
        piglin.getEquipment().setItemInMainHand(new ItemStack(Material.COOKED_PORKCHOP));
        piglin.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 1, false, false));
        piglinHealTicks.put(id, 0);
    }

    private void piglinCrossbow(Piglin piglin, Player target) {
        UUID id = piglin.getUniqueId();
        if (piglin.getLocation().distance(target.getLocation()) > 4.0D) {
            piglinCrossbowTicks.remove(id);
            return;
        }
        int tick = piglinCrossbowTicks.merge(id, 1, Integer::sum);
        Vector away = piglin.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0D);
        if (tick < 30 || away.lengthSquared() == 0.0D) return;
        away.normalize();
        Location backward = piglin.getLocation().clone().add(away);
        Location landingBelow = piglin.getLocation().clone().add(away.clone().multiply(3.0D)).subtract(0.0D, 1.0D, 0.0D);
        if (!backward.getBlock().isPassable() || landingBelow.getBlock().isPassable()) return;
        piglinCrossbowTicks.remove(id);
        piglin.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false));
        piglin.setVelocity(away.multiply(1.0D).setY(0.2D));
        piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_PIGLIN_JEALOUS, 1.0F, 1.5F);
        piglin.getWorld().playSound(piglin.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0F, 1.2F);
    }

    private void creeper(Creeper creeper, Player target) {
        if (creeper.isIgnited()) {
            creeper.setMaxFuseTicks(30);
            ticks.remove(creeper.getUniqueId());
            creeper.removePotionEffect(PotionEffectType.INVISIBILITY);
            return;
        }
        creeper.setMaxFuseTicks(creeper.isInWater() ? 30 : 9999);
        if (creeperExplosionRecovery.remove(creeper.getUniqueId()) != null) {
            creeper.setInvulnerable(false);
            setBase(creeper, Attribute.KNOCKBACK_RESISTANCE, 0.0D);
            return;
        }
        double distance = creeper.getLocation().distance(target.getLocation());
        int current = ticks.getOrDefault(creeper.getUniqueId(), 0);
        if (current == 0 && distance >= 4.0D && distance <= 32.0D && creeper.getNoDamageTicks() == 0) {
            creeper.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 25, 0, false, false));
        } else if (distance < 4.0D) {
            creeper.removePotionEffect(PotionEffectType.INVISIBILITY);
        }
        boolean standstill = creeper.isOnGround() && creeper.getVelocity().getX() * creeper.getVelocity().getX()
                + creeper.getVelocity().getZ() * creeper.getVelocity().getZ() < 1.0E-6D;
        if (current < 25 && (distance > 7.0D || !standstill)) {
            if (current > 0) ticks.remove(creeper.getUniqueId());
            setBase(creeper, Attribute.KNOCKBACK_RESISTANCE, 0.0D);
            return;
        }
        int tick = increase(creeper);
        if (standstill) {
            setBase(creeper, Attribute.KNOCKBACK_RESISTANCE, 1.0D);
            creeper.getWorld().spawnParticle(Particle.SMOKE, creeper.getLocation().add(0, 0.5D, 0), 5, 0.3D, 0.5D, 0.3D, 0.0D);
            creeper.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, creeper.getLocation().add(0, 0.5D, 0), 2, 0.2D, 0.5D, 0.2D, 0.5D);
        }
        if (tick == 25) {
            creeper.getWorld().playSound(creeper.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 1.0F);
        } else if (tick == 30) {
            creeper.setInvulnerable(true);
            creeper.getWorld().createExplosion(creeper.getLocation(), creeper.isPowered() ? 6.0F : 3.0F, false, true, creeper);
            int count = creeper.getPersistentDataContainer().getOrDefault(creeperAttackCountKey, PersistentDataType.INTEGER, 0) + 1;
            creeper.getPersistentDataContainer().set(creeperAttackCountKey, PersistentDataType.INTEGER, count);
            if (count >= 3) creeper.remove();
            else creeperExplosionRecovery.put(creeper.getUniqueId(), true);
            ticks.remove(creeper.getUniqueId());
        }
    }

    private void slime(Slime slime) {
        long nearbySlimes = slime.getNearbyEntities(16, 16, 16).stream()
                .filter(entity -> entity.getType() == org.bukkit.entity.EntityType.SLIME).count();
        long limit = slime instanceof MagmaCube ? 6L : 5L;
        if (nearbySlimes >= limit) {
            ticks.remove(slime.getUniqueId());
            return;
        }
        int size = slime.getSize();
        int threshold = size == 0 ? 160 : 100;
        if (increase(slime) < threshold || size >= 4) return;
        ticks.remove(slime.getUniqueId());
        slime.setSize(size + 1);
        configureSlimeSize(slime);
        slime.getWorld().spawnParticle(Particle.EXPLOSION, slime.getLocation().add(0, 0.8D, 0), 1);
        slime.getWorld().spawnParticle(Particle.INSTANT_EFFECT, slime.getLocation().add(0, 0.8D, 0), 5, 0, 0, 0, 0.1D);
        slime.getWorld().playSound(slime.getLocation(), Sound.ENTITY_SLIME_ATTACK, 1.5F, 0.75F);
    }

    private void configureSlimeSize(Slime slime) {
        int size = slime.getSize();
        double speed = size <= 2 ? 0.7D : size == 3 ? 0.9D : 1.0D;
        double jump = size <= 2 ? 0.8D : size == 3 ? 1.0D : 1.1D;
        double health = size == 1 ? 4.0D : size == 2 ? 8.0D : size == 3 ? 16.0D : 24.0D;
        setBase(slime, Attribute.MOVEMENT_SPEED, speed);
        setBase(slime, Attribute.JUMP_STRENGTH, jump);
        setBase(slime, Attribute.MAX_HEALTH, health);
        slime.setHealth(Math.min(health, slime.getAttribute(Attribute.MAX_HEALTH).getValue()));
    }

    private void shockwave(PiglinBrute brute, Player target) {
        Vector direction = target.getLocation().toVector().subtract(brute.getLocation().toVector()).setY(0.0D);
        Location impact = brute.getLocation().clone();
        if (direction.lengthSquared() > 0.0D) impact.add(direction.normalize().multiply(2.0D));
        brute.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 10, false, false));
        brute.getWorld().spawnParticle(Particle.BLOCK, impact.clone().add(0, 0.5D, 0), 100, 1.5D, 0.0D, 1.5D, 0.5D, Material.STONE.createBlockData());
        brute.getWorld().spawnParticle(Particle.CRIT, impact, 50, 2.0D, 0.5D, 2.0D, 0.5D);
        brute.getWorld().spawnParticle(Particle.EXPLOSION, impact, 5, 2.0D, 0.0D, 2.0D, 0.5D);
        brute.getWorld().playSound(brute.getLocation(), Sound.ITEM_MACE_SMASH_GROUND, 1.5F, 1.5F);
        brute.getWorld().playSound(brute.getLocation(), Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1.5F, 0.5F);
        // 本家の enemy_of_piglin 対象（プレイヤーと敵対Mob）へ範囲ダメージ。
        for (Entity entity : brute.getWorld().getNearbyEntities(impact, 2.0D, 2.0D, 2.0D)) {
            if (!(entity instanceof LivingEntity victim) || victim == brute || victim.isInvulnerable()) continue;
            if (!(victim instanceof Player) && !(victim instanceof Monster)) continue;
            victim.damage(19.0D, brute);
        }
        Marker wave = brute.getWorld().spawn(brute.getLocation(), Marker.class);
        wave.setRotation(brute.getLocation().getYaw(), 0.0F);
        wave.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, "brute");
        wave.getPersistentDataContainer().set(projectileOwnerKey, PersistentDataType.STRING, brute.getUniqueId().toString());
        wave.getPersistentDataContainer().set(projectileDamageKey, PersistentDataType.DOUBLE, 12.0D);
    }

    private void launch(LivingEntity source, Location target, String kind, Material display, double speed) {
        Snowball ball = source.getWorld().spawn(source.getEyeLocation(), Snowball.class);
        ball.setShooter(source);
        ball.setItem(new org.bukkit.inventory.ItemStack(display));
        if (kind.equals("brute")) ball.setGravity(false);
        ball.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, kind);
        ball.setVelocity(target.toVector().subtract(source.getEyeLocation().toVector()).normalize().multiply(speed));
    }

    private boolean teleportRelative(Witch witch, Player target) {
        Location origin = witch.getLocation();
        Vector towardTarget = target.getLocation().toVector().subtract(origin.toVector()).setY(0.0D);
        if (towardTarget.lengthSquared() == 0.0D) return false;
        towardTarget.normalize();
        boolean backwardClear = teleportPassable(origin, towardTarget.clone().multiply(-1.0D), 1);
        boolean forwardClear = teleportPassable(origin, towardTarget, 1);
        if (!backwardClear && !forwardClear) return false;
        Vector direction = !backwardClear ? towardTarget
                : origin.distanceSquared(target.getLocation()) <= 25.0D ? towardTarget.clone().multiply(-1.0D) : towardTarget;
        if (!teleportPassable(origin, direction, 1)) return false;
        Location destination = origin;
        for (int distance = 1; distance <= 6 && teleportPassable(origin, direction, distance); distance++) {
            destination = origin.clone().add(direction.clone().multiply(distance));
        }
        witch.getWorld().spawnParticle(Particle.WITCH, origin.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.0D);
        witch.getWorld().spawnParticle(Particle.DUST, origin.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 1.0D, new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 0, 255), 1.0F));
        witch.teleport(destination);
        witch.getWorld().playSound(destination, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0F, 1.0F);
        witch.getWorld().spawnParticle(Particle.WITCH, destination.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 0.0D);
        witch.getWorld().spawnParticle(Particle.DUST, destination.clone().add(0.0D, 1.0D, 0.0D), 25, 0.3D, 0.5D, 0.3D, 1.0D, new Particle.DustOptions(org.bukkit.Color.fromRGB(204, 0, 255), 1.0F));
        return true;
    }

    private boolean teleportPassable(Location origin, Vector direction, int distance) {
        Location candidate = origin.clone().add(direction.clone().multiply(distance));
        return candidate.getBlock().isPassable() && candidate.clone().add(0.0D, 1.0D, 0.0D).getBlock().isPassable();
    }

    private int increase(LivingEntity entity) {
        return ticks.merge(entity.getUniqueId(), 1, Integer::sum);
    }

    private boolean clearAhead(LivingEntity entity) {
        return entity.getEyeLocation().add(entity.getLocation().getDirection()).getBlock().isPassable();
    }

    private boolean isSpear(LivingEntity entity) {
        if (entity.getEquipment() == null) return false;
        return switch (entity.getEquipment().getItemInMainHand().getType()) {
            case WOODEN_SPEAR, STONE_SPEAR, COPPER_SPEAR, IRON_SPEAR, GOLDEN_SPEAR, DIAMOND_SPEAR, NETHERITE_SPEAR -> true;
            default -> false;
        };
    }

    private void setBase(LivingEntity entity, Attribute attribute, double value) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }
}
