package ru.leonidm.simplebeans.beans;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.proxy.AdvancedProxy;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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

    @NotNull
    @Unmodifiable
    public List<BeanData> getDependencies() {
        List<BeanData> out = new ArrayList<>();

        Parameter[] parameters = executable.getParameters();
        Annotation[][] parameterAnnotations = executable.getParameterAnnotations();
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            Annotation[] annotations = parameterAnnotations[index];

            Bean bean = null;
            for (Annotation annotation : annotations) {
                if (annotation instanceof Bean bean1) {
                    bean = bean1;
                    break;
                }
            }

            String id = bean != null ? bean.id() : "";
            Class<?> beanClass = parameter.getType();

            out.add(new BeanData(beanClass, id));
        }

        return List.copyOf(out);
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
        @NotNull
        @Unmodifiable
        public List<BeanData> getDependencies() {
            List<BeanData> classes = new ArrayList<>(super.getDependencies());
            classes.add(new BeanData(configurationClass, ""));
            return List.copyOf(classes);
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
