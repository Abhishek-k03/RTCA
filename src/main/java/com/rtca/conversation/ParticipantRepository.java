package com.rtca.conversation;

import com.rtca.common.config.CacheConfig;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ParticipantRepository extends JpaRepository<ConversationParticipant, Long> {

    // hot path for every send/subscribe. only "true" is cached
    @Cacheable(cacheNames = CacheConfig.MEMBERSHIP, key = "#p0 + ':' + #p1", unless = "!#result")
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

    // pointers only move forward, so late or duplicate receipts are no-ops
    @Modifying
    @Query("""
            update ConversationParticipant p
            set p.lastReadMessageId = :messageId,
                p.lastDeliveredMessageId = greatest(p.lastDeliveredMessageId, :messageId)
            where p.conversation.id = :conversationId and p.user.id = :userId
              and p.lastReadMessageId < :messageId
            """)
    int advanceRead(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                    @Param("messageId") Long messageId);

    @Modifying
    @Query("""
            update ConversationParticipant p set p.lastDeliveredMessageId = :messageId
            where p.conversation.id = :conversationId and p.user.id = :userId
              and p.lastDeliveredMessageId < :messageId
            """)
    int advanceDelivered(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                         @Param("messageId") Long messageId);
}
