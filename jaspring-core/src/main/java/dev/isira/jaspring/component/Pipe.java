package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// A template pipe. The class must implement `PipeTransform`; it is created once through Spring.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Pipe {

    String name();

    /// Pure pipes are only re-run when their inputs change.
    boolean pure() default true;
}
