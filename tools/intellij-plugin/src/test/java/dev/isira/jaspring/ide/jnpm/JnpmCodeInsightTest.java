package dev.isira.jaspring.ide.jnpm;

import java.util.List;
import java.util.Map;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase;
import dev.isira.jaspring.gradle.jnpm.model.DefaultJnpmModel;

public class JnpmCodeInsightTest extends LightJavaCodeInsightFixtureTestCase {
    @Override
    protected void tearDown() throws Exception {
        try {
            JnpmProjectService.get(getProject()).loadState(new JnpmProjectService.Settings());
        } finally {
            super.tearDown();
        }
    }

    public void testOptionalIntegrationsRegisterWithGradleAndJavascript() {
        if (!Boolean.getBoolean("jnpm.ideIntegrations")) return;
        final var resolvers = com.intellij.openapi.extensions.ExtensionPointName.create("org.jetbrains.plugins.gradle.projectResolve").getExtensionList();
        assertTrue(resolvers.stream().anyMatch(extension -> extension.getClass().getName().equals("dev.isira.jaspring.ide.jnpm.gradle.JnpmResolver")));
        final var language = com.intellij.lang.Language.findLanguageByID("TypeScript");
        assertNotNull(language);
        final var annotators = new com.intellij.lang.LanguageExtension<>("com.intellij.externalAnnotator").allForLanguage(language);
        assertTrue(annotators.stream().anyMatch(JnpmLintAnnotator.class::isInstance));
    }

    public void testGradleModelsKeepIndependentBuildsAndRemoveDisabledModules() {
        final var service = JnpmProjectService.get(getProject());
        service.loadState(new JnpmProjectService.Settings());
        service.replace("/build-a", List.of(model("/build-a", "/build-a/app", true)));
        service.replace("/build-b", List.of(model("/build-b", "/build-b/app", true)));
        assertNotNull(service.forFile("/build-a/app/src/main/typescript/page.ts"));
        assertNull(service.forFile("/build-a/app/src/main/typescript-other/page.ts"));
        service.replace("/build-a", List.of(model("/build-a", "/build-a/app", false)));
        assertNull(service.forFile("/build-a/app/src/main/typescript/page.ts"));
        assertNotNull(service.forFile("/build-b/app/src/main/typescript/page.ts"));
    }

    public void testLintCapturesUnsavedTextAndExplainsMissingDependencies() {
        final var file = myFixture.addFileToProject("src/main/typescript/page.ts", "const saved = 1;");
        myFixture.configureFromExistingVirtualFile(file.getVirtualFile());
        final var root = file.getVirtualFile().getPath().replace("/src/main/typescript/page.ts", "");
        final var service = JnpmProjectService.get(getProject());
        service.loadState(new JnpmProjectService.Settings());
        service.replace(root, List.of(model(root, root, true)));
        com.intellij.ide.trustedProjects.TrustedProjects.setProjectTrusted(getProject(), true);
        WriteCommandAction.runWriteCommandAction(getProject(), () -> myFixture.getEditor().getDocument().setText("const unsaved = 2;"));
        final var annotator = new JnpmLintAnnotator();
        final var input = annotator.collectInformation(myFixture.getFile());
        assertNotNull(input);
        assertEquals("const unsaved = 2;", input.text());
        final var result = annotator.doAnnotate(input);
        assertEquals(input.stamp(), result.stamp());
        assertTrue(result.problems().getFirst().message().contains("Prepare Dependencies"));
    }

    public void testEslintLocationsUseUtf16OffsetsAndClampInvalidColumns() {
        assertEquals(6, JnpmLintAnnotator.offset("😀a\nxyz", 2, 3));
        assertEquals(3, JnpmLintAnnotator.offset("😀a\nxyz", 1, 99));
        assertEquals(7, JnpmLintAnnotator.offset("😀a\nxyz", 99, 99));
    }

    private static DefaultJnpmModel model(String root, String project, boolean enabled) {
        return new DefaultJnpmModel(enabled, project, root, project + "/build", ":app", project + "/src/main/typescript", true, Map.of());
    }
}
