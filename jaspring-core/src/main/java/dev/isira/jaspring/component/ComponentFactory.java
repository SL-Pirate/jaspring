package dev.isira.jaspring.component;

/// Creates component, pipe and directive instances. The Spring implementation uses the bean factory so
/// constructor injection works; tests pass a lambda.
public interface ComponentFactory {

    <T> T create(Class<T> type);

    default void destroy(Object instance) {}

    static ComponentFactory reflective() {
        return new ComponentFactory() {
            @Override
            public <T> T create(Class<T> type) {
                try {
                    final var constructor = type.getDeclaredConstructor();
                    constructor.setAccessible(true);
                    return constructor.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException("Cannot instantiate " + type.getName() + " without Spring", e);
                }
            }
        };
    }
}
