package dev.isira.jaspring.component;

public record PipeDef(String name, boolean pure, Class<?> type) {

    public static PipeDef of(Class<?> type) {
        final var annotation = type.getAnnotation(Pipe.class);
        if (annotation == null) throw new IllegalArgumentException(type.getName() + " is not annotated with @Pipe");
        if (!dev.isira.jaspring.pipe.PipeTransform.class.isAssignableFrom(type)) {
            throw new IllegalStateException("Pipe " + type.getSimpleName() + " must implement PipeTransform");
        }
        return new PipeDef(annotation.name(), annotation.pure(), type);
    }
}
