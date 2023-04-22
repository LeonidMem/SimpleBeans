package ru.leonidm.simplebeans.utils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

public final class GeneralUtils {

    private static File coreJarFile;

    private GeneralUtils() {

    }

    @NotNull
    public static File getCoreJarFile() {
        if (coreJarFile == null) {
            String path = GeneralUtils.class.getProtectionDomain().getCodeSource().getLocation().getPath();
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);

            coreJarFile = new File(decodedPath);
        }

        return coreJarFile;
    }

    @NotNull
    public static File getDeclaringJarFile(@NotNull Class<?> clazz) {
        String path = clazz.getProtectionDomain().getCodeSource().getLocation().getPath();
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        return new File(decodedPath);
    }

    @NotNull
    @Unmodifiable
    public static Set<File> getClassPathFiles() {
        return Arrays.stream(System.getProperty("java.class.path").replace("\\\\", "\\").split(File.pathSeparator))
                .map(Path::of)
                .map(Path::toFile)
                .collect(Collectors.toUnmodifiableSet());
    }
}
