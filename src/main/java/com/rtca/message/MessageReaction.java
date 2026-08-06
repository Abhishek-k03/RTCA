package com.rtca.message;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.Instant;

@Entity
@Table(name = "message_reactions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MessageReaction {

    @EmbeddedId
    private Key id;

    @Column(nullable = false, length = 16)
    private String emoji;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Embeddable
    public record Key(
            @Column(name = "message_id") Long messageId,
            @Column(name = "user_id") Long userId
    ) implements Serializable {
    }
}
