package com.rtca.conversation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByDirectKey(String directKey);

    /** Returns the new id, or empty if another tx already created this pair. */
    @Query(nativeQuery = true, value = """
            insert into conversations (type, direct_key, created_by, created_at, updated_at)
            values ('DIRECT', :directKey, :createdBy, now(), now())
            on conflict (direct_key) do nothing
            returning id
            """)
    Optional<Long> insertDirectIfAbsent(@Param("directKey") String directKey,
                                        @Param("createdBy") Long createdBy);

    @Query(value = """
            select c from Conversation c
            where exists (
                select 1 from ConversationParticipant p
                where p.conversation = c and p.user.id = :userId)
            order by coalesce(c.lastMessageAt, c.createdAt) desc
            """,
            countQuery = """
            select count(p) from ConversationParticipant p where p.user.id = :userId
            """)
    Page<Conversation> findForUser(@Param("userId") Long userId, Pageable pageable);
}
