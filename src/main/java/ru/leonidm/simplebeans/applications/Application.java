package ru.leonidm.simplebeans.applications;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Application {

    String packageName() default "";

    /**
     * User-defined properties in format "key=value"
     */
    String[] properties() default {};

    /**
     * Listed classes of application, that are must be included in context
     */
    Class<?>[] dependencies() default {};

}
