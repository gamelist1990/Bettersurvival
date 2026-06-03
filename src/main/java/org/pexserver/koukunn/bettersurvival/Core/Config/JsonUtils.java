package org.pexserver.koukunn.bettersurvival.Core.Config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public final class JsonUtils {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private JsonUtils() {}

    public static <T> T fromJson(File file, Class<T> clazz) throws IOException {
        try (Reader reader = new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8)) {
            return GSON.fromJson(reader, clazz);
        } catch (RuntimeException ex) {
            throw new IOException("Invalid JSON: " + file.getPath(), ex);
        }
    }

    public static void toJson(File file, Object obj) throws IOException {
        // ensure parent dirs
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        File tempFile = File.createTempFile(file.getName(), ".tmp", parent == null ? null : parent);
        try {
            try (Writer writer = new OutputStreamWriter(new FileOutputStream(tempFile), StandardCharsets.UTF_8)) {
                GSON.toJson(obj, writer);
            } catch (RuntimeException ex) {
                throw new IOException("Invalid JSON: " + file.getPath(), ex);
            }
            try {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            if (tempFile.exists() && !tempFile.equals(file)) {
                tempFile.delete();
            }
        }
    }
}
