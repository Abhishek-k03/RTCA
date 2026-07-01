package com.rtca.conversation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    boolean existsByConversationIdAndUserId(Long conversationId, Long userId);

    Optional<ConversationParticipant> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("""
            select p from ConversationParticipant p join fetch p.user
            where p.conversation.id = :conversationId
            order by p.joinedAt
            """)
    List<ConversationParticipant> findWithUsers(@Param("conversationId") Long conversationId);

    @Query("""
            select p from ConversationParticipant p join fetch p.user
            where p.conversation.id in :conversationIds
            """)
    List<ConversationParticipant> findWithUsers(@Param("conversationIds") Collection<Long> conversationIds);

    @Query("select p.user.id from ConversationParticipant p where p.conversation.id = :conversationId")
    List<Long> findUserIds(@Param("conversationId") Long conversationId);

    long countByConversationId(Long conversationId);
}
