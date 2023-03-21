package ru.leonidm.simplebeans.beans;

import org.jetbrains.annotations.NotNull;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.proxy.AdvancedProxy;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.util.Arrays;

public sealed abstract class BeanInitializer<E extends Executable> permits BeanInitializer.BeanMethod,
                                                                           BeanInitializer.BeanConstructor {

    protected final E executable;
    protected final Class<?> beanClass;
    protected final String id;
    protected final ApplicationContext context;

    protected BeanInitializer(@NotNull E executable, @NotNull Class<?> beanClass, @NotNull String id,
                              @NotNull ApplicationContext context) {
        this.executable = executable;
        executable.setAccessible(true);

        this.beanClass = beanClass;
        this.id = id;
        this.context = context;
    }

    @NotNull
    public static BeanInitializer<Method> of(@NotNull Method method, @NotNull String id, @NotNull ApplicationContext context) {
        return new BeanMethod(method, id, context);
    }

    @NotNull
    public static BeanInitializer<Constructor<?>> of(@NotNull Constructor<?> constructor, @NotNull ApplicationContext context) {
        return new BeanConstructor(constructor, context);
    }

    public boolean canCreate() {
        return executable.getParameterCount() == 0 || Arrays.stream(executable.getParameterTypes()).allMatch(context::hasBean);
    }

    @NotNull
    public final Object create() {
        Object object = initialize(Arrays.stream(executable.getParameterTypes()).map(context::getBean).toArray());
        return AdvancedProxy.proxyIfNeeded(object, beanClass, context);
    }

    @NotNull
    protected abstract Object initialize(@NotNull Object @NotNull [] args);

    @NotNull
    public Class<?> getBeanClass() {
        return beanClass;
    }

    @NotNull
    public String getId() {
        return id;
    }

    @NotNull
    @Override
    public String toString() {
        return executable.toString();
    }

    protected static final class BeanMethod extends BeanInitializer<Method> {

        private final Class<?> configurationClass;

        public BeanMethod(@NotNull Method method, @NotNull String id, @NotNull ApplicationContext context) {
            super(method, method.getReturnType(), id, context);

            configurationClass = method.getDeclaringClass();
        }

        @Override
        public boolean canCreate() {
            return super.canCreate() && context.hasBean(configurationClass);
        }

        @Override
        @NotNull
        protected Object initialize(@NotNull Object @NotNull [] args) {
            try {
                return executable.invoke(context.getBean(configurationClass), args);
            } catch (Exception e) {
                throw ExceptionUtils.wrapToRuntime(e);
            }
        }
    }

    protected static final class BeanConstructor extends BeanInitializer<Constructor<?>> {

        public BeanConstructor(@NotNull Constructor<?> executable, @NotNull ApplicationContext context) {
            super(executable, executable.getDeclaringClass(), "", context);
        }

        @Override
        @NotNull
        protected Object initialize(@NotNull Object @NotNull [] args) {
            try {
                return executable.newInstance(args);
            } catch (Exception e) {
                throw ExceptionUtils.wrapToRuntime(e);
            }
        }
    }
}
