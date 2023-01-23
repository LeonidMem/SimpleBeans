package ru.leonidm.simplebeans.applications;

import org.jetbrains.annotations.NotNull;

import java.util.HashSet;
import java.util.Set;

public final class SimpleApplication {

    private static final Set<Class<?>> RUNNING_APPLICATIONS = new HashSet<>();

    private SimpleApplication() {

    }

    @NotNull
    public static ApplicationContext run(@NotNull Class<?> applicationClass) {
        if (!RUNNING_APPLICATIONS.add(applicationClass)) {
            throw new IllegalArgumentException("Applications %s is already running".formatted(applicationClass.getName()));
        }

        return new ApplicationContext(applicationClass);
    }
}
