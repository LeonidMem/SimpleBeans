package ru.leonidm.simplebeans.proxy;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joor.Reflect;
import ru.leonidm.simplebeans.SimpleBeans;
import ru.leonidm.simplebeans.applications.ApplicationContext;
import ru.leonidm.simplebeans.beans.Bean;
import ru.leonidm.simplebeans.logger.LoggerAdapter;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.io.IOException;
import java.lang.reflect.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public final class AdvancedProxy {

    private static final Map<Class<?>, Class<?>> PROXIED_CLASSES = new HashMap<>();
    private static final Map<Class<?>, Method[]> REAL_METHOD_INDEXES = new HashMap<>();


    private AdvancedProxy() {

    }

    @NotNull
    private static <T> T newProxyInstance(@NotNull T object, @NotNull Class<?> objectClass, @NotNull ApplicationContext context) {
        if (objectClass.isInterface()) {
            Object proxy = Proxy.newProxyInstance(SimpleBeans.class.getClassLoader(), new Class[]{objectClass}, new AspectInvocationHandler(context, object));
            return (T) proxy;
        }

        if (Modifier.isFinal(objectClass.getModifiers())) {
            LoggerAdapter.get().warn("Cannot proxy %s because it is final class".formatted(objectClass.getName()));
            return object;
        }

        Class<?> proxyClass = PROXIED_CLASSES.computeIfAbsent(objectClass, k -> {
            String newPackageName = "ru.leonidm.simplebeans.proxy.generated." + object.getClass().getName();
            String newClassName = "Proxied" + objectClass.getSimpleName();

            Constructor<?>[] constructors = objectClass.getDeclaredConstructors();
            if (constructors.length != 1) {
                throw new IllegalStateException("Class %s must have one and only one constructor to be proxied".formatted(objectClass.getName()));
            }

            Constructor<?> constructor = constructors[0];
            int constructorModifiers = constructor.getModifiers();
            if (!Modifier.isPublic(constructorModifiers) && !Modifier.isProtected(constructorModifiers)) {
                throw new IllegalStateException("Class %s must have public or protected constructor to be proxied".formatted(objectClass.getName()));
            }

            StringBuilder sourceCodeBuilder = new StringBuilder();
            sourceCodeBuilder.append("package ").append(newPackageName).append(";\n\n");
            sourceCodeBuilder.append("public class ").append(newClassName).append(" extends ").append(objectClass.getName())
                    .append(" implements ru.leonidm.simplebeans.proxy.ProxyClass {\n\n");
            sourceCodeBuilder.append("    private final ru.leonidm.simplebeans.proxy.AspectInvocationHandler invocationHandler;\n\n");

            sourceCodeBuilder.append("    public ").append(newClassName).append("(ru.leonidm.simplebeans.proxy.AspectInvocationHandler invocationHandler");
            for (Parameter parameter : constructor.getParameters()) {
                sourceCodeBuilder.append(", ").append(parameter.getType().getTypeName()).append(" ").append(parameter.getName());
            }

            String constructorsArgsNames = Arrays.stream(constructor.getParameters())
                    .map(Parameter::getName)
                    .collect(Collectors.joining(", "));

            sourceCodeBuilder.append(") {\n");
            sourceCodeBuilder.append("        super(" + constructorsArgsNames + ");\n");
            sourceCodeBuilder.append("        this.invocationHandler = invocationHandler;\n");
            sourceCodeBuilder.append("    }\n\n");

            Set<Method> methodsSet = new HashSet<>(List.of(objectClass.getDeclaredMethods()));
            methodsSet.addAll(List.of(objectClass.getMethods()));

            Method[] methods = methodsSet.toArray(Method[]::new);
            REAL_METHOD_INDEXES.put(objectClass, methods);
            for (int index = 0; index < methods.length; index++) {
                Method method = methods[index];

                int modifiers = method.getModifiers();
                if (Modifier.isFinal(modifiers)) {
                    if (method.getDeclaringClass() != Object.class) {
                        LoggerAdapter.get().warn("Cannot proxy %s because it is final method".formatted(method));
                    }
                    continue;
                }

                if (Modifier.isPrivate(modifiers)) {
                    LoggerAdapter.get().warn("Cannot proxy %s because it is private method".formatted(method));
                    continue;
                }

                String accessKey;
                if (Modifier.isPublic(modifiers)) {
                    accessKey = "public ";
                } else if (Modifier.isProtected(modifiers)) {
                    accessKey = "protected ";
                } else {
                    accessKey = "";
                }

                sourceCodeBuilder.append("    ").append(accessKey).append(method.getReturnType().getTypeName()).append(" ").append(method.getName()).append("(");

                String params = Arrays.stream(method.getParameters()).map(parameter -> parameter.getType().getTypeName() + " " + parameter.getName())
                        .collect(Collectors.joining(", "));
                sourceCodeBuilder.append(params).append(") {\n");

                sourceCodeBuilder.append("        ");

                if (method.getReturnType() != Void.TYPE) {
                    sourceCodeBuilder.append("return (").append(method.getReturnType().getTypeName()).append(") ");
                }

                sourceCodeBuilder.append("ru.leonidm.simplebeans.proxy.AdvancedProxy.onMethodCall(this, ").append(index).append(", ");

                if (method.getParameterCount() == 0) {
                    sourceCodeBuilder.append("null");
                } else {
                    String args = Arrays.stream(method.getParameters()).map(Parameter::getName).collect(Collectors.joining(", "));
                    sourceCodeBuilder.append("new Object[]{").append(args).append("}");
                }
                sourceCodeBuilder.append(");\n");

                sourceCodeBuilder.append("    }\n\n");
            }

            sourceCodeBuilder.append("}\n");

            try {
                Files.writeString(Path.of(objectClass.getSimpleName() + ".java"), sourceCodeBuilder);
            } catch (IOException e) {
                e.printStackTrace();
            }

            return Reflect.compile(newPackageName + "." + newClassName, sourceCodeBuilder.toString()).get();
        });

        try {
            Constructor<?> constructor = proxyClass.getConstructors()[0];
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

            args[0] = new AspectInvocationHandler(context, object);

            return (T) constructor.newInstance(args);
        } catch (Exception e) {
            throw ExceptionUtils.wrapToRuntime(e);
        }
    }

    @NotNull
    public static <T> T proxyIfNeeded(T object, @NotNull Class<?> objectClass, @NotNull ApplicationContext context) {
        if (ProxyClass.class.isAssignableFrom(object.getClass())) {
            return object;
        }

        return newProxyInstance(object, objectClass, context);
    }

    @Nullable
    public static Object onMethodCall(@NotNull Object proxyObject, int methodIndex, @Nullable Object @Nullable [] args) {
        try {
            Class<?> proxyClass = proxyObject.getClass();
            Class<?> realClass = proxyClass.getSuperclass();
            Method method = REAL_METHOD_INDEXES.get(realClass)[methodIndex];

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
