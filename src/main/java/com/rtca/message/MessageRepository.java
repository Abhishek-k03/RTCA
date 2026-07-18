package com.rtca.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
            where m.conversationId = :conversationId
            order by m.id desc
            """)
    List<Message> findLatest(@Param("conversationId") Long conversationId, Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversationId = :conversationId and m.id < :before
            order by m.id desc
            """)
    List<Message> findBefore(@Param("conversationId") Long conversationId,
                             @Param("before") Long before, Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversationId = :conversationId and m.id > :after
            order by m.id asc
            """)
    List<Message> findAfter(@Param("conversationId") Long conversationId,
                            @Param("after") Long after, Pageable pageable);

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
