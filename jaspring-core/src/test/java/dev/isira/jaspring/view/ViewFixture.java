package dev.isira.jaspring.view;

import dev.isira.jaspring.component.ComponentFactory;
import dev.isira.jaspring.component.ComponentRegistry;
import dev.isira.jaspring.template.compile.TemplateCompiler;
import dev.isira.jaspring.template.parse.AngularCompilerRuntime;
import dev.isira.jaspring.template.parse.DomSchema;
import dev.isira.jaspring.template.parse.GraalAngularTemplateParser;

final class ViewFixture {

    static final AngularCompilerRuntime RUNTIME = AngularCompilerRuntime.load();
    static final DomSchema SCHEMA = DomSchema.load(RUNTIME);
    static final TemplateCompiler COMPILER = new TemplateCompiler(new GraalAngularTemplateParser(RUNTIME), SCHEMA);

    private ViewFixture() {}

    /// A page with the given components registered and compiled.
    static final class Page {
        final ComponentRegistry registry = new ComponentRegistry(ComponentFactory.reflective());
        final ViewTree tree = new ViewTree();
        final ViewRenderer renderer;
        ComponentView root;

        /// Accepts components, directives and pipes in any order.
        Page(boolean liveEvents, Class<?>... types) {
            for (final var type : types) {
                if (type.isAnnotationPresent(dev.isira.jaspring.component.Directive.class)) registry.registerDirective(type);
                else if (type.isAnnotationPresent(dev.isira.jaspring.component.Pipe.class)) registry.registerPipe(type);
                else registry.registerComponent(type);
            }
            registry.compileAll(COMPILER);
            renderer = new ViewRenderer(registry, SCHEMA, liveEvents);
        }

        String render(Class<?> rootType) {
            root = renderer.createRoot(tree, rootType, java.util.Map.of());
            return tree.assemble(root);
        }

        String refresh() {
            renderer.refresh(tree, root);
            return tree.assemble(root);
        }
    }
}
