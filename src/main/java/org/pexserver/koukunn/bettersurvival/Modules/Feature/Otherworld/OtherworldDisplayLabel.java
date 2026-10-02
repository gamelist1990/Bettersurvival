package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;

/** Builds display labels for players, world groups, and dimensions. */
public final class OtherworldDisplayLabel {
    private OtherworldDisplayLabel() {
    }

    public static String forGroup(Loader plugin, String group) {
        String normalized = group == null || group.isBlank() ? "default" : group;
        OtherworldModule otherworld = plugin == null ? null : plugin.getOtherworldModule();
        return otherworld == null ? normalized : otherworld.displayGroupName(normalized);
    }

    public static String forWorld(Loader plugin, World world) {
        if (world == null) return "unknown";
        OtherworldModule otherworld = plugin == null ? null : plugin.getOtherworldModule();
        return otherworld == null ? world.getName() : otherworld.getWorldDisplayName(world);
    }

    public static String forPlayer(Loader plugin, Player player) {
        if (player == null) {
            return "";
        }
        String name = player.getName();
        OtherworldModule otherworld = plugin == null ? null : plugin.getOtherworldModule();
        if (otherworld == null) {
            return name;
        }
        String group = otherworld.getGroup(player);
        return "default".equalsIgnoreCase(group)
            ? name
            : name + " [" + otherworld.displayGroupName(group) + "]";
    }
}
