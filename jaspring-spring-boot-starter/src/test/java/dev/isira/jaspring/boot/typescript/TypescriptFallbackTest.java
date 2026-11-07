package dev.isira.jaspring.boot.typescript;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

/// The source matches the digest in the manifest fixture (`jaspring/jaspring-typescript.json`), so nothing is
/// compiled and the build's output serves. The source is written at test time from this text, so a formatter
/// cannot change its bytes and break the digest.
@SpringBootTest(classes = TestApplication.class)
@AutoConfigureMockMvc
class TypescriptFallbackTest {

    static final String FRESH = """
            jaspring.hooks.register<HTMLElement, string>('hello', {
              mounted(el, ctx) { el.textContent = ctx.params; },
            });
            """;

    @DynamicPropertySource
    static void sourceDir(DynamicPropertyRegistry registry) throws IOException {
        final var dir = Files.createTempDirectory("jaspring-typescript-fresh");
        Files.writeString(dir.resolve("hello.ts"), FRESH);
        registry.add("jaspring.typescript.source-dir", dir::toString);
    }

    @Autowired MockMvc mockMvc;
    @Autowired TypescriptScripts scripts;

    @Test
    void freshSourcesServeTheBuildOutput() throws Exception {
        assertEquals(1, scripts.scripts().size());
        assertTrue(scripts.inMemory("hello.js").isEmpty(), "nothing compiled at startup");
        final var js = mockMvc.perform(get("/_jaspring/ts/hello.js")).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "max-age=3600")).andReturn().getResponse().getContentAsString();
        assertTrue(js.startsWith("/* prebuilt */"), js);
        mockMvc.perform(get("/_jaspring/ts/missing.js")).andExpect(status().isNotFound());
    }
}
