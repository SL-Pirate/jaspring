package dev.isira.jaspring.boot.web;

import java.time.Duration;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import dev.isira.jaspring.boot.browser.BrowserAssets;
import dev.isira.jaspring.boot.browser.HashedAssetResolver;
import dev.isira.jaspring.browser.ClosureManifest;

/// Serves the client shim and its vendored dependency under `/_jaspring/`, and the WebAssembly build of the
/// application's browser closures under content-addressed, immutable URLs.
@Configuration(proxyBeanMethods = false)
public class JaspringResourceConfiguration implements WebMvcConfigurer {

    private final ClosureManifest closures;

    public JaspringResourceConfiguration(ObjectProvider<ClosureManifest> closures) {
        this.closures = closures.getIfAvailable(() -> ClosureManifest.EMPTY);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(BrowserAssets.PREFIX + "**")
                .addResourceLocations("classpath:/jaspring/wasm/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .resourceChain(true)
                .addResolver(new HashedAssetResolver(closures));
        registry.addResourceHandler("/_jaspring/**")
                .addResourceLocations("classpath:/jaspring/static/")
                .setCachePeriod(3600);
    }
}
