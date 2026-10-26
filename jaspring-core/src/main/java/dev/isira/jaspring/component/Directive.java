package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// An attribute directive: inputs plus host bindings, applied to the elements its selector matches.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Directive {

    String selector();
}
