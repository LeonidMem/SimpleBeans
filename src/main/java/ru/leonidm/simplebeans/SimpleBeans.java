package ru.leonidm.simplebeans;

import ru.leonidm.simplebeans.applications.Application;
import ru.leonidm.simplebeans.utils.BcelClassScanner;
import ru.leonidm.simplebeans.utils.GeneralUtils;

import java.io.File;
import java.lang.reflect.Method;
import java.util.Set;

public final class SimpleBeans {

    private SimpleBeans() {

    }

    public static void main(String[] args) throws Exception {
        File coreFile = GeneralUtils.getCoreJarFile();
        BcelClassScanner bcelClassScanner = BcelClassScanner.of(coreFile);

        Set<Class<?>> classes = bcelClassScanner.getTypesAnnotatedWith(Application.class);

        for (Class<?> mainClass : classes) {
            Method method = mainClass.getMethod("main", String[].class);
            method.invoke(null, (Object) args);
        }
    }
}
