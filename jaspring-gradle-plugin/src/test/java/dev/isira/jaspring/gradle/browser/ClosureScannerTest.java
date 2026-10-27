package dev.isira.jaspring.gradle.browser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClosureScannerTest {

    @TempDir
    Path temp;

    @Test
    void idFormulaMatchesTheCoreVector() {
        // Pinned in jaspring-core's ClosureIdsTest; both sides must derive the same id.
        assertEquals("1cea62e377822423", ClosureScanner.ClosureIds.of("com/example/GeoComponent", "lambda$locate$0",
                "(Ljava/lang/String;Ldev/isira/jaspring/browser/BrowserClient;)Ljava/lang/String;"));
    }

    @Test
    void findsSerializableBrowserTaskLambdasWithTimingCapturesAndReturnShapes() throws IOException {
        final var classes = temp.resolve("classes");
        Fixtures.compile(classes, Map.of("fx.Comp", Fixtures.COMPONENT));

        final var closures = ClosureScanner.scan(List.of(classes), List.of(Fixtures.CORE_JAR));

        assertEquals(5, closures.size(), closures.toString());
        final var byEnclosing = closures.stream().collect(java.util.stream.Collectors.toMap(FoundClosure::enclosingMethod, c -> c));
        assertEquals(FoundClosure.Timing.MOUNT, byEnclosing.get("<init>").timing(), "constructor lambdas mount");
        assertEquals(FoundClosure.Timing.MOUNT, byEnclosing.get("ngOnInit").timing());
        assertEquals(FoundClosure.Timing.EVENT, byEnclosing.get("save").timing());

        final var save = byEnclosing.get("save");
        assertEquals("fx.Comp", save.implClass());
        assertTrue(save.implMethod().startsWith("lambda$save$"), save.implMethod());
        assertEquals(List.of("java.lang.String", "int"), save.captured().stream().map(TypeShapes.Shape::sourceName).toList());
        assertEquals("fx.Comp$Spot", save.returnType());
        assertFalse(save.promise());
        final var spot = (TypeShapes.RecordShape) save.returnShape();
        assertEquals(List.of("city", "zoom", "tags", "kind"), spot.components().stream().map(TypeShapes.Component::name).toList());
        assertTrue(spot.components().get(2).shape() instanceof TypeShapes.ListShape list && list.element().sourceName().equals("java.lang.String"));
        assertTrue(spot.components().get(3).shape() instanceof TypeShapes.OptionalShape optional && optional.value() instanceof TypeShapes.EnumShape);
        assertEquals(16, save.id().length());
        assertEquals(64, save.classSha256().length());

        final var locate = byEnclosing.get("locate");
        assertTrue(locate.promise());
        assertEquals("dev.isira.jaspring.browser.Geolocation$Position", locate.returnType(), "the class literal names the promise type");

        final var kind = byEnclosing.get("kind");
        // javac orders captured values by first use in the body; the descriptor (and SerializedLambda) is the truth.
        assertEquals(List.of("int[]", "fx.Comp$Kind"), kind.captured().stream().map(TypeShapes.Shape::sourceName).toList());
        assertTrue(kind.returnShape() instanceof TypeShapes.EnumShape);
    }

    @Test
    void closuresCapturingThisAreRejectedNamingTheSite() throws IOException {
        final var classes = temp.resolve("this");
        Fixtures.compile(classes, Map.of("fx.CapturesThis", Fixtures.CAPTURES_THIS));
        final var error = assertThrows(IllegalArgumentException.class, () -> ClosureScanner.scan(List.of(classes), List.of()));
        assertTrue(error.getMessage().startsWith("fx.CapturesThis.go:"), error.getMessage());
        assertTrue(error.getMessage().contains("instance members"), error.getMessage());
    }

    @Test
    void erasedCollectionsAtTheTopLevelAreRejected() throws IOException {
        final var classes = temp.resolve("list");
        Fixtures.compile(classes, Map.of("fx.TopLevelList", Fixtures.TOP_LEVEL_LIST));
        final var error = assertThrows(IllegalArgumentException.class, () -> ClosureScanner.scan(List.of(classes), List.of()));
        assertTrue(error.getMessage().contains("wrap it in a record"), error.getMessage());
    }

    @Test
    void promiseClosuresNeedTheTypeToken() throws IOException {
        final var classes = temp.resolve("promise");
        Fixtures.compile(classes, Map.of("fx.PromiseWithoutToken", Fixtures.PROMISE_WITHOUT_TOKEN));
        final var error = assertThrows(IllegalArgumentException.class, () -> ClosureScanner.scan(List.of(classes), List.of()));
        assertTrue(error.getMessage().contains("onBrowserAsync"), error.getMessage());
    }

    @Test
    void idsAreStableAcrossCompilations() throws IOException {
        final var first = temp.resolve("first");
        final var second = temp.resolve("second");
        Fixtures.compile(first, Map.of("fx.Comp", Fixtures.COMPONENT));
        Fixtures.compile(second, Map.of("fx.Comp", Fixtures.COMPONENT));
        final var ids = ClosureScanner.scan(List.of(first), List.of(Fixtures.CORE_JAR)).stream().map(FoundClosure::id).toList();
        assertEquals(ids, ClosureScanner.scan(List.of(second), List.of(Fixtures.CORE_JAR)).stream().map(FoundClosure::id).toList());
    }
}
