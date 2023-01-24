package ru.leonidm.simplebeans.logger;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

final class SystemLogger extends AbstractLogger {

    private boolean warnsEnabled = false;
    private boolean debugEnabled = false;

    @Override
    public void info(@NotNull String string, @Nullable Object @NotNull ... args) {
        System.out.print("[SimpleBeans] ");
        System.out.printf(string, args);
        System.out.println();
    }

    @Override
    public void warn(@NotNull String string, @Nullable Object @NotNull ... args) {
        if (warnsEnabled) {
            System.err.print("[SimpleBeans] ");
            System.err.printf(string, args);
            System.err.println();
        }
    }

    @Override
    public void debug(@NotNull String string, @Nullable Object @NotNull ... args) {
        if (debugEnabled) {
            System.out.print("[SimpleBeans] [DEBUG] ");
            System.out.printf(string, args);
            System.out.println();
        }
    }
}
