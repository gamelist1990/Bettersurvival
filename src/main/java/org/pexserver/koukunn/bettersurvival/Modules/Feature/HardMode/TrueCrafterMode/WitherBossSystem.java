package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.TrueCrafterMode;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Wither;
import org.bukkit.entity.WitherSkull;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.LargeFireball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.ArmorMeta;
import org.bukkit.inventory.meta.trim.ArmorTrim;
import org.bukkit.inventory.meta.trim.TrimMaterial;
import org.bukkit.inventory.meta.trim.TrimPattern;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.Set;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.inventory.meta.SkullMeta;

/** enemy.witherの600HP、75%・50%移行、および召喚フェーズを管理する。 */
public final class WitherBossSystem {
    private final Loader plugin;
    private final Map<UUID, State> states = new HashMap<>();
    private final Map<UUID, Integer> outOfCombatTicks = new HashMap<>();
    private final org.bukkit.NamespacedKey convertedThunderSkullKey;
    private final WitherFieldSystem fields;

    public WitherBossSystem(Loader plugin) {
        this.plugin = plugin;
        convertedThunderSkullKey = new org.bukkit.NamespacedKey(plugin, "truecrafter_wither_thunder_converted");
        fields = new WitherFieldSystem(plugin);
    }

    public boolean tick(Wither wither, Player target) {
        outOfCombatTicks.remove(wither.getUniqueId());
        if (wither.getLocation().distanceSquared(target.getLocation()) <= 25.0D) {
            convertNearbyWitherSkulls(wither, target);
        }
        tickKnights(wither, target);
        State state = states.computeIfAbsent(wither.getUniqueId(), ignored -> initialize(wither));
        if (state.transition) return transition(wither, target, state);
        double healthPercent = wither.getHealth() / wither.getAttribute(Attribute.MAX_HEALTH).getValue() * 100.0D;
        if (state.phase == 1 && healthPercent <= 75.0D) {
            state.phase = 2;
            state.tick = -20;
            wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_WITHER_HURT, 1.5F, 0.7F);
        }
        if (state.phase == 2 && healthPercent <= 50.0D) {
            state.phase = 3;
            state.transition = true;
            state.tick = 0;
            wither.setAI(false);
            wither.setInvulnerable(true);
            wither.setSilent(true);
            wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_WITHER_HURT, 1.5F, 0.5F);
            return true;
        }
        if (state.skill == Skill.NONE) {
            baseMove(wither, target);
            if (state.dashTicks < 0 && state.phase >= 2 && state.tick + 1 == 0) selectDash(state);
            if (state.dashTicks >= 0) tickDash(wither, target, state);
        }
        state.tick++;
        if (state.skill == Skill.NONE && state.dashTicks < 0 && state.tick >= 60) selectSkill(wither, state);
        if (state.skill != Skill.NONE) skill(wither, target, state);
        return true;
    }

    public void prepare(Wither wither) {
        states.computeIfAbsent(wither.getUniqueId(), ignored -> initialize(wither));
    }

    public void remove(UUID id) {
        states.remove(id);
        outOfCombatTicks.remove(id);
    }

    public void clear() {
        states.clear();
        outOfCombatTicks.clear();
    }
    public void shutdown() { fields.shutdown(); }

    /** 本家のOutOfCombatが300tickに達した時の消滅と専用ドロップを再現する。 */
    public void tickOutOfCombat(Wither wither) {
        State state = states.computeIfAbsent(wither.getUniqueId(), ignored -> initialize(wither));
        if (state.transition) {
            transition(wither, null, state);
            return;
        }
        int ticks = outOfCombatTicks.merge(wither.getUniqueId(), 1, Integer::sum);
        if (ticks >= 100 && !wither.getEyeLocation().getBlock().isPassable()) {
            Player nearest = wither.getWorld().getPlayers().stream()
                    .filter(player -> !player.getGameMode().isInvulnerable())
                    .filter(player -> player.getLocation().distanceSquared(wither.getLocation()) <= 4096.0D)
                    .min(java.util.Comparator.comparingDouble(player -> player.getLocation().distanceSquared(wither.getLocation())))
                    .orElse(null);
            if (nearest != null) {
                org.bukkit.util.Vector direction = nearest.getLocation().toVector().subtract(wither.getLocation().toVector()).normalize();
                wither.setVelocity(direction.multiply(0.1D));
            }
        }
        if (ticks < 300) return;
        ItemStack skull = new ItemStack(org.bukkit.Material.WITHER_SKELETON_SKULL, 3);
        ItemMeta meta = skull.getItemMeta();
        meta.lore(java.util.List.of(net.kyori.adventure.text.Component.text("ウィザーは消えてしまったようだ…")));
        skull.setItemMeta(meta);
        wither.getWorld().dropItemNaturally(wither.getLocation(), skull);
        wither.getWorld().spawnParticle(Particle.LARGE_SMOKE, wither.getLocation().add(0, 2, 0), 35, 0.7D, 1D, 0.7D, 0D);
        wither.getWorld().spawnParticle(Particle.DUST, wither.getLocation().add(0, 2, 0), 35, 0.7D, 1D, 0.7D, 0D,
                new Particle.DustOptions(org.bukkit.Color.BLACK, 1.0F), true);
        wither.remove();
        remove(wither.getUniqueId());
    }

    private State initialize(Wither wither) {
        wither.getAttribute(Attribute.MAX_HEALTH).setBaseValue(600.0D);
        wither.setHealth(600.0D);
        wither.getEquipment().setItemInMainHand(new org.bukkit.inventory.ItemStack(org.bukkit.Material.NETHERITE_AXE));
        return new State();
    }

    private boolean transition(Wither wither, Player target, State state) {
        state.tick++;
        Location origin = wither.getLocation();
        if (state.tick <= 60) {
            wither.getWorld().spawnParticle(Particle.END_ROD, origin.clone().add(0, 2, 0), 1, 0.7, 1, 0.7, 0);
            wither.getWorld().spawnParticle(Particle.LARGE_SMOKE, origin.clone().add(0, 2, 0), 1, 0.7, 1, 0.7, 0);
            wither.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, origin.clone().add(0, 2, 0), 5, 0.7, 1, 0.7, 0);
        }
        if (state.tick == 70 || state.tick == 80 || state.tick == 90) spawnKnight(wither, target);
        long knights = wither.getNearbyEntities(48, 48, 48).stream().filter(entity -> entity instanceof WitherSkeleton skeleton
                && skeleton.getPersistentDataContainer().has(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_knight"), org.bukkit.persistence.PersistentDataType.BYTE)).count();
        if (state.tick >= 90 && state.tick < 900 && knights == 0) {
            state.tick = 900;
            wither.getWorld().playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0F, 1.5F);
            wither.getWorld().playSound(origin, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 2.0F, 1.6F);
            wither.getWorld().playSound(origin, Sound.ENTITY_WITHER_HURT, 2.0F, 0.8F);
        }
        if (state.tick < 960) return true;
        return completeTransition(wither, state, origin);
    }

    private boolean completeTransition(Wither wither, State state, Location origin) {
        state.transition = false;
        state.tick = -20;
        state.skill = Skill.NONE;
        state.used = 0;
        wither.setAI(true);
        wither.setInvulnerable(false);
        wither.setSilent(false);
        wither.getWorld().playSound(origin, Sound.ENTITY_WITHER_SHOOT, 1.5F, 0.5F);
        return true;
    }

    /** 本家 enemy.wither/tick/dash: phase 2以降、通常行動のtick 0で1/3ずつ未実行・左・右を選ぶ。 */
    private void selectDash(State state) {
        int selected = ThreadLocalRandom.current().nextInt(3);
        if (selected == 0) return;
        state.dashLeft = selected == 1;
        state.dashTicks = 0;
    }

    private void tickDash(Wither wither, Player target, State state) {
        if (state.dashTicks > 25) {
            state.dashTicks = -1;
            return;
        }
        double speed = 0.0D;
        if (state.dashTicks <= 15) speed += 0.5D;
        if (state.dashTicks >= 15 && state.dashTicks <= 20) speed += 0.5D;
        if (state.dashTicks >= 20 && state.dashTicks <= 25) speed += 0.3D;
        org.bukkit.util.Vector toward = target.getLocation().toVector().subtract(wither.getLocation().toVector()).setY(0.0D);
        if (toward.lengthSquared() == 0.0D) return;
        toward.normalize();
        org.bukkit.util.Vector side = new org.bukkit.util.Vector(-toward.getZ(), 0.0D, toward.getX());
        if (!state.dashLeft) side.multiply(-1.0D);
        Location destination = wither.getLocation().clone().add(side.multiply(speed));
        if (destination.getBlock().isPassable() && destination.clone().add(0.0D, 2.0D, 0.0D).getBlock().isPassable()) {
            wither.teleport(destination);
        } else {
            state.dashTicks = 25;
            return;
        }
        if (state.dashTicks <= 15 && state.dashTicks % 2 == 0) wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 2.0F, 1.2F);
        state.dashTicks++;
    }

    private void selectSkill(Wither wither, State state) {
        state.tick = -20;
        if (state.used == 7) {
            state.skill = Skill.SUMMON;
            state.used = 0;
        } else {
            int selected;
            do selected = ThreadLocalRandom.current().nextInt(3); while ((state.used & (1 << selected)) != 0);
            state.used |= 1 << selected;
            state.skill = Skill.values()[selected + 1];
        }
        wither.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION_OMINOUS, wither.getLocation().add(0, 2, 0), 35, 0.7, 1, 0.7, 0);
        wither.getWorld().playSound(wither.getLocation(), Sound.BLOCK_TRIAL_SPAWNER_ABOUT_TO_SPAWN_ITEM, 3.0F, 2.0F);
    }

    private void skill(Wither wither, Player target, State state) {
        state.tick++;
        switch (state.skill) {
            case HOMING -> {
                if (state.tick >= 0 && state.tick < 5) moveRelative(wither, target, -1.0D);
                else if (state.tick < 10) moveRelative(wither, target, -0.5D);
                else if (state.tick < 15) moveRelative(wither, target, -0.3D);
                else if (state.tick < 20) moveRelative(wither, target, -0.1D);
                int interval = state.phase >= 3 ? 3 : 6;
                if (state.tick >= 0 && state.tick <= 24 && state.tick % interval == 0) homingShot(wither, target);
                if (state.tick >= 60) reset(state);
            }
            case CHARGE -> {
                wither.getWorld().spawnParticle(Particle.DUST_COLOR_TRANSITION, wither.getLocation().add(0, 2, 0),
                        20, 0.7D, 1.0D, 0.7D, 1.0D,
                        new Particle.DustTransition(org.bukkit.Color.fromRGB(51, 51, 51),
                                org.bukkit.Color.fromRGB(224, 247, 147), 1.5F));
                if (state.tick < 5) moveRelative(wither, target, -0.5D);
                else if (state.tick < 10) moveRelative(wither, target, -0.3D);
                else if (state.tick < 15) moveRelative(wither, target, -0.1D);
                else if (state.tick <= 30) moveRelative(wither, target, 1.0D);
                else if (state.tick <= 40) moveRelative(wither, target, 0.5D);
                else if (state.tick <= 45) moveRelative(wither, target, 0.3D);
                else if (state.tick <= 50) moveRelative(wither, target, 0.1D);
                if (state.tick >= 15 && state.tick <= 50 && state.tick % 3 == 0) wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_BREEZE_SLIDE, 1.0F, 0.5F);
                if (state.tick >= 15 && state.tick <= 50) {
                    // 本家はウィザー前方の1×1×1判定箱にいる全プレイヤーへ攻撃する。
                    org.bukkit.util.Vector direction = wither.getLocation().getDirection().setY(0.0D).normalize();
                    for (Player nearby : wither.getWorld().getPlayers()) {
                        org.bukkit.util.Vector offset = nearby.getLocation().toVector().subtract(wither.getLocation().toVector());
                        double forward = offset.getX() * direction.getX() + offset.getZ() * direction.getZ();
                        double sideways = Math.abs(offset.getX() * direction.getZ() - offset.getZ() * direction.getX());
                        if (nearby.isInvulnerable() || forward < 0.0D || forward > 2.0D || sideways > 1.0D
                                || offset.getY() < -1.0D || offset.getY() > 1.0D) continue;
                        nearby.damage(18.0D, wither);
                    }
                }
                if (state.tick >= 60) reset(state);
            }
            case THUNDER -> {
                int interval = state.phase >= 3 ? 10 : 20;
                if (state.tick >= 0 && state.tick <= 100 && state.tick % interval == 0) {
                    java.util.List<Player> candidates = wither.getWorld().getPlayers().stream()
                            .filter(player -> !player.isInvulnerable())
                            .filter(player -> player.getLocation().distanceSquared(wither.getLocation()) <= 2304.0D)
                            .toList();
                    Player selected = candidates.isEmpty() ? target : candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
                    if (selected == null || selected.isInvulnerable()) { reset(state); return; }
                    Location strike = selected.getLocation().clone().add(
                            ThreadLocalRandom.current().nextDouble(-24.0D, 24.0D),
                            ThreadLocalRandom.current().nextDouble(2.0D, 10.0D),
                            ThreadLocalRandom.current().nextDouble(-24.0D, 24.0D));
                    fields.trap(wither, strike, selected);
                    wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 2.0F, 2.0F);
                    wither.getWorld().playSound(wither.getLocation(), Sound.ENTITY_BREEZE_IDLE_GROUND, 1.0F, 2.0F);
                }
                if (state.tick >= 140) reset(state);
            }
            case SUMMON -> {
                if (state.tick == 0) explodeMinions(wither);
                if (state.tick == 20 || state.tick == 30 || state.tick == 40) spawnMinion(wither, target);
                if (state.tick >= 200) reset(state);
            }
            default -> { }
        }
    }

    private void reset(State state) {
        state.skill = Skill.NONE;
        state.tick = -20;
    }

    private void baseMove(Wither wither, Player target) {
        double distance = wither.getLocation().distance(target.getLocation());
        if (distance > 8.0D) moveRelative(wither, target, 0.35D);
        else {
            moveRelative(wither, target, -0.05D);
            wither.setVelocity(wither.getVelocity().setY(-0.05D));
        }
    }

    private void moveRelative(Wither wither, Player target, double speed) {
        org.bukkit.util.Vector direction = target.getLocation().toVector().subtract(wither.getLocation().toVector()).setY(0.0D);
        if (direction.lengthSquared() == 0.0D) return;
        wither.setVelocity(direction.normalize().multiply(speed).setY(wither.getVelocity().getY()));
    }

    private void homingShot(Wither wither, Player target) {
        Snowball shot = wither.getWorld().spawn(wither.getEyeLocation(), Snowball.class);
        shot.setShooter(wither);
        shot.setItem(homingHead());
        shot.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_projectile"), org.bukkit.persistence.PersistentDataType.STRING, "wither_homing");
        shot.setVelocity(target.getEyeLocation().toVector().subtract(wither.getEyeLocation().toVector()).normalize().multiply(2.0D));
    }

    private void spawnKnight(Wither wither, Player target) {
        Location location = randomLocation(wither, 16);
        clearKnightSummonSpace(location);
        WitherSkeleton skeleton = (WitherSkeleton) wither.getWorld().spawnEntity(location, EntityType.WITHER_SKELETON);
        skeleton.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_knight"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        configureKnight(skeleton);
        skeleton.setTarget(target);
    }

    /** 本家の phase_transition/summon_minion/break と同じ3×3×3の召喚空間を確保する。 */
    private void clearKnightSummonSpace(Location location) {
        for (int x = -1; x <= 1; x++) for (int y = 0; y <= 2; y++) for (int z = -1; z <= 1; z++) {
            org.bukkit.block.Block block = location.clone().add(x, y, z).getBlock();
            Material type = block.getType();
            if (type.isAir() || type.getHardness() < 0.0F || Set.of(Material.OBSIDIAN, Material.CRYING_OBSIDIAN,
                    Material.BARRIER, Material.BEDROCK, Material.END_PORTAL_FRAME, Material.END_GATEWAY,
                    Material.NETHER_PORTAL, Material.COMMAND_BLOCK, Material.REPEATING_COMMAND_BLOCK,
                    Material.CHAIN_COMMAND_BLOCK, Material.STRUCTURE_BLOCK, Material.JIGSAW).contains(type)) continue;
            block.breakNaturally();
        }
    }

    /** 本家の近距離時の wither_skull -> wither_thunder 変換を再現する。 */
    private void convertNearbyWitherSkulls(Wither wither, Player target) {
        for (WitherSkull skull : wither.getWorld().getEntitiesByClass(WitherSkull.class)) {
            if (!(skull.getShooter() instanceof Wither shooter) || !shooter.getUniqueId().equals(wither.getUniqueId())) continue;
            if (skull.getLocation().distanceSquared(wither.getLocation()) > 64.0D
                    || skull.getPersistentDataContainer().has(convertedThunderSkullKey, org.bukkit.persistence.PersistentDataType.BYTE)) continue;
            skull.getPersistentDataContainer().set(convertedThunderSkullKey, org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
            skull.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, skull.getLocation().add(0.0D, 0.2D, 0.0D), 20, 0.1D, 0.1D, 0.1D, 1.0D);
            skull.remove();
            fields.thunder(wither, target.getLocation());
        }
    }

    private void spawnMinion(Wither wither, Player target) {
        WitherSkeleton skeleton = (WitherSkeleton) wither.getWorld().spawnEntity(randomLocation(wither, 10), EntityType.WITHER_SKELETON);
        skeleton.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_minion"), org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
        configureMinion(skeleton);
        skeleton.setTarget(target);
        wither.getWorld().spawnParticle(Particle.END_ROD, skeleton.getLocation().add(0, 1.5, 0), 10, 0.2, 0.7, 0.3, 0);
        wither.getWorld().spawnParticle(Particle.LARGE_SMOKE, skeleton.getLocation().add(0, 1.5, 0), 50, 0.2, 0.7, 0.3, 0);
        wither.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1.0F, 2.0F);
        wither.getWorld().playSound(skeleton.getLocation(), Sound.ENTITY_BREEZE_WIND_BURST, 1.5F, 1.0F);
    }

    private void explodeMinions(Wither wither) {
        org.bukkit.NamespacedKey minionKey = new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_minion");
        int exploded = 0;
        for (WitherSkeleton skeleton : wither.getWorld().getEntitiesByClass(WitherSkeleton.class)) {
            if (exploded >= 3 || skeleton.getLocation().distanceSquared(wither.getLocation()) > 1024.0D || !skeleton.getPersistentDataContainer().has(minionKey, org.bukkit.persistence.PersistentDataType.BYTE)) continue;
            LargeFireball fireball = skeleton.getWorld().spawn(skeleton.getLocation().clone().add(0.0D, 0.2D, 0.0D), LargeFireball.class);
            fireball.setShooter(wither);
            fireball.setYield(2.0F);
            fireball.setIsIncendiary(true);
            fireball.setVelocity(new org.bukkit.util.Vector(0.0D, -10.0D, 0.0D));
            skeleton.remove();
            exploded++;
        }
    }

    private Location randomLocation(Wither wither, double range) {
        return wither.getLocation().clone().add(ThreadLocalRandom.current().nextDouble(-range, range), 0,
                ThreadLocalRandom.current().nextDouble(-range, range));
    }

    private void configureKnight(WitherSkeleton skeleton) {
        skeleton.customName(net.kyori.adventure.text.Component.text("ウィザーの騎士"));
        skeleton.getAttribute(Attribute.MAX_HEALTH).setBaseValue(20.0D);
        skeleton.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(0.2D);
        skeleton.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.0D);
        skeleton.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(0.2D);
        skeleton.getAttribute(Attribute.SCALE).setBaseValue(1.15D);
        skeleton.getAttribute(Attribute.FALL_DAMAGE_MULTIPLIER).setBaseValue(0.0D);
        skeleton.setHealth(20.0D);
        skeleton.setAI(false);
        skeleton.setInvulnerable(true);
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, true, false));
        skeleton.getPersistentDataContainer().set(new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_knight_age"), org.bukkit.persistence.PersistentDataType.INTEGER, 0);
        skeleton.getEquipment().setHelmet(knightHead());
        skeleton.getEquipment().setChestplate(trimmedArmor(org.bukkit.Material.NETHERITE_CHESTPLATE));
        skeleton.getEquipment().setLeggings(trimmedArmor(org.bukkit.Material.NETHERITE_LEGGINGS));
        skeleton.getEquipment().setBoots(trimmedArmor(org.bukkit.Material.NETHERITE_BOOTS));
        skeleton.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.BOW));
        skeleton.getEquipment().setHelmetDropChance(0.0F);
        ItemDisplay sheath = skeleton.getWorld().spawn(skeleton.getLocation(), ItemDisplay.class);
        sheath.setRotation(skeleton.getYaw(), 0.0F);
        sheath.setItemStack(new ItemStack(org.bukkit.Material.NETHERITE_SWORD));
        sheath.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "truecrafter_sheath_owner"), org.bukkit.persistence.PersistentDataType.STRING, skeleton.getUniqueId().toString());
        sheath.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
        sheath.setTransformation(new org.bukkit.util.Transformation(new Vector3f(0.0F, -1.1F, -0.3F), new Quaternionf(),
                new Vector3f(0.5F, 0.5F, 0.5F), new Quaternionf(0.0F, 0.0F, 1.0F, 1.0F)));
        skeleton.addPassenger(sheath);
    }

    private ItemStack knightHead() {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.getServer().createProfile(UUID.fromString("be072330-229f-406e-bf0e-373ca9a56194"));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNjRiZmVkZWM0YWU1NDM5MTVmMTlkZDU1OTE1MzkwZDJlNjUzYjAwNTc5MWVmYjQwZDFmYjNjNzQ5MjYyOWQwYyJ9fX0="));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack homingHead() {
        ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        com.destroystokyo.paper.profile.PlayerProfile profile = Bukkit.getServer().createProfile(UUID.fromString("dbdb9027-be2e-4458-b484-38dd2afa2081"));
        profile.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWVkMGQ5MjA1ZjRiMDdhZDgxMWU2OTg4ZDg2NzQ0YWQyZTBhZmFhODJjZDg4NTcwZDhjNmYzM2YxY2VkNDFiYSJ9fX0="));
        meta.setPlayerProfile(profile);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack trimmedArmor(org.bukkit.Material material) {
        ItemStack item = new ItemStack(material);
        ArmorMeta meta = (ArmorMeta) item.getItemMeta();
        meta.setTrim(new ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.RIB));
        item.setItemMeta(meta);
        return item;
    }

    private void configureMinion(WitherSkeleton skeleton) {
        skeleton.customName(net.kyori.adventure.text.Component.text("ウィザーのしもべ"));
        skeleton.getAttribute(Attribute.STEP_HEIGHT).setBaseValue(1.0D);
        skeleton.getAttribute(Attribute.SCALE).setBaseValue(0.85D);
        skeleton.getEquipment().setItemInMainHand(new ItemStack(org.bukkit.Material.NETHERITE_SWORD));
        skeleton.getEquipment().setHelmetDropChance(0.0F);
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 40, 0, true, false));
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, true, false));
        skeleton.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 10, true, false));
    }

    private void tickKnights(Wither wither, Player target) {
        org.bukkit.NamespacedKey knightKey = new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_knight");
        org.bukkit.NamespacedKey ageKey = new org.bukkit.NamespacedKey("bettersurvival", "truecrafter_wither_knight_age");
        for (WitherSkeleton knight : wither.getWorld().getEntitiesByClass(WitherSkeleton.class)) {
            if (!knight.getPersistentDataContainer().has(knightKey, org.bukkit.persistence.PersistentDataType.BYTE) || knight.getLocation().distanceSquared(wither.getLocation()) > 2304.0D) continue;
            int age = knight.getPersistentDataContainer().getOrDefault(ageKey, org.bukkit.persistence.PersistentDataType.INTEGER, 0);
            if (age < 60) {
                knight.getPersistentDataContainer().set(ageKey, org.bukkit.persistence.PersistentDataType.INTEGER, age + 1);
                knight.getWorld().spawnParticle(Particle.END_ROD, knight.getLocation().add(0, 1.5D, 0), 1, 0.2D, 0.7D, 0.3D, 0);
                knight.setTarget(target);
                continue;
            }
            if (!knight.hasAI()) {
                knight.removePotionEffect(PotionEffectType.INVISIBILITY);
                knight.setInvulnerable(false);
                knight.setAI(true);
            }
        }
    }

    private enum Skill { NONE, HOMING, CHARGE, THUNDER, SUMMON }

    private static final class State {
        private int phase = 1;
        private int tick = -10;
        private int used;
        private int dashTicks = -1;
        private boolean dashLeft;
        private boolean transition;
        private Skill skill = Skill.NONE;
    }
}
