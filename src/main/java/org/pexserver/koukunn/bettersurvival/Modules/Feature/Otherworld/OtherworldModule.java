package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.World.Environment;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.*;

/** Manages isolated three-dimension Otherworld groups. */
public class OtherworldModule implements Listener {
    private static final String CONFIG_PATH = "Otherworld/config.json";
    private final Loader plugin;
    private final ConfigManager configManager;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> members = new LinkedHashMap<>();

    public OtherworldModule(Loader plugin) { this.plugin = plugin; this.configManager = plugin.getConfigManager(); load(); }

    public synchronized void load() {
        groups.clear(); members.clear();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        Object rawGroups = config.get("groups");
        if (rawGroups instanceof Map<?, ?> map) for (var entry : map.entrySet()) {
            if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
            String name = entry.getKey().toString().toLowerCase(Locale.ROOT);
            Map<Environment, String> worlds = new EnumMap<>(Environment.class);
            Object rawWorlds = values.get("worlds");
            if (rawWorlds instanceof Map<?, ?> worldMap) for (var world : worldMap.entrySet()) {
                try { worlds.put(Environment.valueOf(world.getKey().toString()), world.getValue().toString()); } catch (IllegalArgumentException ignored) { }
            }
            groups.put(name, new Group(name, worlds));
        }
        if (!groups.containsKey("default")) {
            Map<Environment, String> worlds = new EnumMap<>(Environment.class);
            worlds.put(Environment.NORMAL, "world"); worlds.put(Environment.NETHER, "world_nether"); worlds.put(Environment.THE_END, "world_the_end");
            groups.put("default", new Group("default", worlds));
        }
        Object rawMembers = config.get("members");
        if (rawMembers instanceof Map<?, ?> map) for (var entry : map.entrySet()) {
            Set<UUID> ids = new LinkedHashSet<>();
            if (entry.getValue() instanceof List<?> list) for (Object value : list) try { ids.add(UUID.fromString(value.toString())); } catch (IllegalArgumentException ignored) { }
            members.put(entry.getKey().toString().toLowerCase(Locale.ROOT), ids);
        }
        save();
    }

    public synchronized void save() {
        PEXConfig config = new PEXConfig(); Map<String, Object> groupData = new LinkedHashMap<>();
        for (Group group : groups.values()) { Map<String, String> worlds = new LinkedHashMap<>(); group.worlds.forEach((env, name) -> worlds.put(env.name(), name)); groupData.put(group.name, Map.of("worlds", worlds)); }
        Map<String, Object> memberData = new LinkedHashMap<>(); members.forEach((name, ids) -> memberData.put(name, ids.stream().map(UUID::toString).toList()));
        config.put("groups", groupData); config.put("members", memberData); configManager.saveConfig(CONFIG_PATH, config);
    }

    public synchronized Set<String> getGroupNames() { return Collections.unmodifiableSet(new LinkedHashSet<>(groups.keySet())); }
    public synchronized String getGroup(Player player) { return getGroup(player.getWorld()); }
    public synchronized String getGroup(World world) { for (Group group : groups.values()) if (group.worlds.containsValue(world.getName())) return group.name; return "default"; }
    public synchronized boolean sameGroup(Player first, Player second) { return getGroup(first).equals(getGroup(second)); }
    public synchronized boolean canAccess(Player player, String groupName) { Group group = groups.get(groupName.toLowerCase(Locale.ROOT)); return group != null && (group.name.equals("default") || !isWhitelistEnabled(group.name) || player.isOp() || members.getOrDefault(group.name, Set.of()).contains(player.getUniqueId())); }
    private boolean isWhitelistEnabled(String group) { return members.containsKey(group); }

    public synchronized boolean createGroup(String name) {
        name = name.toLowerCase(Locale.ROOT); if (name.isBlank() || groups.containsKey(name) || name.equals("default")) return false;
        Map<Environment, String> worlds = new EnumMap<>(Environment.class); worlds.put(Environment.NORMAL, name); worlds.put(Environment.NETHER, name + "_nether"); worlds.put(Environment.THE_END, name + "_the_end");
        Group group = new Group(name, worlds); groups.put(name, group);
        for (var entry : worlds.entrySet()) if (Bukkit.getWorld(entry.getValue()) == null) Bukkit.createWorld(new WorldCreator(entry.getValue()).environment(entry.getKey()));
        save(); return true;
    }

    public synchronized boolean move(Player player, String groupName) { groupName = groupName.toLowerCase(Locale.ROOT); Group group = groups.get(groupName); if (group == null || !canAccess(player, groupName)) return false; World world = world(group, Environment.NORMAL); return world != null && player.teleport(world.getSpawnLocation()); }
    private World world(Group group, Environment environment) { String name = group.worlds.get(environment); return name == null ? null : Bukkit.getWorld(name); }

    public synchronized boolean setWhitelist(String group, boolean enabled) { group = group.toLowerCase(Locale.ROOT); if (!groups.containsKey(group) || group.equals("default")) return false; if (enabled) members.putIfAbsent(group, new LinkedHashSet<>()); else members.remove(group); save(); return true; }
    public synchronized boolean addMember(String group, String username) { Player online = Bukkit.getPlayerExact(username); UUID id = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(username).getUniqueId(); if (!groups.containsKey(group) || !isWhitelistEnabled(group)) return false; members.get(group).add(id); save(); return true; }
    public synchronized boolean removeMember(String group, String username) { UUID id = Bukkit.getOfflinePlayer(username).getUniqueId(); Set<UUID> ids = members.get(group.toLowerCase(Locale.ROOT)); if (ids == null) return false; boolean changed = ids.remove(id); save(); return changed; }

    @EventHandler public void onJoin(PlayerJoinEvent event) { plugin.getServer().getScheduler().runTask(plugin, () -> {
        Player player = event.getPlayer();
        String current = getGroup(player);
        if (!current.equals("default") && !canAccess(player, current)) move(player, "default");
        showSelection(player);
    }); }
    private void showSelection(Player player) { List<String> accessible = groups.keySet().stream().filter(name -> canAccess(player, name)).toList(); if (accessible.size() <= 1) return; ChestUI.Builder builder = ChestUI.builder().title("Otherworld").size(Math.min(54, Math.max(9, ((accessible.size() + 8) / 9) * 9))); for (int i = 0; i < accessible.size(); i++) { String name = accessible.get(i); builder.addButtonAt(i, name, Material.GRASS_BLOCK, "移動"); } builder.then((result, p) -> { if (result.success && result.slot != null && result.slot < accessible.size()) move(p, accessible.get(result.slot)); }).show(player); }

    @EventHandler public void onPortal(PlayerPortalEvent event) {
        Location from = event.getFrom(), requested = event.getTo(); if (requested == null) return;
        Group group = groups.get(getGroup(from.getWorld())); if (group == null) return;
        Environment targetEnvironment = requested.getWorld().getEnvironment(); if (targetEnvironment == from.getWorld().getEnvironment()) return;
        World target = world(group, targetEnvironment); if (target == null || !canAccess(event.getPlayer(), group.name)) { event.setCancelled(true); return; }
        double scale = from.getWorld().getEnvironment() == Environment.NORMAL && targetEnvironment == Environment.NETHER ? 0.125 : from.getWorld().getEnvironment() == Environment.NETHER && targetEnvironment == Environment.NORMAL ? 8.0 : 1.0;
        Location destination = new Location(target, from.getX() * scale, Math.max(target.getMinHeight() + 1, Math.min(target.getMaxHeight() - 1, from.getY())), from.getZ() * scale, from.getYaw(), from.getPitch()); event.setTo(destination);
    }
    @EventHandler public void onTeleport(PlayerTeleportEvent event) { if (event.getTo() == null) return; String source = getGroup(event.getFrom().getWorld()), target = getGroup(event.getTo().getWorld()); if (!source.equals(target) && !canAccess(event.getPlayer(), target)) event.setCancelled(true); }
    private record Group(String name, Map<Environment, String> worlds) { }
}
