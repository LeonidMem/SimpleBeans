package ru.leonidm.simplebeans.beans;

import org.jetbrains.annotations.NotNull;

import java.util.Objects;

public class BeanData {

    private final Class<?> beanClass;
    private final String id;

    @NotNull
    public static BeanData of(@NotNull BeanInitializer<?> beanInitializer) {
        return new BeanData(beanInitializer.getBeanClass(), beanInitializer.getId());
    }

    public BeanData(@NotNull Class<?> beanClass, @NotNull String id) {
        this.beanClass = beanClass;
        this.id = id;
    }

    @NotNull
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @Override
    @NotNull
    public String toString() {
        return beanClass.getName() + "{id=" + id + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        BeanData beanData = (BeanData) o;
        return beanClass.equals(beanData.beanClass)
                && id.equals(beanData.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beanClass, id);
    }
}
