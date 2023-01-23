package ru.leonidm.simplebeans.logger;

import org.jetbrains.annotations.NotNull;

public final class LoggerAdapter {

    private static final AbstractLogger INSTANCE;

    static {
        AbstractLogger logger;

        try {
            Class.forName("org.apache.logging.log4j.Logger");
            logger = new Log4JLogger();
        } catch (ClassNotFoundException e) {
            try {
                Class.forName("org.slf4j.Logger");
                logger = new SLF4JLogger();
            } catch (ClassNotFoundException e1) {
                logger = new SystemLogger();
            }
        }

        INSTANCE = logger;
    }

    private LoggerAdapter() {

    }

    @NotNull
    public static AbstractLogger get() {
        return INSTANCE;
    }
}
