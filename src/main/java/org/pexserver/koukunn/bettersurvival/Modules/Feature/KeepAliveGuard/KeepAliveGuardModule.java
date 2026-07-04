package org.pexserver.koukunn.bettersurvival.Modules.Feature.KeepAliveGuard;

import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerKickEvent;
import org.pexserver.koukunn.bettersurvival.Loader;
import org.pexserver.koukunn.bettersurvival.Modules.ToggleModule;

import java.util.Locale;

public class KeepAliveGuardModule implements Listener {
    public static final String FEATURE_KEY = "keepaliveguard";

    private final Loader plugin;
    private final ToggleModule toggle;

    public KeepAliveGuardModule(Loader plugin, ToggleModule toggle) {
        this.plugin = plugin;
        this.toggle = toggle;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerKick(PlayerKickEvent event) {
        if (!toggle.getGlobal(FEATURE_KEY))
            return;
        Player player = event.getPlayer();
        if (!player.isOp())
            return;
        String reason = PlainTextComponentSerializer.plainText().serialize(event.reason());
        if (!isKeepAliveTimeout(reason))
            return;
        event.setCancelled(true);
        plugin.getLogger().warning("[KeepAliveGuard] OP keepalive timeout kick をキャンセルしました: " + player.getName() + " reason=" + reason);
    }

    private boolean isKeepAliveTimeout(String reason) {
        if (reason == null)
            return false;
        String normalized = reason.toLowerCase(Locale.ROOT);
        return normalized.contains("keepalive")
                || normalized.contains("keep alive")
                || normalized.contains("timed out")
                || normalized.contains("timeout");
    }
}
