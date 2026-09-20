package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.entity.Player;
import org.pexserver.koukunn.bettersurvival.Loader;

/** Builds player-facing names without changing identity or avatar lookups. */
public final class OtherworldDisplayLabel {
    private OtherworldDisplayLabel() {
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
