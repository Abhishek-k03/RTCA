package com.rtca.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageRepository extends JpaRepository<Message, Long> {

    boolean existsByIdAndConversationId(Long id, Long conversationId);

    @Query("select m from Message m join fetch m.sender left join fetch m.file where m.id = :id")
    Optional<Message> findWithSender(@Param("id") Long id);

    @Query("""
            select m from Message m join fetch m.sender left join fetch m.file
            where m.sender.id = :senderId and m.clientMessageId = :clientMessageId
            """)
    Optional<Message> findByClientId(@Param("senderId") Long senderId,
                                     @Param("clientMessageId") String clientMessageId);

    @Query("""
            select m from Message m join fetch m.sender left join fetch m.file
            where m.conversationId = :conversationId and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id desc
            """)
    List<Message> findLatest(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                             @Param("minId") Long minId, @Param("maxId") Long maxId, Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender left join fetch m.file
            where m.conversationId = :conversationId and m.id < :before and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id desc
            """)
    List<Message> findBefore(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                             @Param("before") Long before, @Param("minId") Long minId, @Param("maxId") Long maxId,
                             Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender left join fetch m.file
            where m.conversationId = :conversationId and m.id > :after and m.id > :minId and m.id <= :maxId
              and not exists (select 1 from MessageHide h where h.id.messageId = m.id and h.id.userId = :userId)
            order by m.id asc
            """)
    List<Message> findAfter(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                            @Param("after") Long after, @Param("minId") Long minId, @Param("maxId") Long maxId,
                            Pageable pageable);

    Optional<Message> findByFileId(UUID fileId);

    @Query("""
            select count(h) > 0 from MessageHide h
            where h.id.userId = :userId and h.id.messageId = :messageId
            """)
    boolean isHidden(@Param("userId") Long userId, @Param("messageId") Long messageId);

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into message_hides (user_id, message_id) values (:userId, :messageId)
            on conflict do nothing
            """)
    void hide(@Param("userId") Long userId, @Param("messageId") Long messageId);

    /** Newest message each conversation shows this user (after clear, before removal, not hidden). */
    @Query(nativeQuery = true, value = """
            select lm.id from conversation_participants p
            cross join lateral (
                select m.id from messages m
                where m.conversation_id = p.conversation_id
                  and m.id > p.cleared_up_to_message_id
                  and (p.removed_after_message_id is null or m.id <= p.removed_after_message_id)
                  and not exists (select 1 from message_hides h where h.message_id = m.id and h.user_id = p.user_id)
                order by m.id desc
                limit 1
            ) lm
            where p.user_id = :userId and p.conversation_id in (:conversationIds)
            """)
    List<Long> findLastVisibleIds(@Param("userId") Long userId,
                                  @Param("conversationIds") Collection<Long> conversationIds);

    @Query("select m from Message m join fetch m.sender where m.id in :ids")
    List<Message> findAllWithSender(@Param("ids") Collection<Long> ids);

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

    /** Same as insertIfAbsent, for an image with an optional caption. */
    @Query(nativeQuery = true, value = """
            insert into messages (conversation_id, sender_id, content, type, client_message_id, file_id, created_at)
            values (:conversationId, :senderId, :content, 'IMAGE', :clientMessageId, :fileId, now())
            on conflict (sender_id, client_message_id) do nothing
            returning id
            """)
    Optional<Long> insertImageIfAbsent(@Param("conversationId") Long conversationId,
                                       @Param("senderId") Long senderId,
                                       @Param("content") String content,
                                       @Param("clientMessageId") String clientMessageId,
                                       @Param("fileId") UUID fileId);
}
