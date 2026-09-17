from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[1]


def read(rel):
    return (ROOT / rel).read_text(encoding="utf-8")


def write(rel, text):
    (ROOT / rel).write_text(text, encoding="utf-8")


def replace_once(rel, old, new):
    text = read(rel)
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{rel}: expected exactly one match, got {count}: {old[:180]!r}")
    write(rel, text.replace(old, new, 1))


def replace_count(rel, old, new, expected):
    text = read(rel)
    count = text.count(old)
    if count != expected:
        raise RuntimeError(f"{rel}: expected {expected} matches, got {count}: {old[:180]!r}")
    write(rel, text.replace(old, new))


module = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/WebMap/WebMapModule.java"
server = "src/main/java/org/pexserver/koukunn/bettersurvival/Modules/Feature/WebMap/WebMapHttpServer.java"

if "private volatile WebMapHttpSnapshot httpSnapshot" in read(module):
    print("PR #4 phase 2 already applied")
    sys.exit(0)

# ---------------------------------------------------------------------------
# WebMapModule: capture all Bukkit-backed HTTP data on the main thread.
# ---------------------------------------------------------------------------
replace_once(
    module,
    "import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;\n",
    "import org.pexserver.koukunn.bettersurvival.Core.Util.ComponentUtils;\n"
    "import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;\n",
)
replace_once(
    module,
    "    private final WebMapHttpServer httpServer;\n    private final WebMapStatusService statusService;\n",
    "    private final WebMapHttpServer httpServer;\n"
    "    private final WebMapStatusService statusService;\n"
    "    /** Immutable main-thread snapshot consumed by HTTP worker threads. */\n"
    "    private volatile WebMapHttpSnapshot httpSnapshot = WebMapHttpSnapshot.empty();\n",
)
replace_once(
    module,
    "        refreshGlobalEnabled();\n        restorePersistedMarkerSnapshots();\n",
    "        refreshGlobalEnabled();\n"
    "        refreshHttpSnapshot();\n"
    "        restorePersistedMarkerSnapshots();\n",
)
replace_once(
    module,
    "        boolean saved = store.saveSettings(settings);\n        syncRuntimeState();\n",
    "        boolean saved = store.saveSettings(settings);\n"
    "        refreshHttpSnapshot();\n"
    "        syncRuntimeState();\n",
)
replace_once(
    module,
    "            String configuredIp = plugin.getServer().getIp();\n",
    "            String configuredIp = httpSnapshot.serverIp();\n",
)
old_players = '''    public List<Map<String, Object>> getOnlinePlayersSnapshot() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Map<String, Object> row = new ConcurrentHashMap<>();
            String worldKey = player.getWorld().getKey().toString();
            String faceUrl = McApiClient.getFaceUrl(player.getUniqueId(), player.getName(), org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(player));
            row.put("name", player.getName());
            row.put("displayName", player.getName());
            row.put("uuid", player.getUniqueId().toString());
            row.put("world", player.getWorld().getName());
            row.put("worldKey", worldKey);
            row.put("x", player.getLocation().getBlockX());
            row.put("y", player.getLocation().getBlockY());
            row.put("z", player.getLocation().getBlockZ());
            row.put("yaw", player.getLocation().getYaw());
            row.put("chunkReady", dataStore.getChunk(worldKey, player.getLocation().getBlockX() >> 4, player.getLocation().getBlockZ() >> 4) != null);
            row.put("face_url", faceUrl);
            row.put("faceUrl", faceUrl);
            row.put("health", Math.round(player.getHealth()));
            row.put("armor", player.getAttribute(Attribute.ARMOR) == null ? 0 : Math.round((float) player.getAttribute(Attribute.ARMOR).getValue()));
            players.add(row);
        }
        return players;
    }
'''
new_players = '''    public WebMapHttpSnapshot getHttpSnapshot() {
        return httpSnapshot;
    }

    public List<Map<String, Object>> getOnlinePlayersSnapshot() {
        return httpSnapshot.players();
    }

    /**
     * Capture Bukkit Player/World state on the main thread. The returned rows are detached from
     * Bukkit objects before being published to HTTP worker threads.
     */
    private List<Map<String, Object>> captureOnlinePlayersSnapshot() {
        List<Map<String, Object>> players = new ArrayList<>();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Location location = player.getLocation();
            World world = location.getWorld();
            if (world == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            String worldKey = world.getKey().toString();
            String faceUrl = McApiClient.getFaceUrl(player.getUniqueId(), player.getName(),
                    org.pexserver.koukunn.bettersurvival.Core.Util.FloodgateUtil.isBedrock(player));
            row.put("name", player.getName());
            row.put("displayName", player.getName());
            row.put("uuid", player.getUniqueId().toString());
            row.put("world", world.getName());
            row.put("worldKey", worldKey);
            row.put("x", location.getBlockX());
            row.put("y", location.getBlockY());
            row.put("z", location.getBlockZ());
            row.put("yaw", location.getYaw());
            row.put("chunkReady", dataStore.getChunk(worldKey, location.getBlockX() >> 4, location.getBlockZ() >> 4) != null);
            if (faceUrl != null) {
                row.put("face_url", faceUrl);
                row.put("faceUrl", faceUrl);
            }
            row.put("health", Math.round(player.getHealth()));
            var armorAttribute = player.getAttribute(Attribute.ARMOR);
            row.put("armor", armorAttribute == null ? 0 : Math.round((float) armorAttribute.getValue()));
            players.add(row);
        }
        return players;
    }

    /** Build the complete immutable HTTP view while Bukkit API access is legal. */
    private void refreshHttpSnapshot() {
        List<WebMapHttpSnapshot.WorldView> worlds = new ArrayList<>();
        for (World world : plugin.getServer().getWorlds()) {
            WebMapDimensionSettings dimension = getDimensionSettings(world);
            String type = switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "the_end";
                default -> "normal";
            };
            worlds.add(new WebMapHttpSnapshot.WorldView(
                    world.getKey().toString(),
                    world.getName(),
                    dimension.getDisplayName(),
                    type,
                    world.getEnvironment().name(),
                    world.getSpawnLocation().getBlockX(),
                    world.getSpawnLocation().getBlockZ(),
                    dimension.isVisible(),
                    isWorldPublished(world)
            ));
        }
        String serverIp = plugin.getServer().getIp();
        httpSnapshot = new WebMapHttpSnapshot(
                worlds,
                WebMapHttpSnapshot.indexWorlds(worlds),
                captureOnlinePlayersSnapshot(),
                plugin.getServer().getMaxPlayers(),
                ServerInfoUtil.getServerName(),
                ServerInfoUtil.getServerDescription(),
                serverIp == null ? "" : serverIp,
                System.currentTimeMillis()
        );
    }
'''
replace_once(module, old_players, new_players)
replace_once(
    module,
    "            refreshGlobalEnabled();\n            syncRuntimeState();\n            updateGlobalTpsBar();\n",
    "            refreshGlobalEnabled();\n"
    "            refreshHttpSnapshot();\n"
    "            syncRuntimeState();\n"
    "            updateGlobalTpsBar();\n",
)

# ---------------------------------------------------------------------------
# WebMapHttpServer: replace every direct World/ServerInfo Bukkit read with snapshot data.
# ---------------------------------------------------------------------------
replace_once(server, "import org.bukkit.World;\n", "")
replace_once(server, "import org.pexserver.koukunn.bettersurvival.Core.Util.ServerInfoUtil;\n", "")
replace_count(server, "String serverName = ServerInfoUtil.getServerName();", "String serverName = module.getHttpSnapshot().serverName();", 2)
replace_once(server, "String motd = normalizeWhitespace(ServerInfoUtil.getServerDescription());", "String motd = normalizeWhitespace(module.getHttpSnapshot().serverDescription());")
replace_count(
    server,
    "for (World world : module.getPlugin().getServer().getWorlds()) {",
    "for (WebMapHttpSnapshot.WorldView world : module.getHttpSnapshot().worlds()) {",
    3,
)
replace_count(
    server,
    "            WebMapDimensionSettings dimension = module.getDimensionSettings(world);\n"
    "            if (!dimension.isVisible() || !module.isWorldPublished(world)) {\n",
    "            if (!world.visible() || !world.published()) {\n",
    2,
)
replace_count(server, "dimension.getDisplayName()", "world.displayName()", 2)
replace_count(server, "world.getKey().toString()", "world.key()", 6)
replace_count(server, "world.getName()", "world.name()", 10)
replace_count(server, "world.getSpawnLocation().getBlockX()", "world.spawnX()", 3)
replace_count(server, "world.getSpawnLocation().getBlockZ()", "world.spawnZ()", 3)
replace_count(
    server,
    '''switch (world.getEnvironment()) {
                case NETHER -> "nether";
                case THE_END -> "the_end";
                default -> "normal";
            }''',
    "world.type()",
    3,
)
replace_once(server, "world.getEnvironment().name()", "world.environment()")
replace_count(server, "module.getPlugin().getServer().getMaxPlayers()", "module.getHttpSnapshot().maxPlayers()", 2)
replace_count(server, "World world = resolveWorldByName(worldName);", "WebMapHttpSnapshot.WorldView world = resolveWorldByName(worldName);", 5)
replace_count(server, "world == null || !module.isWorldPublished(world)", "world == null || !world.visible() || !world.published()", 5)
replace_once(server, "        WebMapDimensionSettings dimension = module.getDimensionSettings(world);\n", "")
replace_once(
    server,
    '''    private World resolveWorldByName(String worldName) {
        for (WebMapHttpSnapshot.WorldView world : module.getHttpSnapshot().worlds()) {
            if (world.name().equals(worldName)) {
                return world;
            }
        }
        return null;
    }
''',
    '''    private WebMapHttpSnapshot.WorldView resolveWorldByName(String worldName) {
        return module.getHttpSnapshot().findWorld(worldName);
    }
''',
)
replace_once(
    server,
    '''    private String resolveWorldDisplayName(String worldKey) {
        if (worldKey == null || worldKey.isBlank()) {
            return "";
        }
        for (WebMapHttpSnapshot.WorldView world : module.getHttpSnapshot().worlds()) {
            if (world.key().equals(worldKey) || world.name().equals(worldKey)) {
                WebMapDimensionSettings dimension = module.getDimensionSettings(world);
                return dimension.getDisplayName();
            }
        }
        return "";
    }
''',
    '''    private String resolveWorldDisplayName(String worldKey) {
        WebMapHttpSnapshot.WorldView world = module.getHttpSnapshot().findWorld(worldKey);
        return world == null ? "" : world.displayName();
    }
''',
)
replace_once(
    server,
    '''        writeJson(exchange, Map.of(
                "players", module.getOnlinePlayersSnapshot(),
                "max", module.getHttpSnapshot().maxPlayers(),
                "updatedAt", System.currentTimeMillis()
        ), 1);''',
    '''        WebMapHttpSnapshot snapshot = module.getHttpSnapshot();
        writeJson(exchange, Map.of(
                "players", snapshot.players(),
                "max", snapshot.maxPlayers(),
                "updatedAt", snapshot.updatedAt()
        ), 1);''',
)
replace_once(
    server,
    '''        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("players", module.getOnlinePlayersSnapshot());
        payload.put("max", module.getHttpSnapshot().maxPlayers());''',
    '''        WebMapHttpSnapshot snapshot = module.getHttpSnapshot();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("players", snapshot.players());
        payload.put("max", snapshot.maxPlayers());''',
)

# Final static audit: no HTTP worker code may import Bukkit World, call ServerInfoUtil, or dereference Server.
http_text = read(server)
for forbidden in ("org.bukkit.World", "ServerInfoUtil", "getPlugin().getServer()", "module.getDimensionSettings(world)", "module.isWorldPublished(world)"):
    if forbidden in http_text:
        raise RuntimeError(f"{server}: HTTP worker still contains forbidden direct server access: {forbidden}")

print("PR #4 WebMap HTTP snapshot patch applied successfully")
