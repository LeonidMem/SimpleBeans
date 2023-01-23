package ru.leonidm.simplebeans.beans;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * If some annotation is annotated with {@link RegisterAsBean},
 * then class that annotated with the first annotation will be
 * registered as bean
 */
@Target(ElementType.ANNOTATION_TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface RegisterAsBean {

}
