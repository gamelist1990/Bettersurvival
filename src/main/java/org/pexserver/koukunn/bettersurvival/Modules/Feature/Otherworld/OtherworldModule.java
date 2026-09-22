package org.pexserver.koukunn.bettersurvival.Modules.Feature.Otherworld;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
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
import org.bukkit.entity.Mannequin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.pexserver.koukunn.bettersurvival.Core.Config.ConfigManager;
import org.pexserver.koukunn.bettersurvival.Core.Config.PEXConfig;
import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;
import org.pexserver.koukunn.bettersurvival.Core.Util.UI.ChestUI;
import org.pexserver.koukunn.bettersurvival.Loader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
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
    private static final ZoneId LOCK_ZONE = ZoneId.of("Asia/Tokyo");
    private static final DateTimeFormatter LOCK_INPUT_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter LOCK_DISPLAY_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月dd日 HH:mm");

    private final Loader plugin;
    private final ConfigManager configManager;
    private final OtherworldPlayerDataStore playerDataStore;
    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final Map<String, Set<UUID>> members = new LinkedHashMap<>();
    private final Set<String> whitelistEnabledGroups = new LinkedHashSet<>();
    private final Set<String> creatingMirrorKeys = new HashSet<>();
    private final Set<UUID> selectionTransitions = new HashSet<>();
    private final Set<UUID> selectionMenuBypass = new HashSet<>();
    private final Set<UUID> openSelectionMenus = new HashSet<>();
    private final Map<UUID, UUID> lockedDetailSessions = new HashMap<>();
    private final Map<UUID, GameMode> selectionLobbyGameModes = new HashMap<>();
    private final Set<UUID> lobbyEditMode = new HashSet<>();
    private final NamespacedKey lobbyItemKey;
    private final NamespacedKey lobbyNpcGroupKey;
    private final NamespacedKey lobbyFlyKey;
    private final NamespacedKey lobbyPlayersKey;
    private final Map<UUID, String> deathGroups = new HashMap<>();
    private final Map<String, LobbyRoute> lobbyRoutes = new LinkedHashMap<>();
    private String defaultJoinGroup = "default";
    private boolean alwaysLobby;
    private boolean autoMenu = true;
    private boolean lobbyFlightAllowed = true;
    private LobbyPoint lobbySpawn;

    public OtherworldModule(Loader plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerDataStore = new OtherworldPlayerDataStore(plugin);
        this.lobbyItemKey = new NamespacedKey(plugin, "otherworld_lobby_item");
        this.lobbyNpcGroupKey = new NamespacedKey(plugin, "otherworld_lobby_npc_group");
        this.lobbyFlyKey = new NamespacedKey(plugin, "otherworld_lobby_fly");
        this.lobbyPlayersKey = new NamespacedKey(plugin, "otherworld_lobby_players");
        load();
        Bukkit.getScheduler().runTask(plugin, this::scanAndMirrorCustomDimensions);
        Bukkit.getScheduler().runTaskTimer(plugin, this::refreshLobbyNpcs, 20L, 20L);
    }

    public OtherworldPlayerDataStore getPlayerDataStore() {
        return playerDataStore;
    }

    public synchronized void load() {
        groups.clear();
        members.clear();
        whitelistEnabledGroups.clear();
        lobbyRoutes.clear();
        PEXConfig config = configManager.loadConfig(CONFIG_PATH).orElseGet(PEXConfig::new);
        alwaysLobby = readBoolean(config.get("lobbyAlwaysSpawn"), false);
        autoMenu = readBoolean(config.get("lobbyAutoMenu"), true);
        lobbyFlightAllowed = readBoolean(config.get("lobbyFlightAllowed"), true);
        lobbySpawn = readLobbyPoint(config.get("lobbySpawn"));
        Object rawRoutes = config.get("lobbyRoutes");
        if (rawRoutes instanceof Map<?, ?> routes) {
            for (var entry : routes.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
                try {
                    String routeName = normalize(entry.getKey().toString());
                    MonthDay start = MonthDay.parse("--" + values.get("start"));
                    MonthDay end = MonthDay.parse("--" + values.get("end"));
                    LobbyPoint point = readLobbyPoint(values.get("location"));
                    if (!routeName.isBlank() && point != null) {
                        lobbyRoutes.put(routeName, new LobbyRoute(routeName, start, end, point));
                    }
                } catch (RuntimeException ignored) { }
            }
        }
        Object configuredJoinGroup = config.get("defaultJoinGroup");
        defaultJoinGroup = normalize(configuredJoinGroup == null ? "default" : configuredJoinGroup.toString());
        Object rawGroups = config.get("groups");
        if (rawGroups instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                if (!(entry.getValue() instanceof Map<?, ?> values)) continue;
                String name = normalize(entry.getKey().toString());
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
                        if (custom.getValue() != null && !isSelectionLobbyId(custom.getKey().toString())) {
                            customWorlds.put(custom.getKey().toString(), custom.getValue().toString());
                        }
                    }
                }
                Map<String, Long> dimensionSeeds = new LinkedHashMap<>();
                Object rawDimensionSeeds = values.get("dimensionSeeds");
                if (rawDimensionSeeds instanceof Map<?, ?> seedMap) {
                    for (var seed : seedMap.entrySet()) {
                        if (seed.getValue() instanceof Number number) {
                            dimensionSeeds.put(seed.getKey().toString(), number.longValue());
                        }
                    }
                }
                long seed = readSeed(values.get("seed"), name, worlds);
                long lockedUntil = values.get("lockedUntil") instanceof Number number
                    ? number.longValue() : 0L;
                String lockMessage = values.get("lockMessage") == null
                    ? "" : values.get("lockMessage").toString();
                groups.put(name, new Group(name, seed, worlds, customWorlds, dimensionSeeds,
                    lockedUntil, lockMessage));
            }
        }
        if (!groups.containsKey("default")) {
            Map<Environment, String> worlds = new EnumMap<>(Environment.class);
            worlds.put(Environment.NORMAL, "world");
            worlds.put(Environment.NETHER, "world_nether");
            worlds.put(Environment.THE_END, "world_the_end");
            World primary = Bukkit.getWorld("world");
                groups.put("default", new Group("default", primary == null ? 0L : primary.getSeed(), worlds,
                    new LinkedHashMap<>(), new LinkedHashMap<>(), 0L, ""));
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
                members.put(normalize(entry.getKey().toString()), ids);
            }
        }
        Object rawWhitelistEnabled = config.get("whitelistEnabled");
        if (rawWhitelistEnabled instanceof List<?> list) {
            for (Object value : list) {
                String groupName = normalize(value == null ? "" : value.toString());
                if (groups.containsKey(groupName)) {
                    whitelistEnabledGroups.add(groupName);
                    members.putIfAbsent(groupName, new LinkedHashSet<>());
                }
            }
        } else {
            whitelistEnabledGroups.addAll(members.keySet());
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
            values.put("dimensionSeeds", new LinkedHashMap<>(group.dimensionSeeds));
            values.put("lockedUntil", group.lockedUntil);
            values.put("lockMessage", group.lockMessage);
            groupData.put(group.name, values);
        }
        Map<String, Object> memberData = new LinkedHashMap<>();
        members.forEach((name, ids) -> memberData.put(name, ids.stream().map(UUID::toString).toList()));
        config.put("groups", groupData);
        config.put("members", memberData);
        config.put("whitelistEnabled", List.copyOf(whitelistEnabledGroups));
        config.put("defaultJoinGroup", defaultJoinGroup);
        config.put("lobbyAlwaysSpawn", alwaysLobby);
        config.put("lobbyAutoMenu", autoMenu);
        config.put("lobbyFlightAllowed", lobbyFlightAllowed);
        if (lobbySpawn != null) config.put("lobbySpawn", lobbySpawn.toMap());
        Map<String, Object> routeData = new LinkedHashMap<>();
        lobbyRoutes.forEach((name, route) -> routeData.put(name, route.toMap()));
        config.put("lobbyRoutes", routeData);
        configManager.saveConfig(CONFIG_PATH, config);
    }

    private boolean readBoolean(Object raw, boolean fallback) {
        return raw instanceof Boolean value ? value
                : raw == null ? fallback : Boolean.parseBoolean(raw.toString());
    }

    private LobbyPoint readLobbyPoint(Object raw) {
        if (!(raw instanceof Map<?, ?> values)) return null;
        try {
            return new LobbyPoint(
                    ((Number) values.get("x")).doubleValue(),
                    ((Number) values.get("y")).doubleValue(),
                    ((Number) values.get("z")).doubleValue(),
                    values.get("yaw") instanceof Number number ? number.floatValue() : 0.0F,
                    values.get("pitch") instanceof Number number ? number.floatValue() : 0.0F);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    public synchronized Set<String> getGroupNames() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(groups.keySet()));
    }

    public String displayGroupName(String groupName) {
        if (groupName == null || !groupName.startsWith("u_")) {
            return groupName;
        }
        String encoded = groupName.substring(2);
        if (encoded.isEmpty()) {
            return groupName;
        }
        StringBuilder decoded = new StringBuilder();
        for (String part : encoded.split("_")) {
            if (part.isEmpty()) {
                return groupName;
            }
            try {
                int codePoint = Integer.parseInt(part, 16);
                if (!Character.isValidCodePoint(codePoint)) {
                    return groupName;
                }
                decoded.appendCodePoint(codePoint);
            } catch (NumberFormatException ignored) {
                return groupName;
            }
        }
        return decoded.isEmpty() ? groupName : decoded.toString();
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

    /**
     * Returns the persistent gameplay scope. The selection lobby is temporary and
     * must not create a separate save scope for features such as leveling titles.
     */
    public synchronized String getPersistentGroup(Player player) {
        String group = getGroup(player);
        return "selection-lobby".equals(group) ? "default" : group;
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
        if (player == null || groupName == null) return false;
        Group group = groups.get(normalize(groupName));
        return group != null && (player.isOp() || !isWhitelistEnabled(group.name)
                || members.getOrDefault(group.name, Set.of()).contains(player.getUniqueId()));
    }

    public synchronized boolean isGroupLocked(String groupName) {
        Group group = groups.get(normalize(groupName));
        return group != null && group.lockedUntil > Instant.now().toEpochMilli();
    }

    public synchronized boolean canEnter(Player player, String groupName) {
        return player != null && canAccess(player, groupName) && !isGroupLocked(groupName);
    }

    public synchronized String getLockDisplay(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null || group.lockedUntil <= Instant.now().toEpochMilli()) return "§a公開中";
        String date = Instant.ofEpochMilli(group.lockedUntil).atZone(LOCK_ZONE).format(LOCK_DISPLAY_FORMAT);
        String message = group.lockMessage == null || group.lockMessage.isBlank()
                ? "オープンまでお待ちください" : group.lockMessage;
        return "§c§lLOCKED §7| §e" + date + " JST §7| §f" + message;
    }

    public synchronized String getLockMessage(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null || group.lockedUntil <= Instant.now().toEpochMilli()) return "";
        return "§c§lこのOtherworldはまだオープンしていません\n§7公開日時: §e"
                + Instant.ofEpochMilli(group.lockedUntil).atZone(LOCK_ZONE).format(LOCK_DISPLAY_FORMAT)
                + " JST\n§f" + (group.lockMessage == null || group.lockMessage.isBlank()
                ? "オープンまでお待ちください" : group.lockMessage);
    }

    public synchronized boolean setGroupLock(String groupName, String date, String time, String message) {
        Group group = groups.get(normalize(groupName));
        if (group == null || "default".equals(group.name)) return false;
        try {
            LocalDateTime opening = LocalDateTime.parse(date + " " + time, LOCK_INPUT_FORMAT);
            group.lockedUntil = opening.atZone(LOCK_ZONE).toInstant().toEpochMilli();
            group.lockMessage = message == null ? "" : message.trim();
            save();
            return true;
        } catch (DateTimeParseException exception) {
            return false;
        }
    }

    public synchronized boolean clearGroupLock(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null) return false;
        group.lockedUntil = 0L;
        group.lockMessage = "";
        save();
        return true;
    }

    private boolean isWhitelistEnabled(String group) {
        return whitelistEnabledGroups.contains(normalize(group));
    }

    public synchronized boolean createGroup(String name) {
        name = normalize(name);
        if (name.isBlank() || groups.containsKey(name) || name.equals("default")) return false;
        Map<Environment, String> worlds = new EnumMap<>(Environment.class);
        worlds.put(Environment.NORMAL, name);
        worlds.put(Environment.NETHER, name + "_nether");
        worlds.put(Environment.THE_END, name + "_the_end");
        Group group = new Group(name, RANDOM.nextLong(), worlds, new LinkedHashMap<>(), new LinkedHashMap<>(),
            0L, "");
        groups.put(name, group);

        Group defaults = groups.get("default");
        for (var entry : worlds.entrySet()) {
            if (resolveWorldId(entry.getValue()) != null) continue;
            World source = defaults == null ? null : world(defaults, entry.getKey());
            WorldCreator creator = WorldCreator.name(entry.getValue());
            if (source != null) creator.copy(source);
            else creator.environment(entry.getKey());
            creator.seed(seedFor(group, entry.getKey()));
            Bukkit.createWorld(creator);
        }
        mirrorAllKnownCustomDimensions(group);
        save();
        return true;
    }

    public synchronized boolean move(Player player, String groupName) {
        return move(player, groupName, false);
    }

    public synchronized boolean forceMove(Player player, String groupName) {
        return player != null && player.isOp() && move(player, groupName, true);
    }

    private synchronized boolean move(Player player, String groupName, boolean ignoreLock) {
        groupName = normalize(groupName);
        Group group = groups.get(groupName);
        if (group == null || !canAccess(player, groupName) || (!ignoreLock && isGroupLocked(groupName))) {
            if (group != null && canAccess(player, groupName) && isGroupLocked(groupName)) {
                player.sendMessage(getLockMessage(groupName));
            }
            return false;
        }
        World world = ensureGroupWorld(group, Environment.NORMAL);
        if ("default".equals(groupName)) {
            World defaultWorld = resolveWorldId("world");
            if (defaultWorld != null) {
                world = defaultWorld;
            }
        }
        if (world == null || isSelectionLobby(world)) {
            plugin.getLogger().warning("Otherworld move target is unavailable or is the selection lobby: " + groupName);
            return false;
        }
        World destination = world;
        Location target = destination.getSpawnLocation();
        String targetGroup = groupName;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            boolean teleported = player.teleport(target, PlayerTeleportEvent.TeleportCause.PLUGIN);
            if (!teleported || player.getWorld() != destination) {
                plugin.getLogger().warning("Otherworld move failed: group=" + targetGroup
                        + ", target=" + destination.getName() + ", teleport=" + teleported
                        + ", actual=" + player.getWorld().getName());
            }
        });
        return true;
    }

    private World ensureGroupWorld(Group group, Environment environment) {
        if (group == null) {
            return null;
        }
        String worldId = group.worlds.get(environment);
        World existing = resolveWorldId(worldId);
        if (existing != null) {
            return existing;
        }
        if (worldId == null || worldId.isBlank()) {
            return null;
        }
        try {
            WorldCreator creator = WorldCreator.name(worldId)
                    .environment(environment)
                    .seed(seedFor(group, environment));
            Group defaults = groups.get("default");
            World source = defaults == null ? null : resolveWorldId(defaults.worlds.get(environment));
            if (source != null) {
                creator.copy(source);
                creator.environment(environment);
                creator.seed(seedFor(group, environment));
            }
            World created = Bukkit.createWorld(creator);
            if (created == null) {
                plugin.getLogger().warning("Otherworld world creation returned null: " + worldId);
            }
            return created;
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Otherworld world creation failed: " + worldId + " ("
                    + exception.getMessage() + ")");
            return null;
        }
    }

    public synchronized Set<String> getDimensionSelectors(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null) return Set.of();
        LinkedHashSet<String> selectors = new LinkedHashSet<>();
        selectors.add("overworld");
        selectors.add("nether");
        selectors.add("end");
        selectors.addAll(group.customWorlds.keySet());
        return Collections.unmodifiableSet(selectors);
    }

    public synchronized boolean regenerateDimension(String groupName, String selector) {
        String normalizedGroup = normalize(groupName);
        Group group = groups.get(normalizedGroup);
        if (group == null || selector == null || selector.isBlank()) return false;

        String normalizedSelector = selector.trim().toLowerCase(Locale.ROOT);
        Environment environment = switch (normalizedSelector) {
            case "normal", "overworld" -> Environment.NORMAL;
            case "nether" -> Environment.NETHER;
            case "end", "the_end" -> Environment.THE_END;
            default -> null;
        };

        String sourceKey = null;
        String worldId;
        World source = null;
        if (environment != null) {
            worldId = group.worlds.get(environment);
        } else {
            sourceKey = resolveCustomSelector(group, selector);
            if (sourceKey == null || isSelectionLobbyId(sourceKey)) return false;
            worldId = group.customWorlds.get(sourceKey);
            Group defaults = groups.get("default");
            if (defaults != null) source = resolveWorldId(defaults.customWorlds.get(sourceKey));
        }
        if (worldId == null || worldId.isBlank() || isSelectionLobbyId(worldId)) return false;

        World target = resolveWorldId(worldId);
        if (target == null) {
            target = environment == null
                    ? customWorld(group, sourceKey)
                    : ensureGroupWorld(group, environment);
        }
        if (target == null || isSelectionLobby(target)) return false;

        World safeWorld = world(groups.get("default"), Environment.NORMAL);
        if (safeWorld == target) safeWorld = ensureSelectionLobby();
        if (safeWorld == null || safeWorld == target) return false;
        Location safeLocation = safeWorld.getSpawnLocation();
        for (Player player : List.copyOf(target.getPlayers())) {
            if (!player.teleport(safeLocation, PlayerTeleportEvent.TeleportCause.PLUGIN)) return false;
        }

        Path worldPath = target.getWorldFolder().toPath().toAbsolutePath().normalize();
        if (!Bukkit.unloadWorld(target, false)) return false;
        if (!deleteWorldPath(worldPath)) return false;

        long newSeed = RANDOM.nextLong();
        String seedKey = environment == null ? "custom:" + sourceKey : environment.name();
        group.dimensionSeeds.put(seedKey, newSeed);

        World regenerated;
        try {
            if (environment != null) {
                WorldCreator creator = WorldCreator.name(worldId).environment(environment).seed(newSeed);
                Group defaults = groups.get("default");
                World template = defaults == null ? null : resolveWorldId(defaults.worlds.get(environment));
                if (template != null && template != safeWorld) {
                    creator.copy(template).environment(environment).seed(newSeed);
                }
                regenerated = Bukkit.createWorld(creator);
            } else {
                NamespacedKey mirrorKey = NamespacedKey.fromString(worldId);
                if (mirrorKey == null || source == null) return false;
                creatingMirrorKeys.add(worldId);
                regenerated = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).copy(source).seed(newSeed));
            }
        } catch (RuntimeException exception) {
            plugin.getLogger().warning("Otherworld dimension regeneration failed: " + worldId
                    + " (" + exception.getMessage() + ")");
            return false;
        }
        if (regenerated == null) return false;
        save();
        return true;
    }

    private String resolveCustomSelector(Group group, String selector) {
        for (var entry : group.customWorlds.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(selector)
                    || entry.getValue().equalsIgnoreCase(selector)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private long seedFor(Group group, Environment environment) {
        return group.dimensionSeeds.getOrDefault(environment.name(), group.seed);
    }

    private long seedFor(Group group, String sourceKey) {
        return group.dimensionSeeds.getOrDefault("custom:" + sourceKey, group.seed);
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
        whitelistEnabledGroups.remove(name);
        save();
        for (String worldId : worldIds) deleteWorldDirectory(worldId);
        return true;
    }

    private void deleteWorldDirectory(String worldId) {
        if (worldId == null || worldId.contains(":")) return;
        Path worldContainer = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path worldPath = worldContainer.resolve(worldId).toAbsolutePath().normalize();
        Path parent = worldPath.getParent();
        if (parent == null || !parent.equals(worldContainer) || !Files.exists(worldPath)) return;
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

    private boolean deleteWorldPath(Path worldPath) {
        if (worldPath == null) return false;
        Path worldContainer = plugin.getServer().getWorldContainer().toPath().toAbsolutePath().normalize();
        Path normalized = worldPath.toAbsolutePath().normalize();
        if (!normalized.startsWith(worldContainer) || normalized.equals(worldContainer)) return false;
        if (!Files.exists(normalized)) return true;
        try (var paths = Files.walk(normalized)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new java.io.UncheckedIOException(exception);
                }
            });
            return true;
        } catch (IOException | java.io.UncheckedIOException exception) {
            plugin.getLogger().warning("Otherworld regeneration deletion failed: " + normalized
                    + " (" + exception.getMessage() + ")");
            return false;
        }
    }

    private World world(Group group, Environment environment) {
        String id = group.worlds.get(environment);
        return resolveWorldId(id);
    }

    public synchronized boolean setWhitelist(String group, boolean enabled) {
        group = normalize(group);
        if (!groups.containsKey(group)) return false;
        if (enabled) {
            whitelistEnabledGroups.add(group);
            members.putIfAbsent(group, new LinkedHashSet<>());
        } else {
            whitelistEnabledGroups.remove(group);
        }
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

    public synchronized List<String> getWhitelistMembers(String groupName) {
        Set<UUID> ids = members.get(normalize(groupName));
        if (ids == null) return List.of();
        return ids.stream()
                .map(id -> {
                    String name = Bukkit.getOfflinePlayer(id).getName();
                    return name == null || name.isBlank() ? id.toString() : name;
                })
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    public synchronized boolean hasWhitelist(String groupName) {
        return isWhitelistEnabled(groupName);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            Player player = event.getPlayer();
            playerDataStore.ensureDefaultMigration(player);
            String current = getGroup(player);
            if (isSelectionLobby(player.getWorld())) {
                enterSelectionLobby(player);
                openAutomaticSelection(player);
                return;
            }
            if (alwaysLobby) {
                sendToSelectionLobby(player);
                return;
            }
            if (!current.equals("default")) {
                if (isGroupLocked(current)) {
                    sendToSelectionLobby(player);
                    return;
                }
                if (!canEnter(player, current)) {
                    World defaultWorld = world(groups.get("default"), Environment.NORMAL);
                    if (defaultWorld != null) player.teleport(defaultWorld.getSpawnLocation());
                    current = "default";
                } else {
                    playerDataStore.load(player, current);
                    return;
                }
            }

            if (!current.equals("default")) {
                return;
            }

            List<String> accessible = accessibleGroups(player);
            if (!defaultJoinGroup.equals("default") && accessible.contains(defaultJoinGroup)
                    && !isGroupLocked(defaultJoinGroup)
                    && canEnter(player, defaultJoinGroup)) {
                move(player, defaultJoinGroup);
            } else if (accessible.size() > 1) {
                sendToSelectionLobby(player);
            } else if (accessible.size() == 1) {
                String onlyGroup = accessible.get(0);
                if (isGroupLocked(onlyGroup)) {
                    sendToSelectionLobby(player);
                } else if (onlyGroup.equals("default")) {
                    playerDataStore.load(player, "default");
                } else {
                    move(player, onlyGroup);
                }
            } else {
                player.sendMessage("§cアクセス可能な Otherworld がありません。");
            }
        });
    }

    public boolean moveToLobby(Player player) {
        if (player == null) return false;
        World lobby = ensureSelectionLobby();
        if (lobby == null) return false;
        boolean alreadyInLobby = isSelectionLobby(player.getWorld());
        boolean teleported = player.teleport(resolveLobbySpawn(lobby), PlayerTeleportEvent.TeleportCause.PLUGIN);
        if (teleported && alreadyInLobby) {
            enterSelectionLobby(player);
            openAutomaticSelection(player);
        }
        return teleported;
    }

    private void sendToSelectionLobby(Player player) {
        moveToLobby(player);
    }

    private void openAutomaticSelection(Player player) {
        if (autoMenu && !selectionMenuBypass.contains(player.getUniqueId())) {
            enterAutomaticSelectionMode(player);
            showSelection(player);
        } else {
            enterInteractiveLobbyMode(player);
        }
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isSelectionLobby(player.getWorld())) {
            String source = getGroup(event.getFrom());
            if (!"selection-lobby".equals(source)) {
                playerDataStore.save(player, source);
            }
            enterSelectionLobby(player);
            openAutomaticSelection(player);
            return;
        }
        if (isSelectionLobby(event.getFrom())) {
            showAllPlayersTo(player);
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        String groupName = getGroup(player.getWorld());
        if ("selection-lobby".equals(groupName)) {
            groupName = "default";
        }
        deathGroups.put(player.getUniqueId(), groupName);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        String deathGroupName = deathGroups.remove(player.getUniqueId());
        if (deathGroupName == null || "default".equals(deathGroupName)) {
            return;
        }

        Group deathGroup = groups.get(deathGroupName);
        if (deathGroup == null || !canEnter(player, deathGroupName)) {
            return;
        }

        Location requested = event.getRespawnLocation();
        if (requested.getWorld() != null && deathGroupName.equals(getGroup(requested.getWorld()))) {
            return;
        }

        World respawnWorld = ensureGroupWorld(deathGroup, Environment.NORMAL);
        if (respawnWorld == null || isSelectionLobby(respawnWorld)) {
            plugin.getLogger().warning("Otherworld respawn target is unavailable: " + deathGroupName);
            return;
        }

        event.setRespawnLocation(respawnWorld.getSpawnLocation());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        deathGroups.remove(player.getUniqueId());
        if (isSelectionLobby(player.getWorld())) {
            selectionLobbyGameModes.remove(player.getUniqueId());
            lobbyEditMode.remove(player.getUniqueId());
            selectionMenuBypass.remove(player.getUniqueId());
            showAllPlayersTo(player);
            return;
        }
        playerDataStore.ensureDefaultMigration(player);
        playerDataStore.save(player, getGroup(player));
    }

    private List<String> accessibleGroups(Player player) {
        return groups.keySet().stream().filter(name -> canAccess(player, name)).toList();
    }

    public List<String> getAccessibleGroupNames(Player player) {
        return List.copyOf(accessibleGroups(player));
    }

    public boolean spawnLobbyNpc(Player player, String groupName, String skinName) {
        if (player == null || !player.isOp() || !isSelectionLobby(player.getWorld())) return false;
        String normalizedGroup = normalize(groupName);
        if (!groups.containsKey(normalizedGroup) || skinName == null || !skinName.matches("[A-Za-z0-9_]{1,16}")) {
            return false;
        }
        Location location = player.getLocation();
        Mannequin mannequin = player.getWorld().spawn(location, Mannequin.class, entity -> {
            entity.setProfile(ResolvableProfile.resolvableProfile().name(skinName).build());
            entity.setImmovable(true);
            entity.setPersistent(true);
            entity.setInvulnerable(true);
            entity.setCustomNameVisible(true);
            entity.getPersistentDataContainer().set(lobbyNpcGroupKey, PersistentDataType.STRING, normalizedGroup);
        });
        updateLobbyNpc(mannequin);
        refreshLobbyNpcVisibility(mannequin);
        return true;
    }

    public synchronized boolean setLobbySetting(String setting, boolean enabled) {
        switch (setting.toLowerCase(Locale.ROOT)) {
            case "always", "always-spawn", "alwayslobby" -> alwaysLobby = enabled;
            case "automenu", "auto-menu" -> autoMenu = enabled;
            case "fly", "flight" -> {
                lobbyFlightAllowed = enabled;
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (isSelectionLobby(player.getWorld()) && !lobbyEditMode.contains(player.getUniqueId())) {
                        applyLobbyFlight(player);
                    }
                }
            }
            default -> {
                return false;
            }
        }
        save();
        return true;
    }

    public synchronized String getLobbySettingsDisplay() {
        return "always-spawn=" + alwaysLobby + ", auto-menu=" + autoMenu
                + ", fly=" + lobbyFlightAllowed;
    }

    public synchronized boolean setLobbySpawn(Player player) {
        if (player == null || !player.isOp() || !isSelectionLobby(player.getWorld())) return false;
        lobbySpawn = LobbyPoint.from(player.getLocation());
        save();
        return true;
    }

    public synchronized boolean setLobbyRoute(Player player, String name, String startText, String endText) {
        if (player == null || !player.isOp() || !isSelectionLobby(player.getWorld())) return false;
        try {
            String normalizedName = normalize(name);
            MonthDay start = MonthDay.parse("--" + startText);
            MonthDay end = MonthDay.parse("--" + endText);
            if (normalizedName.isBlank()) return false;
            lobbyRoutes.put(normalizedName,
                    new LobbyRoute(normalizedName, start, end, LobbyPoint.from(player.getLocation())));
            save();
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    public synchronized boolean removeLobbyRoute(String name) {
        if (lobbyRoutes.remove(normalize(name)) == null) return false;
        save();
        return true;
    }

    public synchronized List<String> getLobbyRouteNames() {
        return List.copyOf(lobbyRoutes.keySet());
    }

    public synchronized List<String> getLobbyRouteDescriptions() {
        return lobbyRoutes.values().stream()
                .map(route -> route.name + " (" + route.displayStart() + " - " + route.displayEnd() + ")")
                .toList();
    }

    public void showSelection(Player player) {
        showSelection(player, accessibleGroups(player));
    }

    private void showSelection(Player player, List<String> accessible) {
        openSelectionMenus.add(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder().title("§8✦ Otherworld Select ✦").size(54);
        for (int slot = 0; slot < 54; slot++) {
            builder.addButtonAt(slot, "§r", Material.GRAY_STAINED_GLASS_PANE, "§8Otherworld selection");
        }
        int[] buttonSlots = {20, 22, 24, 29, 31, 33, 38, 40, 42};
        for (int i = 0; i < accessible.size() && i < buttonSlots.length; i++) {
            String name = accessible.get(i);
            boolean locked = isGroupLocked(name);
            builder.addButtonAt(
                    buttonSlots[i],
                    locked ? "§c§l🔒 " + displayGroupName(name) : "§a" + displayGroupName(name),
                    locked ? Material.IRON_BARS
                            : name.equals("default") ? Material.GRASS_BLOCK : Material.NETHER_STAR,
                    selectionLore(player, name));
        }
        builder.addButtonAt(53,
                "§c§lサーバーから退出",
                Material.RED_BED,
                "§7クリックしてサーバーから退出します\n§8また遊びに来てください！");
        if (player.isOp()) {
            builder.addButtonAt(45,
                    "§e§lメニューを閉じる",
                    Material.OAK_DOOR,
                    "§7ホットバー式のロビーメニューへ移行します");
        }
        builder.then((result, p) -> {
            if (!result.success || result.slot == null) return;
            if (result.slot == 45 && p.isOp()) {
                selectionMenuBypass.add(p.getUniqueId());
                selectionTransitions.add(p.getUniqueId());
                ChestUI.closeMenu(p);
                enterInteractiveLobbyMode(p);
                applyLobbyHotbar(p);
                return;
            }
            if (result.slot == 53) {
                selectionTransitions.add(p.getUniqueId());
                ChestUI.closeMenu(p);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (p.isOnline()) {
                        p.kick(ComponentUtils.legacy(
                                "§cサーバーから退出しました\n§7また遊びに来てください！"));
                    }
                });
                return;
            }
            for (int i = 0; i < accessible.size() && i < buttonSlots.length; i++) {
                if (buttonSlots[i] == result.slot) {
                    selectionTransitions.add(p.getUniqueId());
                    ChestUI.closeMenu(p);
                    String selectedGroup = accessible.get(i);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        if (!p.isOnline()) {
                            return;
                        }
                        if (move(p, selectedGroup)) {
                            restoreSelectionGameMode(p);
                        } else {
                            selectionTransitions.remove(p.getUniqueId());
                            p.sendMessage("§c" + displayGroupName(selectedGroup) + " へ移動できませんでした");
                            showSelection(p, accessibleGroups(p));
                        }
                    });
                    return;
                }
            }
        }).show(player);
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isSelectionLobby(player.getWorld())
                    && player.getOpenInventory().getTopInventory().getSize() == 54) {
                player.getOpenInventory().getTopInventory().setItem(53, selectionExitItem());
                player.updateInventory();
            }
        });
        startSelectionCountdown(player, accessible, buttonSlots);
    }

    private ItemStack selectionExitItem() {
        ItemStack item = new ItemStack(Material.RED_BED);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, "§c§lサーバーから退出");
        ComponentUtils.setLore(meta,
                "§7クリックしてサーバーから退出します",
                "§8また遊びに来てください！");
        item.setItemMeta(meta);
        return item;
    }

    private String selectionLore(Player player, String groupName) {
        Group group = groups.get(groupName);
        if (!isGroupLocked(groupName)) {
            return "§7クリックして移動\n§8Seed: §f" + (group == null ? 0L : group.seed);
        }
        return "§c§lLOCKED"
                + "\n§7公開まで: §e" + getLockCountdown(groupName)
                + "\n§7公開日時: §e" + getLockDate(groupName)
                + "\n§f" + getLockReason(groupName)
                + "\n§8Seed: §f" + (group == null ? 0L : group.seed)
                + "\n§cロック解除までお待ちください";
    }

    private ItemStack selectionItem(Player player, String groupName) {
        boolean locked = isGroupLocked(groupName);
        ItemStack item = new ItemStack(locked ? Material.IRON_BARS
                : groupName.equals("default") ? Material.GRASS_BLOCK : Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, locked
                ? "§c§l🔒 " + displayGroupName(groupName)
                : "§a" + displayGroupName(groupName));
        ComponentUtils.setLore(meta, selectionLore(player, groupName).split("\n"));
        item.setItemMeta(meta);
        return item;
    }

    private synchronized String getLockCountdown(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null) return "0d 0h 0m 0s";
        long total = Math.max(0L,
                (group.lockedUntil - Instant.now().toEpochMilli() + 999L) / 1000L);
        long days = total / 86_400L;
        long hours = total % 86_400L / 3_600L;
        long minutes = total % 3_600L / 60L;
        long seconds = total % 60L;
        return days + "d " + hours + "h " + minutes + "m " + seconds + "s";
    }

    private synchronized String getLockDate(String groupName) {
        Group group = groups.get(normalize(groupName));
        if (group == null || group.lockedUntil <= 0L) return "公開中";
        return Instant.ofEpochMilli(group.lockedUntil).atZone(LOCK_ZONE)
                .format(LOCK_DISPLAY_FORMAT) + " JST";
    }

    private synchronized String getLockReason(String groupName) {
        Group group = groups.get(normalize(groupName));
        return group == null || group.lockMessage == null || group.lockMessage.isBlank()
                ? "オープンまでお待ちください" : group.lockMessage;
    }

    private void startSelectionCountdown(Player player, List<String> accessible, int[] buttonSlots) {
        new org.bukkit.scheduler.BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline() || !isSelectionLobby(player.getWorld())
                        || player.getOpenInventory().getTopInventory().getSize() != 54) {
                    cancel();
                    return;
                }
                boolean opened = false;
                for (int i = 0; i < accessible.size() && i < buttonSlots.length; i++) {
                    String groupName = accessible.get(i);
                    ItemStack previous = player.getOpenInventory().getTopInventory()
                            .getItem(buttonSlots[i]);
                    boolean wasLocked = previous != null
                            && previous.getType() == Material.IRON_BARS;
                    boolean locked = isGroupLocked(groupName);
                    player.getOpenInventory().getTopInventory().setItem(
                            buttonSlots[i], selectionItem(player, groupName));
                    if (wasLocked && !locked) opened = true;
                }
                player.getOpenInventory().getTopInventory().setItem(53, selectionExitItem());
                player.updateInventory();
                if (opened) {
                    player.sendMessage("§aOtherworldのロックが解除されました。移動先を選択できます。");
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
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
                    var floorBlock = lobby.getBlockAt(x, 63, z);
                    if (floorBlock.getType().isAir()) {
                        floorBlock.setType(Material.BLACK_CONCRETE);
                    }
                }
            }
            lobby.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false);
            lobby.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false);
            lobby.setTime(6000L);
        }
        return lobby;
    }

    private Location resolveLobbySpawn(World lobby) {
        MonthDay today = MonthDay.from(LocalDate.now(LOCK_ZONE));
        for (LobbyRoute route : lobbyRoutes.values()) {
            if (route.includes(today)) return route.location.toLocation(lobby);
        }
        return lobbySpawn == null ? lobby.getSpawnLocation() : lobbySpawn.toLocation(lobby);
    }

    private boolean isSelectionLobby(World world) {
        return world != null && SELECTION_LOBBY_WORLD.equals(world.getName());
    }

    private static boolean isSelectionLobbyId(String id) {
        if (id == null) return false;
        return SELECTION_LOBBY_WORLD.equalsIgnoreCase(id)
                || ("minecraft:" + SELECTION_LOBBY_WORLD).equalsIgnoreCase(id);
    }

    private void enterSelectionLobby(Player player) {
        selectionLobbyGameModes.putIfAbsent(player.getUniqueId(), player.getGameMode());
        lobbyEditMode.remove(player.getUniqueId());
        player.setInvulnerable(true);
        applyLobbyHotbar(player);
        enterInteractiveLobbyMode(player);
        refreshPlayerVisibility();
    }

    private void enterAutomaticSelectionMode(Player player) {
        if (!isSelectionLobby(player.getWorld()) || lobbyEditMode.contains(player.getUniqueId())) return;
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGameMode(GameMode.SPECTATOR);
    }

    private void enterInteractiveLobbyMode(Player player) {
        if (!isSelectionLobby(player.getWorld()) || lobbyEditMode.contains(player.getUniqueId())) return;
        player.setGameMode(GameMode.ADVENTURE);
        applyLobbyFlight(player);
    }

    private void restoreSelectionGameMode(Player player) {
        GameMode previous = selectionLobbyGameModes.remove(player.getUniqueId());
        if (previous != null && player.getGameMode() != previous) player.setGameMode(previous);
        lobbyEditMode.remove(player.getUniqueId());
        player.setInvulnerable(false);
        player.setFlying(false);
        player.setAllowFlight(previous == GameMode.CREATIVE || previous == GameMode.SPECTATOR);
    }

    private void applyLobbyHotbar(Player player) {
        player.getInventory().clear();
        player.getInventory().setItem(0, lobbyItem(Material.NETHER_STAR, "settings", "§b§lロビー設定",
            "§7飛行とプレイヤー表示を変更"));
        player.getInventory().setItem(4, lobbyItem(Material.COMPASS, "menu", "§a§lOtherworld メニュー",
                "§7右クリックして移動先を選択"));
        player.getInventory().setItem(8, lobbyItem(Material.RED_BED, "exit", "§c§lサーバーから退出",
                "§7右クリックして退出"));
        player.getInventory().setHeldItemSlot(4);
        player.updateInventory();
    }

    private ItemStack lobbyItem(Material material, String role, String name, String lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, name);
        ComponentUtils.setLore(meta, lore);
        meta.getPersistentDataContainer().set(lobbyItemKey, PersistentDataType.STRING, role);
        item.setItemMeta(meta);
        return item;
    }

    private String lobbyItemRole(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(lobbyItemKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (!isSelectionLobby(player.getWorld())) return;
        String role = lobbyItemRole(event.getItem());
        if (role == null) return;
        event.setCancelled(true);
        if ("menu".equals(role)) {
            showSelection(player);
        } else if ("settings".equals(role)) {
            showLobbySettings(player);
        } else if ("exit".equals(role)) {
            player.kick(ComponentUtils.legacy("§cサーバーから退出しました\n§7また遊びに来てください！"));
        }
    }

    private void showLobbySettings(Player player) {
        boolean fly = isPersonalFlightEnabled(player);
        boolean playersVisible = arePlayersVisible(player);
        ChestUI.Builder builder = ChestUI.builder().title("§8ロビー設定").size(27);
        for (int slot = 0; slot < 27; slot++) {
            builder.addButtonAt(slot, "§r", Material.GRAY_STAINED_GLASS_PANE, "§8Lobby settings");
        }
        builder.addButtonAt(11,
                fly && lobbyFlightAllowed ? "§a飛行: ON" : "§c飛行: OFF",
                fly && lobbyFlightAllowed ? Material.FEATHER : Material.HEAVY_WEIGHTED_PRESSURE_PLATE,
                lobbyFlightAllowed ? "§7クリックして個人設定を切り替え" : "§c管理者によって無効化されています");
        builder.addButtonAt(15,
                playersVisible ? "§a他のプレイヤー: 表示" : "§c他のプレイヤー: 非表示",
                playersVisible ? Material.PLAYER_HEAD : Material.CARVED_PUMPKIN,
                "§7クリックして表示を切り替え");
        if (player.isOp()) {
            builder.addButtonAt(22, "§c§lAdmin パネル", Material.COMMAND_BLOCK,
                    "§7ロビー全体設定と管理者アクセス");
        }
        builder.then((result, target) -> {
            if (!result.success || result.slot == null) return;
            if (result.slot == 11 && lobbyFlightAllowed) {
                setPersonalFlightEnabled(target, !isPersonalFlightEnabled(target));
                applyLobbyFlight(target);
                showLobbySettings(target);
            } else if (result.slot == 15) {
                setPlayersVisible(target, !arePlayersVisible(target));
                refreshPlayerVisibility();
                showLobbySettings(target);
            } else if (result.slot == 22 && target.isOp()) {
                showAdminPanel(target);
            }
        }).show(player);
    }

    private boolean isPersonalFlightEnabled(Player player) {
        Byte value = player.getPersistentDataContainer().get(lobbyFlyKey, PersistentDataType.BYTE);
        return value == null || value == 1;
    }

    private void setPersonalFlightEnabled(Player player, boolean enabled) {
        player.getPersistentDataContainer().set(lobbyFlyKey, PersistentDataType.BYTE, enabled ? (byte) 1 : (byte) 0);
    }

    private boolean arePlayersVisible(Player player) {
        Byte value = player.getPersistentDataContainer().get(lobbyPlayersKey, PersistentDataType.BYTE);
        return value == null || value == 1;
    }

    private void setPlayersVisible(Player player, boolean visible) {
        player.getPersistentDataContainer().set(lobbyPlayersKey, PersistentDataType.BYTE, visible ? (byte) 1 : (byte) 0);
    }

    private void applyLobbyFlight(Player player) {
        boolean enabled = lobbyFlightAllowed && isPersonalFlightEnabled(player);
        player.setAllowFlight(enabled);
        if (!enabled) player.setFlying(false);
    }

    private void refreshPlayerVisibility() {
        List<Player> lobbyPlayers = new java.util.ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isSelectionLobby(player.getWorld())) lobbyPlayers.add(player);
        }
        for (Player viewer : lobbyPlayers) {
            for (Player target : lobbyPlayers) {
                if (viewer == target) continue;
                if (arePlayersVisible(viewer)) viewer.showPlayer(plugin, target);
                else viewer.hidePlayer(plugin, target);
            }
        }
    }

    private void showAllPlayersTo(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (viewer != target) viewer.showPlayer(plugin, target);
        }
    }

    @EventHandler
    public void onSelectionMenuClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player) || !isSelectionLobby(player.getWorld())) return;
        if (!openSelectionMenus.remove(player.getUniqueId())) return;
        if (selectionTransitions.remove(player.getUniqueId())) return;
        if (!autoMenu || selectionMenuBypass.contains(player.getUniqueId())) {
            enterInteractiveLobbyMode(player);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline() && isSelectionLobby(player.getWorld())) {
                enterAutomaticSelectionMode(player);
                showSelection(player);
            }
        });
    }

    private void showAdminPanel(Player player) {
        if (!player.isOp()) return;
        boolean editing = lobbyEditMode.contains(player.getUniqueId());
        ChestUI.Builder builder = ChestUI.builder().title("§8Otherworld Admin").size(54);
        for (int slot = 0; slot < 54; slot++) {
            builder.addButtonAt(slot, "§r", Material.GRAY_STAINED_GLASS_PANE, "§8Admin panel");
        }
        builder.addButtonAt(4, editing ? "§c編集モードを終了" : "§a編集モードを開始",
                editing ? Material.BARRIER : Material.GOLDEN_PICKAXE,
                editing ? "§7ロビー保護モードへ戻ります" : "§7Creativeでロビーを編集します");
        builder.addButtonAt(0, alwaysLobby ? "§a常にロビー: ON" : "§c常にロビー: OFF",
            Material.ENDER_PEARL, "§7ログイン時に必ずロビーへ送ります");
        builder.addButtonAt(1, autoMenu ? "§a自動GUI: ON" : "§c自動GUI: OFF",
            Material.CHEST, "§7ロビー参加時の選択GUIを切り替えます");
        builder.addButtonAt(7, lobbyFlightAllowed ? "§aロビー飛行: ON" : "§cロビー飛行: OFF",
            Material.FEATHER, "§7全ユーザーのロビー飛行許可を切り替えます");
        builder.addButtonAt(8, "§e現在地を通常スポーンに設定", Material.RECOVERY_COMPASS,
            "§7季節ルートがない期間に使用されます");
        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
        List<String> names = List.copyOf(groups.keySet());
        for (int index = 0; index < names.size() && index < slots.length; index++) {
            String groupName = names.get(index);
            builder.addButtonAt(slots[index],
                    (isGroupLocked(groupName) ? "§c§lLOCKED §f" : "§a") + displayGroupName(groupName),
                    isGroupLocked(groupName) ? Material.IRON_BARS : Material.ENDER_EYE,
                    "§7管理者権限で移動\n" + getLockDisplay(groupName));
        }
        builder.then((result, target) -> {
            if (!result.success || result.slot == null) return;
            if (result.slot == 4) {
                ChestUI.closeMenu(target);
                toggleLobbyEditMode(target);
                return;
            }
            if (result.slot == 0) {
                setLobbySetting("always-spawn", !alwaysLobby);
                showAdminPanel(target);
                return;
            }
            if (result.slot == 1) {
                setLobbySetting("auto-menu", !autoMenu);
                showAdminPanel(target);
                return;
            }
            if (result.slot == 7) {
                setLobbySetting("fly", !lobbyFlightAllowed);
                showAdminPanel(target);
                return;
            }
            if (result.slot == 8) {
                if (setLobbySpawn(target)) target.sendMessage("§aロビーの通常スポーン地点を設定しました");
                showAdminPanel(target);
                return;
            }
            for (int index = 0; index < names.size() && index < slots.length; index++) {
                if (slots[index] != result.slot) continue;
                ChestUI.closeMenu(target);
                String groupName = names.get(index);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (forceMove(target, groupName)) restoreSelectionGameMode(target);
                    else target.sendMessage("§c" + displayGroupName(groupName) + " へ移動できませんでした");
                });
                return;
            }
        }).show(player);
    }

    private void toggleLobbyEditMode(Player player) {
        if (!player.isOp() || !isSelectionLobby(player.getWorld())) return;
        UUID playerId = player.getUniqueId();
        if (lobbyEditMode.remove(playerId)) {
            enterSelectionLobby(player);
            player.sendMessage("§aロビー保護モードへ戻りました");
            return;
        }
        lobbyEditMode.add(playerId);
        player.getInventory().clear();
        player.getInventory().setItem(4, lobbyItem(Material.NETHER_STAR, "settings", "§b§lロビー設定",
            "§7右クリックして編集モードを終了"));
        player.getInventory().setHeldItemSlot(4);
        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        player.sendMessage("§eロビー編集モードを開始しました");
    }

    private boolean isLobbyProtected(Player player) {
        return isSelectionLobby(player.getWorld()) && !lobbyEditMode.contains(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyBlockBreak(BlockBreakEvent event) {
        if (isLobbyProtected(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyBlockPlace(BlockPlaceEvent event) {
        if (isLobbyProtected(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyDrop(PlayerDropItemEvent event) {
        if (isSelectionLobby(event.getPlayer().getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player && isLobbyProtected(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && isLobbyProtected(player)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && isSelectionLobby(player.getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player && isSelectionLobby(player.getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyHunger(FoodLevelChangeEvent event) {
        if (event.getEntity() instanceof Player player && isSelectionLobby(player.getWorld())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLobbyVoidFall(PlayerMoveEvent event) {
        Location target = event.getTo();
        if (!isSelectionLobby(target.getWorld()) || target.getY() > 0.0D || event.getFrom().getY() <= 0.0D) return;
        event.setTo(resolveLobbySpawn(target.getWorld()));
        event.getPlayer().setFallDistance(0.0F);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyCommand(PlayerCommandPreprocessEvent event) {
        if (!event.getPlayer().isOp() && isSelectionLobby(event.getPlayer().getWorld())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cロビーではコマンドを使用できません");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLobbyNpcInteract(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) return;
        String groupName = mannequin.getPersistentDataContainer().get(lobbyNpcGroupKey, PersistentDataType.STRING);
        if (groupName == null) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        selectionMenuBypass.add(player.getUniqueId());
        enterInteractiveLobbyMode(player);
        if (!canAccess(player, groupName)) {
            player.hideEntity(plugin, mannequin);
            player.sendMessage("§cこのOtherworldにはアクセスできません");
            return;
        }
        showLockedGroupDetails(player, groupName);
    }

    private void showLockedGroupDetails(Player player, String groupName) {
        if (!player.isOnline() || !isSelectionLobby(player.getWorld()) || !canAccess(player, groupName)) return;
        boolean locked = isGroupLocked(groupName);
        UUID sessionId = UUID.randomUUID();
        lockedDetailSessions.put(player.getUniqueId(), sessionId);
        ChestUI.Builder builder = ChestUI.builder().title("§8" + displayGroupName(groupName) + " 詳細").size(27);
        for (int slot = 0; slot < 27; slot++) {
            builder.addButtonAt(slot, "§r", Material.GRAY_STAINED_GLASS_PANE, "§8Otherworld details");
        }
        builder.addButtonAt(13,
                locked ? "§c§l公開待ち" : "§a§l参加できます",
                locked ? Material.CLOCK : Material.LIME_DYE,
                locked
                        ? "§7公開まで: §e" + getLockCountdown(groupName)
                        + "\n§7公開日時: §e" + getLockDate(groupName)
                        + "\n§f" + getLockReason(groupName)
                        : "§7公開時刻になりました");
        builder.addButtonAt(15,
            locked && !player.isOp() ? "§c§l参加できません"
                : locked ? "§e§l管理者として参加" : "§a§l参加",
            locked && !player.isOp() ? Material.RED_DYE
                : locked ? Material.COMMAND_BLOCK : Material.LIME_DYE,
            locked && !player.isOp() ? "§7公開までお待ちください"
                : locked ? "§7ロックを無視して参加します" : "§7クリックしてOtherworldへ参加します");
        builder.addButtonAt(11, "§e戻る", Material.ARROW, "§7ロビーへ戻ります");
        builder.then((result, target) -> {
            if (!result.success || result.slot == null) return;
            if (result.slot == 11) {
                ChestUI.closeMenu(target);
                enterInteractiveLobbyMode(target);
                return;
            }
            if (result.slot != 15) return;
            if (isGroupLocked(groupName) && !target.isOp()) {
                target.sendMessage(getLockMessage(groupName));
                showLockedGroupDetails(target, groupName);
                return;
            }
            ChestUI.closeMenu(target);
            boolean moved = target.isOp() ? forceMove(target, groupName) : move(target, groupName);
            if (moved) restoreSelectionGameMode(target);
            else target.sendMessage("§c" + displayGroupName(groupName) + " へ移動できませんでした");
        }).show(player);
        startLockedGroupCountdown(player, groupName, sessionId);
    }

    private void startLockedGroupCountdown(Player player, String groupName, UUID sessionId) {
        new org.bukkit.scheduler.BukkitRunnable() {
            private boolean wasLocked = isGroupLocked(groupName);

            @Override
            public void run() {
                if (!player.isOnline() || !isSelectionLobby(player.getWorld())
                        || !sessionId.equals(lockedDetailSessions.get(player.getUniqueId()))
                        || player.getOpenInventory().getTopInventory().getSize() != 27) {
                    cancel();
                    return;
                }
                boolean locked = isGroupLocked(groupName);
                player.getOpenInventory().getTopInventory().setItem(13,
                        detailStatusItem(groupName, locked));
                player.getOpenInventory().getTopInventory().setItem(15,
                    detailJoinItem(player, locked));
                player.updateInventory();
                if (wasLocked && !locked) {
                    player.sendMessage("§a" + displayGroupName(groupName) + " が公開されました。参加できます。");
                }
                wasLocked = locked;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private ItemStack detailStatusItem(String groupName, boolean locked) {
        ItemStack item = new ItemStack(locked ? Material.CLOCK : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, locked ? "§c§l公開待ち" : "§a§l参加できます");
        ComponentUtils.setLore(meta, (locked
                ? "§7公開まで: §e" + getLockCountdown(groupName)
                + "\n§7公開日時: §e" + getLockDate(groupName)
                + "\n§f" + getLockReason(groupName)
                : "§7公開時刻になりました").split("\n"));
        item.setItemMeta(meta);
        return item;
    }

        private ItemStack detailJoinItem(Player player, boolean locked) {
        ItemStack item = new ItemStack(locked && !player.isOp() ? Material.RED_DYE
            : locked ? Material.COMMAND_BLOCK : Material.LIME_DYE);
        ItemMeta meta = item.getItemMeta();
        ComponentUtils.setDisplayName(meta, locked && !player.isOp() ? "§c§l参加できません"
            : locked ? "§e§l管理者として参加" : "§a§l参加");
        ComponentUtils.setLore(meta, locked && !player.isOp() ? "§7公開までお待ちください"
            : locked ? "§7ロックを無視して参加します" : "§7クリックしてOtherworldへ参加します");
        item.setItemMeta(meta);
        return item;
    }

    private void refreshLobbyNpcs() {
        World lobby = Bukkit.getWorld(SELECTION_LOBBY_WORLD);
        if (lobby == null) return;
        for (Mannequin mannequin : lobby.getEntitiesByClass(Mannequin.class)) {
            if (!mannequin.getPersistentDataContainer().has(lobbyNpcGroupKey, PersistentDataType.STRING)) continue;
            updateLobbyNpc(mannequin);
            refreshLobbyNpcVisibility(mannequin);
        }
    }

    private void updateLobbyNpc(Mannequin mannequin) {
        String groupName = mannequin.getPersistentDataContainer().get(lobbyNpcGroupKey, PersistentDataType.STRING);
        if (groupName == null) return;
        boolean locked = isGroupLocked(groupName);
        mannequin.customName(ComponentUtils.legacy((locked ? "§c§lLOCKED §f" : "§a§l")
                + displayGroupName(groupName)));
        mannequin.setDescription(ComponentUtils.legacy(locked
            ? "§c" + getLockCountdown(groupName) + " §7| クリックして参加"
            : "§eクリックして参加"));
    }

    private void refreshLobbyNpcVisibility(Mannequin mannequin) {
        String groupName = mannequin.getPersistentDataContainer().get(lobbyNpcGroupKey, PersistentDataType.STRING);
        if (groupName == null) return;
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (canAccess(viewer, groupName)) viewer.showEntity(plugin, mannequin);
            else viewer.hideEntity(plugin, mannequin);
        }
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
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.END_PORTAL) {
            event.setTo(copyLocation(requested, target));
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
            if (!canEnter(player, targetGroupName)) {
                event.setCancelled(true);
                if (canAccess(player, targetGroupName) && isGroupLocked(targetGroupName)) {
                    player.sendMessage(getLockMessage(targetGroupName));
                }
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
        if (OtherworldDimensionKeys.isGenerated(sourceKey)
            || isSelectionLobby(source)
            || isSelectionLobbyId(sourceKey)) return;

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
            if (isSelectionLobbyId(entry.getKey())) continue;
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
        long dimensionSeed = seedFor(group, sourceKey);
        try {
            // Preferred path: the bootstrap-generated datapack defines this exact dimension key.
            World created = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).seed(dimensionSeed));
            if (created == null) {
                // Fallback for Paper/data packs that do not expose the generated dimension stem to WorldCreator.
                created = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).copy(source).seed(dimensionSeed));
            }
            if (created == null) {
                group.customWorlds.remove(sourceKey);
                creatingMirrorKeys.remove(mirrorId);
                plugin.getLogger().warning("Custom Dimension mirror creation failed: " + sourceKey + " -> " + mirrorId);
                return false;
            }
            plugin.getLogger().info("Otherworld Dimension mirror: " + sourceKey + " -> " + mirrorId
                    + " (group=" + group.name + ", seed=" + dimensionSeed + ")");
            return true;
        } catch (RuntimeException first) {
            try {
                World fallback = Bukkit.createWorld(WorldCreator.ofKey(mirrorKey).copy(source).seed(dimensionSeed));
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
            if (!isSelectionLobby(player.getWorld())) {
                playerDataStore.save(player, getGroup(player));
            }
        }
        playerDataStore.shutdown();
    }

    private String normalize(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        if (lower.codePoints().allMatch(OtherworldModule::isWorldNameCharacter)) {
            return lower;
        }

        StringBuilder encoded = new StringBuilder("u");
        lower.codePoints().forEach(codePoint -> encoded
                .append('_')
                .append(Integer.toHexString(codePoint)));
        return encoded.toString();
    }

    private static boolean isWorldNameCharacter(int codePoint) {
        return codePoint >= 'a' && codePoint <= 'z'
                || codePoint >= '0' && codePoint <= '9'
                || codePoint == '_' || codePoint == '-' || codePoint == '.' || codePoint == '/';
    }

    private static final class Group {
        private final String name;
        private final long seed;
        private final Map<Environment, String> worlds;
        private final Map<String, String> customWorlds;
        private final Map<String, Long> dimensionSeeds;
        private long lockedUntil;
        private String lockMessage;

        private Group(String name, long seed, Map<Environment, String> worlds, Map<String, String> customWorlds,
                      Map<String, Long> dimensionSeeds, long lockedUntil, String lockMessage) {
            this.name = name;
            this.seed = seed;
            this.worlds = worlds;
            this.customWorlds = customWorlds;
            this.dimensionSeeds = dimensionSeeds;
            this.lockedUntil = lockedUntil;
            this.lockMessage = lockMessage;
        }
    }

    private record LobbyPoint(double x, double y, double z, float yaw, float pitch) {
        private static LobbyPoint from(Location location) {
            return new LobbyPoint(location.getX(), location.getY(), location.getZ(),
                    location.getYaw(), location.getPitch());
        }

        private Location toLocation(World world) {
            return new Location(world, x, y, z, yaw, pitch);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("x", x);
            values.put("y", y);
            values.put("z", z);
            values.put("yaw", yaw);
            values.put("pitch", pitch);
            return values;
        }
    }

    private record LobbyRoute(String name, MonthDay start, MonthDay end, LobbyPoint location) {
        private boolean includes(MonthDay date) {
            if (start.compareTo(end) <= 0) {
                return date.compareTo(start) >= 0 && date.compareTo(end) <= 0;
            }
            return date.compareTo(start) >= 0 || date.compareTo(end) <= 0;
        }

        private Map<String, Object> toMap() {
            Map<String, Object> values = new LinkedHashMap<>();
            values.put("start", String.format(Locale.ROOT, "%02d-%02d", start.getMonthValue(), start.getDayOfMonth()));
            values.put("end", String.format(Locale.ROOT, "%02d-%02d", end.getMonthValue(), end.getDayOfMonth()));
            values.put("location", location.toMap());
            return values;
        }

        private String displayStart() {
            return String.format(Locale.ROOT, "%02d-%02d", start.getMonthValue(), start.getDayOfMonth());
        }

        private String displayEnd() {
            return String.format(Locale.ROOT, "%02d-%02d", end.getMonthValue(), end.getDayOfMonth());
        }
    }
}
