package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Merchant;
import org.bukkit.entity.Player;
import org.bukkit.entity.ShulkerBullet;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.MerchantInventory;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Forge版 Just Leveling v1.7 のクライアント/Mixin依存機能を、可能な範囲で
 * Paperイベントへ置き換えるランタイム。
 */
public final class JustLevelingRuntime implements Listener {
    private static final long COUNTER_WINDOW_MS = 3_000L;
    private static final long PEARL_WINDOW_MS = 1_500L;

    private final Loader plugin;
    private final LevelingSystemModule leveling;
    private final Map<UUID, CounterState> counterAttack = new HashMap<>();
    private final Map<UUID, Long> pearlTeleports = new HashMap<>();
    private final Set<UUID> potionRewriteGuard = new HashSet<>();

    public JustLevelingRuntime(Loader plugin, LevelingSystemModule leveling) {
        this.plugin = plugin;
        this.leveling = leveling;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshPersistentSkills, 20L, 100L);
    }

    public boolean has(Player player, LevelingSkill skill) {
        return leveling.getLevel(player, skill.aptitude()) >= skill.requiredLevel();
    }

    private void refreshPersistentSkills() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (has(player, LevelingSkill.CAT_EYES)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 220, 0, true, false, false));
            }
            if (has(player, LevelingSkill.DIAMOND_SKIN)) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 220, 1, true, false, false));
            }
            if (has(player, LevelingSkill.ATHLETICS) && player.getMaximumAir() < 450) {
                player.setMaximumAir(450); // Forge既定 athleticsModifier=1.5
            }
            applyExtendedPassives(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            refreshPersistentSkills();
            applyExtendedPassives(event.getPlayer());
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = playerFromDamager(event.getDamager());
        if (attacker != null) {
            if (event.getDamager() instanceof AbstractArrow) {
                if (has(attacker, LevelingSkill.QUICK_REPOSITION)) {
                    attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, true, true));
                }
                if (has(attacker, LevelingSkill.STEALTH_MASTERY) && attacker.isSneaking()) {
                    event.setDamage(event.getDamage() * 1.25D);
                }
            } else {
                CounterState state = counterAttack.remove(attacker.getUniqueId());
                if (state != null && state.expiresAt >= System.currentTimeMillis() && has(attacker, LevelingSkill.COUNTER_ATTACK)) {
                    event.setDamage(event.getDamage() + state.bonusDamage);
                }
                if (has(attacker, LevelingSkill.LIMIT_BREAKER) && ThreadLocalRandom.current().nextInt(10_000) < 100) {
                    event.setDamage(event.getDamage() * 999.0D);
                    attacker.sendActionBar("§6LIMIT BREAK!");
                }
            }
        }

        if (event.getEntity() instanceof Player victim && has(victim, LevelingSkill.COUNTER_ATTACK)) {
            counterAttack.put(victim.getUniqueId(), new CounterState(System.currentTimeMillis() + COUNTER_WINDOW_MS,
                    event.getFinalDamage() * 0.50D));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        if (has(killer, LevelingSkill.FIGHTING_SPIRIT)) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, true, true));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ShulkerBullet) || !(event.getHitEntity() instanceof Player player)) return;
        if (!has(player, LevelingSkill.TURTLE_SHIELD)) return;
        Bukkit.getScheduler().runTask(plugin, () -> player.removePotionEffect(PotionEffectType.LEVITATION));
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !has(player, LevelingSkill.STEALTH_MASTERY)) return;
        double distanceSquared = event.getEntity().getLocation().distanceSquared(player.getLocation());
        double normalRange = player.isSneaking() ? 8.0D : 20.0D;
        if (distanceSquared > normalRange * normalRange) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPowderSnow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.SNOW_WALKER)) return;
        player.setFreezeTicks(0);
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.2D, 0).getBlock().getType();
        if ((feet == Material.POWDER_SNOW || below == Material.POWDER_SNOW) && player.getVelocity().getY() < 0.0D) {
            player.setVelocity(player.getVelocity().setY(0.0D));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) return;
        UUID id = player.getUniqueId();
        if (potionRewriteGuard.remove(id)) return;
        PotionEffect effect = event.getNewEffect();
        if (has(player, LevelingSkill.LION_HEART) && isNegative(effect.getType()) && effect.getDuration() > 1) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                potionRewriteGuard.add(id);
                player.removePotionEffect(effect.getType());
                player.addPotionEffect(new PotionEffect(effect.getType(), Math.max(1, effect.getDuration() / 2),
                        effect.getAmplifier(), effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
            });
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.ALCHEMY_MANIPULATION)) return;
        Material type = event.getItem().getType();
        if (type != Material.POTION && type != Material.MILK_BUCKET && type != Material.HONEY_BOTTLE) return;
        if (type != Material.POTION) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            List<PotionEffect> current = new ArrayList<>(player.getActivePotionEffects());
            for (PotionEffect effect : current) {
                if (!isNegative(effect.getType())) {
                    player.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier() + 1,
                            effect.isAmbient(), effect.hasParticles(), effect.hasIcon()), true);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onObsidianDamage(BlockDamageEvent event) {
        if (!has(event.getPlayer(), LevelingSkill.OBSIDIAN_SMASHER)) return;
        Material type = event.getBlock().getType();
        if (type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN) {
            event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 8, true, false, false));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.TREASURE_HUNTER) || !isDirt(event.getBlock().getType())) return;
        // Forge既定 treasureHunterProbability=500 / 10000 = 5%
        if (ThreadLocalRandom.current().nextInt(10_000) >= 500) return;
        Material[] loot = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.EMERALD, Material.LAPIS_LAZULI, Material.DIAMOND};
        Material reward = loot[ThreadLocalRandom.current().nextInt(loot.length)];
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(reward, 1));
        player.sendActionBar("§6Treasure Hunter!");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !has(player, LevelingSkill.CONVERGENCE)) return;
        if (ThreadLocalRandom.current().nextInt(100) >= 8) return; // Forge既定 8%
        ItemStack[] matrix = event.getInventory().getMatrix();
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack item : matrix) {
            if (item != null && !item.getType().isAir()) candidates.add(item);
        }
        if (candidates.isEmpty()) return;
        ItemStack refund = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone();
        refund.setAmount(1);
        player.getInventory().addItem(refund);
        player.sendActionBar("§aConvergence: material refunded");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && has(event.getPlayer(), LevelingSkill.SAFE_PORT)) {
            pearlTeleports.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPearlDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !has(player, LevelingSkill.SAFE_PORT)) return;
        Long at = pearlTeleports.get(player.getUniqueId());
        if (at == null || System.currentTimeMillis() - at > PEARL_WINDOW_MS) return;
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            pearlTeleports.remove(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWormhole(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (!player.isSneaking() || item == null || item.getType() != Material.ENDER_CHEST) return;
        if (!has(player, LevelingSkill.WORMHOLE_STORAGE)) return;
        event.setCancelled(true);
        player.openInventory(player.getEnderChest());
    }

    @EventHandler(ignoreCancelled = true)
    public void onScholar(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !has(player, LevelingSkill.SCHOLAR)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getEnchantments().isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        player.sendMessage("§bScholar §7- §f" + (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name()));
        item.getEnchantments().forEach((enchantment, level) ->
                player.sendMessage("§7 • §d" + enchantment.getKey().getKey() + " §f" + level));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !has(player, LevelingSkill.HAGGLER)) return;
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;
        Merchant merchant = merchantInventory.getMerchant();
        List<MerchantRecipe> recipes = merchant.getRecipes();
        for (MerchantRecipe recipe : recipes) {
            List<ItemStack> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) continue;
            int base = ingredients.getFirst().getAmount();
            recipe.setSpecialPrice(-Math.max(1, (int) Math.ceil(base * 0.20D)));
        }
        merchant.setRecipes(recipes);
    }

    private void applyExtendedPassives(Player player) {
        int strength = leveling.getLevel(player, LevelingAptitude.STRENGTH);
        int intelligence = leveling.getLevel(player, LevelingAptitude.INTELLIGENCE);
        int building = leveling.getLevel(player, LevelingAptitude.BUILDING);
        int luck = leveling.getLevel(player, LevelingAptitude.LUCK);

        // Forge既定: attack knockback 0.4 x 5 tiers
        setBaseMinimum(player, Attribute.ATTACK_KNOCKBACK, LevelingAptitude.STRENGTH.passiveTier5(strength) * 0.4D);
        // PaperではForge独自reach属性を標準のinteraction rangeへ写像する。
        setBaseMinimum(player, Attribute.ENTITY_INTERACTION_RANGE, 3.0D + LevelingAptitude.INTELLIGENCE.passiveTier5(intelligence));
        setBaseMinimum(player, Attribute.BLOCK_INTERACTION_RANGE, 4.5D + LevelingAptitude.BUILDING.passiveTier5(building) * 1.5D);
        setBaseMinimum(player, Attribute.BLOCK_BREAK_SPEED, 1.0D + LevelingAptitude.BUILDING.passiveTier5(building) * 0.5D);
        // critical damage は独自Forge属性のため、実ダメージ補正は攻撃イベント側で近似。
        if (LevelingAptitude.LUCK.passiveTier10(luck) > 0) {
            // 値の保持先として標準Luckも本体側で設定される。ここでは追加処理不要。
        }
    }

    private void setBaseMinimum(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null && instance.getBaseValue() < value) instance.setBaseValue(value);
    }

    private Player playerFromDamager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isNegative(PotionEffectType type) {
        return type.equals(PotionEffectType.POISON)
                || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.SLOWNESS)
                || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.BLINDNESS)
                || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.MINING_FATIGUE)
                || type.equals(PotionEffectType.HUNGER)
                || type.equals(PotionEffectType.NAUSEA)
                || type.equals(PotionEffectType.LEVITATION);
    }

    private boolean isDirt(Material material) {
        return material == Material.DIRT || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT
                || material == Material.GRASS_BLOCK || material == Material.PODZOL || material == Material.MYCELIUM
                || material == Material.MUD || material == Material.MUDDY_MANGROVE_ROOTS;
    }

    private record CounterState(long expiresAt, double bonusDamage) {}
}
