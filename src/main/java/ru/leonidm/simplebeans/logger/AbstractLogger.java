package ru.leonidm.simplebeans.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public sealed abstract class AbstractLogger permits Log4JLogger, SLF4JLogger, SystemLogger {

    public abstract void info(@NotNull String string, @Nullable Object @NotNull ... args);

    public abstract void warn(@NotNull String string, @Nullable Object @NotNull ... args);

    public abstract void debug(@NotNull String string, @Nullable Object @NotNull ... args);

}
