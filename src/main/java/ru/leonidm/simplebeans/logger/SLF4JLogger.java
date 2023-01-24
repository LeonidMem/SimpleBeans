package ru.leonidm.simplebeans.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SLF4JLogger extends AbstractLogger {

    private final Logger logger;

    public SLF4JLogger() {
        logger = LoggerFactory.getLogger("SimpleBeans");
    }

    @Override
    public void info(@NotNull String string, @Nullable Object @NotNull ... args) {
        logger.info(string, args);
    }

    @Override
    public void warn(@NotNull String string, @Nullable Object @NotNull ... args) {
        logger.warn(string, args);
    }

    @Override
    public void debug(@NotNull String string, @Nullable Object @NotNull ... args) {
        logger.debug(string, args);
    }
}
