package com.rtca.conversation;

import com.rtca.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "conversation_participants",
        uniqueConstraints = @UniqueConstraint(name = "uk_participant", columnNames = {"conversation_id", "user_id"}))
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ConversationParticipant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ParticipantRole role;

    @CreationTimestamp
    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Column(name = "last_delivered_message_id", nullable = false)
    @Builder.Default
    private Long lastDeliveredMessageId = 0L;

    @Column(name = "last_read_message_id", nullable = false)
    @Builder.Default
    private Long lastReadMessageId = 0L;

    // hidden from this user's list until the first message
    @Column(nullable = false)
    @Builder.Default
    private boolean hidden = false;

    // messages up to this id were cleared by this member
    @Column(name = "cleared_up_to_message_id", nullable = false)
    @Builder.Default
    private Long clearedUpToMessageId = 0L;

    @Column(name = "removed_at")
    private Instant removedAt;

    // last message a removed member may still see
    @Column(name = "removed_after_message_id")
    private Long removedAfterMessageId;

    public boolean isActive() {
        return removedAt == null;
    }

    /** Highest message id this participant may see. */
    public long visibleUpTo() {
        return isActive() ? Long.MAX_VALUE : removedAfterMessageId;
    }

    public void clear(Long lastMessageId) {
        clearedUpToMessageId = Math.max(clearedUpToMessageId, Math.min(lastMessageId, visibleUpTo()));
    }

    public void remove(Long lastMessageId) {
        removedAt = Instant.now();
        removedAfterMessageId = lastMessageId;
        role = ParticipantRole.MEMBER;
    }

    public void restore() {
        removedAt = null;
        removedAfterMessageId = null;
        role = ParticipantRole.MEMBER;
    }
}
