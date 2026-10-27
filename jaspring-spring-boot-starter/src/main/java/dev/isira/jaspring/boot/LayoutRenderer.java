package dev.isira.jaspring.boot;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import org.springframework.core.io.Resource;

import dev.isira.jaspring.boot.browser.BrowserAssets;

/// Places the rendered root component into the layout document and injects component styles and the client shim.
/// The layout must contain the root component's element (`<app-root></app-root>`); without it the root is
/// appended to the body.
public final class LayoutRenderer {

    private final String layout;
    private final ComponentStyles styles;
    private final JaspringProperties.Transport transport;

    public LayoutRenderer(Resource layout, ComponentStyles styles, JaspringProperties.Transport transport) {
        this.layout = read(layout);
        this.styles = styles;
        this.transport = transport;
    }

    private static String read(Resource resource) {
        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read layout " + resource, e);
        }
    }

    public String render(String rootTagName, String rootHtml, String pageId) {
        return render(rootTagName, rootHtml, pageId, false);
    }

    /// `pending` tells the shim that messages already wait for it (calls made while rendering, late patches),
    /// so the HTTP transport polls once right after load.
    public String render(String rootTagName, String rootHtml, String pageId, boolean pending) {
        return render(rootTagName, rootHtml, pageId, pending, BrowserAssets.NONE);
    }

    /// `assets` decides whether the page preloads the WebAssembly runtime, loads it at idle time, or not at all.
    public String render(String rootTagName, String rootHtml, String pageId, boolean pending, BrowserAssets assets) {
        var document = layout;
        final var placeholder = Pattern.compile("<" + Pattern.quote(rootTagName) + "(?:\\s[^>]*)?>\\s*</" + Pattern.quote(rootTagName) + ">|<" + Pattern.quote(rootTagName) + "(?:\\s[^>]*)?/>");
        final var matcher = placeholder.matcher(document);
        if (matcher.find()) {
            document = document.substring(0, matcher.start()) + rootHtml + document.substring(matcher.end());
        } else {
            document = insertBefore(document, "</body>", rootHtml);
        }
        document = insertBefore(document, "</head>", styles.allAsStyleBlock());
        if (transport != JaspringProperties.Transport.OFF && pageId != null) {
            if (assets.policy() == BrowserAssets.Policy.EAGER) {
                document = insertBefore(document, "</head>", "<link rel=\"preload\" as=\"script\" href=\"" + assets.launcherUrl() + "\">\n"
                        + "<link rel=\"preload\" as=\"fetch\" crossorigin href=\"" + assets.moduleUrl() + "\">\n");
            }
            var scripts = "<script src=\"/_jaspring/idiomorph.min.js\"></script>\n"
                    + "<script src=\"/_jaspring/jaspring.js\" data-jsp-page=\"" + pageId + "\" data-jsp-transport=\""
                    + transport.name().toLowerCase(java.util.Locale.ROOT) + "\"" + (pending ? " data-jsp-pending=\"true\"" : "") + "></script>\n";
            if (assets.policy() != BrowserAssets.Policy.NONE) {
                scripts += "<script src=\"/_jaspring/jaspring-wasm.js\" data-jsp-wasm-js=\"" + assets.launcherUrl() + "\" data-jsp-wasm-bin=\""
                        + assets.moduleUrl() + "\" data-jsp-wasm-policy=\"" + assets.policy().name().toLowerCase(java.util.Locale.ROOT) + "\"></script>\n";
            }
            document = insertBefore(document, "</body>", scripts);
        }
        return document;
    }

    private static String insertBefore(String document, String closingTag, String content) {
        if (content.isEmpty()) return document;
        final var index = document.toLowerCase(java.util.Locale.ROOT).lastIndexOf(closingTag);
        return index < 0 ? document + content : document.substring(0, index) + content + document.substring(index);
    }
}
