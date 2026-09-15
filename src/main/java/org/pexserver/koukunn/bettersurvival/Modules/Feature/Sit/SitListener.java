package org.pexserver.koukunn.bettersurvival.Modules.Feature.Sit;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Loader;

/** /sit が作成した一時座席を後始末する。 */
public final class SitListener implements Listener {
    private static final String TAG = "bettersurvival_sit_seat";
    private final Loader plugin;

    public SitListener(Loader plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getDismounted() instanceof ArmorStand stand && stand.getScoreboardTags().contains(TAG)) {
            Location exit = event.getEntity() instanceof Player player ? findSafeExit(player) : null;
            stand.remove();
            if (event.getEntity() instanceof Player player && exit != null) {
                exit.setYaw(player.getLocation().getYaw());
                exit.setPitch(player.getLocation().getPitch());
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (player.isOnline() && !player.isInsideVehicle()) {
                        player.teleport(exit);
                    }
                });
            }
        }
    }

    private Location findSafeExit(Player player) {
        Location start = player.getLocation().clone().add(0.0D, 1.0D, 0.0D);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                start,
                new Vector(0.0D, -1.0D, 0.0D),
                3.0D,
                FluidCollisionMode.NEVER,
                true);
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }
        Vector position = hit.getHitPosition();
        return new Location(player.getWorld(), player.getLocation().getX(), position.getY() + 0.05D,
                player.getLocation().getZ());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (event.getPlayer().getVehicle() instanceof ArmorStand stand && stand.getScoreboardTags().contains(TAG)) {
            stand.remove();
        }
    }
}
