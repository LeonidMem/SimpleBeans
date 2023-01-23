package ru.leonidm.simplebeans.utils;

import org.jetbrains.annotations.NotNull;

import java.util.function.Function;

public final class ExceptionUtils {

    private ExceptionUtils() {

    }

    @NotNull
    public static RuntimeException wrapToRuntime(@NotNull Throwable t) {
        return wrapToRuntime(t, IllegalStateException::new);
    }

    @NotNull
    public static <T extends Throwable> RuntimeException wrapToRuntime(@NotNull T t, @NotNull Function<T, RuntimeException> initializer) {
        if (t instanceof RuntimeException re) {
            return re;
        }

        return initializer.apply(t);
    }
}
