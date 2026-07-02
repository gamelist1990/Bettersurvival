package org.pexserver.koukunn.bettersurvival.Modules.Feature.LandProtection;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 保護コア1つが管理する保護領域。
 * コアを中心とした正方形（高さは全域）を保護する。
 * レベル・オーナー・燃料・ホワイトリスト・設定を保持し、
 * コアを破壊するとアイテムPDCとして持ち出せる。
 */
public class ClaimRegion {

    private final String worldName;
    private final int x;
    private final int y;
    private final int z;

    /** オーナー。未登録（設置後に一度も開かれていない）の場合は null。 */
    private UUID owner;
    private String ownerName = "";
    /** パーティー共有中のパーティーID。個人所有の場合は null。 */
    private UUID partyId;
    private int level = ClaimLevel.MIN_LEVEL;
    /** 残り燃料（燃料ユニット） */
    private double fuelUnits;
    private long lastUpkeepMillis = System.currentTimeMillis();
    /** ホワイトリスト (uuid -> 表示名) */
    private final Map<UUID, String> whitelist = new LinkedHashMap<>();
    private ClaimSettings settings = new ClaimSettings();
    private long createdAt = System.currentTimeMillis();

    public ClaimRegion(String worldName, int x, int y, int z) {
        this.worldName = worldName;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static String toKey(String worldName, int x, int y, int z) {
        return worldName + ":" + x + ":" + y + ":" + z;
    }

    public String key() {
        return toKey(worldName, x, y, z);
    }

    public String getWorldName() {
        return worldName;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }

    public World getWorld() {
        return Bukkit.getWorld(worldName);
    }

    public Location getCoreLocation() {
        World world = getWorld();
        return world == null ? null : new Location(world, x, y, z);
    }

    public UUID getOwner() {
        return owner;
    }

    public void setOwner(UUID owner) {
        this.owner = owner;
    }

    public String getOwnerName() {
        return ownerName;
    }

    public void setOwnerName(String ownerName) {
        this.ownerName = ownerName == null ? "" : ownerName;
    }

    public UUID getPartyId() {
        return partyId;
    }

    public void setPartyId(UUID partyId) {
        this.partyId = partyId;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = ClaimLevel.clamp(level);
    }

    public int getRadius() {
        return ClaimLevel.radius(level);
    }

    public double getFuelUnits() {
        return fuelUnits;
    }

    public void setFuelUnits(double fuelUnits) {
        this.fuelUnits = Math.max(0, fuelUnits);
    }

    public void addFuelUnits(double units) {
        setFuelUnits(fuelUnits + units);
    }

    public long getLastUpkeepMillis() {
        return lastUpkeepMillis;
    }

    public void setLastUpkeepMillis(long lastUpkeepMillis) {
        this.lastUpkeepMillis = lastUpkeepMillis;
    }

    public Map<UUID, String> getWhitelist() {
        return whitelist;
    }

    public ClaimSettings getSettings() {
        return settings;
    }

    public void setSettings(ClaimSettings settings) {
        this.settings = settings == null ? new ClaimSettings() : settings;
    }

    public long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(long createdAt) {
        this.createdAt = createdAt;
    }

    /** オーナー登録済みかつ燃料が残っている間だけ保護が有効。 */
    public boolean isActive() {
        return owner != null && fuelUnits > 0;
    }

    /** 残り燃料での維持可能時間（秒）。 */
    public long remainingSeconds() {
        double perHour = ClaimLevel.upkeepPerHour(level);
        if (perHour <= 0) {
            return 0;
        }
        return (long) (fuelUnits / perHour * 3600.0);
    }

    /** 指定座標（水平方向のみ判定・高さ無制限）が保護範囲内か。 */
    public boolean containsHorizontal(Location location) {
        if (location.getWorld() == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        int radius = getRadius();
        return Math.abs(location.getBlockX() - x) <= radius
                && Math.abs(location.getBlockZ() - z) <= radius;
    }

    /** 他の保護領域と範囲が重なるか。 */
    public boolean intersects(ClaimRegion other) {
        if (!worldName.equals(other.worldName)) {
            return false;
        }
        int reach = getRadius() + other.getRadius();
        return Math.abs(x - other.x) <= reach && Math.abs(z - other.z) <= reach;
    }

    /** 指定中心・半径の新設予定領域と重なるか。 */
    public boolean intersects(String otherWorld, int otherX, int otherZ, int otherRadius) {
        if (!worldName.equals(otherWorld)) {
            return false;
        }
        int reach = getRadius() + otherRadius;
        return Math.abs(x - otherX) <= reach && Math.abs(z - otherZ) <= reach;
    }
}
