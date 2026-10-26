package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Binds a field or getter to the host element: `class.active`, `attr.role`, `style.width.px` or a property name.
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.METHOD})
public @interface HostBinding {

    String value() default "";
}
