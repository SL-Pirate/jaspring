package dev.isira.jaspring.boot.browser;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.ResourceResolver;
import org.springframework.web.servlet.resource.ResourceResolverChain;

import dev.isira.jaspring.browser.ClosureManifest;
import jakarta.servlet.http.HttpServletRequest;

/// Serves `/_jaspring/app/<digest>/<file>` from `classpath:/jaspring/wasm/<file>` only when the digest is the
/// current build's, so stale URLs miss instead of serving a module that no longer matches the server's manifest.
public final class HashedAssetResolver implements ResourceResolver {

    private final ClosureManifest manifest;

    public HashedAssetResolver(ClosureManifest manifest) {
        this.manifest = manifest;
    }

    @Override
    public Resource resolveResource(HttpServletRequest request, String requestPath, List<? extends Resource> locations, ResourceResolverChain chain) {
        if (manifest.isEmpty() || manifest.assets() == null) return null;
        final var slash = requestPath.indexOf('/');
        if (slash < 0) return null;
        final var digest = requestPath.substring(0, slash);
        final var file = requestPath.substring(slash + 1);
        if (!digest.equals(BrowserAssets.digest(manifest)) || file.indexOf('/') >= 0) return null;
        if (!file.equals(manifest.assets().js().name()) && !file.equals(manifest.assets().wasm().name())) return null;
        return chain.resolveResource(request, file, locations);
    }

    @Override
    public String resolveUrlPath(String resourcePath, List<? extends Resource> locations, ResourceResolverChain chain) {
        return null;
    }
}
