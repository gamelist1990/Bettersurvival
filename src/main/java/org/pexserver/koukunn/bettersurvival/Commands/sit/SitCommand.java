package org.pexserver.koukunn.bettersurvival.Commands.sit;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.pexserver.koukunn.bettersurvival.Core.Command.BaseCommand;
import org.pexserver.koukunn.bettersurvival.Loader;

/** プレイヤーを現在地に座らせる。 */
public final class SitCommand extends BaseCommand {
    private static final double SURFACE_MARGIN = -0.56D;
    private final Loader plugin;

    public SitCommand(Loader plugin) {
        this.plugin = plugin;
    }

    @Override public String getName() { return "sit"; }
    @Override public String getDescription() { return "その場に座る、または立ち上がる"; }

    @Override
    public boolean execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sendError(sender, "プレイヤーのみ使用できます");
            return true;
        }
        if (player.isInsideVehicle()) {
            player.leaveVehicle();
            return true;
        }
        Location surface = findSurface(player);
        if (surface == null) {
            sendError(sender, "足元に座れるブロックがありません");
            return true;
        }
        Location seatLocation = surface.clone();
        seatLocation.setYaw(player.getLocation().getYaw());
        seatLocation.setPitch(0.0F);
        ArmorStand seat = player.getWorld().spawn(seatLocation, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setSmall(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setPersistent(false);
            stand.addScoreboardTag("bettersurvival_sit_seat");
        });
        if (!seat.addPassenger(player)) {
            seat.remove();
            sendError(sender, "ここには座れません");
        } else {
            correctSeatHeight(player, seat, surface.getY() + SURFACE_MARGIN, 1);
            sendSuccess(sender, "座りました。もう一度 /sit または降車キーで立ち上がれます");
        }
        return true;
    }

    private Location findSurface(Player player) {
        Location start = player.getLocation().clone().add(0.0D, 0.35D, 0.0D);
        RayTraceResult hit = player.getWorld().rayTraceBlocks(
                start,
                new Vector(0.0D, -1.0D, 0.0D),
                2.5D,
                FluidCollisionMode.NEVER,
                true);
        if (hit == null || hit.getHitBlock() == null) {
            return null;
        }
        Vector position = hit.getHitPosition();
        return new Location(player.getWorld(), player.getLocation().getX(), position.getY(), player.getLocation().getZ());
    }

    private void correctSeatHeight(Player player, ArmorStand seat, double targetPlayerY, int remainingPasses) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!seat.isValid() || !player.isValid() || player.getVehicle() != seat) {
                return;
            }
            double correction = targetPlayerY - player.getLocation().getY();
            if (Math.abs(correction) > 0.001D) {
                seat.teleport(seat.getLocation().add(0.0D, correction, 0.0D));
            }
            if (remainingPasses > 0) {
                correctSeatHeight(player, seat, targetPlayerY, remainingPasses - 1);
            }
        });
    }
}
