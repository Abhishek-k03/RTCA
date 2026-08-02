package com.rtca.file;

import java.util.UUID;

public final class FileUrls {

    private FileUrls() {
    }

    public static String of(UUID fileId) {
        return fileId == null ? null : "/api/files/" + fileId;
    }
}
