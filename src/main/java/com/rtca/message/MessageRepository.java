package com.rtca.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    boolean existsByIdAndConversationId(Long id, Long conversationId);

    @Query("select m from Message m join fetch m.sender where m.id = :id")
    Optional<Message> findWithSender(@Param("id") Long id);

    @Query("""
            select m from Message m join fetch m.sender
            where m.sender.id = :senderId and m.clientMessageId = :clientMessageId
            """)
    Optional<Message> findByClientId(@Param("senderId") Long senderId,
                                     @Param("clientMessageId") String clientMessageId);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversationId = :conversationId and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id desc
            """)
    List<Message> findLatest(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                             @Param("minId") Long minId, @Param("maxId") Long maxId, Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversationId = :conversationId and m.id < :before and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id desc
            """)
    List<Message> findBefore(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                             @Param("before") Long before, @Param("minId") Long minId, @Param("maxId") Long maxId,
                             Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversationId = :conversationId and m.id > :after and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id asc
            """)
    List<Message> findAfter(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                            @Param("after") Long after, @Param("minId") Long minId, @Param("maxId") Long maxId,
                            Pageable pageable);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into message_hides (user_id, message_id) values (:userId, :messageId)
            on conflict do nothing
            """)
    void hide(@Param("userId") Long userId, @Param("messageId") Long messageId);

    @Query("select coalesce(max(m.id), 0) from Message m where m.conversationId = :conversationId")
    Long findLastId(@Param("conversationId") Long conversationId);

    /** Rows of [conversationId, unreadCount] for the given user. */
    @Query("""
            select m.conversationId, count(m) from Message m, ConversationParticipant p
            where p.conversation.id = m.conversationId
              and p.user.id = :userId
              and m.conversationId in :conversationIds
              and m.id > p.lastReadMessageId
              and m.id > p.clearedUpToMessageId
              and m.deletedAt is null
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
              and (p.removedAfterMessageId is null or m.id <= p.removedAfterMessageId)
              and m.sender.id <> :userId
            group by m.conversationId
            """)
    List<Object[]> countUnread(@Param("userId") Long userId,
                               @Param("conversationIds") Collection<Long> conversationIds);

    /** Empty result means this (sender, clientMessageId) was already stored. */
    @Query(nativeQuery = true, value = """
            insert into messages (conversation_id, sender_id, content, type, client_message_id, created_at)
            values (:conversationId, :senderId, :content, 'TEXT', :clientMessageId, now())
            on conflict (sender_id, client_message_id) do nothing
            returning id
            """)
    Optional<Long> insertIfAbsent(@Param("conversationId") Long conversationId,
                                  @Param("senderId") Long senderId,
                                  @Param("content") String content,
                                  @Param("clientMessageId") String clientMessageId);
}
