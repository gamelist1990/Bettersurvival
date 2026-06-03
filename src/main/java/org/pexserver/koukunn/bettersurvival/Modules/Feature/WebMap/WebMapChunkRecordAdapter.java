package org.pexserver.koukunn.bettersurvival.Modules.Feature.WebMap;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class WebMapChunkRecordAdapter extends TypeAdapter<WebMapChunkRecord> {
    @Override
    public void write(JsonWriter out, WebMapChunkRecord value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        out.beginObject();
        out.name("x").value(value.getX());
        out.name("z").value(value.getZ());
        out.name("color");
        String color = WebMapColorPool.canonicalize(value.getColor());
        if (color == null) {
            out.nullValue();
        } else {
            out.value(color);
        }
        out.name("pixels");
        out.beginArray();
        String[] pixels = value.getPixels();
        if (pixels != null) {
            for (String pixel : pixels) {
                String canonicalPixel = WebMapColorPool.canonicalize(pixel);
                if (canonicalPixel == null) {
                    out.nullValue();
                } else {
                    out.value(canonicalPixel);
                }
            }
        }
        out.endArray();
        out.name("updatedAt").value(value.getUpdatedAt());
        out.endObject();
    }

    @Override
    public WebMapChunkRecord read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        int x = 0;
        int z = 0;
        String color = "";
        String[] pixels = null;
        long updatedAt = 0L;
        in.beginObject();
        while (in.hasNext()) {
            String name = in.nextName();
            switch (name) {
                case "x" -> x = in.nextInt();
                case "z" -> z = in.nextInt();
                case "color" -> color = readString(in);
                case "pixels" -> pixels = readPixels(in);
                case "updatedAt" -> updatedAt = in.nextLong();
                default -> in.skipValue();
            }
        }
        in.endObject();
        return new WebMapChunkRecord(x, z, color, pixels, updatedAt);
    }

    private String readString(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return WebMapColorPool.canonicalize(in.nextString());
    }

    private String[] readPixels(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        java.util.List<String> pixels = new java.util.ArrayList<>(256);
        in.beginArray();
        while (in.hasNext()) {
            pixels.add(readString(in));
        }
        in.endArray();
        return pixels.toArray(new String[0]);
    }
}
