package dev.isira.jaspring.boot;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackages;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import dev.isira.jaspring.boot.live.HttpTransportConfiguration;
import dev.isira.jaspring.boot.live.ProtocolCodec;
import dev.isira.jaspring.boot.live.WebSocketTransportConfiguration;
import dev.isira.jaspring.boot.web.JaspringResourceConfiguration;
import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.di.ComponentScanner;
import dev.isira.jaspring.di.PageScope;
import dev.isira.jaspring.di.SpringComponentFactory;
import dev.isira.jaspring.live.LivePageRegistry;
import dev.isira.jaspring.pipe.builtin.BuiltinPipes;
import dev.isira.jaspring.style.StyleProcessor;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;

/// Wires Jaspring into a servlet web application: compiles every scanned component at startup, registers the
/// `jaspring-page` bean scope, and installs the transports selected by `jaspring.live.transport`.
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@EnableConfigurationProperties(JaspringProperties.class)
@Import({JaspringResourceConfiguration.class, HttpTransportConfiguration.class, WebSocketTransportConfiguration.class})
public class JaspringAutoConfiguration {

    /// Registered as a post-processor so `jaspring-page` scoped beans resolve before any component is created.
    @Bean
    public static BeanFactoryPostProcessor jaspringPageScope() {
        return (ConfigurableListableBeanFactory beanFactory) -> beanFactory.registerScope(PageScope.NAME, new PageScope());
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    public AngularCompilerRuntime angularCompilerRuntime() {
        return AngularCompilerRuntime.load();
    }

    @Bean
    @ConditionalOnMissingBean
    public DomSchema domSchema(AngularCompilerRuntime runtime) {
        return DomSchema.load(runtime);
    }

    @Bean
    @ConditionalOnMissingBean
    public TemplateCompiler templateCompiler(AngularCompilerRuntime runtime, DomSchema schema) {
        return new TemplateCompiler(new GraalAngularTemplateParser(runtime), schema);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentFactory componentFactory(ConfigurableListableBeanFactory beanFactory) {
        return new SpringComponentFactory(beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentRegistry componentRegistry(ComponentFactory factory, TemplateCompiler compiler, JaspringProperties properties,
            ConfigurableListableBeanFactory beanFactory) {
        final var packages = properties.getBasePackages().isEmpty() ? AutoConfigurationPackages.get(beanFactory) : properties.getBasePackages();
        final var found = new ComponentScanner(beanFactory.getBeanClassLoader()).scan(List.copyOf(packages));
        final var registry = new ComponentRegistry(factory);
        BuiltinPipes.registerAll(registry);
        found.pipes().forEach(registry::registerPipe);
        found.directives().forEach(registry::registerDirective);
        found.components().forEach(registry::registerComponent);
        registry.compileAll(compiler);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ComponentStyles componentStyles(ComponentRegistry registry, AngularCompilerRuntime runtime, JaspringProperties properties,
            org.springframework.core.io.ResourceLoader resourceLoader) {
        if (!properties.getTailwind().isEnabled()) {
            return new ComponentStyles(new StyleProcessor(runtime).processAll(registry));
        }
        final var tailwind = new dev.isira.jaspring.style.TailwindProcessor(runtime,
                read(resourceLoader.getResource(properties.getTailwind().getStylesheet())));
        final var layout = read(resourceLoader.getResource(properties.getLayout()));
        final var global = tailwind.global(dev.isira.jaspring.style.TailwindCandidates.of(registry, layout));
        return new ComponentStyles(new StyleProcessor(runtime, tailwind::componentStyle).processAll(registry), global);
    }

    private static String read(org.springframework.core.io.Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException("Cannot read " + resource, e);
        }
    }

    @Bean
    @ConditionalOnMissingBean
    public LivePageRegistry livePageRegistry(JaspringProperties properties) {
        return new LivePageRegistry(properties.getPageTtl(), properties.getMaxPagesPerSession(), Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(name = "jaspring.live.transport", havingValue = "off", matchIfMissing = false)
    public LivePageSweeper noopSweeper() {
        return LivePageSweeper.disabled();
    }

    @Bean
    @ConditionalOnMissingBean
    public LivePageSweeper livePageSweeper(LivePageRegistry registry) {
        return LivePageSweeper.every10Seconds(registry);
    }

    @Bean
    @ConditionalOnMissingBean
    public LayoutRenderer layoutRenderer(JaspringProperties properties, ComponentStyles styles, org.springframework.core.io.ResourceLoader resourceLoader) {
        return new LayoutRenderer(resourceLoader.getResource(properties.getLayout()), styles, properties.getLive().getTransport());
    }

    @Bean
    @ConditionalOnMissingBean
    public ProtocolCodec protocolCodec() {
        return new ProtocolCodec();
    }

    @Bean
    @ConditionalOnMissingBean
    public JaspringRuntime jaspringRuntime(ComponentRegistry registry, DomSchema schema, LivePageRegistry pages, LayoutRenderer layout,
            JaspringProperties properties) {
        return new JaspringRuntime(registry, schema, pages, layout, properties);
    }
}
