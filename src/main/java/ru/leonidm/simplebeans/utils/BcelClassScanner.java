package ru.leonidm.simplebeans.utils;

import org.apache.bcel.classfile.Field;
import org.apache.bcel.classfile.JavaClass;
import org.apache.bcel.classfile.Method;
import org.apache.bcel.generic.Type;
import org.apache.bcel.util.ClassPath;
import org.apache.bcel.util.ClassQueue;
import org.apache.bcel.util.Repository;
import org.apache.bcel.util.SyntheticRepository;
import org.jetbrains.annotations.NotNull;
import ru.leonidm.commons.functions.Unchecked;
import ru.leonidm.simplebeans.SimpleBeans;
import ru.leonidm.simplebeans.logger.LoggerAdapter;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

public final class BcelClassScanner {

    private static final Set<String> PRINTED_ABOUT = new HashSet<>();
    private static final Map<Set<File>, BcelClassScanner> APPLICATION_TO_SCANNER = new HashMap<>();

    private final Repository repository;
    private final Set<String> classNames;
    private final ClassLoader classLoader;

    public BcelClassScanner(@NotNull ClassLoader mainClassLoader, @NotNull Collection<File> files, @NotNull Collection<File> scanFiles) {
        String fullPath = files.stream()
                .map(File::getPath)
                .collect(Collectors.joining(File.pathSeparator));

        Set<String> modifiableClassNames = new HashSet<>();

        JarFile currentJarFile = null;
        try (ClassPath classPath = new ClassPath(SyntheticRepository.getInstance().getClassPath(), fullPath)) {
            repository = SyntheticRepository.getInstance(classPath);

            for (File file : files) {

                Iterator<String> classIterator;
                if (file.isDirectory()) {
                    classIterator = new DirClassIterator(file);
                } else {
                    currentJarFile = new JarFile(file);
                    classIterator = new JarClassIterator(currentJarFile);
                }

                while (classIterator.hasNext()) {
                    String className = classIterator.next();
                    repository.loadClass(className);
                    if (scanFiles.contains(file)) {
                        modifiableClassNames.add(className);
                    }
                }

                if (currentJarFile != null) {
                    currentJarFile.close();
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } finally {
            try {
                if (currentJarFile != null) {
                    currentJarFile.close();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        classNames = Collections.unmodifiableSet(modifiableClassNames);
        classLoader = mainClassLoader;
    }

    @NotNull
    public static BcelClassScanner of(@NotNull File file) {
        Set<File> files = Collections.singleton(file);
        return BcelClassScanner.of(files, files);
    }

    @NotNull
    public static BcelClassScanner of(@NotNull Collection<File> files, @NotNull Collection<File> scanFiles) {
        return APPLICATION_TO_SCANNER.computeIfAbsent(Set.copyOf(files), k -> {
            return new BcelClassScanner(SimpleBeans.class.getClassLoader(), files, scanFiles);
        });
    }

    @NotNull
    public Repository getRepository() {
        return repository;
    }

    private static void cannotCheck(@NotNull String className) {
        String[] packages = className.split("\\.");
        String key;
        if (packages.length > 3) {
            key = packages[0] + '.' + packages[1] + '.' + packages[2];
        } else if (packages.length == 3) {
            key = packages[0] + '.' + packages[1];
        } else if (packages.length == 2) {
            key = packages[0];
        } else {
            key = "<empty>";
        }

        if (PRINTED_ABOUT.add(key)) {
            LoggerAdapter.get().warn("[BcelClassScanner] Cannot load class of {}.* to check its super classes", key);
        }
    }

    @NotNull
    private List<String> getSuperClassesNamesSafety(@NotNull JavaClass clazz) {
        List<String> allSuperClasses = new ArrayList<>();
        String superClassName = clazz.getSuperclassName();
        try {
            for (; clazz != null; clazz = clazz.getSuperClass()) {
                superClassName = clazz.getSuperclassName();
                allSuperClasses.add(superClassName);
            }
        } catch (Exception e) {
            cannotCheck(superClassName);

            if (!allSuperClasses.contains("java.lang.Object")) {
                allSuperClasses.add("java.lang.Object");
            }
        }

        return allSuperClasses;
    }

    @NotNull
    private Set<String> getInterfacesNamesSafety(@NotNull JavaClass clazz) {
        ClassQueue queue = new ClassQueue();
        Set<String> allInterfacesNames = new HashSet<>();

        queue.enqueue(clazz);

        while (!queue.empty()) {
            JavaClass clazz1 = queue.dequeue();
            String className = clazz1.getClassName();

            try {
                allInterfacesNames.addAll(List.of(clazz1.getInterfaceNames()));

                className = clazz1.getSuperclassName();
                JavaClass superClass = clazz1.getSuperClass();
                if (!clazz.isInterface() && superClass != null) {
                    queue.enqueue(superClass);
                }
            } catch (Exception e) {
                cannotCheck(className);
            }
        }
        return allInterfacesNames;
    }

    @NotNull
    public Set<Class<?>> loadClasses(@NotNull Set<JavaClass> wrappedClasses) {
        return wrappedClasses.stream()
                .map(JavaClass::getClassName)
                .map(Unchecked.function(classLoader::loadClass))
                .collect(Collectors.toSet());
    }

    @NotNull
    public Set<JavaClass> getWrappedClasses(@NotNull Predicate<JavaClass> classPredicate) {
        Set<JavaClass> classes = new HashSet<>();
        for (String className : classNames) {
            JavaClass javaClass = repository.findClass(className);
            if (classPredicate.test(javaClass)) {
                classes.add(javaClass);
            }
        }

        return classes;
    }

    @NotNull
    public Set<Class<?>> getClasses(@NotNull Predicate<JavaClass> classPredicate) {
        return loadClasses(getWrappedClasses(classPredicate));
    }

    @NotNull
    public Set<JavaClass> getWrappedTypesAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        String annotationClassName = "L" + annotationClass.getName().replace('.', '/') + ";";
        return getWrappedClasses(javaClass -> {
            return Arrays.stream(javaClass.getAnnotationEntries())
                    .anyMatch(annotationEntry -> annotationEntry.getAnnotationType().equals(annotationClassName));
        });
    }

    @NotNull
    public Set<Class<?>> getTypesAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        return loadClasses(getWrappedTypesAnnotatedWith(annotationClass));
    }

    @NotNull
    public Set<JavaClass> getWrappedSubclassesOf(@NotNull Class<?> clazz, boolean deep) {
        if (clazz.isInterface()) {
            throw new IllegalArgumentException("Got interface %s".formatted(clazz.getName()));
        }

        String className = clazz.getName();

        if (deep) {
            return getWrappedClasses(javaClass -> {
                return getSuperClassesNamesSafety(javaClass).stream()
                        .anyMatch(className::equals);
            });
        }

        return getWrappedClasses(javaClass -> {
            return className.equals(javaClass.getSuperclassName());
        });
    }

    @NotNull
    public Set<Class<?>> getSubclassesOf(@NotNull Class<?> clazz, boolean deep) {
        return loadClasses(getWrappedSubclassesOf(clazz, deep));
    }

    @NotNull
    public Set<JavaClass> getWrappedImplementationsOf(@NotNull Class<?> clazz, boolean deep) {
        if (!clazz.isInterface()) {
            throw new IllegalArgumentException("Got non-interface %s".formatted(clazz.getName()));
        }

        String interfaceName = clazz.getName();

        if (deep) {
            return getWrappedClasses(javaClass -> {
                return getInterfacesNamesSafety(javaClass).stream()
                        .anyMatch(interfaceName::equals);
            });
        }

        return getWrappedClasses(javaClass -> {
            return Arrays.asList(javaClass.getInterfaceNames()).contains(interfaceName);
        });
    }

    @NotNull
    public Set<Class<?>> getImplementationsOf(@NotNull Class<?> clazz, boolean deep) {
        return loadClasses(getWrappedImplementationsOf(clazz, deep));
    }

    @NotNull
    public Set<JavaClass> getWrappedInheritorsOf(@NotNull Class<?> clazz, boolean deep) {
        if (clazz.isInterface()) {
            return getWrappedImplementationsOf(clazz, deep);
        } else {
            return getWrappedSubclassesOf(clazz, deep);
        }
    }

    @NotNull
    public Set<Class<?>> getInheritorsOf(@NotNull Class<?> clazz, boolean deep) {
        return loadClasses(getWrappedInheritorsOf(clazz, deep));
    }


    @NotNull
    private <E extends Executable> Set<E> loadExecutable(@NotNull Set<WrappedExecutable> wrappedExecutables) {
        Set<Executable> executables = wrappedExecutables.stream()
                .map(Unchecked.function(wrappedExecutable -> {
                    JavaClass javaClass = wrappedExecutable.javaClass;
                    Class<?> executableClass = classLoader.loadClass(javaClass.getClassName());

                    Method executable = wrappedExecutable.method;
                    String name = executable.getName();

                    Class<?>[] arguments = Arrays.stream(executable.getArgumentTypes())
                            .map(Type::getClassName)
                            .map(Unchecked.function(className -> {
                                if (className.charAt(0) == '[') {
                                    Class<?> clazz;
                                    if (className.length() == 2) {
                                        clazz = switch (className.charAt(1)) {
                                            case 'Z' -> Boolean.TYPE;
                                            case 'B' -> Byte.TYPE;
                                            case 'C' -> Character.TYPE;
                                            case 'S' -> Short.TYPE;
                                            case 'I' -> Integer.TYPE;
                                            case 'J' -> Long.TYPE;
                                            case 'F' -> Float.TYPE;
                                            case 'D' -> Double.TYPE;
                                            default -> throw new IllegalArgumentException("Cannot understand primitive class %s"
                                                    .formatted(className.charAt(1)));
                                        };
                                    } else {
                                        clazz = classLoader.loadClass(className.substring(2, className.length() - 1).replace('/', '.'));
                                    }

                                    return Array.newInstance(clazz, 0).getClass();
                                }

                                return classLoader.loadClass(className);
                            }))
                            .toArray(Class[]::new);

                    if (name.equals("<init>")) {
                        return executableClass.getDeclaredConstructor(arguments);
                    } else {
                        return executableClass.getDeclaredMethod(name, arguments);
                    }
                }))
                .collect(Collectors.toSet());
        return (Set<E>) executables;
    }

    @NotNull
    private Set<WrappedExecutable> getWrappedExecutables(@NotNull Predicate<Method> executablePredicate, boolean constructor) {
        Set<WrappedExecutable> methods = new HashSet<>();
        for (String className : classNames) {
            JavaClass javaClass = repository.findClass(className);

            Arrays.stream(javaClass.getMethods())
                    .filter(method -> constructor == method.getName().equals("<init>"))
                    .filter(executablePredicate)
                    .map(method -> new WrappedExecutable(javaClass, method, constructor))
                    .forEach(methods::add);
        }

        return methods;
    }

    @NotNull
    public Set<java.lang.reflect.Method> loadMethods(@NotNull Set<WrappedExecutable> wrappedMethods) {
        return loadExecutable(wrappedMethods);
    }

    @NotNull
    public Set<WrappedExecutable> getWrappedMethods(@NotNull Predicate<Method> methodPredicate) {
        return getWrappedExecutables(methodPredicate, false);
    }

    @NotNull
    public Set<WrappedExecutable> getWrappedMethodsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        String annotationClassName = "L" + annotationClass.getName().replace('.', '/') + ";";
        return getWrappedMethods(method -> {
            return Arrays.stream(method.getAnnotationEntries())
                    .anyMatch(annotationEntry -> annotationEntry.getAnnotationType().equals(annotationClassName));
        });
    }

    @NotNull
    public Set<java.lang.reflect.Method> getMethodsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        return loadMethods(getWrappedMethodsAnnotatedWith(annotationClass));
    }


    @NotNull
    public Set<Constructor<Object>> loadConstructors(@NotNull Set<WrappedExecutable> wrappedMethods) {
        return loadExecutable(wrappedMethods);
    }

    @NotNull
    public Set<WrappedExecutable> getWrappedConstructors(@NotNull Predicate<Method> methodPredicate) {
        return getWrappedExecutables(methodPredicate, true);
    }

    @NotNull
    public Set<WrappedExecutable> getWrappedConstructorsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        String annotationClassName = "L" + annotationClass.getName().replace('.', '/') + ";";
        return getWrappedConstructors(method -> {
            return Arrays.stream(method.getAnnotationEntries())
                    .anyMatch(annotationEntry -> annotationEntry.getAnnotationType().equals(annotationClassName));
        });
    }

    @NotNull
    public Set<Constructor<Object>> getConstructorsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        return loadConstructors(getWrappedConstructorsAnnotatedWith(annotationClass));
    }


    @NotNull
    public Set<java.lang.reflect.Field> loadFields(@NotNull Set<WrappedField> wrappedFields) {
        return wrappedFields.stream()
                .map(Unchecked.function(wrappedField -> {
                    JavaClass javaClass = wrappedField.javaClass;
                    Class<?> methodClass = classLoader.loadClass(javaClass.getClassName());

                    Field field = wrappedField.field;
                    String name = field.getName();
                    return methodClass.getDeclaredField(name);
                }))
                .collect(Collectors.toSet());
    }

    @NotNull
    public Set<WrappedField> getWrappedFields(@NotNull Predicate<Field> fieldPredicate) {
        Set<WrappedField> fields = new HashSet<>();
        for (String className : classNames) {
            JavaClass javaClass = repository.findClass(className);

            Arrays.stream(javaClass.getFields())
                    .filter(fieldPredicate)
                    .map(method -> new WrappedField(javaClass, method))
                    .forEach(fields::add);
        }

        return fields;
    }

    @NotNull
    public Set<WrappedField> getWrappedFieldsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        String annotationClassName = "L" + annotationClass.getName().replace('.', '/') + ";";
        return getWrappedFields(method -> {
            return Arrays.stream(method.getAnnotationEntries())
                    .anyMatch(annotationEntry -> annotationEntry.getAnnotationType().equals(annotationClassName));
        });
    }

    @NotNull
    public Set<java.lang.reflect.Field> getFieldsAnnotatedWith(@NotNull Class<? extends Annotation> annotationClass) {
        return loadFields(getWrappedFieldsAnnotatedWith(annotationClass));
    }

    @FunctionalInterface
    private interface ExecutableGetter<E extends Executable> {
        E get(@NotNull Class<?> clazz, @NotNull String name, @NotNull Class<?>[] arguments) throws Exception;
    }

    public static final class WrappedExecutable {

        private final JavaClass javaClass;
        private final Method method;
        private final boolean constructor;

        private WrappedExecutable(@NotNull JavaClass javaClass, @NotNull Method method, boolean constructor) {
            this.javaClass = javaClass;
            this.method = method;
            this.constructor = constructor;
        }

        @NotNull
        public JavaClass getJavaClass() {
            return javaClass;
        }

        @NotNull
        public Method getMethod() {
            return method;
        }

        public boolean isConstructor() {
            return constructor;
        }
    }

    public static final class WrappedField {

        private final JavaClass javaClass;
        private final Field field;

        private WrappedField(@NotNull JavaClass javaClass, @NotNull Field field) {
            this.javaClass = javaClass;
            this.field = field;
        }

        @NotNull
        public JavaClass getJavaClass() {
            return javaClass;
        }

        @NotNull
        public Field getField() {
            return field;
        }
    }

    @NotNull
    private static String normalizeClassName(@NotNull String path) {
        return path.substring(0, path.lastIndexOf(".")).replace('/', '.').replace('\\', '.');
    }

    private static final class JarClassIterator implements Iterator<String> {

        private final Enumeration<JarEntry> entries;
        private String nextElement;

        public JarClassIterator(@NotNull JarFile jarFile) {
            this.entries = jarFile.entries();
            nextElement = next();
        }

        @Override
        public boolean hasNext() {
            return nextElement != null;
        }

        @Override
        public String next() {
            String outElement = nextElement;

            nextElement = null;
            while (entries.hasMoreElements() && nextElement == null) {
                JarEntry jarEntry = entries.nextElement();
                if (isClassEntry(jarEntry)) {
                    nextElement = jarEntry.getName().substring(0, jarEntry.getName().lastIndexOf(".")).replace("/", ".");
                    break;
                }
            }

            return outElement;
        }

        private static boolean isClassEntry(@NotNull JarEntry jarEntry) {
            if (jarEntry.isDirectory()) {
                return false;
            }

            String entryName = jarEntry.getName();
            return entryName.endsWith(".class") && !entryName.endsWith("module-info.class");
        }
    }

    private static final class DirClassIterator implements Iterator<String> {

        private final LinkedList<File> directories = new LinkedList<>();
        private final LinkedList<File> classFiles = new LinkedList<>();
        private final int pathPrefixLength;

        public DirClassIterator(@NotNull File root) {
            directories.add(root);
            pathPrefixLength = root.getAbsolutePath().length() + 1;
        }

        @Override
        public boolean hasNext() {
            return !directories.isEmpty() || !classFiles.isEmpty();
        }

        @Override
        public String next() {
            while (classFiles.isEmpty()) {
                File directory = directories.pop();
                File[] files = directory.listFiles(file -> {
                    if (file.isDirectory()) {
                        return true;
                    }

                    String fileName = file.getName();
                    return fileName.endsWith(".class") && !fileName.equals("module-info.class");
                });

                for (File file : Objects.requireNonNull(files)) {
                    if (file.isDirectory()) {
                        directories.add(file);
                    } else {
                        classFiles.add(file);
                    }
                }
            }

            File file = classFiles.pop();

            return normalizeClassName(file.getAbsolutePath().substring(pathPrefixLength));
        }
    }
}
