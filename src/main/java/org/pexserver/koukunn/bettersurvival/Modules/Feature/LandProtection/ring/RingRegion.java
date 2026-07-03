package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 土地保護内に設置できる闘技場「リング」。
 * pos1 / pos2 で選択した矩形領域で、内部では PVP が有効になる。
 * 名前付きで 1 つの保護領域に複数設置できる。
 * KeepInventory・即時復活・復活地点・Duel モードなどの設定を保持する。
 */
public class RingRegion {

    /** 復活地点として設定できるリング境界からの最大距離 */
    public static final int RESPAWN_MAX_DISTANCE = 50;
    /** Duel 開始時に確保する最低距離 */
    public static final int DUEL_MIN_DISTANCE = 10;
    /** リング一辺の最小サイズ */
    public static final int MIN_SIDE = 4;
    /** リングの縦方向の余裕（床下・上空） */
    public static final int VERTICAL_BELOW = 2;
    public static final int VERTICAL_ABOVE = 12;

    /** Duel の開始位置の決め方 */
    public enum StartMode {
        RANDOM("§eランダム", "リング内のランダムな地点から開始します"),
        FREE("§a自由", "位置の移動は行いません（そのまま開始）"),
        CUSTOM("§bカスタム", "設定した開始位置 1 / 2 から向かい合って開始します");

        private final String displayName;
        private final String description;

        StartMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public StartMode next() {
            StartMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    /** 決着時にリング内へ残ったアイテム・設置ブロックの処理方法 */
    public enum CleanupMode {
        DELETE("§c消去", "決着時にリング内のアイテムと設置ブロックを消し去ります"),
        RETURN_CHEST("§a返却", "指定したラージチェストへ全て入れます");

        private final String displayName;
        private final String description;

        CleanupMode(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getDescription() {
            return description;
        }

        public CleanupMode next() {
            CleanupMode[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    private final String claimKey;
    private final String name;
    private final String worldName;
    private int minX;
    private int minY;
    private int minZ;
    private int maxX;
    private int maxY;
    private int maxZ;

    private boolean keepInventory = true;
    private boolean instantRespawn = true;
    /** Duel モード（有効時は Duel 開始まで殴れない） */
    private boolean duelMode = true;
    /** 自動マッチング（対戦ボタン）の対象にするか */
    private boolean autoMatch = false;
    private StartMode startMode = StartMode.RANDOM;
    private CleanupMode cleanupMode = CleanupMode.DELETE;
    /** 返却モード時の投入先ラージチェスト（未設定は null） */
    private Location returnChest;

    /** 復活地点（未設定は null） */
    private Location respawnPoint;
    /** カスタム開始位置（未設定は null） */
    private Location pos1;
    private Location pos2;

    public RingRegion(String claimKey, String name, String worldName,
                      int x1, int y1, int z1, int x2, int y2, int z2) {
        this.claimKey = claimKey;
        this.name = name;
        this.worldName = worldName;
        setBounds(x1, y1, z1, x2, y2, z2);
    }

    /** リングの一意キー (claimKey#name小文字)。 */
    public static String makeId(String claimKey, String name) {
        return claimKey + "#" + name.toLowerCase(Locale.ROOT);
    }

    public String ringId() {
        return makeId(claimKey, name);
    }

    public String getClaimKey() {
        return claimKey;
    }

    public String getName() {
        return name;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public void setBounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    public int getMinX() {
        return minX;
    }

    public int getMinY() {
        return minY;
    }

    public int getMinZ() {
        return minZ;
    }

    public int getMaxX() {
        return maxX;
    }

    public int getMaxY() {
        return maxY;
    }

    public int getMaxZ() {
        return maxZ;
    }

    public int getSizeX() {
        return maxX - minX + 1;
    }

    public int getSizeZ() {
        return maxZ - minZ + 1;
    }

    public Location getCenterLocation() {
        World world = getWorld();
        return world == null ? null
                : new Location(world, (minX + maxX + 1) / 2.0, minY, (minZ + maxZ + 1) / 2.0);
    }

    public boolean isKeepInventory() {
        return keepInventory;
    }

    public void setKeepInventory(boolean keepInventory) {
        this.keepInventory = keepInventory;
    }

    public boolean isInstantRespawn() {
        return instantRespawn;
    }

    public void setInstantRespawn(boolean instantRespawn) {
        this.instantRespawn = instantRespawn;
    }

    public boolean isDuelMode() {
        return duelMode;
    }

    public void setDuelMode(boolean duelMode) {
        this.duelMode = duelMode;
    }

    public boolean isAutoMatch() {
        return autoMatch;
    }

    public void setAutoMatch(boolean autoMatch) {
        this.autoMatch = autoMatch;
    }

    public StartMode getStartMode() {
        return startMode;
    }

    public void setStartMode(StartMode startMode) {
        this.startMode = startMode == null ? StartMode.RANDOM : startMode;
    }

    public CleanupMode getCleanupMode() {
        return cleanupMode;
    }

    public void setCleanupMode(CleanupMode cleanupMode) {
        this.cleanupMode = cleanupMode == null ? CleanupMode.DELETE : cleanupMode;
    }

    public Location getReturnChest() {
        return returnChest;
    }

    public void setReturnChest(Location returnChest) {
        this.returnChest = returnChest;
    }

    public Location getRespawnPoint() {
        return respawnPoint;
    }

    public void setRespawnPoint(Location respawnPoint) {
        this.respawnPoint = respawnPoint;
    }

    public Location getPos1() {
        return pos1;
    }

    public void setPos1(Location pos1) {
        this.pos1 = pos1;
    }

    public Location getPos2() {
        return pos2;
    }

    public void setPos2(Location pos2) {
        this.pos2 = pos2;
    }

    /** 指定座標がリング内（矩形・縦は選択範囲±余裕）か。 */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        double y = location.getY();
        if (y < minY - VERTICAL_BELOW || y > maxY + VERTICAL_ABOVE) {
            return false;
        }
        int bx = location.getBlockX();
        int bz = location.getBlockZ();
        return bx >= minX && bx <= maxX && bz >= minZ && bz <= maxZ;
    }

    /** リング矩形（水平）からの距離。内側なら 0。 */
    public double horizontalDistanceToBox(Location location) {
        double x = location.getX();
        double z = location.getZ();
        double dx = Math.max(0, Math.max(minX - x, x - (maxX + 1)));
        double dz = Math.max(0, Math.max(minZ - z, z - (maxZ + 1)));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 復活地点として設定可能な位置か（リングから50ブロック以内）。 */
    public boolean isValidRespawnPoint(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return horizontalDistanceToBox(location) <= RESPAWN_MAX_DISTANCE;
    }

    // ================= 永続化 =================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("claimKey", claimKey);
        map.put("name", name);
        map.put("world", worldName);
        map.put("minX", minX);
        map.put("minY", minY);
        map.put("minZ", minZ);
        map.put("maxX", maxX);
        map.put("maxY", maxY);
        map.put("maxZ", maxZ);
        map.put("keepInventory", keepInventory);
        map.put("instantRespawn", instantRespawn);
        map.put("duelMode", duelMode);
        map.put("autoMatch", autoMatch);
        map.put("startMode", startMode.name());
        map.put("cleanupMode", cleanupMode.name());
        map.put("returnChest", writeLoc(returnChest));
        map.put("respawn", writeLoc(respawnPoint));
        map.put("pos1", writeLoc(pos1));
        map.put("pos2", writeLoc(pos2));
        return map;
    }

    public static RingRegion fromMap(Map<String, Object> map) {
        if (!(map.get("world") instanceof String world)
                || !(map.get("claimKey") instanceof String claimKey)) {
            return null;
        }
        String name = map.get("name") instanceof String s && !s.isEmpty() ? s : "ring";
        RingRegion ring;
        if (map.containsKey("minX")) {
            ring = new RingRegion(claimKey, name, world,
                    readInt(map.get("minX")), readInt(map.get("minY")), readInt(map.get("minZ")),
                    readInt(map.get("maxX")), readInt(map.get("maxY")), readInt(map.get("maxZ")));
        } else if (map.containsKey("radius")) {
            // 旧フォーマット（中心+半径の円形）からの移行
            int cx = readInt(map.get("x"));
            int cy = readInt(map.get("y"));
            int cz = readInt(map.get("z"));
            int radius = Math.max(MIN_SIDE, readInt(map.get("radius")));
            ring = new RingRegion(claimKey, name, world,
                    cx - radius, cy, cz - radius, cx + radius, cy, cz + radius);
        } else {
            return null;
        }
        ring.keepInventory = readBool(map.get("keepInventory"), true);
        ring.instantRespawn = readBool(map.get("instantRespawn"), true);
        ring.duelMode = readBool(map.get("duelMode"), true);
        ring.autoMatch = readBool(map.get("autoMatch"), false);
        if (map.get("startMode") instanceof String s) {
            try {
                ring.startMode = StartMode.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (map.get("cleanupMode") instanceof String s) {
            try {
                ring.cleanupMode = CleanupMode.valueOf(s);
            } catch (IllegalArgumentException ignored) {
            }
        }
        ring.returnChest = readLoc(world, map.get("returnChest"));
        ring.respawnPoint = readLoc(world, map.get("respawn"));
        ring.pos1 = readLoc(world, map.get("pos1"));
        ring.pos2 = readLoc(world, map.get("pos2"));
        return ring;
    }

    private static String writeLoc(Location loc) {
        if (loc == null) {
            return null;
        }
        return loc.getX() + "," + loc.getY() + "," + loc.getZ() + ","
                + loc.getYaw() + "," + loc.getPitch();
    }

    private static Location readLoc(String world, Object raw) {
        if (!(raw instanceof String s) || s.isEmpty()) {
            return null;
        }
        String[] parts = s.split(",");
        if (parts.length < 3) {
            return null;
        }
        try {
            World w = Bukkit.getWorld(world);
            Location loc = new Location(w,
                    Double.parseDouble(parts[0]), Double.parseDouble(parts[1]), Double.parseDouble(parts[2]));
            if (parts.length >= 5) {
                loc.setYaw(Float.parseFloat(parts[3]));
                loc.setPitch(Float.parseFloat(parts[4]));
            }
            return loc;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static int readInt(Object value) {
        return value instanceof Number n ? n.intValue() : 0;
    }

    private static boolean readBool(Object value, boolean fallback) {
        return value instanceof Boolean b ? b : fallback;
    }
}
