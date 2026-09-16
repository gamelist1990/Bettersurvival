package org.pexserver.koukunn.bettersurvival.Modules.Feature.HardMode.LeveingSystem;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Merchant;
import org.bukkit.inventory.MerchantInventory;
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

/** Forge版 Just Leveling v1.7 のクライアント/Mixin依存機能を Paper イベントへ置き換える。 */
public final class JustLevelingRuntime implements Listener {
    private static final long COUNTER_WINDOW_MS = 3_000L;
    private static final long PEARL_WINDOW_MS = 1_500L;
    private static final int VANILLA_MAX_AIR = 300;
    private static final int ATHLETICS_MAX_AIR = 450;

    private final Loader plugin;
    private final LevelingSystemModule leveling;
    private final Map<UUID, CounterState> counterAttack = new HashMap<>();
    private final Map<UUID, Long> pearlTeleports = new HashMap<>();
    private final Set<UUID> potionRewriteGuard = new HashSet<>();
    private final Map<UUID, MerchantDiscountState> merchantDiscounts = new HashMap<>();

    public JustLevelingRuntime(Loader plugin, LevelingSystemModule leveling) {
        this.plugin = plugin;
        this.leveling = leveling;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> refreshPersistentSkills(), 20L, 100L);
    }

    public boolean has(Player player, LevelingSkill skill) {
        return leveling.getLevel(player, skill.aptitude()) >= skill.requiredLevel();
    }

    private void refreshPersistentSkills() {
        for (Player player : Bukkit.getOnlinePlayers()) refreshPersistentSkills(player);
    }

    private void refreshPersistentSkills(Player player) {
        syncOwnedPotion(player, PotionEffectType.NIGHT_VISION, has(player, LevelingSkill.CAT_EYES), 0);
        syncOwnedPotion(player, PotionEffectType.RESISTANCE, has(player, LevelingSkill.DIAMOND_SKIN), 1);
        if (has(player, LevelingSkill.ATHLETICS)) {
            if (player.getMaximumAir() < ATHLETICS_MAX_AIR) player.setMaximumAir(ATHLETICS_MAX_AIR);
        } else if (player.getMaximumAir() == ATHLETICS_MAX_AIR) {
            player.setMaximumAir(VANILLA_MAX_AIR);
        }
        applyExtendedPassives(player);
    }

    private void syncOwnedPotion(Player player, PotionEffectType type, boolean enabled, int amplifier) {
        PotionEffect current = player.getPotionEffect(type);
        if (enabled) {
            player.addPotionEffect(new PotionEffect(type, 220, amplifier, true, false, false));
            return;
        }
        if (current != null && current.isAmbient() && !current.hasParticles() && !current.hasIcon()
                && current.getAmplifier() == amplifier) {
            player.removePotionEffect(type);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin, () -> refreshPersistentSkills(event.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        restoreMerchantDiscount(id);
        counterAttack.remove(id);
        pearlTeleports.remove(id);
        potionRewriteGuard.remove(id);
        refreshPersistentSkills(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        Player attacker = playerFromDamager(event.getDamager());
        if (attacker != null) {
            int luck = leveling.getLevel(attacker, LevelingAptitude.LUCK);
            int criticalTier = LevelingAptitude.LUCK.passiveTier10(luck);
            if (criticalTier > 0 && attacker.getFallDistance() > 0.0F && !attacker.isOnGround()) {
                event.setDamage(event.getDamage() * (1.0D + 0.25D * criticalTier));
            }
            if (event.getDamager() instanceof AbstractArrow) {
                if (has(attacker, LevelingSkill.QUICK_REPOSITION)) attacker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, true, true));
                if (has(attacker, LevelingSkill.STEALTH_MASTERY) && attacker.isSneaking()) event.setDamage(event.getDamage() * 1.25D);
            } else {
                CounterState state = counterAttack.remove(attacker.getUniqueId());
                if (state != null && state.expiresAt >= System.currentTimeMillis() && has(attacker, LevelingSkill.COUNTER_ATTACK)) event.setDamage(event.getDamage() + state.bonusDamage);
                if (has(attacker, LevelingSkill.LIMIT_BREAKER) && ThreadLocalRandom.current().nextInt(10_000) < 100) {
                    event.setDamage(event.getDamage() * 999.0D);
                    attacker.sendActionBar("§6限界突破！");
                }
            }
        }
        if (event.getEntity() instanceof Player victim && has(victim, LevelingSkill.COUNTER_ATTACK)) {
            counterAttack.put(victim.getUniqueId(), new CounterState(System.currentTimeMillis() + COUNTER_WINDOW_MS, event.getFinalDamage() * 0.50D));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && has(killer, LevelingSkill.FIGHTING_SPIRIT)) killer.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 60, 0, true, true, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        if (event.getEntity() instanceof ShulkerBullet && event.getHitEntity() instanceof Player player && has(player, LevelingSkill.TURTLE_SHIELD)) {
            Bukkit.getScheduler().runTask(plugin, () -> player.removePotionEffect(PotionEffectType.LEVITATION));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player) || !has(player, LevelingSkill.STEALTH_MASTERY)) return;
        double range = player.isSneaking() ? 8.0D : 20.0D;
        if (event.getEntity().getLocation().distanceSquared(player.getLocation()) > range * range) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPowderSnow(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.SNOW_WALKER)) return;
        player.setFreezeTicks(0);
        Material feet = player.getLocation().getBlock().getType();
        Material below = player.getLocation().clone().subtract(0, 0.2D, 0).getBlock().getType();
        if ((feet == Material.POWDER_SNOW || below == Material.POWDER_SNOW) && player.getVelocity().getY() < 0.0D) player.setVelocity(player.getVelocity().setY(0.0D));
    }

    @EventHandler(ignoreCancelled = true)
    public void onPotionEffect(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player) || event.getNewEffect() == null) return;
        UUID id = player.getUniqueId();
        if (potionRewriteGuard.remove(id)) return;
        PotionEffect effect = event.getNewEffect();
        if (has(player, LevelingSkill.LION_HEART) && isNegative(effect.getType()) && effect.getDuration() > 1) {
            rewriteEffect(player, effect, Math.max(1, effect.getDuration() / 2), effect.getAmplifier());
            return;
        }
        int magic = leveling.getLevel(player, LevelingAptitude.MAGIC);
        int beneficialTier = LevelingAptitude.MAGIC.passiveTier10(magic);
        if (beneficialTier > 0 && !isNegative(effect.getType()) && effect.getDuration() > 1) rewriteEffect(player, effect, effect.getDuration() + beneficialTier * 60, effect.getAmplifier());
    }

    private void rewriteEffect(Player player, PotionEffect effect, int duration, int amplifier) {
        UUID id = player.getUniqueId();
        Bukkit.getScheduler().runTask(plugin, () -> {
            potionRewriteGuard.add(id);
            player.removePotionEffect(effect.getType());
            player.addPotionEffect(new PotionEffect(effect.getType(), duration, amplifier, effect.isAmbient(), effect.hasParticles(), effect.hasIcon()));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.ALCHEMY_MANIPULATION) || event.getItem().getType() != Material.POTION) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                if (!isNegative(effect.getType())) player.addPotionEffect(new PotionEffect(effect.getType(), effect.getDuration(), effect.getAmplifier() + 1, effect.isAmbient(), effect.hasParticles(), effect.hasIcon()), true);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onObsidianDamage(BlockDamageEvent event) {
        Material type = event.getBlock().getType();
        if (has(event.getPlayer(), LevelingSkill.OBSIDIAN_SMASHER) && (type == Material.OBSIDIAN || type == Material.CRYING_OBSIDIAN)) event.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, 8, true, false, false));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!has(player, LevelingSkill.TREASURE_HUNTER) || !isDirt(event.getBlock().getType())) return;
        if (ThreadLocalRandom.current().nextInt(10_000) >= 500) return;
        Material[] loot = {Material.IRON_NUGGET, Material.GOLD_NUGGET, Material.EMERALD, Material.LAPIS_LAZULI, Material.DIAMOND};
        Material reward = loot[ThreadLocalRandom.current().nextInt(loot.length)];
        event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), new ItemStack(reward));
        player.sendActionBar("§6お宝を発見！");
    }

    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || !has(player, LevelingSkill.CONVERGENCE)) return;
        if (ThreadLocalRandom.current().nextInt(100) >= 8) return;
        List<ItemStack> candidates = new ArrayList<>();
        for (ItemStack item : event.getInventory().getMatrix()) if (item != null && !item.getType().isAir()) candidates.add(item);
        if (candidates.isEmpty()) return;
        ItemStack refund = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size())).clone();
        refund.setAmount(1);
        player.getInventory().addItem(refund);
        player.sendActionBar("§a収束: 素材を1個還元しました");
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearlTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL && has(event.getPlayer(), LevelingSkill.SAFE_PORT)) pearlTeleports.put(event.getPlayer().getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPearlDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !has(player, LevelingSkill.SAFE_PORT)) return;
        Long at = pearlTeleports.get(player.getUniqueId());
        if (at != null && System.currentTimeMillis() - at <= PEARL_WINDOW_MS && event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(true);
            pearlTeleports.remove(player.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onWormhole(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (player.isSneaking() && item != null && item.getType() == Material.ENDER_CHEST && has(player, LevelingSkill.WORMHOLE_STORAGE)) {
            event.setCancelled(true);
            player.openInventory(player.getEnderChest());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onScholar(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!player.isSneaking() || !has(player, LevelingSkill.SCHOLAR)) return;
        ItemStack item = event.getItem();
        if (item == null || item.getEnchantments().isEmpty()) return;
        ItemMeta meta = item.getItemMeta();
        player.sendMessage("§b学者 §7- §f" + (meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name()));
        item.getEnchantments().forEach((enchantment, level) -> player.sendMessage("§7 • §d" + enchantment.getKey().getKey() + " §f" + level));
    }

    @EventHandler(ignoreCancelled = true)
    public void onMerchantOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !has(player, LevelingSkill.HAGGLER)) return;
        if (!(event.getInventory() instanceof MerchantInventory merchantInventory)) return;
        restoreMerchantDiscount(player.getUniqueId());
        Merchant merchant = merchantInventory.getMerchant();
        List<MerchantRecipe> recipes = merchant.getRecipes();
        List<Integer> originals = new ArrayList<>(recipes.size());
        for (MerchantRecipe recipe : recipes) {
            originals.add(recipe.getSpecialPrice());
            List<ItemStack> ingredients = recipe.getIngredients();
            if (ingredients.isEmpty()) continue;
            int discount = Math.max(1, (int) Math.ceil(ingredients.getFirst().getAmount() * 0.20D));
            recipe.setSpecialPrice(recipe.getSpecialPrice() - discount);
        }
        merchant.setRecipes(recipes);
        merchantDiscounts.put(player.getUniqueId(), new MerchantDiscountState(merchant, originals));
    }

    @EventHandler
    public void onMerchantClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) restoreMerchantDiscount(player.getUniqueId());
    }

    private void restoreMerchantDiscount(UUID playerId) {
        MerchantDiscountState state = merchantDiscounts.remove(playerId);
        if (state == null) return;
        List<MerchantRecipe> recipes = state.merchant.getRecipes();
        int count = Math.min(recipes.size(), state.originalSpecialPrices.size());
        for (int i = 0; i < count; i++) recipes.get(i).setSpecialPrice(state.originalSpecialPrices.get(i));
        state.merchant.setRecipes(recipes);
    }

    private void applyExtendedPassives(Player player) {
        int strength = leveling.getLevel(player, LevelingAptitude.STRENGTH);
        int intelligence = leveling.getLevel(player, LevelingAptitude.INTELLIGENCE);
        int building = leveling.getLevel(player, LevelingAptitude.BUILDING);
        setBase(player, Attribute.ATTACK_KNOCKBACK, LevelingAptitude.STRENGTH.passiveTier5(strength) * 0.4D);
        setBase(player, Attribute.ENTITY_INTERACTION_RANGE, 3.0D + LevelingAptitude.INTELLIGENCE.passiveTier5(intelligence));
        setBase(player, Attribute.BLOCK_INTERACTION_RANGE, 4.5D + LevelingAptitude.BUILDING.passiveTier5(building) * 1.5D);
        setBase(player, Attribute.BLOCK_BREAK_SPEED, 1.0D + LevelingAptitude.BUILDING.passiveTier5(building) * 0.5D);
    }

    private void setBase(Player player, Attribute attribute, double value) {
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) instance.setBaseValue(value);
    }

    private Player playerFromDamager(Entity entity) {
        if (entity instanceof Player player) return player;
        if (entity instanceof AbstractArrow arrow && arrow.getShooter() instanceof Player player) return player;
        return null;
    }

    private boolean isNegative(PotionEffectType type) {
        return type.equals(PotionEffectType.POISON) || type.equals(PotionEffectType.WITHER)
                || type.equals(PotionEffectType.SLOWNESS) || type.equals(PotionEffectType.WEAKNESS)
                || type.equals(PotionEffectType.BLINDNESS) || type.equals(PotionEffectType.DARKNESS)
                || type.equals(PotionEffectType.MINING_FATIGUE) || type.equals(PotionEffectType.HUNGER)
                || type.equals(PotionEffectType.NAUSEA) || type.equals(PotionEffectType.LEVITATION);
    }

    private boolean isDirt(Material material) {
        return material == Material.DIRT || material == Material.COARSE_DIRT || material == Material.ROOTED_DIRT
                || material == Material.GRASS_BLOCK || material == Material.PODZOL || material == Material.MYCELIUM
                || material == Material.MUD || material == Material.MUDDY_MANGROVE_ROOTS;
    }

    private record CounterState(long expiresAt, double bonusDamage) {}
    private record MerchantDiscountState(Merchant merchant, List<Integer> originalSpecialPrices) {}
}
