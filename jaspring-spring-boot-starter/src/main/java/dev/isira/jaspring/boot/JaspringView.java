package dev.isira.jaspring.boot;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.servlet.View;
import org.springframework.web.servlet.support.RequestContextUtils;

import dev.isira.jaspring.live.PageSpec;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/// Return this from a controller to render a component as the page:
/// `return JaspringView.of(HomeComponent.class).input("title", "Hello");`
public final class JaspringView implements View {

    private final Class<?> rootComponent;
    private final Map<String, Object> inputs = new LinkedHashMap<>();

    private JaspringView(Class<?> rootComponent) {
        this.rootComponent = rootComponent;
    }

    public static JaspringView of(Class<?> rootComponent) {
        return new JaspringView(rootComponent);
    }

    public JaspringView input(String name, Object value) {
        inputs.put(name, value);
        return this;
    }

    public PageSpec spec() {
        return new PageSpec(rootComponent, Map.copyOf(inputs));
    }

    @Override
    public String getContentType() {
        return "text/html;charset=UTF-8";
    }

    @Override
    public void render(Map<String, ?> model, HttpServletRequest request, HttpServletResponse response) throws Exception {
        final var context = RequestContextUtils.findWebApplicationContext(request);
        if (context == null) throw new IllegalStateException("No Spring web application context for this request");
        final var runtime = context.getBean(JaspringRuntime.class);
        // The session must exist before the shim connects, because the page id is bound to it.
        final var session = request.getSession(true);
        final var html = runtime.renderDocument(spec(), session.getId(), null);
        response.setContentType(getContentType());
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(html);
    }
}
