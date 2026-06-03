package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.annotations.JsonAdapter;

@JsonAdapter(WebMapChunkRecordAdapter.class)
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
        this.color = WebMapColorPool.canonicalize(color);
        this.pixels = WebMapColorPool.canonicalize(pixels);
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
        this.color = WebMapColorPool.canonicalize(color);
    }

    public String[] getPixels() {
        return pixels;
    }

    public void setPixels(String[] pixels) {
        this.pixels = WebMapColorPool.canonicalize(pixels);
    }

    public long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(long updatedAt) {
        this.updatedAt = updatedAt;
    }
}
