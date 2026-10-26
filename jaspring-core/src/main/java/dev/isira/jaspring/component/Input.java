package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A field the parent template may bind with `[name]="..."` or a static attribute.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Input {

    /// Public name when it differs from the field name.
    String value() default "";

    boolean required() default false;
}
