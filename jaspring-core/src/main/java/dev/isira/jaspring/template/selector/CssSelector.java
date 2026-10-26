package dev.isira.jaspring.template.selector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/// Port of Angular's `CssSelector`: element, classes, attributes and `:not()` parts of one compound selector.
public final class CssSelector {

    private static final Pattern SELECTOR = Pattern.compile(
            "(\\:not\\()|"                                               // 1: :not(
            + "(([\\.\\#]?)[-\\w]+)|"                                    // 2: tag, 3: prefix
            + "(?:\\[([-.\\w*\\\\$]+)(?:=([\"']?)([^\\]\"']*)\\5)?\\])|"  // 4: attribute, 5: quote, 6: value
            + "(\\))|"                                                   // 7: )
            + "(\\s*,\\s*)");                                            // 8: separator

    private String element;
    private final List<String> classNames = new ArrayList<>();
    private final List<String> attributes = new ArrayList<>();
    private final List<CssSelector> notSelectors = new ArrayList<>();

    public static List<CssSelector> parse(String selector) {
        final var results = new ArrayList<CssSelector>();
        var current = new CssSelector();
        var root = current;
        var inNot = false;
        final var matcher = SELECTOR.matcher(selector);
        while (matcher.find()) {
            if (matcher.group(1) != null) {
                if (inNot) throw new IllegalArgumentException("Nesting :not in a selector is not allowed");
                inNot = true;
                current = new CssSelector();
                root.notSelectors.add(current);
            }
            final var tag = matcher.group(2);
            if (tag != null) {
                final var prefix = matcher.group(3);
                if ("#".equals(prefix)) current.addAttribute("id", tag.substring(1));
                else if (".".equals(prefix)) current.addClassName(tag.substring(1));
                else current.element = tag;
            }
            final var attribute = matcher.group(4);
            if (attribute != null) {
                current.addAttribute(unescape(attribute), matcher.group(6));
            }
            if (matcher.group(7) != null) {
                inNot = false;
                current = root;
            }
            if (matcher.group(8) != null) {
                if (inNot) throw new IllegalArgumentException("Multiple selectors in :not are not supported");
                results.add(root.finish());
                root = current = new CssSelector();
            }
        }
        results.add(root.finish());
        return List.copyOf(results);
    }

    private CssSelector finish() {
        if (!notSelectors.isEmpty() && element == null && classNames.isEmpty() && attributes.isEmpty()) {
            element = "*";
        }
        return this;
    }

    private static String unescape(String attribute) {
        final var result = new StringBuilder();
        var escaping = false;
        for (var i = 0; i < attribute.length(); i++) {
            final var c = attribute.charAt(i);
            if (c == '\\') {
                escaping = true;
                continue;
            }
            if (c == '$' && !escaping) {
                throw new IllegalArgumentException("Error in attribute selector \"" + attribute + "\". Unescaped \"$\" is not supported.");
            }
            escaping = false;
            result.append(c);
        }
        return result.toString();
    }

    void addAttribute(String name, String value) {
        attributes.add(name);
        attributes.add(value == null || value.isEmpty() ? "" : value.toLowerCase(Locale.ROOT));
    }

    void addClassName(String name) {
        classNames.add(name.toLowerCase(Locale.ROOT));
    }

    public String element() {
        return element;
    }

    public boolean isElementSelector() {
        return element != null && classNames.isEmpty() && attributes.isEmpty() && notSelectors.isEmpty();
    }

    /// Builds the selector of a rendered element: its tag, static classes and attribute names (bound ones included).
    public static CssSelector forElement(String tagName, Map<String, String> attributes, Set<String> boundNames) {
        final var selector = new CssSelector();
        selector.element = tagName;
        attributes.forEach((name, value) -> {
            if (name.equals("class")) {
                for (final var className : value.trim().split("\\s+")) {
                    if (!className.isEmpty()) selector.addClassName(className);
                }
            }
            selector.addAttribute(name, value);
        });
        for (final var name : boundNames) {
            selector.addAttribute(name, "");
        }
        return selector;
    }

    /// Whether `candidate` (an element) satisfies this selector.
    public boolean matches(CssSelector candidate) {
        if (element != null && !element.equals("*") && !element.equalsIgnoreCase(candidate.element)) return false;
        for (final var className : classNames) {
            if (!candidate.classNames.contains(className)) return false;
        }
        for (var i = 0; i < attributes.size(); i += 2) {
            final var name = attributes.get(i);
            final var value = attributes.get(i + 1);
            if (!candidate.hasAttribute(name, value)) return false;
        }
        for (final var not : notSelectors) {
            if (not.matches(candidate)) return false;
        }
        return true;
    }

    private boolean hasAttribute(String name, String value) {
        for (var i = 0; i < attributes.size(); i += 2) {
            if (attributes.get(i).equalsIgnoreCase(name) && (value.isEmpty() || attributes.get(i + 1).equals(value))) return true;
        }
        return false;
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder(element == null ? "" : element);
        classNames.forEach(name -> sb.append('.').append(name));
        for (var i = 0; i < attributes.size(); i += 2) {
            sb.append('[').append(attributes.get(i));
            if (!attributes.get(i + 1).isEmpty()) sb.append('=').append(attributes.get(i + 1));
            sb.append(']');
        }
        notSelectors.forEach(not -> sb.append(":not(").append(not).append(')'));
        return sb.toString();
    }
}
