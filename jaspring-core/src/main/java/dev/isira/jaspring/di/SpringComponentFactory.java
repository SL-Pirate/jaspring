package dev.isira.jaspring.di;

import org.springframework.beans.factory.config.AutowireCapableBeanFactory;

import dev.isira.jaspring.component.ComponentFactory;

/// Creates components through Spring's bean factory: constructor injection of any bean, `@PostConstruct`,
/// and `@PreDestroy` on destroy. Instances are never registered as singletons.
public final class SpringComponentFactory implements ComponentFactory {

    private final AutowireCapableBeanFactory beanFactory;

    public SpringComponentFactory(AutowireCapableBeanFactory beanFactory) {
        this.beanFactory = beanFactory;
    }

    @Override
    public <T> T create(Class<T> type) {
        return beanFactory.createBean(type);
    }

    @Override
    public void destroy(Object instance) {
        beanFactory.destroyBean(instance);
    }
}
