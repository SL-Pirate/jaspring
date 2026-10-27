package dev.isira.jaspring.boot.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import dev.isira.jaspring.boot.testapp.components.BrowserComponent;
import dev.isira.jaspring.boot.testapp.components.BrowserPageComponent;
import dev.isira.jaspring.boot.testapp.components.CounterComponent;
import dev.isira.jaspring.browser.ClosureManifest;

class BrowserAssetsTest {

    final ClosureManifest manifest = ClosureManifestLoader.load(new DefaultResourceLoader());

    @Test
    void loaderParsesClosuresAndAssets() {
        assertEquals(2, manifest.closures().size());
        assertEquals("jaspring-app.js.wasm", manifest.assets().wasm().name());
        assertTrue(manifest.hasMountClosures(BrowserPageComponent.class));
        assertEquals(java.util.Set.of("measure"), manifest.eventMethods(BrowserComponent.class));
        assertTrue(manifest.find("aaaaaaaaaaaaaaaa").isPresent());
    }

    @Test
    void policyFollowsTheRenderedComponents() {
        final var eager = BrowserAssets.forPage(manifest, List.of(CounterComponent.class, BrowserPageComponent.class));
        assertEquals(BrowserAssets.Policy.EAGER, eager.policy());
        assertTrue(eager.launcherUrl().matches("/_jaspring/app/[0-9a-f]{16}/jaspring-app\\.js"), eager.launcherUrl());
        assertEquals(eager.launcherUrl() + ".wasm", eager.moduleUrl(), "the module sits beside the launcher");

        assertEquals(BrowserAssets.Policy.IDLE, BrowserAssets.forPage(manifest, List.of(CounterComponent.class)).policy());
        assertEquals(BrowserAssets.Policy.IDLE, BrowserAssets.forPage(manifest, List.of(BrowserComponent.class)).policy());

        final var none = BrowserAssets.forPage(ClosureManifest.EMPTY, List.of(BrowserPageComponent.class));
        assertEquals(BrowserAssets.Policy.NONE, none.policy());
        assertNull(none.launcherUrl());
    }

    @Test
    void digestChangesWithEitherAsset() {
        final var assets = manifest.assets();
        final var other = new ClosureManifest("x", new ClosureManifest.Assets(assets.js(), new ClosureManifest.Asset("jaspring-app.js.wasm", "00", 1)), List.of());
        assertTrue(!BrowserAssets.digest(manifest).equals(BrowserAssets.digest(other)));
    }
}
