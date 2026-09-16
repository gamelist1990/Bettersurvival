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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
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
 * Every group owns one persistent base seed shared by its Overworld/Nether/End and mirrored
 * custom dimensions. Custom dimension JSONs are duplicated during Paper bootstrap; at runtime
 * this module binds those generated dimension keys to physical worlds and routes teleports to the
 * correct group.
 */
public class OtherworldModule implements Listener {
    private static final String CONFIG_PATH = "Otherworld/config.json";
    private static final SecureRandom RANDOM = new SecureRandom();

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
                        if (custom.getValue() != null) customWorlds.put(custom.getKey().toString(), custom.getValue().toString());
                    }
                }
                long seed = readSeed(values.get("seed"), name, worlds);
                groups.put(name, new Group(name, seed, worlds, customWorlds));
            }
        }
        if (!groups.containsKey("default")) {
            Map<Environment, String> worlds = new EnumMap<>(Environment.class);
            worlds.put(Environment.NORMAL, "world");
            worlds.put(Environment.NETHER, "world_nether");
            worlds.put(Environment.THE_END, "world_the_end");
            World primary = Bukkit.getWorld("world");
            groups.put("default", new Group("default", primary == null ? 0L : primary.getSeed(), worlds, new LinkedHashMap<>()));
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

    private long readSeed(Object raw, String groupName, Map<Environment, String> worlds) {
        if (raw instanceof Number number) return number.longValue();
        if (groupName.equals("default")) {
            World primary = resolveWorldId(worlds.get(Environment.NORMAL));
            return primary == null ? 0L : primary.getSeed();
        }
        World existing = resolveWorldId(worlds.get(Environment.NORMAL));
        return existing == null ? RANDOM.nextLong() : existing.getSeed();
    }

    public synchronized void save() {
        PEXConfig config = new PEXConfig();
        Map<String, Object> groupData = new LinkedHashMap<>();
        for (Group group : groups.values()) {
            Map<String, String> worlds = new LinkedHashMap<>();
            group.worlds.forEach((env, name) -> worlds.put(env.name(), name));
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("seed", group.seed);
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

    public synchronized long getGroupSeed(String groupName) {
        Group group = groups.get(normalize(groupName));
        return group == null ? 0L : group.seed;
    }

    /** source-dimension-key -> default physical world id */
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
            if (group.worlds.values().stream().anyMatch(id -> matchesWorldId(world, id))
                    || group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id))) {
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
        Group group = new Group(name, RANDOM.nextLong(), worlds, new LinkedHashMap<>());
        groups.put(name, group);

        Group defaults = groups.get("default");
        for (var entry : worlds.entrySet()) {
            if (resolveWorldId(entry.getValue()) != null) continue;
            World source = defaults == null ? null : world(defaults, entry.getKey());
            WorldCreator creator = WorldCreator.name(entry.getValue());
            if (source != null) creator.copy(source);
            else creator.environment(entry.getKey());
            creator.seed(group.seed);
            Bukkit.createWorld(creator);
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

    public synchronized boolean deleteGroup(String name) {
        name = normalize(name);
        Group group = groups.get(name);
        if (group == null || name.equals("default")) return false;

        World fallback = world(groups.get("default"), Environment.NORMAL);
        if (fallback == null) return false;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (getGroup(player).equals(name) && !player.teleport(fallback.getSpawnLocation())) return false;
        }

        Set<String> worldIds = new LinkedHashSet<>(group.worlds.values());
        worldIds.addAll(group.customWorlds.values());
        for (String worldId : worldIds) {
            World loaded = resolveWorldId(worldId);
            if (loaded != null && !Bukkit.unloadWorld(loaded, false)) return false;
        }
        groups.remove(name);
        members.remove(name);
        save();
        for (String worldId : worldIds) deleteWorldDirectory(worldId);
        return true;
    }

    private void deleteWorldDirectory(String worldId) {
        if (worldId == null || worldId.contains(":")) return;
        Path worldPath = plugin.getServer().getWorldContainer().toPath().resolve(worldId).normalize();
        if (!worldPath.getParent().equals(plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize())) return;
        try (var paths = Files.walk(worldPath)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException exception) {
                    plugin.getLogger().warning("Otherworld deletion failed: " + path + " (" + exception.getMessage() + ")");
                }
            });
        } catch (IOException exception) {
            plugin.getLogger().warning("Otherworld deletion failed: " + worldPath + " (" + exception.getMessage() + ")");
        }
    }

    private World world(Group group, Environment environment) {
        String id = group.worlds.get(environment);
        return resolveWorldId(id);
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
            builder.addButtonAt(i, name, Material.GRASS_BLOCK, "移動\n§7Seed: §f" + groups.get(name).seed);
        }
        builder.then((result, p) -> {
            if (result.success && result.slot != null && result.slot < accessible.size()) move(p, accessible.get(result.slot));
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
        if (!sourceGroupName.equals(targetGroupName) && !canAccess(player, targetGroupName)) event.setCancelled(true);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        World loaded = event.getWorld();
        String key = loaded.getKey().toString();
        synchronized (this) {
            if (creatingMirrorKeys.remove(key)) return;
            if (isKnownPhysicalWorld(loaded)) return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> detectAndMirrorCustomDimension(loaded));
    }

    public void scanAndMirrorCustomDimensions() {
        for (World world : new java.util.ArrayList<>(Bukkit.getWorlds())) detectAndMirrorCustomDimension(world);
    }

    private synchronized void detectAndMirrorCustomDimension(World source) {
        if (source == null || isKnownPhysicalWorld(source)) return;
        String sourceKey = source.getKey().toString();
        if (OtherworldDimensionKeys.isGenerated(sourceKey)) return;

        Group defaults = groups.get("default");
        if (defaults == null) return;
        boolean changed = !sourceKey.equals(defaults.customWorlds.put(sourceKey, sourceKey));
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
            World source = resolveWorldId(entry.getValue());
            if (source != null) ensureCustomMirror(group, entry.getKey(), source);
        }
    }

    private synchronized boolean ensureCustomMirror(Group group, String sourceKey, World source) {
        NamespacedKey mirrorKey = OtherworldDimensionKeys.mirrorKey(group.name, sourceKey);
        String mirrorId = mirrorKey.toString();
        String previous = group.customWorlds.put(sourceKey, mirrorId);
        World existing = resolveWorldId(mirrorId);
        if (existing != null) return !mirrorId.equals(previous);

        creatingMirrorKeys.add(mirrorId);
        try {
            // Preferred path: the bootstrap-generated datapack defines this exact dimension key.
            World created = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).seed(group.seed));
            if (created == null) {
                // Fallback for Paper/data packs that do not expose the generated dimension stem to WorldCreator.
                created = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).copy(source).seed(group.seed));
            }
            if (created == null) {
                group.customWorlds.remove(sourceKey);
                creatingMirrorKeys.remove(mirrorId);
                plugin.getLogger().warning("Custom Dimension mirror creation failed: " + sourceKey + " -> " + mirrorId);
                return false;
            }
            plugin.getLogger().info("Otherworld Dimension mirror: " + sourceKey + " -> " + mirrorId
                    + " (group=" + group.name + ", seed=" + group.seed + ")");
            return true;
        } catch (RuntimeException first) {
            try {
                World fallback = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).copy(source).seed(group.seed));
                if (fallback != null) {
                    plugin.getLogger().warning("Dimension registry binding unavailable for " + mirrorId
                            + "; using runtime generator copy fallback: " + first.getMessage());
                    return true;
                }
            } catch (RuntimeException second) {
                first.addSuppressed(second);
            }
            group.customWorlds.remove(sourceKey);
            creatingMirrorKeys.remove(mirrorId);
            plugin.getLogger().warning("Custom Dimension mirror creation failed for " + sourceKey + " / " + group.name + ": " + first.getMessage());
            return false;
        }
    }

    private synchronized String sourceCustomKey(World world) {
        if (world == null) return null;
        Group defaults = groups.get("default");
        if (defaults == null) return null;
        for (var entry : defaults.customWorlds.entrySet()) if (matchesWorldId(world, entry.getValue())) return entry.getKey();
        for (Group group : groups.values()) {
            for (var entry : group.customWorlds.entrySet()) if (matchesWorldId(world, entry.getValue())) return entry.getKey();
        }
        if (!isKnownPhysicalWorld(world) && !OtherworldDimensionKeys.isGenerated(world.getKey().toString())) return world.getKey().toString();
        return null;
    }

    private synchronized World customWorld(Group group, String sourceKey) {
        if (group == null || sourceKey == null) return null;
        String id = group.customWorlds.get(sourceKey);
        if (id == null && group.name.equals("default")) {
            Group defaults = groups.get("default");
            id = defaults == null ? null : defaults.customWorlds.get(sourceKey);
        }
        return resolveWorldId(id);
    }

    private synchronized boolean isCustomWorldOfGroup(World world, Group group) {
        return world != null && group != null && group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id));
    }

    private synchronized boolean isKnownPhysicalWorld(World world) {
        if (world == null) return false;
        for (Group group : groups.values()) {
            if (group.worlds.values().stream().anyMatch(id -> matchesWorldId(world, id))
                    || group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id))) return true;
        }
        return false;
    }

    private World resolveWorldId(String id) {
        if (id == null || id.isBlank()) return null;
        NamespacedKey key = NamespacedKey.fromString(id);
        if (key != null) {
            World keyed = Bukkit.getWorld(key);
            if (keyed != null) return keyed;
        }
        return Bukkit.getWorld(id);
    }

    private boolean matchesWorldId(World world, String id) {
        if (world == null || id == null) return false;
        return id.equals(world.getName()) || id.equals(world.getKey().toString());
    }

    private Location copyLocation(Location source, World world) {
        return new Location(world, source.getX(),
                Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 1, source.getY())),
                source.getZ(), source.getYaw(), source.getPitch());
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).trim();
    }

    private static final class Group {
        private final String name;
        private final long seed;
        private final Map<Environment, String> worlds;
        private final Map<String, String> customWorlds;

        private Group(String name, long seed, Map<Environment, String> worlds, Map<String, String> customWorlds) {
            this.name = name;
            this.seed = seed;
            this.worlds = worlds;
            this.customWorlds = customWorlds;
        }
    }
}
