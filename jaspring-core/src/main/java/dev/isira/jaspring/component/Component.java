package dev.isira.jaspring.component;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a Jaspring component: a Java class with an Angular template. Instances are created per live page
/// through Spring (constructor injection works); they are never singletons.
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Component {

    /// CSS selector, as in Angular: `app-user-card`, `[appHighlight]`, `button[primary]`.
    String selector();

    /// Template file, resolved relative to the component's package on the classpath.
    String templateUrl() default "";

    String template() default "";

    /// Stylesheet file, resolved like `templateUrl`.
    String styleUrl() default "";

    String[] styles() default {};

    ChangeDetectionStrategy changeDetection() default ChangeDetectionStrategy.DEFAULT;

    boolean preserveWhitespaces() default false;

    Schema[] schemas() default {};
}
