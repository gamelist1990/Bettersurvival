package org.pexserver.koukunn.bettersurvival.Modules.Feature.AutoFishing;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.FishHook;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class AutoFishingModule implements Listener {

    private static final String FEATURE_KEY = "autofishing";
    private static final long RECAST_DELAY_TICKS = 8L;
    private static final double STOP_MOVE_DISTANCE_SQUARED = 0.25D;
    private static final float STOP_YAW_DELTA = 12.0F;
    private static final float STOP_PITCH_DELTA = 12.0F;

    private final ToggleModule toggle;
    private final Plugin plugin;
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
            session.autoRetrieving = true;
            int damage = event.getHook().retrieve(session.hand);
            session.autoRetrieving = false;
            if (damage > 0)
                player.damageItemStack(session.hand, damage);
            player.swingHand(session.hand);
            scheduleRecast(player, session);
            return;
        }

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
        Location location = player.getLocation();
        sessions.put(player.getUniqueId(), new FishingSession(
                location.toVector(),
                location.getYaw(),
                location.getPitch(),
                hand,
                event.getHook().getUniqueId()
        ));
    }

    private void scheduleRecast(Player player, FishingSession session) {
        if (session.recastScheduled)
            return;
        session.recastScheduled = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            FishingSession current = sessions.get(player.getUniqueId());
            if (current != session)
                return;
            current.recastScheduled = false;
            if (!shouldContinue(player, current)) {
                sessions.remove(player.getUniqueId());
                return;
            }
            FishHook activeHook = current.getHook();
            if (activeHook != null && activeHook.isValid())
                return;
            FishHook newHook = player.launchProjectile(FishHook.class,
                    player.getEyeLocation().getDirection().normalize().multiply(1.5D));
            player.swingHand(current.hand);
            current.hookId = newHook.getUniqueId();
        }, RECAST_DELAY_TICKS);
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
        if (player.isSneaking())
            return false;
        if (!isFishingRod(player.getInventory().getItemInMainHand()))
            return false;

        Location location = player.getLocation();
        if (location.toVector().distanceSquared(session.anchor) > STOP_MOVE_DISTANCE_SQUARED)
            return false;
        if (angleDifference(location.getYaw(), session.yaw) > STOP_YAW_DELTA)
            return false;
        if (angleDifference(location.getPitch(), session.pitch) > STOP_PITCH_DELTA)
            return false;
        return true;
    }

    private boolean matchesHook(FishingSession session, FishHook hook) {
        return session.hookId != null && session.hookId.equals(hook.getUniqueId());
    }

    private boolean isFishingRod(ItemStack item) {
        return item != null && item.getType() == Material.FISHING_ROD && item.getAmount() > 0;
    }

    private float angleDifference(float first, float second) {
        float diff = Math.abs(first - second) % 360.0F;
        return diff > 180.0F ? 360.0F - diff : diff;
    }

    private static class FishingSession {
        private final Vector anchor;
        private final float yaw;
        private final float pitch;
        private final EquipmentSlot hand;
        private UUID hookId;
        private boolean autoRetrieving;
        private boolean recastScheduled;

        private FishingSession(Vector anchor, float yaw, float pitch, EquipmentSlot hand, UUID hookId) {
            this.anchor = anchor;
            this.yaw = yaw;
            this.pitch = pitch;
            this.hand = hand;
            this.hookId = hookId;
        }

        private FishHook getHook() {
            if (hookId == null)
                return null;
            if (!(Bukkit.getEntity(hookId) instanceof FishHook))
                return null;
            return (FishHook) Bukkit.getEntity(hookId);
        }
    }
}
