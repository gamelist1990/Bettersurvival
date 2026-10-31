package org.pexserver.koukunn.bettersurvival.Modules.Feature.NameTag;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Loader;

/** Persistent self-selected tags and administrative roles rendered by the PEX nametag engine. */
public final class NameTagModule implements org.bukkit.event.Listener {
    private static final String PATH = "NameTag/players.json";
    private final Loader plugin;
    private final NametagHandler handler;
    private final Map<UUID, String> tags = new HashMap<>();
    private final Map<UUID, String> roles = new HashMap<>();
    private final Map<UUID, Component> lastRendered = new HashMap<>();
    private final BukkitTask task;

    public NameTagModule(Loader plugin) {
        this.plugin = plugin;
        PEXConfig saved = plugin.getConfigManager().loadConfig(PATH).orElseGet(PEXConfig::new);
        for (var entry : saved.getData().entrySet()) {
            try {
                UUID id = UUID.fromString(entry.getKey());
                if (!(entry.getValue() instanceof Map<?, ?> data)) continue;
                if (data.get("tag") instanceof String tag) tags.put(id, new CustomTag(tag).value());
                if (data.get("role") instanceof String role && validRole(role)) roles.put(id, role);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Invalid saved nametag for " + entry.getKey());
            }
        }
        handler = new NametagHandler(plugin);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOnline, 15L, 20L);
    }

    public static boolean validRole(String role) {
        return role != null && !role.isBlank() && role.codePointCount(0, role.length()) <= 16
                && role.codePoints().noneMatch(c -> c == '§' || Character.isISOControl(c)
                || Character.getType(c) == Character.FORMAT || Character.getType(c) == Character.SURROGATE);
    }

    public boolean setTag(Player player, String input) {
        String tag = input == null ? null : new CustomTag(input).value();
        String old = tags.get(player.getUniqueId());
        put(tags, player.getUniqueId(), tag);
        if (!save()) { put(tags, player.getUniqueId(), old); return false; }
        refresh(player);
        return true;
    }

    public boolean setRole(Player player, String role) {
        if (role != null && !validRole(role)) throw new IllegalArgumentException("役職は1〜16文字で指定してください");
        String old = roles.get(player.getUniqueId());
        put(roles, player.getUniqueId(), role);
        if (!save()) { put(roles, player.getUniqueId(), old); return false; }
        refresh(player);
        return true;
    }

    private static void put(Map<UUID, String> map, UUID id, String value) {
        if (value == null) map.remove(id); else map.put(id, value);
    }

    private boolean save() {
        PEXConfig config = new PEXConfig();
        var ids = new java.util.HashSet<>(tags.keySet());
        ids.addAll(roles.keySet());
        for (UUID id : ids) {
            Map<String, String> entry = new HashMap<>();
            if (tags.containsKey(id)) entry.put("tag", tags.get(id));
            if (roles.containsKey(id)) entry.put("role", roles.get(id));
            config.put(id.toString(), entry);
        }
        return plugin.getConfigManager().saveConfig(PATH, config);
    }

    private void refreshOnline() {
        var online = Bukkit.getOnlinePlayers();
        lastRendered.keySet().removeIf(id -> Bukkit.getPlayer(id) == null);
        for (Player player : online) refresh(player);
    }

    public void refresh(Player player) {
        if (!player.isOnline()) return;
        Component name = Component.empty();
        String role = roles.get(player.getUniqueId());
        if (role == null && player.isOp()) role = "Admin";
        if (role != null) name = name.append(Component.text("[" + role + "] ", NamedTextColor.RED));
        String tag = tags.get(player.getUniqueId());
        if (tag != null) name = name.append(Component.text("[" + tag + "] ", NamedTextColor.AQUA));
        name = name.append(player.displayName());
        if (!name.equals(lastRendered.put(player.getUniqueId(), name))) handler.setCustomName(player, List.of(name));
    }

    @org.bukkit.event.EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastRendered.remove(event.getPlayer().getUniqueId());
    }

    public void shutdown() { task.cancel(); handler.shutdown(); org.bukkit.event.HandlerList.unregisterAll(this); lastRendered.clear(); }
}
