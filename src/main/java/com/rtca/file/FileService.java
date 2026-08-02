package com.rtca.file;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.ParticipantRepository;
import com.rtca.message.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.unit.DataSize;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileRepository fileRepository;
    private final FileStorage storage;
    private final FileProperties properties;
    private final MessageRepository messageRepository;
    private final ParticipantRepository participantRepository;

    public record Download(Resource resource, String contentType, long size) {
    }

    /** Validates and stores an uploaded image. The bytes go away again if the transaction rolls back. */
    @Transactional
    public StoredFile store(Long ownerId, FilePurpose purpose, MultipartFile upload, Integer width, Integer height) {
        if (upload == null || upload.isEmpty()) {
            throw new BadRequestException("File is empty");
        }
        DataSize max = purpose == FilePurpose.AVATAR ? properties.maxAvatarSize() : properties.maxImageSize();
        if (upload.getSize() > max.toBytes()) {
            throw new BadRequestException("File is larger than " + max.toMegabytes() + " MB");
        }
        String type = ImageTypes.detect(read(upload, in -> in.readNBytes(ImageTypes.HEADER_BYTES)))
                .orElseThrow(() -> new BadRequestException("Only JPEG, PNG, GIF and WebP images are allowed"));

        StoredFile file = fileRepository.save(StoredFile.builder()
                .ownerId(ownerId)
                .purpose(purpose)
                .contentType(type)
                .sizeBytes(upload.getSize())
                .width(width)
                .height(height)
                .build());
        UUID id = file.getId();
        read(upload, in -> {
            storage.save(id, in);
            return null;
        });
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    storage.delete(id);
                }
            }
        });
        return file;
    }

    /** The row goes now, the bytes once that is committed. */
    public void deleteAfterCommit(UUID id) {
        fileRepository.findById(id).ifPresent(fileRepository::delete);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                storage.delete(id);
            }
        });
    }

    /** 404 for anything the viewer may not see, so file ids can't be probed. */
    @Transactional(readOnly = true)
    public Download open(Long viewerId, UUID id) {
        Supplier<NotFoundException> notFound = () -> new NotFoundException("File not found");
        StoredFile file = fileRepository.findById(id)
                .filter(f -> canView(viewerId, f))
                .orElseThrow(notFound);
        return new Download(storage.load(id).orElseThrow(notFound), file.getContentType(), file.getSizeBytes());
    }

    // avatars are visible to everyone signed in, chat images follow the same rules as history
    private boolean canView(Long viewerId, StoredFile file) {
        if (file.getPurpose() == FilePurpose.AVATAR) {
            return true;
        }
        return messageRepository.findByFileId(file.getId())
                .filter(m -> !m.isDeleted() && !messageRepository.isHidden(viewerId, m.getId()))
                .flatMap(m -> participantRepository.findByConversationIdAndUserId(m.getConversationId(), viewerId)
                        .filter(p -> m.getId() > p.getClearedUpToMessageId() && m.getId() <= p.visibleUpTo()))
                .isPresent();
    }

    private interface StreamReader<T> {
        T read(InputStream in) throws IOException;
    }

    private static <T> T read(MultipartFile upload, StreamReader<T> reader) {
        try (InputStream in = upload.getInputStream()) {
            return reader.read(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read upload", e);
        }
    }
}
