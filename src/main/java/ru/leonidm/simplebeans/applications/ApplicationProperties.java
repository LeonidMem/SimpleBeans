package ru.leonidm.simplebeans.applications;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ApplicationProperties {

    private final Map<String, String> properties = new HashMap<>();

    public ApplicationProperties(@NotNull Class<?> applicationClass) {
        Application application = applicationClass.getAnnotation(Application.class);
        Objects.requireNonNull(application);

        for (String property : application.properties()) {
            String[] split = property.split("[^\\\\]=");
            if (split.length != 2) {
                throw new IllegalStateException("Cannot handle property \"%s\"".formatted(property));
            }

            String key = split[0];
            String value = split[1];
            properties.put(key, value);
        }
    }

    @Nullable
    public String getProperty(@NotNull String key) {
        return properties.get(key);
    }

    @Contract("_, null -> null; _, !null -> !null")
    public String getProperty(@NotNull String key, @Nullable String defaultValue) {
        return properties.getOrDefault(key, defaultValue);
    }
}
