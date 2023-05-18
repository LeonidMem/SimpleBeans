package ru.leonidm.simplebeans.applications;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;
import org.jetbrains.annotations.UnmodifiableView;
import ru.leonidm.simplebeans.beans.Autowired;
import ru.leonidm.simplebeans.beans.Bean;
import ru.leonidm.simplebeans.beans.BeanData;
import ru.leonidm.simplebeans.beans.BeanInitializer;
import ru.leonidm.simplebeans.beans.BeansDependencyTree;
import ru.leonidm.simplebeans.beans.Component;
import ru.leonidm.simplebeans.beans.Configuration;
import ru.leonidm.simplebeans.proxy.AspectInvocationHandler;
import ru.leonidm.simplebeans.proxy.ProxyClass;
import ru.leonidm.simplebeans.proxy.aspects.After;
import ru.leonidm.simplebeans.proxy.aspects.Aspect;
import ru.leonidm.simplebeans.proxy.aspects.Before;
import ru.leonidm.simplebeans.proxy.aspects.PointCutType;
import ru.leonidm.simplebeans.proxy.aspects.WrappedPointCut;
import ru.leonidm.simplebeans.utils.BcelClassScanner;
import ru.leonidm.simplebeans.utils.ExceptionUtils;
import ru.leonidm.simplebeans.utils.GeneralUtils;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class ApplicationContext {

    private static final String BASE_PACKAGE_NAME = "ru.leonidm.simplebeans.";
    private static final Map<Class<?>, ApplicationContext> APPLICATION_CLASS_TO_CONTEXT = new HashMap<>();
    private final String packageName;
    private final ApplicationProperties properties;
    private final BcelClassScanner bcelClassScanner;
    private final Map<BeanData, Object> beanClassToInstance = new HashMap<>();
    private final Set<WrappedPointCut> pointCuts = new HashSet<>();
    private final Map<Method, EnumMap<PointCutType, List<WrappedPointCut>>> pointCutsCache = new HashMap<>();

    public ApplicationContext(@NotNull Class<?> applicationClass) {
        Application application = applicationClass.getAnnotation(Application.class);
        if (application == null) {
            throw new IllegalArgumentException("Application class %s does not contains @Application".formatted(applicationClass.getName()));
        }

        addBean(ApplicationContext.class, this);

        String rawPackageName = application.packageName();
        if (rawPackageName.isEmpty()) {
            packageName = applicationClass.getPackageName() + '.';
        } else {
            packageName = rawPackageName.endsWith(".") ? rawPackageName : rawPackageName + '.';
        }

        properties = new ApplicationProperties(applicationClass);
        APPLICATION_CLASS_TO_CONTEXT.put(applicationClass, this);

        Set<File> classPath = GeneralUtils.getClassPathFiles();
        bcelClassScanner = BcelClassScanner.of(classPath, classPath);

        List<Class<?>> beansClasses = new ArrayList<>();

        Optional<Class<?>> optionalProxyClass = bcelClassScanner.getImplementationsOf(ProxyClass.class, false).stream().findAny();
        if (optionalProxyClass.isPresent()) {
            throw new IllegalStateException("Class %s implements ProxyClass that is forbidden".formatted(optionalProxyClass.get()));
        }

        BeansDependencyTree dependencyTree = new BeansDependencyTree(this);

        bcelClassScanner.getTypesAnnotatedWith(Component.class).forEach(annotationClass -> {
            if (!annotationClass.isAnnotation()) {
                return;
            }

            if (!contains(annotationClass)) {
                return;
            }

            bcelClassScanner.getTypesAnnotatedWith(annotationClass.asSubclass(Annotation.class)).forEach(beanClass -> {
                if (annotationClass == Component.class && beanClass.isAnnotation()) {
                    return;
                }

                if (!contains(beanClass)) {
                    return;
                }

                if (!beanClassToInstance.containsKey(new BeanData(beanClass, ""))) {
                    try {
                        Constructor<?>[] constructors = beanClass.getDeclaredConstructors();
                        if (constructors.length != 1) {
                            throw new IllegalStateException("Bean %s must have only one constructor".formatted(beanClass.getName()));
                        }

                        BeanInitializer<?> beanInitializer = BeanInitializer.of(constructors[0], this);
                        dependencyTree.add(beanInitializer);
                        beansClasses.add(beanClass);
                    } catch (Exception e) {
                        throw ExceptionUtils.wrapToRuntime(e);
                    }
                }
            });
        });

        bcelClassScanner.getTypesAnnotatedWith(Configuration.class).stream()
                .filter(this::contains)
                .forEach(configurationClass -> {
                    Arrays.stream(configurationClass.getDeclaredMethods())
                            .filter(method -> method.isAnnotationPresent(Bean.class))
                            .filter(method -> method.getReturnType() != Void.TYPE)
                            .map(method -> {
                                beansClasses.add(method.getReturnType());
                                return BeanInitializer.of(method, method.getAnnotation(Bean.class).id(), this);
                            })
                            .forEach(dependencyTree::add);
                });

        dependencyTree.initializeBeans();

        beansClasses.stream().map(Class::getDeclaredFields)
                .flatMap(Arrays::stream)
                .filter(field -> field.isAnnotationPresent(Autowired.class))
                .forEach(field -> {
                    field.setAccessible(true);

                    Autowired autowired = field.getAnnotation(Autowired.class);

                    Object declaredBean = getNonProxiedBean(field.getDeclaringClass());

                    Object autowiredBean;
                    try {
                        autowiredBean = getBean(field.getType(), autowired.id());
                    } catch (IllegalArgumentException e) {
                        throw new IllegalStateException("%s depends on %s that is not reachable"
                                .formatted(new BeanData(field.getDeclaringClass(), ""), new BeanData(field.getType(), autowired.id())));
                    }

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
                                String id = bean != null ? bean.id() : autowired.id();

                                try {
                                    return getBean(parameter.getType(), id);
                                } catch (IllegalArgumentException e) {
                                    throw new IllegalStateException("%s depends on %s that is not reachable"
                                            .formatted(new BeanData(method.getDeclaringClass(), ""), new BeanData(parameter.getType(), id)));
                                }
                            })
                            .toArray();

                    try {
                        method.invoke(declaredBean, args);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new IllegalStateException(e);
                    }
                });

        // TODO: move this logic into proxy package
        beansClasses.stream()
                .filter(beanClass -> beanClass.isAnnotationPresent(Aspect.class))
                .map(Class::getDeclaredMethods)
                .flatMap(Arrays::stream)
                .forEach(advice -> {
                    if (advice.isAnnotationPresent(Before.class)) {
                        if (advice.getReturnType() != Void.TYPE) {
                            throw new IllegalStateException("@Before point cut %s must return void".formatted(advice));
                        }

                        registerAspectAdvice(advice, Before.class, Before::value, PointCutType.BEFORE);
                    }

                    if (advice.isAnnotationPresent(After.class)) {
                        registerAspectAdvice(advice, After.class, After::value, PointCutType.AFTER);
                    }
                });
    }

    @NotNull
    public static ApplicationContext fromApplicationClass(@NotNull Class<?> applicationClass) {
        return APPLICATION_CLASS_TO_CONTEXT.get(applicationClass);
    }

    private boolean contains(@NotNull Class<?> clazz) {
        return clazz.getName().startsWith(packageName) || clazz.getName().startsWith(BASE_PACKAGE_NAME);
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

    @NotNull
    public BcelClassScanner getClassScanner() {
        return bcelClassScanner;
    }

    @NotNull
    public ApplicationProperties getProperties() {
        return properties;
    }

    @NotNull
    @UnmodifiableView
    public Collection<Object> getBeans() {
        return Collections.unmodifiableCollection(beanClassToInstance.values());
    }

    private <A extends Annotation> void registerAspectAdvice(@NotNull Method pointCut, @NotNull Class<A> annotationClass,
                                                             @NotNull Function<A, String> valueGetter, @NotNull PointCutType pointCutType) {
        Class<?> aspectClass = pointCut.getDeclaringClass();
        if (!aspectClass.isAnnotationPresent(Aspect.class)) {
            throw new IllegalStateException("@%s point cut %s is used not in @Aspect class".formatted(annotationClass.getSimpleName(), pointCut));
        }

        A annotation = pointCut.getAnnotation(annotationClass);

        WrappedPointCut wrappedPointCut;
        try {
            wrappedPointCut = WrappedPointCut.of(getBean(aspectClass), pointCut, valueGetter.apply(annotation), pointCutType);
        } catch (Exception e) {
            throw new IllegalStateException("Got exception on loading pointcut %s".formatted(pointCut), e);
        }
        pointCuts.add(wrappedPointCut);
    }

    public boolean hasBean(@NotNull Class<?> beanClass) {
        return hasBean(beanClass, "");
    }

    public boolean hasBean(@NotNull Class<?> beanClass, @NotNull String id) {
        return beanClassToInstance.containsKey(new BeanData(beanClass, id));
    }

    @NotNull
    public <B> B getBean(@NotNull Class<B> beanClass) {
        return getBean(beanClass, "");
    }

    @NotNull
    public <B> B getBean(@NotNull Class<B> beanClass, @NotNull String id) {
        B bean = (B) beanClassToInstance.get(new BeanData(beanClass, id));
        if (bean == null) {
            throw new IllegalArgumentException("Bean %s does not exist".formatted(beanClass.getName()));
        }

        return bean;
    }

    public <B> void addBean(@NotNull Class<B> beanClass, @NotNull B bean) {
        addBean(beanClass, "", bean);
    }

    public <B> void addBean(@NotNull Class<B> beanClass, @NotNull String id, @NotNull B bean) {
        beanClassToInstance.put(new BeanData(beanClass, id), bean);
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
}
