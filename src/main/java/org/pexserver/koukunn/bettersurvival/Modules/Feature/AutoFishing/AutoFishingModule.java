package org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFishing;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.loot.LootContext;
import org.bukkit.loot.LootTable;
import org.bukkit.loot.LootTables;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Paper 26.1.2 系ではプレイヤーの釣竿右クリックをそのまま安全に再現できる公開 API がないため、
 * このモジュールは BITE 検知後にサーバー側で釣果生成を完結させる方式を採用している。
 * このため、通常の釣りと比べて以下の点で挙動が異なる。
 * 現状: 上手く機能している為これで完成とするが、将来的にPaper側でAPIが拡張されバニラの動作をそのまま活用できる際には,
 * このモジュールはリファクタリングする予定。
 *
 * 釣果テーブルは Minecraft 1.21 系の fishing loot table に合わせ、
 * fish / junk / treasure の親テーブル重みを floor(weight + quality * luck) で計算する。
 * luck にはプレイヤーの Attribute.LUCK と釣竿の Luck of the Sea レベルを加算し、
 * treasure は open water のときだけ抽選対象に含める。
 *
 * Lure による待機短縮は通常の FishHook 挙動側で処理される前提で、
 * BITE 後のみこのモジュールが介入する。
 * Unbreaking / Mending は damageItemStack と ExperienceOrb 生成により通常どおり反映される。
 *
 * Paper 側で loot の luck 計算式が将来変わった場合や、
 * GlobalConfiguration.misc.useAlternativeLuckFormula を使う場合はこの実装も見直しが必要。
 */
public class AutoFishingModule implements Listener {

    private static final String FEATURE_KEY = "autofishing";
    private static final long RECAST_DELAY_TICKS = 8L;
    private static final long RECAST_RETRY_DELAY_TICKS = 2L;
    private static final int JUNK_WEIGHT = 10;
    private static final int JUNK_QUALITY = -2;
    private static final int TREASURE_WEIGHT = 5;
    private static final int TREASURE_QUALITY = 2;
    private static final int FISH_WEIGHT = 85;
    private static final int FISH_QUALITY = -1;

    private final ToggleModule toggle;
    private final Plugin plugin;
    private final Random random = new Random();
    private final Map<UUID, FishingSession> sessions = new HashMap<>();
    @SuppressWarnings("unused")
    private final BukkitTask monitorTask;

    public AutoFishingModule(ToggleModule toggle) {
        this.toggle = toggle;
        this.plugin = Loader.getPlugin(Loader.class);
        this.monitorTask = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tickSessions, 2L, 2L);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY))
            return;

        PlayerFishEvent.State state = event.getState();
        if (state == PlayerFishEvent.State.FISHING) {
            startSession(player, event);
            return;
        }

        FishingSession session = sessions.get(player.getUniqueId());
        if (session == null)
            return;
        if (!matchesHook(session, event.getHook()))
            return;

        if (state == PlayerFishEvent.State.BITE) {
            if (!shouldContinue(player, session)) {
                sessions.remove(player.getUniqueId());
                return;
            }
            event.setCancelled(true);
            catchFish(player, session, event.getHook());
            return;
        }

        if (state == PlayerFishEvent.State.LURED)
            return;

        if (session.autoRetrieving)
            return;

        if (state == PlayerFishEvent.State.CAUGHT_FISH || state == PlayerFishEvent.State.FAILED_ATTEMPT) {
            scheduleRecast(player, session);
            return;
        }

        sessions.remove(player.getUniqueId());
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sessions.remove(event.getPlayer().getUniqueId());
    }

    private void startSession(Player player, PlayerFishEvent event) {
        EquipmentSlot hand = event.getHand();
        if (hand != EquipmentSlot.HAND)
            return;
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!isFishingRod(rod))
            return;
        sessions.put(player.getUniqueId(), new FishingSession(hand, event.getHook().getUniqueId()));
    }

    private void catchFish(Player player, FishingSession session, FishHook hook) {
        if (session.autoRetrieving)
            return;
        session.autoRetrieving = true;
        FishingSession current = sessions.get(player.getUniqueId());
        if (current != session) {
            session.autoRetrieving = false;
            return;
        }
        if (!shouldContinue(player, current)) {
            current.autoRetrieving = false;
            sessions.remove(player.getUniqueId());
            return;
        }
        if (!matchesHook(current, hook) || !hook.isValid()) {
            current.autoRetrieving = false;
            return;
        }
        Location hookLocation = hook.getLocation();
        LootTable fishingLoot = getFishingLootTable(player, hook);
        if (fishingLoot == null) {
            spawnCaughtItem(player, hookLocation, new ItemStack(Material.COD));
        } else {
            LootContext.Builder builder = new LootContext.Builder(hookLocation).killer(player);
            builder.luck(getFishingLuck(player));
            for (ItemStack loot : fishingLoot.populateLoot(random, builder.build())) {
                if (loot == null || loot.getType().isAir())
                    continue;
                spawnCaughtItem(player, hookLocation, loot);
            }
        }
        spawnFishingExperience(player, hookLocation);
        player.damageItemStack(current.hand, 1);
        player.swingHand(current.hand);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0F, 1.0F);
        current.hookId = null;
        current.autoRetrieving = false;
        hook.remove();
        scheduleRecast(player, current);
    }

    private void scheduleRecast(Player player, FishingSession session) {
        if (session.recastScheduled)
            return;
        scheduleRecast(player, session, RECAST_DELAY_TICKS);
    }

    private void scheduleRecast(Player player, FishingSession session, long delayTicks) {
        session.recastScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FishingSession current = sessions.get(player.getUniqueId());
            if (current != session)
                return;
            if (!shouldContinue(player, current)) {
                current.recastScheduled = false;
                sessions.remove(player.getUniqueId());
                return;
            }
            FishHook activeHook = player.getFishHook();
            if (activeHook != null && activeHook.isValid()) {
                current.recastScheduled = false;
                scheduleRecast(player, current, RECAST_RETRY_DELAY_TICKS);
                return;
            }
            current.recastScheduled = false;
            FishHook newHook = player.launchProjectile(FishHook.class,
                    player.getEyeLocation().getDirection().normalize().multiply(1.5D));
            player.swingHand(current.hand);
            current.hookId = newHook.getUniqueId();
        }, delayTicks);
    }

    private void tickSessions() {
        if (sessions.isEmpty())
            return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            FishingSession session = sessions.get(player.getUniqueId());
            if (session == null)
                continue;
            if (!shouldContinue(player, session))
                sessions.remove(player.getUniqueId());
        }
    }

    private boolean shouldContinue(Player player, FishingSession session) {
        if (!player.isOnline() || player.isDead())
            return false;
        if (!toggle.getGlobal(FEATURE_KEY))
            return false;
        if (!toggle.isEnabledFor(player.getUniqueId().toString(), FEATURE_KEY))
            return false;
        if (!isFishingRod(player.getInventory().getItemInMainHand()))
            return false;
        return true;
    }

    private boolean matchesHook(FishingSession session, FishHook hook) {
        return session.hookId != null && session.hookId.equals(hook.getUniqueId());
    }

    private boolean isFishingRod(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD && item.getAmount() > 0;
    }

    private LootTable getFishingLootTable(Player player, FishHook hook) {
        float luck = getFishingLuck(player);
        int fishWeight = getVanillaLootWeight(FISH_WEIGHT, FISH_QUALITY, luck);
        int junkWeight = getVanillaLootWeight(JUNK_WEIGHT, JUNK_QUALITY, luck);
        int treasureWeight = hook.isInOpenWater() ? getVanillaLootWeight(TREASURE_WEIGHT, TREASURE_QUALITY, luck) : 0;
        int totalWeight = fishWeight + junkWeight + treasureWeight;
        if (totalWeight <= 0)
            return LootTables.FISHING_FISH.getLootTable();
        int roll = random.nextInt(totalWeight);
        if (roll < junkWeight)
            return LootTables.FISHING_JUNK.getLootTable();
        roll -= junkWeight;
        if (roll < treasureWeight)
            return LootTables.FISHING_TREASURE.getLootTable();
        return LootTables.FISHING_FISH.getLootTable();
    }

    private float getFishingLuck(Player player) {
        float attributeLuck = 0.0F;
        if (player.getAttribute(Attribute.LUCK) != null)
            attributeLuck = (float) player.getAttribute(Attribute.LUCK).getValue();
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null)
            return attributeLuck;
        return attributeLuck + rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
    }

    private int getVanillaLootWeight(int weight, int quality, float luck) {
        return Math.max((int) Math.floor(weight + quality * luck), 0);
    }

    private void spawnCaughtItem(Player player, Location hookLocation, ItemStack loot) {
        Vector velocity = createVanillaReturnVelocity(hookLocation, player.getLocation());
        hookLocation.getWorld().dropItem(hookLocation, loot, item -> item.setVelocity(velocity));
    }

    private void spawnFishingExperience(Player player, Location hookLocation) {
        int exp = ThreadLocalRandom.current().nextInt(1, 7);
        Location expLocation = player.getLocation().add(0.0D, 0.5D, 0.5D);
        expLocation.getWorld().spawn(expLocation, ExperienceOrb.class, orb -> {
            orb.setExperience(exp);
            orb.setVelocity(new Vector(0.0D, 0.0D, 0.0D));
        });
    }

    /**
     * Minecraft 1.21 系の FishingHook#retrieve に合わせた釣果アイテムの初速。
     * x = (playerX - hookX) * 0.1
     * y = (playerY - hookY) * 0.1 + sqrt(sqrt(dx^2 + dy^2 + dz^2)) * 0.08
     * z = (playerZ - hookZ) * 0.1
     */
    private Vector createVanillaReturnVelocity(Location hookLocation, Location playerLocation) {
        double x = playerLocation.getX() - hookLocation.getX();
        double y = playerLocation.getY() - hookLocation.getY();
        double z = playerLocation.getZ() - hookLocation.getZ();
        double lift = Math.sqrt(Math.sqrt(x * x + y * y + z * z)) * 0.08D;
        return new Vector(x * 0.1D, y * 0.1D + lift, z * 0.1D);
    }

    private static class FishingSession {
        private final EquipmentSlot hand;
        private UUID hookId;
        private boolean autoRetrieving;
        private boolean recastScheduled;

        private FishingSession(EquipmentSlot hand, UUID hookId) {
            this.hand = hand;
            this.hookId = hookId;
        }
    }
}
