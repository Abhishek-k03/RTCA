package com.rtca.message.dto;

import com.rtca.file.FileUrls;
import com.rtca.file.StoredFile;
import com.rtca.message.Message;
import com.rtca.message.MessageType;

import java.time.Instant;
import java.util.List;
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
        Image image,
        ReplyPreview replyTo,
        List<Reaction> reactions
) {
    private static final int PREVIEW_LENGTH = 160;

    public record Image(String url, Integer width, Integer height) {
    }

    /** The quoted message, shortened. content is null once it was deleted. */
    public record ReplyPreview(Long id, UUID senderId, String senderName, String content, MessageType type,
                               boolean deleted) {
    }

    public static MessageResponse from(Message m) {
        return from(m, List.of());
    }

    public static MessageResponse from(Message m, List<Reaction> reactions) {
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
                image(m),
                replyTo(m),
                m.isDeleted() ? List.of() : reactions
        );
    }

    private static Image image(Message m) {
        StoredFile f = m.getFile();
        return f == null || m.isDeleted() ? null : new Image(FileUrls.of(f.getId()), f.getWidth(), f.getHeight());
    }

    private static ReplyPreview replyTo(Message m) {
        Message r = m.getReplyTo();
        if (r == null || m.isDeleted()) {
            return null;
        }
        String name = r.getSender().getDisplayName() != null ? r.getSender().getDisplayName() : r.getSender().getUsername();
        String content = r.isDeleted() ? null : shorten(r.getContent());
        return new ReplyPreview(r.getId(), r.getSender().getPublicId(), name, content, r.getType(), r.isDeleted());
    }

    private static String shorten(String s) {
        return s.length() <= PREVIEW_LENGTH ? s : s.substring(0, PREVIEW_LENGTH).stripTrailing() + "…";
    }
}
