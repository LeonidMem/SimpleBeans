package ru.leonidm.simplebeans.proxy;

import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.This;
import net.bytebuddy.matcher.ElementMatchers;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.beans.Bean;
import ru.leonidm.simplebeans.logger.LoggerAdapter;
import ru.leonidm.simplebeans.proxy.aspects.Aspect;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class AdvancedProxy {

    private static final Map<Class<?>, Class<?>> PROXIED_CLASSES = new HashMap<>();

    private AdvancedProxy() {

    }

    @NotNull
    private static <T> T newProxyInstance(@NotNull T object, @NotNull Class<?> objectClass, @NotNull ApplicationContext context) {
        if (objectClass.isInterface()) {
            Object proxy = Proxy.newProxyInstance(objectClass.getClassLoader(), new Class[]{objectClass}, new AspectInvocationHandler(context, object));
            return (T) proxy;
        }

        Class<?> proxyClass = PROXIED_CLASSES.computeIfAbsent(objectClass, k -> {
            if (objectClass.isAnnotationPresent(Aspect.class)) {
                return objectClass;
            }

            if (Modifier.isFinal(objectClass.getModifiers())) {
                LoggerAdapter.get().debug("Cannot proxy {} because it is final class", objectClass.getName());
                return objectClass;
            }

            Constructor<?>[] constructors = objectClass.getDeclaredConstructors();
            if (constructors.length != 1) {
                LoggerAdapter.get().debug("Cannot proxy {} because it has more than one constructor", objectClass.getName());
                return objectClass;
            }

            Constructor<?> constructor = constructors[0];
            int constructorModifiers = constructor.getModifiers();
            if (!Modifier.isPublic(constructorModifiers) && !Modifier.isProtected(constructorModifiers)) {
                LoggerAdapter.get().debug("Cannot proxy {} because it has non-public and non-protected constructor", objectClass.getName());
                return objectClass;
            }

            LoggerAdapter.get().debug("Creating proxy class for {}", objectClass.getSimpleName());

            DynamicType.Builder<?> builder = new ByteBuddy()
                    .subclass(objectClass)
                    .implement(ProxyClass.class)
                    .defineField("invocationHandler", AspectInvocationHandler.class, Modifier.PRIVATE)
                    .annotateType(objectClass.getAnnotations());

            Set<Method> methodsSet = new HashSet<>(List.of(objectClass.getDeclaredMethods()));
            methodsSet.addAll(List.of(objectClass.getMethods()));

            for (Method method : methodsSet) {
                int modifiers = method.getModifiers();
                if (Modifier.isFinal(modifiers)) {
                    if (method.getDeclaringClass() != Object.class) {
                        LoggerAdapter.get().debug("Cannot proxy {} because it is final method", method);
                    }
                    continue;
                }

                if (Modifier.isPrivate(modifiers)) {
                    LoggerAdapter.get().debug("Cannot proxy {} because it is private method", method);
                    continue;
                }

                builder = builder.method(ElementMatchers.is(method))
                        .intercept(MethodDelegation.to(AspectInterceptor.INSTANCE))
                        .annotateMethod(method.getAnnotations());
            }

            try (DynamicType.Unloaded<?> unloaded = builder.make()) {
                Class<?> loadedClass = unloaded.load(objectClass.getClassLoader()).getLoaded();
                LoggerAdapter.get().debug("Created proxy class for {}", objectClass.getSimpleName());
                return loadedClass;
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        });

        if (proxyClass == objectClass) {
            return object;
        }

        try {
            Constructor<?> constructor = proxyClass.getConstructors()[0];
            // TODO: cache args
            Object[] args = Arrays.stream(constructor.getParameters()).map(parameter -> {
                if (parameter.getType() == AspectInvocationHandler.class) {
                    return null;
                }

                Bean bean = parameter.getAnnotation(Bean.class);
                if (bean != null) {
                    return context.getBean(parameter.getType(), bean.id());
                }

                return context.getBean(parameter.getType());
            }).toArray();

            T t = (T) constructor.newInstance(args);
            Field field = t.getClass().getDeclaredField("invocationHandler");
            field.setAccessible(true);
            field.set(t, new AspectInvocationHandler(context, object));

            return t;
        } catch (Exception e) {
            throw ExceptionUtils.wrapToRuntime(e);
        }
    }

    @NotNull
    public static <T> T proxyIfNeeded(@NotNull T object, @NotNull Class<?> objectClass, @NotNull ApplicationContext context) {
        if (ProxyClass.class.isAssignableFrom(object.getClass())) {
            return object;
        }

        return newProxyInstance(object, objectClass, context);
    }

    public static class AspectInterceptor {

        public static final AspectInterceptor INSTANCE = new AspectInterceptor();

        private AspectInterceptor() {

        }

        @RuntimeType
        @Nullable
        public Object onMethodCall(@This @NotNull Object proxyObject,
                                   @Origin @NotNull Method method,
                                   @AllArguments @Nullable Object @NotNull [] args) {
            try {
                Class<?> proxyClass = proxyObject.getClass();

                Field field = proxyClass.getDeclaredField("invocationHandler");
                field.setAccessible(true);
                AspectInvocationHandler invocationHandler = (AspectInvocationHandler) field.get(proxyObject);

                Object result = invocationHandler.invoke(proxyObject, method, args);
                if (result != null) {
                    return proxyIfNeeded(result, method.getReturnType(), invocationHandler.getContext());
                }

                return null;
            } catch (Throwable e) {
                throw ExceptionUtils.wrapToRuntime(e);
            }
        }
    }
}
