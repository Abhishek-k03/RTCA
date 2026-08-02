package com.rtca.file;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;

/** Files on a local disk or mounted volume. */
@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(FileProperties properties) {
        this.root = properties.dir().toAbsolutePath().normalize();
    }

    @Override
    public void save(UUID id, InputStream content) {
        Path target = path(id);
        try {
            Files.createDirectories(target.getParent());
            // write aside first so a half written file is never served
            Path tmp = Files.createTempFile(target.getParent(), id.toString(), ".tmp");
            try {
                Files.copy(content, tmp, StandardCopyOption.REPLACE_EXISTING);
                Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(tmp);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not store file " + id, e);
        }
    }

    @Override
    public Optional<Resource> load(UUID id) {
        Path file = path(id);
        return Files.isRegularFile(file) ? Optional.of(new FileSystemResource(file)) : Optional.empty();
    }

    @Override
    public void delete(UUID id) {
        try {
            Files.deleteIfExists(path(id));
        } catch (IOException e) {
            log.warn("Could not delete file {}: {}", id, e.getMessage());
        }
    }

    // spread over subfolders so no single folder gets huge
    private Path path(UUID id) {
        String name = id.toString();
        return root.resolve(name.substring(0, 2)).resolve(name);
    }
}
