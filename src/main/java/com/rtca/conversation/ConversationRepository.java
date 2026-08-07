package com.rtca.conversation;

import jakarta.persistence.LockModeType;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    @Query("select c.id from Conversation c where c.publicId = :publicId")
    Optional<Long> findIdByPublicId(@Param("publicId") UUID publicId);

    @Query("select c.publicId from Conversation c where c.id = :id")
    Optional<UUID> findPublicIdById(@Param("id") Long id);

    /** Bumps the version on commit, so concurrent group edits conflict. */
    @Lock(LockModeType.OPTIMISTIC_FORCE_INCREMENT)
    @Query("select c from Conversation c where c.id = :id")
    Optional<Conversation> findForUpdate(@Param("id") Long id);

    /** Returns the new id, or empty if another tx already created this pair. */
    @Query(nativeQuery = true, value = """
            insert into conversations (type, direct_key, created_by, created_at, updated_at)
            values ('DIRECT', :directKey, :createdBy, now(), now())
            on conflict (direct_key) do nothing
            returning id
            """)
    Optional<Long> insertDirectIfAbsent(@Param("directKey") String directKey,
                                        @Param("createdBy") Long createdBy);

    // only moves forward, safe with concurrent senders
    @Modifying
    @Query("""
            update Conversation c set c.lastMessageAt = :at
            where c.id = :id and (c.lastMessageAt is null or c.lastMessageAt < :at)
            """)
    int touchLastMessageAt(@Param("id") Long id, @Param("at") Instant at);

    @Query(value = """
            select c from Conversation c
            join ConversationParticipant p on p.conversation = c and p.user.id = :userId and p.hidden = false
            order by case when p.removedAt is not null then p.removedAt
                          else coalesce(c.lastMessageAt, c.createdAt) end desc
            """,
            countQuery = """
            select count(p) from ConversationParticipant p where p.user.id = :userId and p.hidden = false
            """)
    Page<Conversation> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select c.id from Conversation c where c.avatarId = :avatarId")
    Optional<Long> findIdByAvatarId(@Param("avatarId") UUID avatarId);
}
