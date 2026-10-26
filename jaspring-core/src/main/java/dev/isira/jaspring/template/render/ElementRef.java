package dev.isira.jaspring.template.render;

import java.util.Map;

/// What a `#ref` on a plain element resolves to on the server. The DOM node itself does not exist here;
/// handlers can still read the attributes the template rendered.
public record ElementRef(String tagName, Map<String, String> attributes) {}
