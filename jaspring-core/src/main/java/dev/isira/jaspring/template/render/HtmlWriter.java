package dev.isira.jaspring.template.render;

/// Appends HTML with the escaping each position needs. Text escapes `& < >`; attribute values also escape `"`.
public final class HtmlWriter {

    private final StringBuilder out;

    public HtmlWriter() {
        this(new StringBuilder());
    }

    public HtmlWriter(StringBuilder out) {
        this.out = out;
    }

    public HtmlWriter text(String text) {
        for (var i = 0; i < text.length(); i++) {
            final var c = text.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                default -> out.append(c);
            }
        }
        return this;
    }

    public HtmlWriter raw(String html) {
        out.append(html);
        return this;
    }

    public HtmlWriter attribute(String name, String value) {
        out.append(' ').append(name);
        if (value != null) {
            out.append("=\"");
            for (var i = 0; i < value.length(); i++) {
                final var c = value.charAt(i);
                switch (c) {
                    case '&' -> out.append("&amp;");
                    case '<' -> out.append("&lt;");
                    case '>' -> out.append("&gt;");
                    case '"' -> out.append("&quot;");
                    default -> out.append(c);
                }
            }
            out.append('"');
        }
        return this;
    }

    public HtmlWriter openTag(String name) {
        out.append('<').append(name);
        return this;
    }

    public HtmlWriter closeStartTag() {
        out.append('>');
        return this;
    }

    public HtmlWriter closeTag(String name) {
        out.append("</").append(name).append('>');
        return this;
    }

    public int length() {
        return out.length();
    }

    @Override
    public String toString() {
        return out.toString();
    }
}
