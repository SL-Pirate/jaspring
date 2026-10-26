package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Listens to an event on the host element; `args` are template expressions such as `$event`.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface HostListener {

    String value();

    String[] args() default {};
}
