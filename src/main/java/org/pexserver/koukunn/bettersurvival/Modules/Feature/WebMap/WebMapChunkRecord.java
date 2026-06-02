package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

public class WebMapChunkRecord {
    private int x;
    private int z;
    private String color;
    private String[] pixels;
    private long updatedAt;

    public WebMapChunkRecord() {
    }

    public WebMapChunkRecord(int x, int z, String color, String[] pixels, long updatedAt) {
        this.x = x;
        this.z = z;
        this.color = color;
        this.pixels = pixels;
        this.updatedAt = updatedAt;
    }

    public int getX() {
        return x;
    }

    public int getZ() {
        return z;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public String[] getPixels() {
        return pixels;
    }

    public void setPixels(String[] pixels) {
        this.pixels = pixels;
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
