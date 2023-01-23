package ru.leonidm.simplebeans.utils;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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
}
