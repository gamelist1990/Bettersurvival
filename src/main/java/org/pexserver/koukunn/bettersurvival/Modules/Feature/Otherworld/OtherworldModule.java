package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Manages isolated Otherworld groups.
 *
 * Besides the vanilla NORMAL/NETHER/THE_END worlds, every extra loaded world/dimension is
 * automatically treated as a custom dimension template. Each non-default Otherworld group gets
 * its own physical mirror of that template, so chunks/entities/blocks never share state between
 * groups.
 */
public class OtherworldModule implements Listener {
    private static final String CONFIG_PATH = "Otherworld/config.json";
    private static final String MIRROR_PREFIX = "owdim_";

    private final Loader plugin;
    private final ConfigManager configManager;
    private final OtherworldPlayerDataStore playerDataStore;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> members = new LinkedHashMap<>();
    private final Set<String> creatingMirrorKeys = new HashSet<>();

    public OtherworldModule(Loader plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerDataStore = new OtherworldPlayerDataStore(plugin);
        load();
        Bukkit.getScheduler().runTask(plugin, this::scanAndMirrorCustomDimensions);
    }

    public OtherworldPlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }

    public synchronized void load() {
        groups.clear();
        members.clear();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        Object rawGroups = config.get("groups");
        if (rawGroups instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
                String name = entry.getKey().toString().toLowerCase(Locale.ROOT);
                Map<Environment, String> worlds = new EnumMap<>(Environment.class);
                Object rawWorlds = values.get("worlds");
                if (rawWorlds instanceof Map<?, ?> worldMap) {
                    for (var world : worldMap.entrySet()) {
                        try {
                            worlds.put(Environment.valueOf(world.getKey().toString()), world.getValue().toString());
                        } catch (IllegalArgumentException ignored) { }
                    }
                }
                Map<String, String> customWorlds = new LinkedHashMap<>();
                Object rawCustom = values.get("customWorlds");
                if (rawCustom instanceof Map<?, ?> customMap) {
                    for (var custom : customMap.entrySet()) {
                        if (custom.getValue() != null) {
                            customWorlds.put(custom.getKey().toString(), custom.getValue().toString());
                        }
                    }
                }
                groups.put(name, new Group(name, worlds, customWorlds));
            }
        }
        if (!groups.containsKey("default")) {
            Map<Environment, String> worlds = new EnumMap<>(Environment.class);
            worlds.put(Environment.NORMAL, "world");
            worlds.put(Environment.NETHER, "world_nether");
            worlds.put(Environment.THE_END, "world_the_end");
            groups.put("default", new Group("default", worlds, new LinkedHashMap<>()));
        }
        Object rawMembers = config.get("members");
        if (rawMembers instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                Set<UUID> ids = new LinkedHashSet<>();
                if (entry.getValue() instanceof List<?> list) {
                    for (Object value : list) {
                        try { ids.add(UUID.fromString(value.toString())); } catch (IllegalArgumentException ignored) { }
                    }
                }
                members.put(entry.getKey().toString().toLowerCase(Locale.ROOT), ids);
            }
        }
        save();
    }

    public synchronized void save() {
        PEXConfig config = new PEXConfig();
        Map<String, Object> groupData = new LinkedHashMap<>();
        for (Group group : groups.values()) {
            Map<String, String> worlds = new LinkedHashMap<>();
            group.worlds.forEach((env, name) -> worlds.put(env.name(), name));
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("worlds", worlds);
            values.put("customWorlds", new LinkedHashMap<>(group.customWorlds));
            groupData.put(group.name, values);
        }
        Map<String, Object> memberData = new LinkedHashMap<>();
        members.forEach((name, ids) -> memberData.put(name, ids.stream().map(UUID::toString).toList()));
        config.put("groups", groupData);
        config.put("members", memberData);
        configManager.saveConfig(CONFIG_PATH, config);
    }

    public synchronized Set<String> getGroupNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groups.keySet()));
    }

    /** source-dimension-key -> default physical world name */
    public synchronized Map<String, String> getDetectedCustomDimensions() {
        Group group = groups.get("default");
        return group == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(group.customWorlds));
    }

    public synchronized Map<String, String> getCustomDimensions(String groupName) {
        Group group = groups.get(normalize(groupName));
        return group == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(group.customWorlds));
    }

    public synchronized String getGroup(Player player) {
        return player == null ? "default" : getGroup(player.getWorld());
    }

    public synchronized String getGroup(World world) {
        if (world == null) return "default";
        for (Group group : groups.values()) {
            if (group.worlds.containsValue(world.getName()) || group.customWorlds.containsValue(world.getName())) {
                return group.name;
            }
        }
        return "default";
    }

    public synchronized boolean sameGroup(Player first, Player second) {
        return getGroup(first).equals(getGroup(second));
    }

    public synchronized boolean canAccess(Player player, String groupName) {
        if (groupName == null) return false;
        Group group = groups.get(groupName.toLowerCase(Locale.ROOT));
        return group != null && (group.name.equals("default") || !isWhitelistEnabled(group.name)
                || player.isOp() || members.getOrDefault(group.name, Set.of()).contains(player.getUniqueId()));
    }

    private boolean isWhitelistEnabled(String group) {
        return members.containsKey(group);
    }

    public synchronized boolean createGroup(String name) {
        name = normalize(name);
        if (name.isBlank() || groups.containsKey(name) || name.equals("default")) return false;
        Map<Environment, String> worlds = new EnumMap<>(Environment.class);
        worlds.put(Environment.NORMAL, name);
        worlds.put(Environment.NETHER, name + "_nether");
        worlds.put(Environment.THE_END, name + "_the_end");
        Group group = new Group(name, worlds, new LinkedHashMap<>());
        groups.put(name, group);
        for (var entry : worlds.entrySet()) {
            if (Bukkit.getWorld(entry.getValue()) == null) {
                Bukkit.createWorld(new WorldCreator(entry.getValue()).environment(entry.getKey()));
            }
        }
        mirrorAllKnownCustomDimensions(group);
        save();
        return true;
    }

    public synchronized boolean move(Player player, String groupName) {
        groupName = normalize(groupName);
        Group group = groups.get(groupName);
        if (group == null || !canAccess(player, groupName)) return false;
        World world = world(group, Environment.NORMAL);
        return world != null && player.teleport(world.getSpawnLocation());
    }

    private World world(Group group, Environment environment) {
        String name = group.worlds.get(environment);
        return name == null ? null : Bukkit.getWorld(name);
    }

    public synchronized boolean setWhitelist(String group, boolean enabled) {
        group = normalize(group);
        if (!groups.containsKey(group) || group.equals("default")) return false;
        if (enabled) members.putIfAbsent(group, new LinkedHashSet<>());
        else members.remove(group);
        save();
        return true;
    }

    public synchronized boolean addMember(String group, String username) {
        group = normalize(group);
        Player online = Bukkit.getPlayerExact(username);
        UUID id = online != null ? online.getUniqueId() : Bukkit.getOfflinePlayer(username).getUniqueId();
        if (!groups.containsKey(group) || !isWhitelistEnabled(group)) return false;
        members.get(group).add(id);
        save();
        return true;
    }

    public synchronized boolean removeMember(String group, String username) {
        UUID id = Bukkit.getOfflinePlayer(username).getUniqueId();
        Set<UUID> ids = members.get(normalize(group));
        if (ids == null) return false;
        boolean changed = ids.remove(id);
        save();
        return changed;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            playerDataStore.ensureDefaultMigration(player);
            String current = getGroup(player);
            if (!current.equals("default") && !canAccess(player, current)) {
                move(player, "default");
            } else {
                playerDataStore.load(player, current);
                showSelection(player);
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        String source = getGroup(event.getFrom());
        String target = getGroup(player.getWorld());
        if (source.equals(target)) return;
        playerDataStore.ensureDefaultMigration(player);
        playerDataStore.save(player, source);
        playerDataStore.load(player, target);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        playerDataStore.ensureDefaultMigration(player);
        playerDataStore.save(player, getGroup(player));
    }

    private void showSelection(Player player) {
        List<String> accessible = groups.keySet().stream().filter(name -> canAccess(player, name)).toList();
        if (accessible.size() <= 1) return;
        ChestUI.Builder builder = ChestUI.builder().title("Otherworld")
                .size(Math.min(54, Math.max(9, ((accessible.size() + 8) / 9) * 9)));
        for (int i = 0; i < accessible.size(); i++) {
            String name = accessible.get(i);
            builder.addButtonAt(i, name, Material.GRASS_BLOCK, "移動");
        }
        builder.then((result, p) -> {
            if (result.success && result.slot != null && result.slot < accessible.size()) {
                move(p, accessible.get(result.slot));
            }
        }).show(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        Location from = event.getFrom();
        Location requested = event.getTo();
        if (requested == null || requested.getWorld() == null) return;
        String sourceGroupName = getGroup(from.getWorld());
        Group sourceGroup = groups.get(sourceGroupName);
        if (sourceGroup == null) return;

        String customSourceKey = sourceCustomKey(requested.getWorld());
        if (customSourceKey != null) {
            World mirror = customWorld(sourceGroup, customSourceKey);
            if (mirror == null || !canAccess(event.getPlayer(), sourceGroup.name)) {
                event.setCancelled(true);
                return;
            }
            event.setTo(copyLocation(requested, mirror));
            return;
        }

        Environment targetEnvironment = requested.getWorld().getEnvironment();
        if (targetEnvironment == from.getWorld().getEnvironment()) return;
        World target = world(sourceGroup, targetEnvironment);
        if (target == null || !canAccess(event.getPlayer(), sourceGroup.name)) {
            event.setCancelled(true);
            return;
        }
        double scale = from.getWorld().getEnvironment() == Environment.NORMAL && targetEnvironment == Environment.NETHER
                ? 0.125D
                : from.getWorld().getEnvironment() == Environment.NETHER && targetEnvironment == Environment.NORMAL ? 8.0D : 1.0D;
        event.setTo(new Location(target, from.getX() * scale,
                Math.max(target.getMinHeight() + 1, Math.min(target.getMaxHeight() - 1, from.getY())),
                from.getZ() * scale, from.getYaw(), from.getPitch()));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location targetLocation = event.getTo();
        if (targetLocation == null || targetLocation.getWorld() == null) return;
        Player player = event.getPlayer();
        String sourceGroupName = getGroup(event.getFrom().getWorld());
        Group sourceGroup = groups.get(sourceGroupName);
        if (sourceGroup == null) return;

        World requestedWorld = targetLocation.getWorld();
        String customSourceKey = sourceCustomKey(requestedWorld);
        if (customSourceKey != null) {
            World desired = customWorld(sourceGroup, customSourceKey);
            if (desired != null && desired != requestedWorld) {
                event.setTo(copyLocation(targetLocation, desired));
                return;
            }
        }

        // A custom dimension's return portal usually points at the default vanilla dimension.
        // Redirect it to the same vanilla environment inside the current Otherworld group.
        if (!sourceGroupName.equals("default") && isCustomWorldOfGroup(event.getFrom().getWorld(), sourceGroup)) {
            String requestedGroup = getGroup(requestedWorld);
            if (requestedGroup.equals("default")) {
                World counterpart = world(sourceGroup, requestedWorld.getEnvironment());
                if (counterpart != null) {
                    event.setTo(copyLocation(targetLocation, counterpart));
                    return;
                }
            }
        }

        String targetGroupName = getGroup(event.getTo().getWorld());
        if (!sourceGroupName.equals(targetGroupName) && !canAccess(player, targetGroupName)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World loaded = event.getWorld();
        String key = loaded.getKey().toString();
        synchronized (this) {
            if (creatingMirrorKeys.remove(key)) return;
            if (isKnownPhysicalWorld(loaded.getName())) return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> detectAndMirrorCustomDimension(loaded));
    }

    public void scanAndMirrorCustomDimensions() {
        for (World world : new java.util.ArrayList<>(Bukkit.getWorlds())) {
            detectAndMirrorCustomDimension(world);
        }
    }

    private synchronized void detectAndMirrorCustomDimension(World source) {
        if (source == null || isKnownPhysicalWorld(source.getName())) return;
        String sourceKey = source.getKey().toString();
        if (isGeneratedMirrorKey(sourceKey)) return;

        Group defaults = groups.get("default");
        if (defaults == null) return;
        boolean changed = !source.getName().equals(defaults.customWorlds.put(sourceKey, source.getName()));
        for (Group group : groups.values()) {
            if (group.name.equals("default")) continue;
            changed |= ensureCustomMirror(group, sourceKey, source);
        }
        if (changed) save();
    }

    private synchronized void mirrorAllKnownCustomDimensions(Group group) {
        Group defaults = groups.get("default");
        if (defaults == null) return;
        for (var entry : defaults.customWorlds.entrySet()) {
            World source = Bukkit.getWorld(entry.getValue());
            if (source != null) ensureCustomMirror(group, entry.getKey(), source);
        }
    }

    private synchronized boolean ensureCustomMirror(Group group, String sourceKey, World source) {
        String existingName = group.customWorlds.get(sourceKey);
        if (existingName != null && Bukkit.getWorld(existingName) != null) return false;

        NamespacedKey mirrorKey = mirrorKey(group.name, sourceKey);
        String mirrorName = mirrorKey.getKey();
        group.customWorlds.put(sourceKey, mirrorName);
        World existing = Bukkit.getWorld(mirrorKey);
        if (existing != null) return true;

        creatingMirrorKeys.add(mirrorKey.toString());
        try {
            WorldCreator creator = WorldCreator.ofKey(mirrorKey).copy(source).seed(source.getSeed());
            World created = Bukkit.createWorld(creator);
            if (created == null) {
                group.customWorlds.remove(sourceKey);
                creatingMirrorKeys.remove(mirrorKey.toString());
                plugin.getLogger().warning("Custom Dimension mirror creation failed: " + sourceKey + " -> " + mirrorKey);
                return false;
            }
            plugin.getLogger().info("Otherworld Custom Dimension mirror: " + sourceKey + " -> " + mirrorKey + " (group=" + group.name + ")");
            return true;
        } catch (RuntimeException ex) {
            group.customWorlds.remove(sourceKey);
            creatingMirrorKeys.remove(mirrorKey.toString());
            plugin.getLogger().warning("Custom Dimension mirror creation failed for " + sourceKey + " / " + group.name + ": " + ex.getMessage());
            return false;
        }
    }

    private synchronized String sourceCustomKey(World world) {
        if (world == null) return null;
        Group defaults = groups.get("default");
        if (defaults == null) return null;
        for (var entry : defaults.customWorlds.entrySet()) {
            if (entry.getValue().equals(world.getName())) return entry.getKey();
        }
        for (Group group : groups.values()) {
            for (var entry : group.customWorlds.entrySet()) {
                if (entry.getValue().equals(world.getName())) return entry.getKey();
            }
        }
        if (!isKnownPhysicalWorld(world.getName()) && !isGeneratedMirrorKey(world.getKey().toString())) {
            return world.getKey().toString();
        }
        return null;
    }

    private synchronized World customWorld(Group group, String sourceKey) {
        if (group == null || sourceKey == null) return null;
        String name = group.customWorlds.get(sourceKey);
        if (name == null && group.name.equals("default")) {
            Group defaults = groups.get("default");
            name = defaults == null ? null : defaults.customWorlds.get(sourceKey);
        }
        return name == null ? null : Bukkit.getWorld(name);
    }

    private synchronized boolean isCustomWorldOfGroup(World world, Group group) {
        return world != null && group != null && group.customWorlds.containsValue(world.getName());
    }

    private synchronized boolean isKnownPhysicalWorld(String worldName) {
        for (Group group : groups.values()) {
            if (group.worlds.containsValue(worldName) || group.customWorlds.containsValue(worldName)) return true;
        }
        return false;
    }

    private NamespacedKey mirrorKey(String groupName, String sourceKey) {
        String path = MIRROR_PREFIX + safe(groupName) + "_" + safe(sourceKey);
        return new NamespacedKey(plugin, path);
    }

    private boolean isGeneratedMirrorKey(String key) {
        if (key == null) return false;
        String prefix = plugin.getName().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_") + ":" + MIRROR_PREFIX;
        return key.toLowerCase(Locale.ROOT).startsWith(prefix);
    }

    private Location copyLocation(Location source, World world) {
        return new Location(world, source.getX(),
                Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 1, source.getY())),
                source.getZ(), source.getYaw(), source.getPitch());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private String safe(String value) {
        String safe = value == null ? "unknown" : value.toLowerCase(Locale.ROOT).replace(':', '_').replace('/', '_');
        safe = safe.replaceAll("[^a-z0-9._-]", "_");
        if (safe.length() > 80) safe = safe.substring(0, 80);
        return safe;
    }

    private static final class Group {
        private final String name;
        private final Map<Environment, String> worlds;
        private final Map<String, String> customWorlds;

        private Group(String name, Map<Environment, String> worlds, Map<String, String> customWorlds) {
            this.name = name;
            this.worlds = worlds;
            this.customWorlds = customWorlds;
        }
    }
}
