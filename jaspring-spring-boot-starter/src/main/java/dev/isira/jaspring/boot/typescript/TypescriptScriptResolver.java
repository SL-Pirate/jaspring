package dev.isira.jaspring.boot.typescript;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import jakarta.servlet.http.HttpServletRequest;

/// Serves a script the startup fallback compiled from memory; everything else comes from `classpath:/jaspring/ts/`.
public final class TypescriptScriptResolver implements ResourceResolver {

    private final TypescriptScripts scripts;

    public TypescriptScriptResolver(TypescriptScripts scripts) {
        this.scripts = scripts;
    }

    @Override
    public Resource resolveResource(HttpServletRequest request, String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return scripts.inMemory(requestPath).orElseGet(() -> chain.resolveResource(request, requestPath, locations));
    }

    @Override
    public String resolveUrlPath(String resourcePath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return chain.resolveUrlPath(resourcePath, locations);
    }
}
