package ru.leonidm.simplebeans.proxy.aspects;

import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import ru.leonidm.simplebeans.utils.ExceptionUtils;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.Arrays;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class WrappedPointCut {

    @Language("RegExp")
    public static final String MASK_PATTERN = "^((?:[\\w*]+\\.|)+)([\\w*]+)(\\((((?:[\\w.*]+,\\s*)*[\\w.*]+)|\\*|\\.\\.\\.|)\\)|)$";
    public static final Pattern COMPILED_MASK_PATTERN = Pattern.compile(MASK_PATTERN);

    private static final Pattern ARGUMENTS_SEPARATOR = Pattern.compile(",\\s*");

    private final PointCutHandler pointCutHandler;
    private final Predicate<Method> methodPredicate;
    private final PointCutType pointCutType;
    private final boolean isVoid;

    private WrappedPointCut(@NotNull PointCutHandler pointCutHandler, @NotNull Predicate<Method> methodPredicate,
                            @NotNull PointCutType pointCutType, boolean isVoid) {
        this.pointCutHandler = pointCutHandler;
        this.methodPredicate = methodPredicate;
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
        }, buildMask(mask), pointCutType, pointCut.getReturnType() == Void.TYPE);
    }

    @NotNull
    public static Predicate<Method> buildMask(@NotNull String mask) {
        Matcher matcher = COMPILED_MASK_PATTERN.matcher(mask);
        if (!matcher.matches()) {
            throw new IllegalStateException("Got bad mask '%s'".formatted(mask));
        }

        String rawClassMask = matcher.group(1);
        if (rawClassMask.endsWith(".")) {
            rawClassMask = rawClassMask.substring(0, rawClassMask.length() - 1);
        }

        StringBuilder classMaskBuilder = new StringBuilder(rawClassMask.length());
        boolean isPreviousStar = false;
        for (char chr : rawClassMask.toCharArray()) {
            if (isPreviousStar) {
                if (chr == '*') {
                    classMaskBuilder.append(".*");
                } else {
                    classMaskBuilder.append("[^\\.]*");
                }

                isPreviousStar = false;
            } else if (chr == '*') {
                isPreviousStar = true;
            } else if (chr == '.') {
                classMaskBuilder.append("\\.");
            } else {
                classMaskBuilder.append(chr);
            }
        }

        String classMask = classMaskBuilder.toString();

        String methodNameMask = matcher.group(2).replace("*", "[^\\.]*");
        String argumentsMask;

        argumentsMask = switch (matcher.groupCount()) {
            case 2 -> "*";
            case 3 -> "";
            case 5 -> matcher.group(5);
            default -> {
                throw new IllegalStateException("Got bad mask '%s'".formatted(mask));
            }
        };

        Predicate<Method> argumentsPredicate;

        if (argumentsMask == null || argumentsMask.equals("*") || argumentsMask.equals("...")) {
            argumentsPredicate = (method) -> true;
        } else if (argumentsMask.isEmpty()) {
            argumentsPredicate = (method) -> method.getParameterCount() == 0;
        } else {
            String[] arguments = ARGUMENTS_SEPARATOR.split(argumentsMask);
            Pattern[] compiledArguments = new Pattern[arguments.length];
            for (int i = 0; i < arguments.length; i++) {
                compiledArguments[i] = Pattern.compile(
                        "^" + arguments[i].replace("**", ".+").replace("*", "[^\\.]+") + "$"
                );
            }

            argumentsPredicate = (method) -> {
                if (method.getParameterCount() != compiledArguments.length) {
                    return false;
                }

                Class<?>[] parameters = method.getParameterTypes();
                for (int i = 0; i < parameters.length; i++) {
                    if (!compiledArguments[i].matcher(parameters[i].getName()).matches()) {
                        return false;
                    }
                }

                return true;
            };
        }

        Predicate<Method> classPredicate;

        if (classMask.isEmpty() || classMask.equals(".+")) {
            classPredicate = (method) -> true;
        } else {
            Pattern compiledClass = Pattern.compile("^" + classMask + "$");
            classPredicate = (method) -> {
                return compiledClass.matcher(method.getDeclaringClass().getName()).matches();
            };
        }

        Predicate<Method> methodNamePredicate;

        if (methodNameMask.equals("[^\\.]+")) {
            methodNamePredicate = (method) -> true;
        } else {
            Pattern compiledMethodName = Pattern.compile("^" + methodNameMask + "$");
            methodNamePredicate = (method) -> {
                return compiledMethodName.matcher(method.getName()).matches();
            };
        }

        return (method) -> {
            return methodNamePredicate.test(method) && classPredicate.test(method) && argumentsPredicate.test(method);
        };
    }

    public boolean doesFitMask(@NotNull Method method) {
        return methodPredicate.test(method);
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
