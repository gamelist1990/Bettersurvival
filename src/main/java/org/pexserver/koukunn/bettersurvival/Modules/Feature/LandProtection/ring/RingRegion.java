package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection.ring;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 土地保護内に設置できる闘技場「リング」。
 * 円形の領域で、内部では PVP が有効になる。
 * KeepInventory・即時復活・復活地点・Duel モードなどの設定を保持する。
 * リングは 1 つの保護領域（claimKey）につき 1 つまで。
 */
public class RingRegion {

    /** 復活地点として設定できるリング中心からの最大距離 */
    public static final int RESPAWN_MAX_DISTANCE = 50;
    /** Duel 開始時に確保する最低距離 */
    public static final int DUEL_MIN_DISTANCE = 10;
    /** リング半径の下限・上限 */
    public static final int MIN_RADIUS = 6;
    public static final int MAX_RADIUS = 64;
    /** リングの縦方向の有効範囲（中心Yからの上下） */
    public static final int VERTICAL_BELOW = 5;
    public static final int VERTICAL_ABOVE = 12;

    /** Duel の開始位置の決め方 */
    public enum StartMode {
        RANDOM("§eランダム", "リング内のランダムな地点から開始します"),
        FREE("§a自由", "位置の移動は行いません（そのまま開始）"),
        CUSTOM("§bカスタム", "設定した pos1 / pos2 から向かい合って開始します");

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
    private final String worldName;
    private int centerX;
    private int centerY;
    private int centerZ;
    private int radius = 12;

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

    public RingRegion(String claimKey, String worldName, int centerX, int centerY, int centerZ) {
        this.claimKey = claimKey;
        this.worldName = worldName;
        this.centerX = centerX;
        this.centerY = centerY;
        this.centerZ = centerZ;
    }

    public String getClaimKey() {
        return claimKey;
    }

    public String getWorldName() {
        return worldName;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public int getCenterX() {
        return centerX;
    }

    public int getCenterY() {
        return centerY;
    }

    public int getCenterZ() {
        return centerZ;
    }

    public void setCenter(int x, int y, int z) {
        this.centerX = x;
        this.centerY = y;
        this.centerZ = z;
    }

    public Location getCenterLocation() {
        World world = getWorld();
        return world == null ? null : new Location(world, centerX + 0.5, centerY, centerZ + 0.5);
    }

    public int getRadius() {
        return radius;
    }

    public void setRadius(int radius) {
        this.radius = Math.max(MIN_RADIUS, Math.min(MAX_RADIUS, radius));
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

    /** 指定座標がリング内（円形・縦は中心Y±の範囲）か。 */
    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        double y = location.getY();
        if (y < centerY - VERTICAL_BELOW || y > centerY + VERTICAL_ABOVE) {
            return false;
        }
        double dx = location.getX() - (centerX + 0.5);
        double dz = location.getZ() - (centerZ + 0.5);
        return dx * dx + dz * dz <= (double) radius * radius;
    }

    /** リング中心からの水平距離。 */
    public double horizontalDistance(Location location) {
        double dx = location.getX() - (centerX + 0.5);
        double dz = location.getZ() - (centerZ + 0.5);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 復活地点として設定可能な位置か（リング中心から50ブロック以内）。 */
    public boolean isValidRespawnPoint(Location location) {
        if (location == null || location.getWorld() == null
                || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return horizontalDistance(location) <= RESPAWN_MAX_DISTANCE;
    }

    // ================= 永続化 =================

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", worldName);
        map.put("x", centerX);
        map.put("y", centerY);
        map.put("z", centerZ);
        map.put("radius", radius);
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

    public static RingRegion fromMap(String claimKey, Map<String, Object> map) {
        if (!(map.get("world") instanceof String world)) {
            return null;
        }
        RingRegion ring = new RingRegion(claimKey, world,
                readInt(map.get("x")), readInt(map.get("y")), readInt(map.get("z")));
        ring.setRadius(readInt(map.get("radius")) <= 0 ? 12 : readInt(map.get("radius")));
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
