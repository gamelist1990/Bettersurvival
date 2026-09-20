package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Evoker;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.PigZombie;
import org.bukkit.entity.Piglin;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** TrueCrafterModeの戦闘システムをPaper APIだけで再現する。 */
public final class TrueCrafterModeModule implements Listener {
    private static final float SKELETON_SHEATH_SCALE = 0.12F;
    private static final float WITHER_SKELETON_SHEATH_SCALE = 0.5F;

    private final Loader plugin;
    private final TrueCrafterSettings settings;
    private final NamespacedKey enhancedKey;
    private final NamespacedKey variantKey;
    private final NamespacedKey projectileKey;
    private final NamespacedKey endermanModeKey;
    private final NamespacedKey witherKnightKey;
    private final NamespacedKey witherMinionKey;
    private final NamespacedKey zealotKey;
    private final NamespacedKey sheathOwnerKey;
    private final NamespacedKey sheathRenderVersionKey;
    private final NamespacedKey creeperAttackCountKey;
    private final Map<UUID, Integer> rangedBackSteps = new HashMap<>();
    private final Map<UUID, Integer> terrainDigTicks = new HashMap<>();
    private final Map<UUID, Integer> terrainPlaceTicks = new HashMap<>();
    private final Map<UUID, Integer> terrainBridgeTicks = new HashMap<>();
    private final Map<UUID, Integer> terrainPlaceCooldowns = new HashMap<>();
    private final Set<UUID> creeperDigRecovery = new HashSet<>();
    private final Map<UUID, Integer> endermanBreakTicks = new HashMap<>();
    private final Map<UUID, Integer> witherKnightCooldowns = new HashMap<>();
    private final Map<UUID, Integer> witherKnightShots = new HashMap<>();
    private final TemporaryEnemyBlockSystem temporaryBlocks;
    private final StandardEnemyAiSystem standardEnemyAi;
    private final SheathedWeaponSystem sheathedWeaponSystem;
    private final OminousCampfireSystem ominousCampfire;
    private final EvokerAiSystem evokerAi;
    private final WitherBossSystem witherBoss;
    private final EnderDragonBossSystem enderDragonBoss;
    private final EnderZealotAiSystem enderZealotAi;
    private final ProjectileMotionSystem projectileMotion;
    private final HardModeLootFactory lootFactory;
    private final MobProfileInitializer mobProfiles;
    private final Map<Attribute, NamespacedKey> modifierKeys = new HashMap<>();
    private BukkitTask aiTask;

    public TrueCrafterModeModule(Loader plugin) {
        this.plugin = plugin;
        settings = new TrueCrafterSettings(plugin);
        enhancedKey = new NamespacedKey(plugin, "truecrafter_enhanced");
        variantKey = new NamespacedKey(plugin, "truecrafter_variant");
        projectileKey = new NamespacedKey(plugin, "truecrafter_projectile");
        endermanModeKey = new NamespacedKey(plugin, "truecrafter_enderman_mode");
        witherKnightKey = new NamespacedKey(plugin, "truecrafter_wither_knight");
        witherMinionKey = new NamespacedKey(plugin, "truecrafter_wither_minion");
        zealotKey = new NamespacedKey(plugin, "truecrafter_zealot");
        sheathOwnerKey = new NamespacedKey(plugin, "truecrafter_sheath_owner");
        sheathRenderVersionKey = new NamespacedKey(plugin, "truecrafter_sheath_render_v6");
        creeperAttackCountKey = new NamespacedKey(plugin, "truecrafter_creeper_attack_count");
        temporaryBlocks = new TemporaryEnemyBlockSystem(plugin);
        standardEnemyAi = new StandardEnemyAiSystem(plugin);
        sheathedWeaponSystem = new SheathedWeaponSystem(plugin);
        evokerAi = new EvokerAiSystem();
        witherBoss = new WitherBossSystem(plugin, sheathedWeaponSystem);
        enderDragonBoss = new EnderDragonBossSystem(plugin);
        enderZealotAi = new EnderZealotAiSystem();
        projectileMotion = new ProjectileMotionSystem(plugin);
        lootFactory = new HardModeLootFactory();
        mobProfiles = new MobProfileInitializer(plugin);
        modifierKeys.put(Attribute.MAX_HEALTH, new NamespacedKey(plugin, "truecrafter_health"));
        modifierKeys.put(Attribute.ATTACK_DAMAGE, new NamespacedKey(plugin, "truecrafter_damage"));
        modifierKeys.put(Attribute.MOVEMENT_SPEED, new NamespacedKey(plugin, "truecrafter_speed"));
        modifierKeys.put(Attribute.FOLLOW_RANGE, new NamespacedKey(plugin, "truecrafter_range"));
        modifierKeys.put(Attribute.STEP_HEIGHT, new NamespacedKey(plugin, "truecrafter_step"));
        modifierKeys.put(Attribute.KNOCKBACK_RESISTANCE, new NamespacedKey(plugin, "truecrafter_knockback"));
        ominousCampfire = new OminousCampfireSystem(plugin, settings, this::setHeatLevel);
        Bukkit.getPluginManager().registerEvents(ominousCampfire, plugin);
        if (isEnabled()) enableRuntime();
    }

    public boolean isEnabled() {
        return settings.enabled();
    }

    public String setEnabled(boolean enabled) {
        settings.enabled(enabled);
        if (enabled) enableRuntime();
        else disableRuntime();
        return null;
    }

    public int heatLevel() {
        return settings.heatLevel();
    }

    public void setHeatLevel(int level) {
        settings.heatLevel(level);
        if (isEnabled()) Bukkit.getWorlds().forEach(world -> world.getLivingEntities().forEach(entity -> {
            removeEnhancement(entity);
            enhance(entity);
        }));
    }

    public void shutdown() {
        if (aiTask != null) aiTask.cancel();
        temporaryBlocks.shutdown();
        ominousCampfire.shutdown();
        projectileMotion.shutdown();
        witherBoss.shutdown();
        enderDragonBoss.shutdown();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent event) {
        if (!isEnabled()) return;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent event) {
        if (!isEnabled()) return;
        event.getEntities().stream()
                .filter(ItemDisplay.class::isInstance)
                .map(ItemDisplay.class::cast)
                .forEach(this::removeOrphanedSheath);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onAttack(EntityDamageByEntityEvent event) {
        if (!isEnabled()) return;
        if (event.getEntity() instanceof Player player
                && event.getDamager() instanceof AbstractArrow arrow
                && ("elite_arrow".equals(arrow.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING))
                || "elite_wither_arrow".equals(arrow.getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING)))
                && player.isBlocking()) {
            arrow.setGravity(true);
            arrow.setVelocity(new org.bukkit.util.Vector());
            event.setCancelled(true);
            return;
        }
        if (event.getEntity() instanceof Slime zealot && zealot.getPersistentDataContainer().has(new NamespacedKey(plugin, "truecrafter_zealot"), PersistentDataType.BYTE)) {
            zealot.getWorld().playSound(zealot.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.0F, 1.5F);
            zealot.getWorld().playSound(zealot.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0F, 1.5F);
        }
        if (!(event.getEntity() instanceof Player player)) return;
        LivingEntity attacker = attacker(event.getDamager());
        if (attacker == null || !isEnemy(attacker)) return;
        String variant = variant(attacker);
        if (attacker instanceof WitherSkeleton) player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 120, 1));
        if (variant.contains("elite")) event.setDamage(event.getDamage() * 1.35D);
    }

    @EventHandler(ignoreCancelled = true)
    public void onWitherKnightHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton)
                || !skeleton.getPersistentDataContainer().has(witherKnightKey, PersistentDataType.BYTE)) return;
        skeleton.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_WITHER_HURT, 0.5F, 1.5F);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        String kind = event.getEntity().getPersistentDataContainer().get(projectileKey, PersistentDataType.STRING);
        if (kind == null) return;
        if (kind.equals("dragon_homing") && event.getHitBlock() != null) {
            Location impact = event.getEntity().getLocation();
            impact.getWorld().spawnParticle(Particle.DRAGON_BREATH, impact, 50, 0.5D, 0.5D, 0.5D, 0.3D);
            impact.getWorld().playSound(impact, Sound.ENTITY_DRAGON_FIREBALL_EXPLODE, 1.5F, 1.0F);
            scheduleDragonSphere(impact);
        }
        if (kind.equals("zealot") && event.getHitBlock() != null) {
            event.getEntity().setGlowing(true);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (event.getEntity().isValid()) event.getEntity().setGlowing(false);
            });
        }
        if (!(event.getHitEntity() instanceof Player player)) return;
        if (kind.equals("web")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 3));
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 40, 5));
            player.getWorld().spawnParticle(Particle.ITEM_COBWEB, player.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.05);
        } else if (kind.equals("poison")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 140, 0));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_SLIME_HURT_SMALL, 1.0F, 1.2F);
        } else if (kind.equals("void")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0));
            player.damage(5.0D, event.getEntity());
        } else if (kind.equals("brute")) {
            player.damage(12.0D, event.getEntity());
            player.setVelocity(player.getLocation().toVector().subtract(event.getEntity().getLocation().toVector()).normalize().multiply(1.1D).setY(0.35D));
        } else if (kind.equals("zealot")) {
            player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_EYE_DEATH, 1.0F, 1.5F);
            player.damage(6.0D, event.getEntity());
        } else if (kind.equals("dragon_homing")) {
            player.damage(7.0D, event.getEntity());
        } else if (kind.equals("wither_homing")) {
            player.damage(7.0D, event.getEntity());
        } else if (kind.equals("elite_wither_arrow")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 400, 1));
        }
    }

    private void scheduleDragonSphere(Location origin) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private int age;
            @Override public void run() {
                ++age;
                origin.getWorld().spawnParticle(Particle.DRAGON_BREATH, origin, 10, 1.5D, 1.5D, 1.5D, 0.01D);
                if (age >= 10 && age % 20 == 0) {
                    for (Player player : origin.getWorld().getPlayers()) {
                        if (!player.getGameMode().isInvulnerable() && player.getLocation().distanceSquared(origin) <= 9.0D) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.INSTANT_DAMAGE, 1, 0, true, false));
                        }
                    }
                }
                if (age >= 140) cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof AbstractArrow arrow) || !(arrow.getShooter() instanceof LivingEntity shooter)) return;
        String kind = shooter instanceof WitherSkeleton
                ? "elite_wither_arrow" : variant(shooter).equals("elite") ? "elite_arrow" : null;
        if (kind == null) return;
        arrow.setGravity(false);
        arrow.setCritical(true);
        arrow.setVelocity(arrow.getVelocity().normalize().multiply(1.5D));
        arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, kind);
        if (kind.equals("elite_wither_arrow")) arrow.setFireTicks(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onKnightShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof WitherSkeleton skeleton) || !skeleton.getPersistentDataContainer().has(witherKnightKey, PersistentDataType.BYTE)) return;
        UUID id = skeleton.getUniqueId();
        if (witherKnightCooldowns.getOrDefault(id, 0) > 0) return;
        if (witherKnightShots.merge(id, 1, Integer::sum) < 4) return;
        witherKnightShots.remove(id);
        witherKnightCooldowns.put(id, 140);
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (event.getEntity() instanceof Slime zealot
                && zealotKey != null
                && zealot.getPersistentDataContainer().has(zealotKey, PersistentDataType.BYTE)) {
            ItemDisplay eye = zealot.getPassengers().stream().filter(ItemDisplay.class::isInstance)
                    .map(ItemDisplay.class::cast).findFirst().orElse(null);
            if (eye != null) {
                zealot.removePassenger(eye);
                animateZealotEyeDeath(eye, zealot);
            }
            zealot.getPassengers().forEach(Entity::remove);
        }
        String ownerId = event.getEntity().getUniqueId().toString();
        event.getEntity().getWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(display -> ownerId.equals(display.getPersistentDataContainer().get(sheathOwnerKey, PersistentDataType.STRING)))
                .forEach(Entity::remove);
        if (event.getEntity() instanceof LivingEntity skeleton && isRangedSkeleton(skeleton)) {
            Location deathLocation = skeleton.getLocation();
            skeleton.getWorld().getNearbyEntities(deathLocation, 3.0D, 3.0D, 3.0D).stream()
                    .filter(ItemDisplay.class::isInstance)
                    .map(ItemDisplay.class::cast)
                    .filter(display -> display.getPersistentDataContainer().get(sheathOwnerKey, PersistentDataType.STRING) == null)
                    .filter(display -> display.getItemStack().getType() == Material.STONE_SWORD
                            || display.getItemStack().getType() == Material.STONE_AXE
                            || display.getItemStack().getType() == Material.NETHERITE_SWORD)
                    .forEach(Entity::remove);
        }
        rangedBackSteps.remove(event.getEntity().getUniqueId());
        sheathedWeaponSystem.remove(event.getEntity().getUniqueId());
        terrainDigTicks.remove(event.getEntity().getUniqueId());
        terrainPlaceTicks.remove(event.getEntity().getUniqueId());
        terrainBridgeTicks.remove(event.getEntity().getUniqueId());
        terrainPlaceCooldowns.remove(event.getEntity().getUniqueId());
        creeperDigRecovery.remove(event.getEntity().getUniqueId());
        endermanBreakTicks.remove(event.getEntity().getUniqueId());
        witherKnightCooldowns.remove(event.getEntity().getUniqueId());
        witherKnightShots.remove(event.getEntity().getUniqueId());
        standardEnemyAi.remove(event.getEntity().getUniqueId());
        evokerAi.remove(event.getEntity().getUniqueId());
        witherBoss.remove(event.getEntity().getUniqueId());
        enderDragonBoss.remove(event.getEntity().getUniqueId());
        enderZealotAi.remove(event.getEntity().getUniqueId());
    }

    /** enemy_part.ender_zealot_eyeの60tick破壊演出とドラゴンへの遅延ダメージを処理する。 */
    private void animateZealotEyeDeath(ItemDisplay eye, LivingEntity source) {
        Location origin = eye.getLocation().clone();
        new org.bukkit.scheduler.BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                ticks++;
                if (!eye.isValid()) {
                    cancel();
                    return;
                }
                if (ticks == 1) {
                    eye.setGlowColorOverride(org.bukkit.Color.RED);
                    origin.getWorld().playSound(origin, Sound.BLOCK_GLASS_BREAK, 1.0F, 0.7F);
                    origin.getWorld().playSound(origin, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0F, 0.5F);
                    origin.getWorld().playSound(origin, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0F, 0.7F);
                }
                if (ticks >= 10) origin.getWorld().spawnParticle(Particle.REVERSE_PORTAL, origin.clone().add(0.0D, 0.25D, 0.0D), 5, 0.2D, 0.2D, 0.2D, 2.0D);
                if (ticks == 10) {
                    origin.getWorld().playSound(origin, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 3.0F, 0.8F);
                    origin.getWorld().playSound(origin, Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 3.0F, 1.0F);
                }
                if (ticks < 60) return;
                origin.getWorld().spawnParticle(Particle.EXPLOSION, origin, 7, 1.5D, 0.5D, 1.5D, 0.0D);
                origin.getWorld().getNearbyEntities(origin, 128.0D, 128.0D, 128.0D).stream()
                        .filter(entity -> entity instanceof EnderDragon)
                        .map(entity -> (EnderDragon) entity)
                        .min(java.util.Comparator.comparingDouble(dragon -> dragon.getLocation().distanceSquared(origin)))
                        .ifPresent(dragon -> dragon.damage(15.0D, source));
                eye.remove();
                cancel();
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSleep(PlayerBedEnterEvent event) {
        if (!isEnabled() || heatLevel() < 5) return;
        event.setUseBed(Event.Result.DENY);
        event.getPlayer().sendMessage("§c熱量5では眠って夜を飛ばせません。");
    }


    private void enableRuntime() {
        if (aiTask != null) aiTask.cancel();
        aiTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickEnemies, 1L, 1L);
    }

    private void disableRuntime() {
        if (aiTask != null) {
            aiTask.cancel();
            aiTask = null;
        }
        rangedBackSteps.clear();
        sheathedWeaponSystem.clear();
        terrainDigTicks.clear();
        terrainPlaceTicks.clear();
        terrainBridgeTicks.clear();
        terrainPlaceCooldowns.clear();
        creeperDigRecovery.clear();
        endermanBreakTicks.clear();
        witherKnightCooldowns.clear();
        witherKnightShots.clear();
        standardEnemyAi.clear();
        evokerAi.clear();
        witherBoss.clear();
        enderDragonBoss.clear();
        enderZealotAi.clear();
        Bukkit.getWorlds().forEach(world -> world.getLivingEntities().forEach(this::removeEnhancement));
    }

    private void tickEnemies() {
        if (!isEnabled()) return;
        for (World world : Bukkit.getWorlds()) {
            world.getEntitiesByClass(ItemDisplay.class).forEach(this::removeOrphanedSheath);
            for (LivingEntity living : world.getLivingEntities()) {
                initializeNearbyEnemy(living);
                if (!(living instanceof Mob mob) || !isEnemy(living)
                        || !living.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE)) continue;
                if (living instanceof Creeper creeper && creeperDigRecovery.remove(creeper.getUniqueId())) {
                    creeper.setInvulnerable(false);
                    AttributeInstance knockback = creeper.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
                    if (knockback != null) knockback.setBaseValue(0.0D);
                }
                enhance(living);
                standardEnemyAi.tickAmbient(living);
                refreshExistingSkeletonSheath(living);
                syncRangedSkeletonSheath(living);
                if (living instanceof EnderDragon dragon) {
                    enderDragonBoss.tick(dragon, dragonTarget(dragon));
                    continue;
                }
                if (living instanceof Slime zealot && zealot.getPersistentDataContainer().has(zealotKey, PersistentDataType.BYTE)) {
                    Player zealotTarget = chaserTarget(zealot);
                    if (zealotTarget != null) enderZealotAi.tick(zealot, zealotTarget);
                    continue;
                }
                if (living.getPersistentDataContainer().has(witherKnightKey, PersistentDataType.BYTE)) tickKnightCooldown(living.getUniqueId());
                if (living instanceof PigZombie pigZombie) forcePigZombieTarget(pigZombie);
                if (isChaser(living) && mob.getTarget() == null) {
                    Player chaserTarget = chaserTarget(living);
                    if (chaserTarget != null) mob.setTarget(chaserTarget);
                }
                LivingEntity target = mob.getTarget();
                if (living instanceof Enderman enderman) {
                    String endermanMode = enderman.getPersistentDataContainer().get(endermanModeKey, PersistentDataType.STRING);
                    boolean outerChaser = "outer_chaser".equals(endermanMode);
                    Player automaticTarget = outerChaser ? outerChaserTarget(enderman)
                            : "normal".equals(endermanMode) ? endermanTarget(enderman) : null;
                    if (automaticTarget != null) {
                        enderman.setTarget(automaticTarget);
                        target = automaticTarget;
                    }
                    if (!outerChaser && "normal".equals(enderman.getPersistentDataContainer().get(endermanModeKey, PersistentDataType.STRING)) && target instanceof Player player) {
                        breakEndermanBlocks(enderman, player);
                        continue;
                    }
                    if ("neutral".equals(endermanMode)) continue;
                }
                if (!(target instanceof Player player) || player.getGameMode().isInvulnerable()
                        || living.getWorld() != player.getWorld()
                        || living.getLocation().distanceSquared(player.getLocation()) > 4096.0D) {
                    if (living instanceof Wither wither) witherBoss.tickOutOfCombat(wither);
                    continue;
                }
                switchRangedWeapon(living, player);
                if (standardEnemyAi.tick(living, player)) {
                    if (isChaser(living)) alterTerrain(living, player);
                    continue;
                }
                if (living instanceof Evoker evoker) {
                    evokerAi.tick(evoker, player);
                    if (isChaser(living)) alterTerrain(living, player);
                    continue;
                }
                if (living instanceof Wither wither) {
                    witherBoss.tick(wither, player);
                    continue;
                }
                if (isChaser(living)) alterTerrain(living, player);
            }
        }
    }

    private Player dragonTarget(EnderDragon dragon) {
        return dragon.getWorld().getPlayers().stream()
                .filter(player -> !player.getGameMode().isInvulnerable())
                .filter(player -> player.getLocation().distanceSquared(dragon.getLocation()) <= 16384.0D)
                .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(dragon.getLocation())))
                .orElse(null);
    }

    private void tickKnightCooldown(UUID id) {
        int cooldown = witherKnightCooldowns.getOrDefault(id, 0);
        if (cooldown <= 1) {
            witherKnightCooldowns.remove(id);
            return;
        }
        witherKnightCooldowns.put(id, cooldown - 1);
    }

    private void forcePigZombieTarget(PigZombie pigZombie) {
        Player nearest = null;
        double nearestDistance = 48.0D * 48.0D;
        for (Player player : pigZombie.getWorld().getPlayers()) {
            if (player.getGameMode().isInvulnerable()) continue;
            double distance = pigZombie.getLocation().distanceSquared(player.getLocation());
            if (distance > nearestDistance) continue;
            nearest = player;
            nearestDistance = distance;
        }
        if (nearest == null) return;
        pigZombie.setAngry(true);
        pigZombie.setTarget(nearest);
    }

    private boolean isChaser(LivingEntity entity) {
        EntityType type = entity.getType();
        if (type == EntityType.ZOMBIFIED_PIGLIN) return heatLevel() >= 4;
        if (heatLevel() < 3) return false;
        return switch (type) {
            case ZOMBIE, HUSK, ZOMBIE_VILLAGER, CREEPER, WITCH, VINDICATOR, PILLAGER,
                    PIGLIN_BRUTE -> true;
            default -> false;
        };
    }

    private Player chaserTarget(LivingEntity entity) {
        Player closest = null;
        double closestDistance = 48.0D * 48.0D;
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getGameMode().isInvulnerable()) continue;
            double distance = entity.getLocation().distanceSquared(player.getLocation());
            if (distance > closestDistance) continue;
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    private void initializeNearbyEnemy(LivingEntity entity) {
        if (heatLevel() < 2 || !isEnemy(entity)
                || entity.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE)
                || entity instanceof Piglin piglin && !piglin.isAdult()
                || entity.getWorld().getPlayers().stream().noneMatch(player ->
                player.getLocation().distanceSquared(entity.getLocation()) <= 4096.0D)) return;
        enhance(entity);
        if (entity.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE)) {
            spawnSourceReinforcement(entity);
        }
    }

    /** mob_manager:init の熱量3以上の自然Mob増援抽選を再現する。 */
    private void spawnSourceReinforcement(LivingEntity entity) {
        int heat = heatLevel();
        if (heat < 3) return;
        EntityType reinforcement = null;
        double probability = 0.0D;
        switch (entity.getType()) {
            case SPIDER -> { reinforcement = EntityType.CAVE_SPIDER; probability = 1.0D / 25.0D; }
            case GUARDIAN -> { reinforcement = EntityType.DROWNED; probability = 1.0D / 25.0D; }
            case WITCH -> { reinforcement = EntityType.VINDICATOR; probability = 1.0D / 50.0D; }
            case EVOKER -> {
                if (heat >= 4) { reinforcement = EntityType.ILLUSIONER; probability = 0.1D; }
            }
            case PIGLIN -> {
                if (heat >= 4 && entity instanceof Piglin piglin && piglin.isAdult()) {
                    reinforcement = EntityType.PIGLIN_BRUTE;
                    probability = 0.1D;
                }
            }
            default -> { }
        }
        if (reinforcement != null && chance(probability)) entity.getWorld().spawnEntity(entity.getLocation(), reinforcement);
    }

    private void enhance(LivingEntity entity) {
        if (entity instanceof Slime slime && slime.getPersistentDataContainer().has(zealotKey, PersistentDataType.BYTE)) return;
        if (entity instanceof WitherSkeleton witherSkeleton
                && (witherSkeleton.getPersistentDataContainer().has(witherKnightKey, PersistentDataType.BYTE)
                || witherSkeleton.getPersistentDataContainer().has(witherMinionKey, PersistentDataType.BYTE))) return;
        if (!isEnemy(entity) || entity.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE)) return;
        int heat = heatLevel();
        assignVariant(entity);
        mobProfiles.apply(entity, variant(entity), heat);
        if (isChaser(entity)) mobProfiles.applyChaser(entity);
        if (heat >= 4 && !(entity instanceof EnderDragon)) {
            add(entity, Attribute.MAX_HEALTH, heat == 4 ? 0.25D : 0.5D);
            mobProfiles.applyHeat(entity, heat);
        }
        lootFactory.applySpawnEquipment(entity, heat);
        prepareRangedSkeleton(entity, heat);
        if (entity instanceof Wither wither) witherBoss.prepare(wither);
        if (entity instanceof EnderDragon dragon) enderDragonBoss.prepare(dragon);
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null) entity.setHealth(health.getValue());
        entity.getPersistentDataContainer().set(enhancedKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void assignVariant(LivingEntity entity) {
        String variant = "normal";
        if (heatLevel() >= 3 && entity.getType() == EntityType.ZOMBIE && chance(0.15D)) variant = "zombie_brute";
        else if (heatLevel() >= 3 && isRangedSkeleton(entity)
                && entity.getType() != EntityType.WITHER_SKELETON && chance(0.15D)) variant = "elite";
        entity.getPersistentDataContainer().set(variantKey, PersistentDataType.STRING, variant);
        if (variant.equals("elite")) {
            equipElite(entity, heatLevel());
        } else equipHelmet(entity);
    }

    private void removeEnhancement(LivingEntity entity) {
        if (!entity.getPersistentDataContainer().has(enhancedKey, PersistentDataType.BYTE)) return;
        sheathedWeaponSystem.restore(entity);
        entity.getPassengers().stream().filter(ItemDisplay.class::isInstance).forEach(Entity::remove);
        for (Map.Entry<Attribute, NamespacedKey> entry : modifierKeys.entrySet()) remove(entity, entry.getKey(), entry.getValue());
        mobProfiles.restore(entity);
        AttributeInstance health = entity.getAttribute(Attribute.MAX_HEALTH);
        if (health != null && entity.getHealth() > health.getValue()) entity.setHealth(health.getValue());
        entity.getPersistentDataContainer().remove(enhancedKey);
        entity.getPersistentDataContainer().remove(variantKey);
    }

    private void add(LivingEntity entity, Attribute attribute, double amount) {
        NamespacedKey key = modifierKeys.get(attribute);
        add(entity, attribute, key, amount);
    }

    private void add(LivingEntity entity, Attribute attribute, NamespacedKey key, double amount) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (key == null || instance == null || instance.getModifier(key) != null) return;
        instance.addModifier(new AttributeModifier(key, amount, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    private void remove(LivingEntity entity, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) return;
        AttributeModifier modifier = instance.getModifier(key);
        if (modifier != null) instance.removeModifier(modifier);
    }

    private void alterTerrain(LivingEntity enemy, Player target) {
        if (heatLevel() < 3 || enemy.getWorld() != target.getWorld()
                || enemy.getLocation().distanceSquared(target.getLocation()) > 2304.0D) return;
        Vector direction = target.getLocation().toVector().subtract(enemy.getLocation().toVector()).setY(0).normalize();
        Block ahead = enemy.getEyeLocation().add(direction).getBlock();
        Block below = enemy.getLocation().subtract(0.0D, 1.0D, 0.0D).getBlock();
        boolean targetBelow = Math.abs(target.getLocation().getX() - enemy.getLocation().getX()) <= 8.0D
                && Math.abs(target.getLocation().getZ() - enemy.getLocation().getZ()) <= 8.0D
                && target.getLocation().getY() <= enemy.getLocation().getY() - 2.0D
                && target.getLocation().getY() >= enemy.getLocation().getY() - 34.0D;
        boolean digDown = targetBelow && isDiggable(below.getType());
        if (digDown || !enemy.hasLineOfSight(target) && isDiggable(ahead.getType())) {
            // abstract.chaser_ai/search/tick の紫色の掘削探索パーティクル。
            enemy.getWorld().spawnParticle(Particle.DUST, enemy.getEyeLocation(), 1,
                    0.2D, 0.2D, 0.2D, 0.0D,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(128, 0, 128), 1.0F), true);
            int tick = terrainDigTicks.merge(enemy.getUniqueId(), 1, Integer::sum);
            boolean creeperBlast = enemy instanceof Creeper
                    && enemy.getLocation().distanceSquared(target.getLocation()) <= 256.0D
                    && Math.abs(enemy.getLocation().getY() - target.getLocation().getY()) <= 2.0D;
            if (tick >= 20) enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false));
            if (creeperBlast && tick == 20) enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_CREEPER_PRIMED, 1.0F, 1.0F);
            if (tick == 20 || tick == 25 || tick == 30 || tick == 35) {
                enemy.getWorld().playSound(enemy.getLocation(), Sound.BLOCK_STONE_BREAK, 1.0F, 0.7F);
                enemy.getWorld().playSound(enemy.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 1.0F, 0.5F);
            }
            if (tick < (creeperBlast ? 50 : 40)) return;
            if (creeperBlast && enemy instanceof Creeper creeper) {
                creeper.setInvulnerable(true);
                creeper.getWorld().createExplosion(creeper.getLocation(), creeper.isPowered() ? 6.0F : 3.0F, false, true, creeper);
                int count = creeper.getPersistentDataContainer().getOrDefault(creeperAttackCountKey, PersistentDataType.INTEGER, 0) + 1;
                creeper.getPersistentDataContainer().set(creeperAttackCountKey, PersistentDataType.INTEGER, count);
                if (count >= 3) creeper.remove();
                else creeperDigRecovery.add(creeper.getUniqueId());
                terrainDigTicks.remove(enemy.getUniqueId());
                return;
            }
            if (digDown) {
                breakDiggable(below);
            } else {
                breakDiggable(ahead);
                breakDiggable(enemy.getLocation().add(direction).getBlock());
                breakDiggable(enemy.getLocation().add(direction).add(0.0D, 1.0D, 0.0D).getBlock());
                breakDiggable(enemy.getEyeLocation().add(0.0D, 1.0D, 0.0D).getBlock());
            }
            terrainDigTicks.remove(enemy.getUniqueId());
            return;
        }
        terrainDigTicks.remove(enemy.getUniqueId());
        UUID id = enemy.getUniqueId();
        if (terrainBridgeTicks.containsKey(id)) {
            tickTerrainBridge(enemy, target, direction);
            return;
        }
        boolean standstill = enemy.isOnGround()
                && enemy.getVelocity().clone().setY(0.0D).lengthSquared() <= 0.01D;
        if (!standstill) {
            terrainPlaceTicks.computeIfPresent(id, (key, value) -> value <= 1 ? null : value - 1);
            return;
        }
        if (terrainPlaceTicks.merge(id, 1, Integer::sum) < 20) return;
        int verticalDifference = enemy.getLocation().getBlockY() - target.getLocation().getBlockY();
        if (verticalDifference < 0) {
            int cooldown = terrainPlaceCooldowns.getOrDefault(id, 0);
            Block current = enemy.getLocation().getBlock();
            Block upperForward = enemy.getEyeLocation().add(direction).add(0.0D, 1.0D, 0.0D).getBlock();
            if (cooldown <= 0 && canPlaceEnemyBlock(current) && isCollisionSafe(upperForward)) {
                enemy.teleport(enemy.getLocation().add(0.0D, 1.0D, 0.0D));
                terrainPlaceCooldowns.put(id, 5);
            }
        }
        Block placementBelow = enemy.getLocation().subtract(0.0D, 1.0D, 0.0D).getBlock();
        if (terrainPlaceCooldowns.containsKey(id) && canPlaceEnemyBlock(placementBelow)) {
            temporaryBlocks.place(placementBelow);
        }
        terrainPlaceCooldowns.computeIfPresent(id, (key, value) -> value <= 1 ? null : value - 1);
        if (verticalDifference == 0 && enemy.isOnGround()) {
            terrainPlaceTicks.remove(id);
            terrainBridgeTicks.put(id, 0);
        }
    }

    private void tickTerrainBridge(LivingEntity enemy, Player target, Vector direction) {
        if (!enemy.isOnGround()) return;
        UUID id = enemy.getUniqueId();
        enemy.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 0, false, false));
        Block below = enemy.getLocation().subtract(0.0D, 1.0D, 0.0D).getBlock();
        Block lowerBelow = enemy.getLocation().subtract(0.0D, 2.0D, 0.0D).getBlock();
        Block forward = enemy.getLocation().add(direction).subtract(0.0D, 1.0D, 0.0D).getBlock();
        Block fartherForward = enemy.getLocation().add(direction.clone().multiply(2.0D)).subtract(0.0D, 1.0D, 0.0D).getBlock();
        if (canPlaceEnemyBlock(lowerBelow) && canPlaceEnemyBlock(forward)) temporaryBlocks.place(forward);
        if (canPlaceEnemyBlock(lowerBelow) && canPlaceEnemyBlock(fartherForward)) temporaryBlocks.place(fartherForward);
        int leftBridgeTicks = temporaryBlocks.isTemporaryNear(below.getLocation().add(0.5D, 0.5D, 0.5D), 1.0D)
                ? 0 : terrainBridgeTicks.getOrDefault(id, 0) + 1;
        int verticalDifference = enemy.getLocation().getBlockY() - target.getLocation().getBlockY();
        if (leftBridgeTicks >= 30 || verticalDifference >= 2) {
            if (verticalDifference >= 2 && temporaryBlocks.isTemporary(below)) below.breakNaturally();
            terrainBridgeTicks.remove(id);
            return;
        }
        terrainBridgeTicks.put(id, leftBridgeTicks);
    }

    private boolean canPlaceEnemyBlock(Block block) {
        Material material = block.getType();
        String name = material.name();
        return material.isAir()
                || material == Material.WATER
                || material == Material.LAVA
                || material == Material.SHORT_GRASS
                || material == Material.TALL_GRASS
                || material == Material.FERN
                || material == Material.LARGE_FERN
                || material == Material.CRIMSON_ROOTS
                || material == Material.WARPED_ROOTS
                || material == Material.NETHER_SPROUTS
                || material == Material.DEAD_BUSH
                || material == Material.BROWN_MUSHROOM
                || material == Material.RED_MUSHROOM
                || material == Material.VINE
                || material == Material.STONE_PRESSURE_PLATE
                || material == Material.SEA_PICKLE
                || material == Material.KELP
                || material == Material.KELP_PLANT
                || material == Material.SEAGRASS
                || material == Material.TALL_SEAGRASS
                || material == Material.POTATOES
                || material == Material.WHEAT
                || material == Material.MELON_STEM
                || material == Material.ATTACHED_MELON_STEM
                || material == Material.PUMPKIN_STEM
                || material == Material.ATTACHED_PUMPKIN_STEM
                || material == Material.CARROTS
                || material == Material.BEETROOTS
                || name.endsWith("_SAPLING")
                || name.endsWith("_CORAL")
                || name.endsWith("_CORAL_FAN")
                || name.endsWith("_FLOWER")
                || material == Material.SUNFLOWER
                || material == Material.LILAC
                || material == Material.ROSE_BUSH
                || material == Material.PEONY;
    }

    private boolean isCollisionSafe(Block block) {
        Material material = block.getType();
        String name = material.name();
        return canPlaceEnemyBlock(block)
                || material == Material.TRIPWIRE_HOOK
                || material == Material.TRIPWIRE
                || material == Material.TORCH
                || material == Material.WALL_TORCH
                || material == Material.REDSTONE_TORCH
                || material == Material.REDSTONE_WALL_TORCH
                || material == Material.LIGHT_WEIGHTED_PRESSURE_PLATE
                || material == Material.HEAVY_WEIGHTED_PRESSURE_PLATE
                || material == Material.LEVER
                || material == Material.LADDER
                || material == Material.SNOW
                || material == Material.COBWEB
                || material == Material.SUGAR_CANE
                || material == Material.MOSS_CARPET
                || name.endsWith("_BUTTON")
                || name.endsWith("_CARPET")
                || name.endsWith("_RAIL")
                || name.endsWith("_PRESSURE_PLATE")
                || name.endsWith("_BANNER")
                || name.endsWith("_SIGN");
    }

    private void breakDiggable(Block block) {
        if (!isDiggable(block.getType())) return;
        block.getWorld().spawnParticle(Particle.BLOCK, block.getLocation().add(0.5, 0.5, 0.5), 18, 0.35, 0.35, 0.35, block.getBlockData());
        block.breakNaturally();
    }

    private boolean isDiggable(Material material) {
        // 本家 #lib:no_dig は「掘れるブロック一覧」ではなく、保護対象の除外タグ。
        // そのため石材・木材・鉱石なども含め、固体ブロックを対象にする。
        if (!material.isSolid() || material.isAir()) return false;
        return !Set.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.NETHER_PORTAL,
                Material.END_PORTAL_FRAME, Material.ENCHANTING_TABLE, Material.BARRIER,
                Material.BEDROCK, Material.END_GATEWAY, Material.COMMAND_BLOCK,
                Material.REPEATING_COMMAND_BLOCK, Material.CHAIN_COMMAND_BLOCK,
                Material.STRUCTURE_BLOCK, Material.JIGSAW, Material.MOVING_PISTON).contains(material);
    }


    private Player outerChaserTarget(Enderman enderman) {
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player player : enderman.getWorld().getPlayers()) {
            if (player.getGameMode().isInvulnerable()) continue;
            boolean pumpkin = player.getInventory().getHelmet() != null && player.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN;
            double noticeRange = pumpkin ? 6.0D : 24.0D;
            double angerRange = pumpkin ? 4.0D : 16.0D;
            double distance = enderman.getLocation().distance(player.getLocation());
            if (distance > noticeRange || distance > angerRange || distance >= closestDistance) continue;
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    private Player endermanTarget(Enderman enderman) {
        Player closest = null;
        double closestDistance = Double.MAX_VALUE;
        for (Player player : enderman.getWorld().getPlayers()) {
            if (player.getGameMode().isInvulnerable()) continue;
            boolean pumpkin = player.getInventory().getHelmet() != null && player.getInventory().getHelmet().getType() == Material.CARVED_PUMPKIN;
            double angerRange = pumpkin ? 4.0D : 16.0D;
            double distance = enderman.getLocation().distance(player.getLocation());
            if (distance > angerRange || distance >= closestDistance) continue;
            closest = player;
            closestDistance = distance;
        }
        return closest;
    }

    private void breakEndermanBlocks(Enderman enderman, Player target) {
        UUID id = enderman.getUniqueId();
        if (enderman.getWorld() != target.getWorld()) {
            endermanBreakTicks.remove(id);
            return;
        }
        if (enderman.getLocation().distance(target.getLocation()) > 5.0D) return;
        if (endermanBreakTicks.merge(id, 1, Integer::sum) < 30) return;
        endermanBreakTicks.remove(id);
        Vector forward = target.getLocation().toVector().subtract(enderman.getLocation().toVector()).setY(0);
        if (forward.lengthSquared() == 0.0D) return;
        forward.normalize();
        Vector sideways = new Vector(-forward.getZ(), 0.0D, forward.getX()).multiply(0.5D);
        Location base = enderman.getLocation().add(forward);
        for (int height = 0; height < 3; height++) {
            for (int side = -1; side <= 1; side++) breakDiggable(base.clone().add(sideways.clone().multiply(side)).add(0.0D, height, 0.0D).getBlock());
        }
    }

    private void prepareRangedSkeleton(LivingEntity entity, int heat) {
        if (!isRangedSkeleton(entity) || entity.getEquipment() == null) return;
        EntityEquipment equipment = entity.getEquipment();
        entity.setCanPickupItems(false);
        String ownerId = entity.getUniqueId().toString();
        entity.getWorld().getEntitiesByClass(ItemDisplay.class).stream()
                .filter(display -> ownerId.equals(display.getPersistentDataContainer().get(sheathOwnerKey, PersistentDataType.STRING)))
                .filter(display -> !entity.getPassengers().contains(display))
                .forEach(Entity::remove);
        if (isRangedSkeleton(entity)) {
            entity.getWorld().getNearbyEntities(entity.getLocation(), 2.0D, 2.0D, 2.0D).stream()
                    .filter(ItemDisplay.class::isInstance)
                    .map(ItemDisplay.class::cast)
                    .filter(display -> display.getPersistentDataContainer().get(sheathOwnerKey, PersistentDataType.STRING) == null)
                    .filter(display -> display.getItemStack().getType() == Material.STONE_SWORD
                            || display.getItemStack().getType() == Material.STONE_AXE
                            || display.getItemStack().getType() == Material.NETHERITE_SWORD)
                    .forEach(Entity::remove);
        }
        boolean witherSheath = entity.getType() == EntityType.WITHER_SKELETON;
        float sheathScale = witherSheath ? WITHER_SKELETON_SHEATH_SCALE : SKELETON_SHEATH_SCALE;
        entity.getPassengers().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).forEach(sheath -> {
            sheath.setRotation(witherSheath ? entity.getYaw() : entity.getBodyYaw(), 0.0F);
            sheath.getPersistentDataContainer().set(sheathOwnerKey, PersistentDataType.STRING, ownerId);
            sheath.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0.0F, -1.1F, -0.3F), new Quaternionf(),
                    new Vector3f(sheathScale, sheathScale, sheathScale), new Quaternionf(0.0F, 0.0F, witherSheath ? 1.0F : -2.4F, 1.0F)));
        });
        if (equipment.getItemInMainHand().getType().isAir()) equipment.setItemInMainHand(new ItemStack(Material.BOW));
        ItemStack stowedWeapon = equipment.getItemInOffHand().getType().isAir()
                ? witherSheath ? new ItemStack(Material.STONE_SWORD) : lootFactory.eliteHatchet(false, heat)
                : equipment.getItemInOffHand().clone();
        if (entity.getPassengers().stream().noneMatch(passenger -> passenger instanceof ItemDisplay)) {
            ItemDisplay sheath = entity.getWorld().spawn(entity.getLocation(), ItemDisplay.class);
            sheath.setRotation(witherSheath ? entity.getYaw() : entity.getBodyYaw(), 0.0F);
            sheath.setItemStack(witherSheath ? new ItemStack(Material.STONE_SWORD) : lootFactory.eliteHatchet(false, heat));
            sheath.getPersistentDataContainer().set(sheathOwnerKey, PersistentDataType.STRING, ownerId);
            sheath.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            sheath.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0.0F, -1.1F, -0.3F), new Quaternionf(),
                    new Vector3f(sheathScale, sheathScale, sheathScale),
                    new Quaternionf(0.0F, 0.0F, witherSheath ? 1.0F : -2.4F, 1.0F)));
            entity.addPassenger(sheath);
        }
        sheathedWeaponSystem.initialize(entity, stowedWeapon);
        entity.getPersistentDataContainer().set(sheathRenderVersionKey, PersistentDataType.BYTE, (byte) 1);
        if (entity.getType() == EntityType.PARCHED || entity.getType() == EntityType.WITHER_SKELETON || equipment.getHelmet() != null) return;
        int color = entity.getType() == EntityType.STRAY ? 6387319 : entity.getType() == EntityType.BOGGED ? 3887645 : 11250603;
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(color));
        helmet.setItemMeta(meta);
        equipment.setHelmet(helmet);
    }

    private void refreshExistingSkeletonSheath(LivingEntity entity) {
        if (!isRangedSkeleton(entity)
                || entity.getPersistentDataContainer().has(sheathRenderVersionKey, PersistentDataType.BYTE)) return;
        boolean witherSheath = entity.getType() == EntityType.WITHER_SKELETON;
        float sheathScale = witherSheath ? WITHER_SKELETON_SHEATH_SCALE : SKELETON_SHEATH_SCALE;
        entity.getPassengers().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast).forEach(sheath -> {
            sheath.setRotation(witherSheath ? entity.getYaw() : entity.getBodyYaw(), 0.0F);
            sheath.getPersistentDataContainer().set(sheathOwnerKey, PersistentDataType.STRING, entity.getUniqueId().toString());
            sheath.setTransformation(new org.bukkit.util.Transformation(
                    new Vector3f(0.0F, -1.1F, -0.3F), new Quaternionf(),
                    new Vector3f(sheathScale, sheathScale, sheathScale),
                    new Quaternionf(0.0F, 0.0F, witherSheath ? 1.0F : -2.4F, 1.0F)));
        });
        entity.getPersistentDataContainer().set(sheathRenderVersionKey, PersistentDataType.BYTE, (byte) 1);
        sheathedWeaponSystem.refreshDisplay(entity);
    }

    private void syncRangedSkeletonSheath(LivingEntity entity) {
        if (!isRangedSkeleton(entity)) return;
        float bodyYaw = entity.getType() == EntityType.WITHER_SKELETON ? entity.getYaw() : entity.getBodyYaw();
        entity.getPassengers().stream().filter(ItemDisplay.class::isInstance).map(ItemDisplay.class::cast)
                .filter(display -> entity.getUniqueId().toString().equals(display.getPersistentDataContainer()
                        .get(sheathOwnerKey, PersistentDataType.STRING)))
                .filter(display -> Math.abs(display.getYaw() - bodyYaw) > 0.1F)
                .forEach(display -> display.setRotation(bodyYaw, 0.0F));
    }

    private void removeOrphanedSheath(ItemDisplay display) {
        String ownerId = display.getPersistentDataContainer().get(sheathOwnerKey, PersistentDataType.STRING);
        if (ownerId == null) return;
        Entity vehicle = display.getVehicle();
        if (!(vehicle instanceof LivingEntity owner)
                || !owner.isValid()
                || owner.isDead()
                || !owner.getUniqueId().toString().equals(ownerId)
                || !owner.getPassengers().contains(display)) {
            display.remove();
        }
    }

    private boolean isRangedSkeleton(LivingEntity entity) {
        return switch (entity.getType()) {
            case SKELETON, STRAY, BOGGED, PARCHED, WITHER_SKELETON -> true;
            default -> false;
        };
    }

    private void equipElite(LivingEntity entity, int heat) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null) return;
        String eliteName = switch (entity.getType()) {
            case STRAY -> "エリートストレイ";
            case BOGGED -> "エリートボグド";
            default -> "エリートスケルトン";
        };
        entity.customName(net.kyori.adventure.text.Component.text(eliteName));
        entity.setCustomNameVisible(false);
        int color = entity.getType() == EntityType.STRAY ? 6387319 : entity.getType() == EntityType.BOGGED ? 5468202 : 13619154;
        ItemStack chest = new ItemStack(Material.LEATHER_CHESTPLATE);
        LeatherArmorMeta chestMeta = (LeatherArmorMeta) chest.getItemMeta();
        chestMeta.setColor(org.bukkit.Color.fromRGB(color));
        chest.setItemMeta(chestMeta);
        ArmorMeta armorMeta = (ArmorMeta) chest.getItemMeta();
        TrimMaterial trimMaterial = entity.getType() == EntityType.STRAY ? TrimMaterial.DIAMOND
                : entity.getType() == EntityType.BOGGED ? TrimMaterial.REDSTONE : TrimMaterial.IRON;
        armorMeta.setTrim(new ArmorTrim(trimMaterial, TrimPattern.SHAPER));
        chest.setItemMeta(armorMeta);
        equipment.setChestplate(chest);
        equipment.setLeggings(new ItemStack(Material.IRON_LEGGINGS));
        equipment.setBoots(new ItemStack(Material.IRON_BOOTS));
        equipment.setHelmet(eliteSkeletonHead(entity));
        equipment.setItemInMainHand(new ItemStack(Material.BOW));
        equipment.setItemInOffHand(lootFactory.eliteHatchet(true, heat));
        equipment.setHelmetDropChance(0.0F);
        equipment.setChestplateDropChance(0.0F);
        equipment.setLeggingsDropChance(0.0F);
        equipment.setBootsDropChance(0.0F);
        equipment.setItemInMainHandDropChance(0.085F);
    }

    private ItemStack eliteSkeletonHead(LivingEntity entity) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        String texture = switch (entity.getType()) {
            case STRAY -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMmMyMDIwYzk3NDQ2YWM4ZWZlNTJiYjg5MDhhYmNiNjc2NzFiMWMzMmNlOGEwY2RjZTdlZTFiMzAyNTIzMDAzMiJ9fX0=";
            case BOGGED -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTNkNWEyMGMzMDYxZGUwZDlkODQzMWM5YjkyZDlkYzMzNjc4Mzk2MzRhOTUxMzAzYWVmMWU1ZDU5NzNjNGJhMSJ9fX0=";
            default -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMjk0Yjc4ODc4NzE1ZGZlZmJlZjBkOWFhMzI3NTlmNThhYzQ3OWIzYzgwYjIzODFmYzBkNDdhNDU3ODdiYjQyNyJ9fX0=";
        };
        UUID profileId = switch (entity.getType()) {
            case STRAY -> UUID.fromString("2e065ddf-25ad-4e27-8ec8-164db5808390");
            case BOGGED -> UUID.fromString("ec93246e-665a-40cf-be80-9e0a45be6826");
            default -> UUID.fromString("21cfb5b6-f4d2-4a21-89f7-258f27a3ca8b");
        };
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.getServer().createProfile(profileId);
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", texture));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private void switchRangedWeapon(LivingEntity entity, Player target) {
        boolean elite = variant(entity).equals("elite");
        boolean skeleton = isRangedSkeleton(entity);
        if ((!elite && !skeleton) || entity.getEquipment() == null) return;
        double distance = entity.getLocation().distance(target.getLocation());
        sheathedWeaponSystem.tick(entity, target);
        if (!skeleton || entity.getEquipment().getItemInMainHand().getType() == Material.BOW || distance > 4.0D) return;
        if (distance <= 2.0D && target.getNoDamageTicks() > 0 && entity.isOnGround()) rangedBackSteps.put(entity.getUniqueId(), 40);
        int ticks = rangedBackSteps.merge(entity.getUniqueId(), 1, Integer::sum);
        if (ticks < 40 || !entity.isOnGround()) return;
        Vector away = entity.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0.0D);
        if (away.lengthSquared() == 0.0D) return;
        away.normalize();
        Location backward = entity.getLocation().clone().add(away);
        Location landingBelow = entity.getLocation().clone().add(away.clone().multiply(3.0D)).subtract(0.0D, 1.0D, 0.0D);
        if (!backward.getBlock().isPassable() || landingBelow.getBlock().isPassable()) return;
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20, 10, false, false));
        entity.setVelocity(away.multiply(0.7D).setY(0.4D));
        if (entity.getType() == EntityType.PARCHED) entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_PARCHED_AMBIENT, 1.0F, 2.0F);
        if (entity.getType() == EntityType.STRAY) entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_STRAY_DEATH, 1.0F, 2.0F);
        if (entity.getType() == EntityType.BOGGED) entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_BOGGED_DEATH, 1.0F, 2.0F);
        entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_GOAT_LONG_JUMP, 1.0F, 1.2F);
        rangedBackSteps.remove(entity.getUniqueId());
    }

    private void equipHelmet(LivingEntity entity) {
        EntityEquipment equipment = entity.getEquipment();
        if (equipment == null || equipment.getHelmet() != null) return;
        if (entity.getType() != EntityType.ZOMBIE) return;
        ItemStack helmet = new ItemStack(Material.LEATHER_HELMET);
        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        meta.setColor(org.bukkit.Color.fromRGB(43176));
        helmet.setItemMeta(meta);
        equipment.setHelmet(helmet);
    }

    private LivingEntity attacker(Entity damager) {
        if (damager instanceof LivingEntity living) return living;
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living) return living;
        return null;
    }

    private boolean isEnemy(LivingEntity entity) {
        return entity instanceof Monster || entity instanceof Wither || entity instanceof EnderDragon;
    }

    private String variant(LivingEntity entity) {
        return entity.getPersistentDataContainer().getOrDefault(variantKey, PersistentDataType.STRING, "normal");
    }

    private boolean chance(double probability) {
        return ThreadLocalRandom.current().nextDouble() < probability;
    }

}
