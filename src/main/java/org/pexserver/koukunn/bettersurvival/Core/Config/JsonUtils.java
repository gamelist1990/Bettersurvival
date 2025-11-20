package org.pexserver.koukunn.bettersurvival.Core.Config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;

public final class JsonUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {}

    public static <T> T fromJson(File file, Class<T> clazz) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, clazz);
        }
    }

    public static void toJson(File file, Object obj) throws IOException {
        // ensure parent dirs
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), StandardCharsets.UTF_8)) {
            GSON.toJson(obj, writer);
        }
    }
}
