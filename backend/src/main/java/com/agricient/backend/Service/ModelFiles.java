package com.agricient.backend.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

import org.springframework.core.io.ClassPathResource;

/**
 * ONNX Runtime needs real files on disk, but inside a packaged JAR the model files are not on disk.
 * This copies a model (and its optional ".data" weights file, which must sit next to it) out of the
 * classpath into one temporary folder, once, and returns the path to load from.
 */
public final class ModelFiles {

    private static Path folder;

    private ModelFiles() {
    }

    public static synchronized Path extract(String name) throws IOException {
        if (folder == null) {
            folder = Files.createTempDirectory("agricient-models");
            Path dir = folder;
            // the folder is only a cache of files that are inside the JAR, so it is safe to remove on exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> deleteQuietly(dir)));
        }
        copy(name, true);
        copy(name + ".data", false);
        return folder.resolve(name);
    }

    public static List<String> readLines(String name) throws IOException {
        try (InputStream in = new ClassPathResource(name).getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).lines().toList();
        }
    }

    private static void copy(String name, boolean required) throws IOException {
        ClassPathResource resource = new ClassPathResource(name);
        if (!resource.exists()) {
            if (required) {
                throw new IOException("Model file not found in the application: " + name);
            }
            return;
        }
        Path target = folder.resolve(name);
        if (Files.exists(target)) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void deleteQuietly(Path dir) {
        try (var files = Files.list(dir)) {
            files.forEach(f -> f.toFile().delete());
            dir.toFile().delete();
        } catch (Exception ignored) {
            // nothing to do: the operating system clears temporary folders anyway
        }
    }
}
