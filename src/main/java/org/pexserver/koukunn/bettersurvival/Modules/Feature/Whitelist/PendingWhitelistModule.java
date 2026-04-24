package org.pexserver.koukunn.bettersurvival.Modules.Feature.Whitelist;

import com.destroystokyo.paper.event.profile.ProfileWhitelistVerifyEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;

import java.util.List;

public class PendingWhitelistModule implements Listener {
    private final Plugin plugin;
    private final PendingWhitelistStore store;

    public PendingWhitelistModule(Plugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.store = new PendingWhitelistStore(configManager);
    }

    public AddResult addPending(String name) {
        if (store.contains(name)) {
            return AddResult.ALREADY_PENDING;
        }
        return store.add(name) ? AddResult.PENDING_ADDED : AddResult.INVALID_NAME;
    }

    public RemoveResult removePending(String name) {
        return store.remove(name) ? RemoveResult.PENDING_ONLY : RemoveResult.NOT_FOUND;
    }

    public List<String> getPendingNames() {
        return store.getPendingNames();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProfileWhitelistVerify(ProfileWhitelistVerifyEvent event) {
        if (!event.isWhitelistEnabled() || event.isWhitelisted()) {
            return;
        }
        String name = event.getPlayerProfile().getName();
        if (name == null || !store.contains(name)) {
            return;
        }
        event.setWhitelisted(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        String name = event.getPlayer().getName();
        if (!store.contains(name)) {
            return;
        }
        event.getPlayer().setWhitelisted(true);
        store.complete(name);
        plugin.getLogger().info("[PendingWhitelist] whitelist に追加: " + name);
    }

    public enum AddResult {
        PENDING_ADDED,
        ALREADY_PENDING,
        INVALID_NAME
    }

    public enum RemoveResult {
        PENDING_ONLY,
        NOT_FOUND
    }
}
