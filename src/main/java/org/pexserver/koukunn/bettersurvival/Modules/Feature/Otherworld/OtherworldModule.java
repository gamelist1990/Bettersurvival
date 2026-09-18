package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
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
import java.util.HashMap;
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
    private static final String SELECTION_LOBBY_WORLD = "otherworld_lobby";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final Loader plugin;
    private final ConfigManager configManager;
    private final OtherworldPlayerDataStore playerDataStore;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> members = new LinkedHashMap<>();
    private final Set<String> creatingMirrorKeys = new HashSet<>();
    private final Set<UUID> selectionTransitions = new HashSet<>();
    private final Map<UUID, GameMode> selectionLobbyGameModes = new HashMap<>();
    private String defaultJoinGroup = "default";

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
        Object configuredJoinGroup = config.get("defaultJoinGroup");
        defaultJoinGroup = normalize(configuredJoinGroup == null ? "default" : configuredJoinGroup.toString());
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
        if (!groups.containsKey(defaultJoinGroup)) defaultJoinGroup = "default";
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
        config.put("defaultJoinGroup", defaultJoinGroup);
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
        if (isSelectionLobby(world)) return "selection-lobby";
        for (Group group : groups.values()) {
            if (group.worlds.values().stream().anyMatch(id -> matchesWorldId(world, id))
                    || group.customWorlds.values().stream().anyMatch(id -> matchesWorldId(world, id))) {
                return group.name;
            }
        }
        return "default";
    }

    /** Resolve an Otherworld scope from a persisted physical world id/name without requiring a loaded World. */
    public synchronized String getGroupByWorldId(String worldId) {
        if (worldId == null || worldId.isBlank()) return "default";
        for (Group group : groups.values()) {
            if (group.worlds.values().stream().anyMatch(id -> worldId.equalsIgnoreCase(id))
                    || group.customWorlds.values().stream().anyMatch(id -> worldId.equalsIgnoreCase(id))) {
                return group.name;
            }
        }
        World loaded = resolveWorldId(worldId);
        return loaded == null ? "default" : getGroup(loaded);
    }

    public synchronized boolean sameGroup(Player first, Player second) {
        return getGroup(first).equals(getGroup(second));
    }

    public synchronized boolean canAccess(Player player, String groupName) {
        if (groupName == null) return false;
        Group group = groups.get(groupName.toLowerCase(Locale.ROOT));
        return group != null && (!isWhitelistEnabled(group.name)
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

    public synchronized String getDefaultJoinGroup() {
        return defaultJoinGroup;
    }

    public synchronized boolean setDefaultJoinGroup(String groupName) {
        String normalized = normalize(groupName);
        if (!groups.containsKey(normalized)) return false;
        defaultJoinGroup = normalized;
        save();
        return true;
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
        if (!groups.containsKey(group)) return false;
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
            if (isSelectionLobby(player.getWorld())) {
                enterSelectionLobby(player);
                List<String> accessible = accessibleGroups(player);
                if (accessible.size() > 1) showSelection(player, accessible);
                return;
            }
            if (!current.equals("default") && !canAccess(player, current)) {
                World defaultWorld = world(groups.get("default"), Environment.NORMAL);
                if (defaultWorld != null) player.teleport(defaultWorld.getSpawnLocation());
                current = "default";
            }
            if (!current.equals("default")) {
                playerDataStore.load(player, current);
                return;
            }

            List<String> accessible = accessibleGroups(player);
            if (!defaultJoinGroup.equals("default") && accessible.contains(defaultJoinGroup)) {
                move(player, defaultJoinGroup);
            } else if (accessible.size() > 1) {
                World lobby = ensureSelectionLobby();
                if (lobby != null && player.teleport(lobby.getSpawnLocation())) {
                    enterSelectionLobby(player);
                    showSelection(player, accessible);
                }
            } else if (accessible.size() == 1) {
                String onlyGroup = accessible.get(0);
                if (onlyGroup.equals("default")) {
                    playerDataStore.load(player, "default");
                } else {
                    move(player, onlyGroup);
                }
            } else {
                player.sendMessage("§cアクセス可能な Otherworld がありません。");
            }
        });
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isSelectionLobby(player.getWorld())) {
            enterSelectionLobby(player);
            return;
        }
        if (isSelectionLobby(event.getFrom())) {
            playerDataStore.load(player, getGroup(player.getWorld()));
            return;
        }
        String source = getGroup(event.getFrom());
        String target = getGroup(player.getWorld());
        if (source.equals(target)) return;
        playerDataStore.save(player, source);
        playerDataStore.ensureDefaultMigration(player);
        playerDataStore.load(player, target);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (isSelectionLobby(player.getWorld())) {
            playerDataStore.save(player, "default");
            selectionLobbyGameModes.remove(player.getUniqueId());
            return;
        }
        playerDataStore.ensureDefaultMigration(player);
        playerDataStore.save(player, getGroup(player));
    }

    private List<String> accessibleGroups(Player player) {
        return groups.keySet().stream().filter(name -> canAccess(player, name)).toList();
    }

    private void showSelection(Player player, List<String> accessible) {
        if (accessible.size() <= 1) return;
        ChestUI.Builder builder = ChestUI.builder().title("§8✦ Otherworld Select ✦").size(54);
        for (int slot = 0; slot < 54; slot++) {
            builder.addButtonAt(slot, "§r", Material.GRAY_STAINED_GLASS_PANE, "§8Otherworld selection");
        }
        int[] buttonSlots = {20, 22, 24, 29, 31, 33, 38, 40, 42};
        for (int i = 0; i < accessible.size() && i < buttonSlots.length; i++) {
            String name = accessible.get(i);
            Material icon = name.equals("default") ? Material.GRASS_BLOCK : Material.NETHER_STAR;
            builder.addButtonAt(buttonSlots[i], "§a" + name, icon,
                    "§7クリックして移動\n§8Seed: §f" + groups.get(name).seed);
        }
        builder.then((result, p) -> {
            if (!result.success || result.slot == null) return;
            for (int i = 0; i < accessible.size() && i < buttonSlots.length; i++) {
                if (buttonSlots[i] == result.slot) {
                    selectionTransitions.add(p.getUniqueId());
                    ChestUI.closeMenu(p);
                    if (move(p, accessible.get(i))) restoreSelectionGameMode(p);
                    return;
                }
            }
        }).show(player);
    }

    private World ensureSelectionLobby() {
        World existing = Bukkit.getWorld(SELECTION_LOBBY_WORLD);
        if (existing != null) return existing;
        WorldCreator creator = WorldCreator.name(SELECTION_LOBBY_WORLD)
                .generateStructures(false)
                .generator(new ChunkGenerator() {
                    @Override
                    public boolean shouldGenerateNoise() {
                        return false;
                    }

                    @Override
                    public boolean shouldGenerateSurface() {
                        return false;
                    }

                    @Override
                    public boolean shouldGenerateCaves() {
                        return false;
                    }

                    @Override
                    public boolean shouldGenerateDecorations() {
                        return false;
                    }

                    @Override
                    public boolean shouldGenerateMobs() {
                        return false;
                    }

                    @Override
                    public boolean shouldGenerateStructures() {
                        return false;
                    }
                });
        World lobby = Bukkit.createWorld(creator);
        if (lobby != null) {
            lobby.setSpawnLocation(0, 64, 0);
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    lobby.getBlockAt(x, 63, z).setType(Material.BLACK_CONCRETE);
                }
            }
            lobby.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
            lobby.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
            lobby.setTime(6000L);
        }
        return lobby;
    }

    private boolean isSelectionLobby(World world) {
        return world != null && SELECTION_LOBBY_WORLD.equals(world.getName());
    }

    private void enterSelectionLobby(Player player) {
        selectionLobbyGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode());
        if (player.getGameMode() != GameMode.SPECTATOR) player.setGameMode(GameMode.SPECTATOR);
    }

    private void restoreSelectionGameMode(Player player) {
        GameMode previous = selectionLobbyGameModes.remove(player.getUniqueId());
        if (previous != null && player.getGameMode() != previous) player.setGameMode(previous);
    }

    @EventHandler
    public void onSelectionMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isSelectionLobby(player.getWorld())) return;
        UUID playerId = player.getUniqueId();
        if (selectionTransitions.remove(playerId)) return;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isSelectionLobby(player.getWorld())) {
                showSelection(player, accessibleGroups(player));
            }
        });
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
        if (isSelectionLobby(event.getTo().getWorld()) || isSelectionLobby(event.getFrom().getWorld())) return;
        if (!sourceGroupName.equals(targetGroupName)) {
            if (!canAccess(player, targetGroupName)) {
                event.setCancelled(true);
                return;
            }
            playerDataStore.save(player, sourceGroupName);
        }
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

    public void shutdown() {
        for (Player player : List.copyOf(Bukkit.getOnlinePlayers())) {
            playerDataStore.save(player, getGroup(player));
        }
        playerDataStore.shutdown();
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
