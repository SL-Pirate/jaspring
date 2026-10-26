package dev.isira.jaspring.ide;

import com.intellij.lang.injection.InjectedLanguageManager;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.psi.*;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.util.Arrays;

public class JaspringCodeInsightTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected LightProjectDescriptor getProjectDescriptor() { return JAVA_21; }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        myFixture.addClass("package dev.isira.jaspring.component; public @interface Component { String selector(); String templateUrl() default \"\"; String template() default \"\"; String styleUrl() default \"\"; String[] styles() default {}; }");
        myFixture.addClass("package dev.isira.jaspring.component; public @interface Directive { String selector(); }");
        myFixture.addClass("package dev.isira.jaspring.component; public @interface Pipe { String name(); }");
        myFixture.addClass("package dev.isira.jaspring.component; public @interface Input { String value() default \"\"; boolean required() default false; }");
        myFixture.addClass("package dev.isira.jaspring.component; public @interface Output { String value() default \"\"; }");
        myFixture.addClass("package dev.isira.jaspring.signals; public interface Signal<T> { T get(); }");
        myFixture.addClass("package dev.isira.jaspring.signals; public interface InputSignal<T> extends Signal<T> {}");
        myFixture.addClass("package dev.isira.jaspring.signals; public interface OutputRef<T> {}");
        myFixture.addClass("package dev.isira.jaspring.signals; public interface ModelSignal<T> extends Signal<T>, OutputRef<T> {}");
        myFixture.addClass("package demo; public record Todo(String title, boolean done) {}");
        myFixture.addClass("""
                package demo;
                import dev.isira.jaspring.component.Component;
                import dev.isira.jaspring.signals.Signal;
                @Component(selector = "app-counter", templateUrl = "counter.component.html")
                public class Counter {
                    private String title;
                    private Object todos;
                    public Signal<Todo> selected;
                    public java.util.List<Todo> todos() { return null; }
                    public String getGreeting() { return "hello"; }
                    public void choose(Todo todo) {}
                }
                """);
        final var root = myFixture.getTempDirFixture().findOrCreateDir("resources");
        ModuleRootModificationUtil.updateModel(getModule(), model ->
                model.getContentEntries()[0].addSourceFolder(root, JavaResourceRootType.RESOURCE));
    }

    private PsiFile template(String source) {
        final var file = myFixture.addFileToProject("resources/demo/counter.component.html", source.replace("<caret>", ""));
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        final var caret = source.indexOf("<caret>");
        if (caret >= 0) myFixture.getEditor().getCaretModel().moveToOffset(caret);
        return myFixture.getFile();
    }

    private PsiElement targetAtCaret() {
        final var reference = myFixture.getFile().findReferenceAt(myFixture.getCaretOffset());
        assertNotNull("No reference at caret in " + myFixture.getFile().getLanguage(), reference);
        final var target = reference.resolve();
        assertNotNull("Unresolved: " + reference, target);
        return target;
    }

    public void testDetectsPackageRelativeTemplateInSeparateResourceRoot() {
        final var file = template("<h1>{{ title }}</h1>");
        assertEquals(1, JaspringProjectModel.owners(file).size());
        assertEquals(JaspringHtmlLanguage.INSTANCE, file.getLanguage());
    }

    public void testPrivateComponentFieldNavigatesToJava() {
        template("<h1>{{ ti<caret>tle }}</h1>");
        assertInstanceOf(targetAtCaret(), PsiField.class);
        assertEquals("title", ((PsiNamedElement) targetAtCaret()).getName());
    }

    public void testGetterPropertyNavigatesToAccessor() {
        template("{{ greet<caret>ing }}");
        assertEquals("getGreeting", ((PsiMethod) targetAtCaret()).getName());
    }

    public void testGetterRenameUpdatesPropertyAndMethodSyntax() {
        final var file = template("{{ greeting }} {{ getGreeting() }}");
        final var type = JaspringProjectModel.owners(file).getFirst().type();
        myFixture.renameElement(type.findMethodsByName("getGreeting", false)[0], "getWelcome");
        assertEquals("{{ welcome }} {{ getWelcome() }}", file.getText());
    }

    public void testSignalCallExposesRecordMembers() {
        template("{{ selected().ti<caret>tle() }}");
        assertEquals("title", ((PsiNamedElement) targetAtCaret()).getName());
    }

    public void testComparisonsAndTagStringsInsideExpressionsPreserveHtmlTree() {
        final var file = template("@if (todos().size() < 3) { <p>{{ title === '<div>' ? ti<caret>tle : 'empty' }}</p> }");
        assertEquals("title", ((PsiField) targetAtCaret()).getName());
        assertNull(PsiTreeUtil.findChildOfType(file, PsiErrorElement.class));
    }

    public void testElseIfConditionResolvesJavaMembers() {
        template("@if (false) { no } @else if (greet<caret>ing) { yes }");
        assertEquals("getGreeting", ((PsiMethod) targetAtCaret()).getName());
    }

    public void testLetValueCarriesSignalResultType() {
        template("@let todo = selected(); <p>{{ todo.ti<caret>tle }}</p>");
        assertEquals("title", ((PsiNamedElement) targetAtCaret()).getName());
    }

    public void testForVariableCarriesJavaGenericElementType() {
        template("@for (todo of todos(); track todo.title()) { <p>{{ todo.ti<caret>tle() }}</p> }");
        assertEquals("title", ((PsiNamedElement) targetAtCaret()).getName());
    }

    public void testCompletesComponentFieldsAndMethods() {
        template("<h1>{{ <caret> }}</h1>");
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "title", "selected", "todos", "choose", "greeting");
    }

    public void testCompletesMembersOfSignalValue() {
        template("{{ selected().<caret> }}");
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "title", "done");
    }

    public void testBindingExpressionNavigatesAndEventCompletes() {
        template("<button [title]=\"ti<caret>tle\" (click)=\"choose($event)\">choose</button>");
        assertEquals("title", ((PsiField) targetAtCaret()).getName());
    }

    public void testJavaRenameUpdatesTemplateReferences() {
        final var file = template("<p [title]=\"title\">{{ title }}</p>");
        final var type = JavaPsiFacade.getInstance(getProject()).findClass("demo.Counter", file.getResolveScope());
        myFixture.renameElement(type.findFieldByName("title", false), "heading");
        assertEquals("<p [title]=\"heading\">{{ heading }}</p>", file.getText());
    }

    public void testRenamePreservesObjectKeysStringsAndArrowLocals() {
        final var file = template("{{ {title: title, literal: 'title'} }} {{ todos().map(title => title.title) }} {{ title }}");
        final var type = JaspringProjectModel.owners(file).getFirst().type();
        myFixture.renameElement(type.findFieldByName("title", false), "heading");
        assertEquals("{{ {title: heading, literal: 'title'} }} {{ todos().map(title => title.title) }} {{ heading }}", file.getText());
    }

    public void testModelFieldRenameUpdatesInputOutputAndTwoWayBindings() {
        final var child = myFixture.addClass("""
                package demo;
                import dev.isira.jaspring.component.Component;
                import dev.isira.jaspring.signals.ModelSignal;
                @Component(selector = "app-child", template = "")
                public class Child { private ModelSignal<String> value; }
                """);
        final var file = template("<app-child [value]=\"title\" (valueChange)=\"choose($event)\"></app-child><app-child [(value)]=\"title\"></app-child>");
        myFixture.renameElement(child.findFieldByName("value", false), "selection");
        assertEquals("<app-child [selection]=\"title\" (selectionChange)=\"choose($event)\"></app-child><app-child [(selection)]=\"title\"></app-child>", file.getText());
    }

    public void testCompletesComponentTagsAndInputAliases() {
        myFixture.addClass("""
                package demo;
                import dev.isira.jaspring.component.*;
                @Component(selector = "app-child", template = "")
                public class Child { @Input("label") public String name; }
                """);
        template("<app-<caret>>");
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "app-child", "app-counter");
    }

    public void testCompletesBindingsOnChildComponent() {
        myFixture.addClass("""
                package demo;
                import dev.isira.jaspring.component.*;
                @Component(selector = "app-child", template = "")
                public class Child { @Input("label") public String name; }
                """);
        template("<app-child <caret>></app-child>");
        myFixture.completeBasic();
        assertContainsElements(myFixture.getLookupElementStrings(), "[label]");
    }

    public void testCustomPipeNavigatesToJava() {
        myFixture.addClass("package demo; @dev.isira.jaspring.component.Pipe(name = \"shout\") public class Shout {}");
        template("{{ title | sh<caret>out }}");
        assertEquals("Shout", ((PsiClass) targetAtCaret()).getName());
    }

    public void testMissingResourceIsReported() {
        myFixture.enableInspections(new JaspringResourceInspection());
        myFixture.configureByText("Missing.java", """
                package demo;
                @dev.isira.jaspring.component.Component(selector = "app-missing", templateUrl = <warning descr="Jaspring resource not found: missing.html">"missing.html"</warning>)
                public class Missing {}
                """);
        myFixture.checkHighlighting(true, false, false);
    }

    public void testResourceLiteralNavigatesToTemplate() {
        final var file = template("<p>hello</p>");
        final var type = JaspringProjectModel.owners(file).getFirst().type();
        final var literal = (PsiLiteralExpression) type.getAnnotation(JaspringProjectModel.COMPONENT).findAttributeValue("templateUrl");
        assertTrue(Arrays.stream(literal.getReferences()).anyMatch(r -> file.equals(r.resolve())));
    }

    public void testAliasedInputNavigatesWithoutChangingAliasOnFieldRename() {
        final var child = myFixture.addClass("""
                package demo;
                import dev.isira.jaspring.component.*;
                @Component(selector = "app-child", template = "")
                public class Child { @Input("label") public String name; }
                """);
        final var file = template("<app-child [la<caret>bel]=\"title\"></app-child>");
        final var attribute = PsiTreeUtil.findChildOfType(file, XmlAttribute.class);
        assertTrue(Arrays.stream(attribute.getReferences()).anyMatch(r -> child.findFieldByName("name", false).equals(r.resolve())));
        myFixture.renameElement(child.findFieldByName("name", false), "caption");
        assertTrue(file.getText().contains("[label]"));
    }

    public void testOrdinaryHtmlIsNotClaimed() {
        final var file = myFixture.addFileToProject("ordinary.html", "{{ unknown }}");
        assertTrue(JaspringProjectModel.owners(file).isEmpty());
        assertFalse(file.getLanguage().equals(JaspringHtmlLanguage.INSTANCE));
    }

    public void testInlineTemplateIsInjected() {
        final var type = myFixture.addClass("""
                package demo;
                @dev.isira.jaspring.component.Component(selector = "app-inline", template = "<p>{{ title }}</p>")
                public class Inline { public String title; }
                """);
        final var host = type.getAnnotation(JaspringProjectModel.COMPONENT).findAttributeValue("template");
        final var injected = InjectedLanguageManager.getInstance(getProject()).getInjectedPsiFiles(host);
        assertNotNull(injected);
        assertEquals(JaspringHtmlLanguage.INSTANCE, injected.getFirst().first.getLanguage());
    }
}
