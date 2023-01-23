package ru.leonidm.simplebeans.proxy.aspects;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;

public final class WrappedPointCut {

    public static final Object SAME_RESULT = new Object();

    private final PointCutHandler pointCutHandler;
    private final String mask;
    private final PointCutType pointCutType;
    private final boolean isVoid;

    private WrappedPointCut(@NotNull PointCutHandler pointCutHandler, @NotNull String mask,
                            @NotNull PointCutType pointCutType, boolean isVoid) {
        this.pointCutHandler = pointCutHandler;
        this.mask = mask;
        this.pointCutType = pointCutType;
        this.isVoid = isVoid;
    }

    @NotNull
    public static WrappedPointCut of(@NotNull Object aspectInstance, @NotNull Method pointCut, @NotNull String mask,
                                     @NotNull PointCutType pointCutType) {
        PointCutHandler[] arguments = new PointCutHandler[pointCut.getParameterCount()];

        Parameter[] parameters = pointCut.getParameters();
        for (int index = 0; index < parameters.length; index++) {
            Parameter parameter = parameters[index];
            Class<?> type = parameter.getType();
            if (type == Class.class) {
                arguments[index] = (method, args, result) -> method.getDeclaringClass();
            } else if (type == Method.class) {
                arguments[index] = (method, args, result) -> method;
            } else if (type == Object[].class) {
                arguments[index] = (method, args, result) -> args;
            } else if (type == Object.class) {
                if (pointCutType == PointCutType.BEFORE) {
                    throw new IllegalStateException("@Before point cut %s cannot access the result".formatted(pointCut));
                }

                arguments[index] = (method, args, result) -> result;
            } else {
                throw new IllegalStateException("Got unknown argument %s in point cut %s".formatted(type.getName(), pointCut));
            }
        }

        return new WrappedPointCut((method, args, result) -> {
            try {
                Object[] finalArgs = Arrays.stream(arguments).map(arg -> arg.handle(method, args, result)).toArray();
                return pointCut.invoke(aspectInstance, finalArgs);
            } catch (Exception e) {
                throw ExceptionUtils.wrapToRuntime(e);
            }
        }, mask, pointCutType, pointCut.getReturnType() == Void.TYPE);
    }

    // TODO: smarter masks
    public boolean doesFitMask(@NotNull Method method) {
        for (Class<?> currentClass = method.getDeclaringClass(); currentClass != null; currentClass = currentClass.getSuperclass()) {
            String className = method.getDeclaringClass().getName() + ".";
            if (!mask.startsWith(className)) {
                continue;
            }

            String methodName = mask.substring(className.length());
            return method.getName().equals(methodName);
        }

        return false;
    }

    @NotNull
    public PointCutType getPointCut() {
        return pointCutType;
    }

    public boolean isVoid() {
        return isVoid;
    }

    @Nullable
    public Object run(@NotNull Method method, @Nullable Object @Nullable [] args, @Nullable Object result) {
        try {
            return pointCutHandler.handle(method, args, result);
        } catch (Exception e) {
            throw ExceptionUtils.wrapToRuntime(e);
        }
    }

    @FunctionalInterface
    private interface PointCutHandler {

        @Nullable
        Object handle(@NotNull Method method, @Nullable Object @Nullable [] args, @Nullable Object result);

    }
}
