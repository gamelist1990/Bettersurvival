package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import java.util.concurrent.ConcurrentHashMap;

public final class WebMapColorPool {
    private static final ConcurrentHashMap<String, String> COLORS = new ConcurrentHashMap<>();

    private WebMapColorPool() {
    }

    public static String canonicalize(String color) {
        if (color == null || color.isEmpty()) {
            return color;
        }
        String cached = COLORS.putIfAbsent(color, color);
        return cached == null ? color : cached;
    }

    public static String[] canonicalize(String[] pixels) {
        if (pixels == null || pixels.length == 0) {
            return pixels;
        }
        for (int index = 0; index < pixels.length; index++) {
            pixels[index] = canonicalize(pixels[index]);
        }
        return pixels;
    }
}
