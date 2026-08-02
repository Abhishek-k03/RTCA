package com.rtca.message.dto;

import com.rtca.file.FileUrls;
import com.rtca.file.StoredFile;
import com.rtca.message.Message;
import com.rtca.message.MessageType;

import java.time.Instant;
import java.util.UUID;

public record MessageResponse(
        Long id,
        UUID conversationId,
        UUID senderId,
        String senderUsername,
        String content,
        MessageType type,
        String clientMessageId,
        Instant createdAt,
        Instant editedAt,
        boolean deleted,
        Image image
) {
    public record Image(String url, Integer width, Integer height) {
    }

    public static MessageResponse from(Message m) {
        return new MessageResponse(
                m.getId(),
                m.getConversation().getPublicId(),
                m.getSender().getPublicId(),
                m.getSender().getUsername(),
                m.isDeleted() ? null : m.getContent(),
                m.getType(),
                m.getClientMessageId(),
                m.getCreatedAt(),
                m.getEditedAt(),
                m.isDeleted(),
                image(m)
        );
    }

    private static Image image(Message m) {
        StoredFile f = m.getFile();
        return f == null || m.isDeleted() ? null : new Image(FileUrls.of(f.getId()), f.getWidth(), f.getHeight());
    }
}
