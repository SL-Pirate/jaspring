package dev.isira.jaspring.browser;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/// The id of a compiled closure, shared by the server (from `SerializedLambda`) and the Gradle plugin (from the
/// class file): the first 8 bytes of SHA-256 over `implClass#implMethod` + descriptor, as 16 hex characters.
/// `implClass` is the binary name with slashes, as both sources report it.
public final class ClosureIds {

    private ClosureIds() {
    }

    public static String of(String implClass, String implMethodName, String implMethodSignature) {
        final var text = implClass.replace('.', '/') + "#" + implMethodName + implMethodSignature;
        try {
            final var digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
