package dev.isira.jaspring.template.security;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/// Port of Angular's `_sanitizeHtml`: keeps only the elements and attributes on Angular's allowlists, sanitizes URL
/// attributes and re-encodes text. Angular parses with the browser; this uses a small tolerant tokenizer instead,
/// so unparseable fragments become text rather than markup.
public final class HtmlSanitizer {

    private static final Set<String> VOID_ELEMENTS = Set.of("area", "br", "col", "hr", "img", "wbr");

    private static final Set<String> VALID_ELEMENTS = union(VOID_ELEMENTS, tags(
            "colgroup,dd,dt,li,p,tbody,td,tfoot,th,thead,tr,rp,rt,"
            + "address,article,aside,blockquote,caption,center,del,details,dialog,dir,div,dl,figure,figcaption,footer,h1,h2,h3,h4,h5,"
            + "h6,header,hgroup,hr,ins,main,map,menu,nav,ol,pre,section,summary,table,ul,"
            + "a,abbr,acronym,audio,b,bdi,bdo,big,br,cite,code,del,dfn,em,font,i,img,ins,kbd,label,map,mark,picture,q,ruby,rp,rt,s,"
            + "samp,small,source,span,strike,strong,sub,sup,time,track,tt,u,var,video"));

    private static final Set<String> URI_ATTRS = tags("background,cite,href,itemtype,longdesc,poster,src,xlink:href");

    private static final Set<String> VALID_ATTRS = union(URI_ATTRS, tags(
            "abbr,accesskey,align,alt,autoplay,axis,bgcolor,border,cellpadding,cellspacing,class,clear,color,cols,colspan,"
            + "compact,controls,coords,datetime,default,dir,download,face,headers,height,hidden,hreflang,hspace,"
            + "ismap,itemscope,itemprop,kind,label,lang,language,loop,media,muted,nohref,nowrap,open,preload,rel,rev,role,rows,rowspan,rules,"
            + "scope,scrolling,shape,size,sizes,span,srclang,srcset,start,summary,tabindex,target,title,translate,type,usemap,"
            + "valign,value,vspace,width,"
            + "aria-activedescendant,aria-atomic,aria-autocomplete,aria-busy,aria-checked,aria-colcount,aria-colindex,"
            + "aria-colspan,aria-controls,aria-current,aria-describedby,aria-details,aria-disabled,aria-dropeffect,"
            + "aria-errormessage,aria-expanded,aria-flowto,aria-grabbed,aria-haspopup,aria-hidden,aria-invalid,"
            + "aria-keyshortcuts,aria-label,aria-labelledby,aria-level,aria-live,aria-modal,aria-multiline,"
            + "aria-multiselectable,aria-orientation,aria-owns,aria-placeholder,aria-posinset,aria-pressed,aria-readonly,"
            + "aria-relevant,aria-required,aria-roledescription,aria-rowcount,aria-rowindex,aria-rowspan,aria-selected,"
            + "aria-setsize,aria-sort,aria-valuemax,aria-valuemin,aria-valuenow,aria-valuetext"));

    private static final Set<String> SKIP_CONTENT_IF_INVALID = Set.of("script", "style", "template");

    private static final Map<String, String> NAMED_ENTITIES = Map.of(
            "amp", "&", "lt", "<", "gt", ">", "quot", "\"", "apos", "'", "nbsp", " ");

    private HtmlSanitizer() {}

    public static String sanitize(Object unsafeHtml) {
        if (unsafeHtml == null) return "";
        return new HtmlSanitizer().run(unsafeHtml.toString());
    }

    private final StringBuilder out = new StringBuilder();
    private final List<String> openElements = new ArrayList<>();
    private boolean sanitizedSomething;

    boolean sanitizedSomething() {
        return sanitizedSomething;
    }

    private String run(String html) {
        var i = 0;
        var skipDepth = 0;
        String skipping = null;
        while (i < html.length()) {
            final var lt = html.indexOf('<', i);
            if (lt < 0) {
                if (skipping == null) text(decode(html.substring(i)));
                break;
            }
            if (lt > i && skipping == null) {
                text(decode(html.substring(i, lt)));
            }
            final var tag = Tokenizer.tag(html, lt);
            if (tag == null) {
                if (skipping == null) text("<");
                i = lt + 1;
                continue;
            }
            i = tag.end();
            if (tag.comment()) {
                sanitizedSomething = true;
                continue;
            }
            final var name = tag.name().toLowerCase(Locale.ROOT);
            if (skipping != null) {
                if (tag.closing() && name.equals(skipping) && --skipDepth == 0) skipping = null;
                else if (!tag.closing() && name.equals(skipping)) skipDepth++;
                continue;
            }
            if (tag.closing()) {
                endElement(name);
            } else {
                if (!startElement(name, tag.attributes()) && SKIP_CONTENT_IF_INVALID.contains(name)) {
                    skipping = name;
                    skipDepth = 1;
                }
            }
        }
        while (!openElements.isEmpty()) {
            closeTag(openElements.removeLast());
        }
        return out.toString();
    }

    /// @return whether the element is valid; invalid elements still have their content traversed
    private boolean startElement(String name, List<String[]> attributes) {
        if (!VALID_ELEMENTS.contains(name)) {
            sanitizedSomething = true;
            return false;
        }
        out.append('<').append(name);
        for (final var attribute : attributes) {
            final var lower = attribute[0].toLowerCase(Locale.ROOT);
            if (!VALID_ATTRS.contains(lower)) {
                sanitizedSomething = true;
                continue;
            }
            var value = decode(attribute[1]);
            if (URI_ATTRS.contains(lower)) value = UrlSanitizer.sanitize(value);
            out.append(' ').append(attribute[0]).append("=\"").append(encodeEntities(value)).append('"');
        }
        out.append('>');
        if (!VOID_ELEMENTS.contains(name)) openElements.add(name);
        return true;
    }

    private void endElement(String name) {
        final var index = openElements.lastIndexOf(name);
        if (index < 0) return;
        while (openElements.size() > index) {
            closeTag(openElements.removeLast());
        }
    }

    private void closeTag(String name) {
        out.append("</").append(name).append('>');
    }

    private void text(String chars) {
        out.append(encodeEntities(chars));
    }

    static String encodeEntities(String value) {
        final var sb = new StringBuilder(value.length() + 16);
        value.codePoints().forEach(cp -> {
            if (cp == '&') sb.append("&amp;");
            else if (cp == '<') sb.append("&lt;");
            else if (cp == '>') sb.append("&gt;");
            else if (cp >= '#' && cp <= '~' || cp == ' ' || cp == '|' || cp == '!') sb.appendCodePoint(cp);
            else sb.append("&#").append(cp).append(';');
        });
        return sb.toString();
    }

    static String decode(String text) {
        if (text.indexOf('&') < 0) return text;
        final var sb = new StringBuilder(text.length());
        var i = 0;
        while (i < text.length()) {
            final var c = text.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            final var semicolon = text.indexOf(';', i);
            if (semicolon < 0 || semicolon - i > 10) {
                sb.append(c);
                i++;
                continue;
            }
            final var entity = text.substring(i + 1, semicolon);
            final var decoded = decodeEntity(entity);
            if (decoded == null) {
                sb.append(c);
                i++;
            } else {
                sb.append(decoded);
                i = semicolon + 1;
            }
        }
        return sb.toString();
    }

    private static String decodeEntity(String entity) {
        try {
            if (entity.startsWith("#x") || entity.startsWith("#X")) return Character.toString(Integer.parseInt(entity.substring(2), 16));
            if (entity.startsWith("#")) return Character.toString(Integer.parseInt(entity.substring(1)));
        } catch (IllegalArgumentException e) {
            return null;
        }
        return NAMED_ENTITIES.get(entity);
    }

    // Angular's lists repeat a few names (del, ins, map...), which Set.of would reject.
    private static Set<String> tags(String list) {
        return Set.copyOf(java.util.Arrays.asList(list.split(",")));
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        final var all = new java.util.HashSet<>(a);
        all.addAll(b);
        return Set.copyOf(all);
    }

    /// Reads one tag starting at `<`. Attribute values keep their raw (still encoded) text.
    static final class Tokenizer {

        record Tag(String name, boolean closing, boolean comment, List<String[]> attributes, int end) {}

        static Tag tag(String html, int start) {
            if (html.startsWith("<!--", start)) {
                final var close = html.indexOf("-->", start + 4);
                return new Tag("", false, true, List.of(), close < 0 ? html.length() : close + 3);
            }
            var i = start + 1;
            final var closing = i < html.length() && html.charAt(i) == '/';
            if (closing) i++;
            final var nameStart = i;
            while (i < html.length() && isNameChar(html.charAt(i))) i++;
            if (i == nameStart) return null;
            final var name = html.substring(nameStart, i);
            final var attributes = new ArrayList<String[]>();
            while (i < html.length()) {
                while (i < html.length() && Character.isWhitespace(html.charAt(i))) i++;
                if (i >= html.length()) return null;
                final var c = html.charAt(i);
                if (c == '>') return new Tag(name, closing, false, attributes, i + 1);
                if (c == '/') {
                    i++;
                    continue;
                }
                final var attrStart = i;
                while (i < html.length() && !Character.isWhitespace(html.charAt(i)) && html.charAt(i) != '=' && html.charAt(i) != '>' && html.charAt(i) != '/') i++;
                if (i == attrStart) {
                    i++;
                    continue;
                }
                final var attrName = html.substring(attrStart, i);
                var value = "";
                while (i < html.length() && Character.isWhitespace(html.charAt(i))) i++;
                if (i < html.length() && html.charAt(i) == '=') {
                    i++;
                    while (i < html.length() && Character.isWhitespace(html.charAt(i))) i++;
                    if (i < html.length() && (html.charAt(i) == '"' || html.charAt(i) == '\'')) {
                        final var quote = html.charAt(i);
                        final var close = html.indexOf(quote, i + 1);
                        if (close < 0) return null;
                        value = html.substring(i + 1, close);
                        i = close + 1;
                    } else {
                        final var valueStart = i;
                        while (i < html.length() && !Character.isWhitespace(html.charAt(i)) && html.charAt(i) != '>') i++;
                        value = html.substring(valueStart, i);
                    }
                }
                attributes.add(new String[] {attrName, value});
            }
            return null;
        }

        private static boolean isNameChar(char c) {
            return Character.isLetterOrDigit(c) || c == '-' || c == ':' || c == '_';
        }
    }
}
