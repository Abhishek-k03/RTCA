package com.rtca.message;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface ReactionRepository extends JpaRepository<MessageReaction, MessageReaction.Key> {

    @Modifying
    @Query(nativeQuery = true, value = """
            insert into message_reactions (message_id, user_id, emoji) values (:messageId, :userId, :emoji)
            on conflict (message_id, user_id) do update set emoji = excluded.emoji, created_at = now()
            """)
    void upsert(@Param("messageId") Long messageId, @Param("userId") Long userId, @Param("emoji") String emoji);

    @Modifying
    @Query("delete from MessageReaction r where r.id.messageId = :messageId and r.id.userId = :userId")
    void remove(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Modifying
    @Query("delete from MessageReaction r where r.id.messageId = :messageId")
    void removeAll(@Param("messageId") Long messageId);

    /** Rows of [messageId, emoji, user public id], oldest first. */
    @Query("""
            select r.id.messageId, r.emoji, u.publicId from MessageReaction r
            join User u on u.id = r.id.userId
            where r.id.messageId in :messageIds
            order by r.createdAt
            """)
    List<Object[]> findForMessages(@Param("messageIds") Collection<Long> messageIds);
}
