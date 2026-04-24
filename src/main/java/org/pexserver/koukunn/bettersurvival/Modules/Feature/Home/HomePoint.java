package org.pexserver.koukunn.bettersurvival.Modules.Feature.Home;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Map;

public class HomePoint {
    private final String name;
    private final String world;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public HomePoint(String name, Location location) {
        this.name = name;
        this.world = location.getWorld() == null ? "" : location.getWorld().getName();
        this.x = location.getX();
        this.y = location.getY();
        this.z = location.getZ();
        this.yaw = location.getYaw();
        this.pitch = location.getPitch();
    }

    public HomePoint(String name, String world, double x, double y, double z, float yaw, float pitch) {
        this.name = name;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public String getName() {
        return name;
    }

    public String getWorld() {
        return world;
    }

    public Location toLocation() {
        World targetWorld = Bukkit.getWorld(world);
        if (targetWorld == null) return null;
        return new Location(targetWorld, x, y, z, yaw, pitch);
    }

    public String formatLocation() {
        return world + " X:" + Math.round(x) + " Y:" + Math.round(y) + " Z:" + Math.round(z);
    }

    public Map<String, Object> serialize() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("world", world);
        map.put("x", x);
        map.put("y", y);
        map.put("z", z);
        map.put("yaw", yaw);
        map.put("pitch", pitch);
        return map;
    }

    public static HomePoint deserialize(String name, Map<String, Object> map) {
        String world = stringValue(map.get("world"));
        double x = doubleValue(map.get("x"));
        double y = doubleValue(map.get("y"));
        double z = doubleValue(map.get("z"));
        float yaw = (float) doubleValue(map.get("yaw"));
        float pitch = (float) doubleValue(map.get("pitch"));
        return new HomePoint(name, world, x, y, z, yaw, pitch);
    }

    private static String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static double doubleValue(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        return 0.0;
    }
}
