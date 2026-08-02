package com.rtca.file;

import org.springframework.core.io.Resource;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

/** Where file bytes live. Metadata and access rules stay in the database. */
public interface FileStorage {

    void save(UUID id, InputStream content);

    Optional<Resource> load(UUID id);

    void delete(UUID id);
}
