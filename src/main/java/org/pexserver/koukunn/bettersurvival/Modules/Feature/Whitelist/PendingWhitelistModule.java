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
    private static final String BEDROCK_UUID_PREFIX = "00000000-0000-0000-0009";

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

    public boolean isAlreadyWhitelisted(String lookupName, boolean isBedrock) {
        String normalized = sanitizeLookupName(lookupName, isBedrock);
        if (normalized.isEmpty()) {
            return false;
        }
        return plugin.getServer().getWhitelistedPlayers().stream().anyMatch(player -> {
            if (player == null || player.getName() == null || player.getUniqueId() == null) {
                return false;
            }
            if (!player.getName().equalsIgnoreCase(normalized)) {
                return false;
            }
            return isBedrockUuid(player.getUniqueId().toString()) == isBedrock;
        });
    }

    public boolean isAlreadyWhitelisted(String name) {
        String normalized = sanitizeLookupName(name, false);
        if (normalized.isEmpty()) {
            return false;
        }
        return plugin.getServer().getWhitelistedPlayers().stream().anyMatch(player -> {
            if (player == null || player.getName() == null) {
                return false;
            }
            return player.getName().equalsIgnoreCase(normalized);
        });
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
        ALREADY_WHITELISTED,
        INVALID_NAME
    }

    public enum RemoveResult {
        PENDING_ONLY,
        NOT_FOUND
    }

    private String sanitizeLookupName(String lookupName, boolean isBedrock) {
        if (lookupName == null) {
            return "";
        }
        String sanitized = lookupName.trim();
        if (sanitized.isEmpty()) {
            return "";
        }
        if (isBedrock) {
            return sanitized.replace(' ', '_');
        }
        return sanitized;
    }

    private boolean isBedrockUuid(String uuid) {
        return uuid != null && uuid.startsWith(BEDROCK_UUID_PREFIX);
    }
}
