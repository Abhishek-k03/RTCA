package com.rtca.file;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

/** Image type from the file's first bytes, never from what the client claims. */
final class ImageTypes {

    static final int HEADER_BYTES = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private ImageTypes() {
    }

    static Optional<String> detect(byte[] head) {
        if (matches(head, 0, JPEG)) {
            return Optional.of("image/jpeg");
        }
        if (matches(head, 0, PNG)) {
            return Optional.of("image/png");
        }
        if (matches(head, 0, ascii("GIF87a")) || matches(head, 0, ascii("GIF89a"))) {
            return Optional.of("image/gif");
        }
        if (matches(head, 0, ascii("RIFF")) && matches(head, 8, ascii("WEBP"))) {
            return Optional.of("image/webp");
        }
        return Optional.empty();
    }

    private static boolean matches(byte[] data, int offset, byte[] expected) {
        if (data.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if (data[offset + i] != expected[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] ascii(String s) {
        return s.getBytes(StandardCharsets.US_ASCII);
    }
}
