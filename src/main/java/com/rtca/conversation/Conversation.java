package com.rtca.conversation;

import com.rtca.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Conversation extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ConversationType type;

    @Column(length = 100)
    private String name;

    /** "minUserId:maxUserId" for direct chats, null for groups. */
    @Column(name = "direct_key", unique = true, length = 64)
    private String directKey;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "last_message_at")
    private Instant lastMessageAt;

    public boolean isGroup() {
        return type == ConversationType.GROUP;
    }

    public static String directKey(Long a, Long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
