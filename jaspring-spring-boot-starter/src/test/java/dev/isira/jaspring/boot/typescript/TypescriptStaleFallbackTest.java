package dev.isira.jaspring.boot.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import dev.isira.jaspring.boot.testapp.TestApplication;

/// The source differs from the manifest's digest, so the fallback compiles it at startup and serves it from memory.
@SpringBootTest(classes = TestApplication.class, properties = "jaspring.dev-mode=true")
@AutoConfigureMockMvc
class TypescriptStaleFallbackTest {

    static final String STALE = """
            jaspring.hooks.register<HTMLElement, string>('hello', {
              mounted(el, ctx) { el.textContent = 'stale: ' + ctx.params; },
            });
            """;

    @DynamicPropertySource
    static void sourceDir(DynamicPropertyRegistry registry) throws IOException {
        final var dir = Files.createTempDirectory("jaspring-typescript-stale");
        Files.writeString(dir.resolve("hello.ts"), STALE);
        registry.add("jaspring.typescript.source-dir", dir::toString);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TypescriptScripts scripts;

    @Test
    void staleSourcesAreCompiledAndServedFromMemory() throws Exception {
        assertEquals(System.getProperty("jaspring.versions.typescript", "6.0.3"), scripts.typescript());
        assertTrue(scripts.inMemory("hello.js").isPresent());
        final var js = mockMvc.perform(get("/_jaspring/ts/hello.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-cache")).andReturn().getResponse().getContentAsString();
        assertTrue(js.contains("'stale: ' + ctx.params"), js);
        assertFalse(js.contains("HTMLElement"), js);

        final var html = mockMvc.perform(get("/")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        final var digest = scripts.scripts().getFirst().sha256().substring(0, 8);
        assertTrue(html.contains("<script type=\"module\" src=\"/_jaspring/ts/hello.js?v=" + digest + "\"></script>"), html);
    }
}
