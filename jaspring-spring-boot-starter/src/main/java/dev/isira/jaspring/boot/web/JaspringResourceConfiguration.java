package dev.isira.jaspring.boot.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// Serves the client shim and its vendored dependency under `/_jaspring/`.
@Configuration(proxyBeanMethods = false)
public class JaspringResourceConfiguration implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/_jaspring/**")
                .addResourceLocations("classpath:/jaspring/static/")
                .setCachePeriod(3600);
    }
}
