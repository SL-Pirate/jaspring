package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// An `OutputEmitter` field the parent template may listen to with `(name)="..."`.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Output {

    String value() default "";
}
