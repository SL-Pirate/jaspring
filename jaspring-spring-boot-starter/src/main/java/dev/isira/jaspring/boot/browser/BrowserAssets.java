package dev.isira.jaspring.boot.browser;

import java.util.Collection;

import dev.isira.jaspring.browser.ClosureManifest;

/// How one page loads the WebAssembly runtime: not at all, at idle time, or eagerly with preload hints.
/// Assets live under `/_jaspring/app/<digest>/`, where the digest covers both files, so the launcher finds its
/// module beside itself and the URLs can be cached immutably.
public record BrowserAssets(Policy policy, String launcherUrl, String moduleUrl) {

    public enum Policy { NONE, IDLE, EAGER }

    public static final String PREFIX = "/_jaspring/app/";
    public static final BrowserAssets NONE = new BrowserAssets(Policy.NONE, null, null);

    /// `EAGER` when a rendered component runs a closure while mounting; `IDLE` when the application has closures
    /// at all (any later page or event may need the runtime); `NONE` without a build.
    public static BrowserAssets forPage(ClosureManifest manifest, Collection<Class<?>> renderedComponents) {
        if (manifest.isEmpty() || manifest.assets() == null) return NONE;
        final var eager = renderedComponents.stream().anyMatch(manifest::hasMountClosures);
        final var directory = PREFIX + digest(manifest) + "/";
        return new BrowserAssets(eager ? Policy.EAGER : Policy.IDLE, directory + manifest.assets().js().name(), directory + manifest.assets().wasm().name());
    }

    /// Sixteen hex characters derived from both asset digests.
    public static String digest(ClosureManifest manifest) {
        final var combined = manifest.assets().js().sha256() + manifest.assets().wasm().sha256();
        try {
            final var digest = java.security.MessageDigest.getInstance("SHA-256").digest(combined.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
