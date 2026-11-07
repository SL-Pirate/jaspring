package dev.isira.jaspring.boot.web;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.isira.jaspring.boot.JaspringProperties;
import dev.isira.jaspring.boot.browser.BrowserAssets;
import dev.isira.jaspring.boot.browser.HashedAssetResolver;
import dev.isira.jaspring.boot.typescript.TypescriptScriptResolver;
import dev.isira.jaspring.boot.typescript.TypescriptScripts;
import dev.isira.jaspring.browser.ClosureManifest;

/// Serves the client shim and its vendored dependency under `/_jaspring/`, the WebAssembly build of the
/// application's browser closures under content-addressed, immutable URLs, and the compiled TypeScript under
/// `/_jaspring/ts/` (from the build's resources, or from memory when the startup fallback compiled them).
@Configuration(proxyBeanMethods = false)
public class JaspringResourceConfiguration implements WebMvcConfigurer {

    private final ClosureManifest closures;
    private final TypescriptScripts scripts;
    private final boolean devMode;

    public JaspringResourceConfiguration(ObjectProvider<ClosureManifest> closures, ObjectProvider<TypescriptScripts> scripts,
            ObjectProvider<JaspringProperties> properties) {
        this.closures = closures.getIfAvailable(() -> ClosureManifest.EMPTY);
        this.scripts = scripts.getIfAvailable(() -> TypescriptScripts.NONE);
        this.devMode = properties.getIfAvailable(JaspringProperties::new).isDevMode();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(BrowserAssets.PREFIX + "**")
                .addResourceLocations("classpath:/jaspring/wasm/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(true)
                .addResolver(new HashedAssetResolver(closures));
        // Plain URLs with a one-hour cache; the injected tags carry a `?v=` digest, so a new build is fetched at once.
        registry.addResourceHandler(TypescriptScripts.URL_PREFIX + "**")
                .addResourceLocations("classpath:/jaspring/ts/")
                .setCacheControl(devMode ? CacheControl.noCache() : CacheControl.maxAge(Duration.ofHours(1)))
                .resourceChain(true)
                .addResolver(new TypescriptScriptResolver(scripts));
        registry.addResourceHandler("/_jaspring/**")
                .addResourceLocations("classpath:/jaspring/static/")
                .setCachePeriod(3600);
    }
}
