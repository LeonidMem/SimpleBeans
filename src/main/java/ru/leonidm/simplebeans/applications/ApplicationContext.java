package ru.leonidm.simplebeans.applications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import ru.leonidm.simplebeans.beans.*;
import ru.leonidm.simplebeans.proxy.AspectInvocationHandler;
import ru.leonidm.simplebeans.proxy.ProxyClass;
import ru.leonidm.simplebeans.proxy.aspects.*;
import ru.leonidm.simplebeans.utils.BcelClassScanner;
import ru.leonidm.simplebeans.utils.ExceptionUtils;
import ru.leonidm.simplebeans.utils.GeneralUtils;
import ru.leonidm.simplebeans.utils.Pair;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ApplicationContext {

    private final Map<Pair<Class<?>, String>, Object> beanClassToInstance = new HashMap<>();
    private final LinkedList<BeanInitializer<?>> waitingInitializers = new LinkedList<>();
    private final Set<WrappedPointCut> pointCuts = new HashSet<>();
    private final Map<Method, EnumMap<PointCutType, List<WrappedPointCut>>> pointCutsCache = new HashMap<>();

    public ApplicationContext(@NotNull Class<?> applicationClass) {
        BcelClassScanner bcelClassScanner = BcelClassScanner.of(GeneralUtils.getDeclaringJarFile(applicationClass));

        List<Class<?>> beansClasses = new ArrayList<>();

        Optional<Class<?>> optionalProxyClass = bcelClassScanner.getImplementationsOf(ProxyClass.class, false).stream().findAny();
        if (optionalProxyClass.isPresent()) {
            throw new IllegalStateException("Class %s implements ProxyClass that is forbidden".formatted(optionalProxyClass.get()));
        }

        bcelClassScanner.getTypesAnnotatedWith(RegisterAsBean.class).forEach(annotationClass -> {
            if (!annotationClass.isAnnotation()) {
                throw new IllegalStateException("Found @RegisterAsBean on %s, but this annotation can annotate only other annotations"
                        .formatted(annotationClass.getName()));
            }

            bcelClassScanner.getTypesAnnotatedWith(annotationClass.asSubclass(Annotation.class)).forEach(beanClass -> {
                if (!beanClassToInstance.containsKey(Pair.of(beanClass, ""))) {
                    try {
                        Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
                        if (constructors.length != 1) {
                            throw new IllegalStateException("Bean %s must have one and only one constructor".formatted(beanClass.getName()));
                        }

                        BeanInitializer<?> beanInitializer = BeanInitializer.of(constructors[0], this);
                        initializeIfCan(beanInitializer);
                        beansClasses.add(beanClass);
                    } catch (Exception e) {
                        throw ExceptionUtils.wrapToRuntime(e);
                    }
                }
            });
        });

        bcelClassScanner.getTypesAnnotatedWith(Configuration.class).forEach(configurationClass -> {
            Arrays.stream(configurationClass.getDeclaredMethods())
                    .filter(method -> method.isAnnotationPresent(Bean.class))
                    .filter(method -> method.getReturnType() != Void.TYPE)
                    .map(method -> {
                        beansClasses.add(method.getReturnType());
                        return BeanInitializer.of(method, method.getAnnotation(Bean.class).id(), this);
                    })
                    .forEach(this::initializeIfCan);
        });

        int previousSize = -1;
        while (previousSize != waitingInitializers.size() && !waitingInitializers.isEmpty()) {
            previousSize = waitingInitializers.size();

            waitingInitializers.removeIf(initializer -> {
                if (initializer.canCreate()) {
                    beanClassToInstance.put(toKeyPair(initializer), initializer.create());
                    return true;
                }

                return false;
            });
        }

        if (!waitingInitializers.isEmpty()) {
            throw new IllegalStateException("Cannot load beans %s".formatted(waitingInitializers));
        }

        beansClasses.stream().map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .filter(field -> field.isAnnotationPresent(Autowired.class))
                .forEach(field -> {
                    field.setAccessible(true);

                    Autowired autowired = field.getAnnotation(Autowired.class);

                    Object declaredBean = getNonProxiedBean(field.getDeclaringClass());
                    Object autowiredBean = getBean(field.getType(), autowired.id());

                    try {
                        field.set(declaredBean, autowiredBean);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }
                });

        beansClasses.stream().map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .filter(method -> method.isAnnotationPresent(Autowired.class))
                .forEach(method -> {
                    method.setAccessible(true);

                    Autowired autowired = method.getAnnotation(Autowired.class);

                    Object declaredBean = getNonProxiedBean(method.getDeclaringClass());

                    Object[] args = Arrays.stream(method.getParameters())
                            .map(parameter -> {
                                Bean bean = parameter.getAnnotation(Bean.class);
                                if (bean != null) {
                                    return getBean(parameter.getType(), bean.id());
                                }

                                return getBean(parameter.getType(), autowired.id());
                            })
                            .toArray();

                    try {
                        method.invoke(declaredBean, args);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                });

        bcelClassScanner.getMethodsAnnotatedWith(Before.class).forEach(pointCut -> {
            if (pointCut.getReturnType() != Void.TYPE) {
                throw new IllegalStateException("@Before point cut %s must return void".formatted(pointCut));
            }

            registerPointCut(pointCut, Before.class, Before::value, PointCutType.BEFORE);
        });

        bcelClassScanner.getMethodsAnnotatedWith(After.class).forEach(pointCut -> {
            registerPointCut(pointCut, After.class, After::value, PointCutType.AFTER);
        });
    }

    private void initializeIfCan(@NotNull BeanInitializer<?> beanInitializer) {
        if (!beanInitializer.canCreate()) {
            waitingInitializers.add(beanInitializer);
        } else {
            beanClassToInstance.put(toKeyPair(beanInitializer), beanInitializer.create());
        }
    }

    @NotNull
    private Object getNonProxiedBean(@NotNull Class<?> beanClass) {
        Object bean = getBean(beanClass);

        if (bean instanceof ProxyClass proxyClass) {
            try {
                Field invocationHandlerField = proxyClass.getClass().getDeclaredField("invocationHandler");
                invocationHandlerField.setAccessible(true);
                AspectInvocationHandler invocationHandler = (AspectInvocationHandler) invocationHandlerField.get(bean);
                return invocationHandler.getRealObject();
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }

        return bean;
    }

    private <A extends Annotation> void registerPointCut(@NotNull Method pointCut, @NotNull Class<A> annotationClass,
                                                         @NotNull Function<A, String> valueGetter, @NotNull PointCutType pointCutType) {
        Class<?> aspectClass = pointCut.getDeclaringClass();
        if (!aspectClass.isAnnotationPresent(Aspect.class)) {
            throw new IllegalStateException("@%s point cut %s is used not in @Aspect class".formatted(annotationClass.getSimpleName(), pointCut));
        }

        A annotation = pointCut.getAnnotation(annotationClass);
        WrappedPointCut wrappedPointCut = WrappedPointCut.of(getBean(aspectClass), pointCut, valueGetter.apply(annotation), pointCutType);
        pointCuts.add(wrappedPointCut);
    }

    public boolean hasBean(@NotNull Class<?> beanClass) {
        return hasBean(beanClass, "");
    }

    public boolean hasBean(@NotNull Class<?> beanClass, @NotNull String id) {
        return beanClassToInstance.containsKey(Pair.of(beanClass, id));
    }

    @NotNull
    public <B> B getBean(@NotNull Class<B> beanClass) {
        return getBean(beanClass, "");
    }

    @NotNull
    public <B> B getBean(@NotNull Class<B> beanClass, @NotNull String id) {
        B bean = (B) beanClassToInstance.get(Pair.of(beanClass, id));
        if (bean == null) {
            throw new IllegalArgumentException("Bean %s does not exist".formatted(beanClass.getName()));
        }

        return bean;
    }

    @NotNull
    @Unmodifiable
    public List<WrappedPointCut> getPointCuts(@NotNull Method method, @NotNull PointCutType pointCutType) {
        return pointCutsCache.computeIfAbsent(method, k -> {
            EnumMap<PointCutType, List<WrappedPointCut>> cache = new EnumMap<>(PointCutType.class);

            List<WrappedPointCut> fitMask = pointCuts.stream()
                    .filter(wrappedPointCut -> wrappedPointCut.doesFitMask(method))
                    .collect(Collectors.toList());

            for (PointCutType pointCutType1 : PointCutType.values()) {
                List<WrappedPointCut> list = fitMask.stream()
                        .filter(wrappedPointCut -> wrappedPointCut.getPointCut() == pointCutType1)
                        .collect(Collectors.toList());

                List<WrappedPointCut> sortedList = new ArrayList<>();
                list.stream().filter(WrappedPointCut::isVoid).forEach(sortedList::add);
                list.stream().filter(wrappedPointCut -> !wrappedPointCut.isVoid()).forEach(sortedList::add);

                cache.put(pointCutType1, Collections.unmodifiableList(sortedList));
            }

            return cache;
        }).getOrDefault(pointCutType, List.of());
    }

    @NotNull
    private static Pair<Class<?>, String> toKeyPair(@NotNull BeanInitializer<?> beanInitializer) {
        return Pair.of(beanInitializer.getBeanClass(), beanInitializer.getId());
    }
}
