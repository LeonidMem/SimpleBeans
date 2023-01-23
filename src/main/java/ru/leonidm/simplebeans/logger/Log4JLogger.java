package ru.leonidm.simplebeans.logger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class Log4JLogger extends AbstractLogger {

    private final Logger logger;

    public Log4JLogger() {
        logger = LogManager.getLogger("SimpleBeans");
    }

    @Override
    public void info(@NotNull String string, @Nullable Object @NotNull ... args) {
        logger.info(string, args);
    }

    @Override
    public void warn(@NotNull String string, @Nullable Object @NotNull ... args) {
        logger.warn(string, args);
    }
}
