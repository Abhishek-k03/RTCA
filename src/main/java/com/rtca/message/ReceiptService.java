package com.rtca.message;

import com.rtca.common.exception.NotFoundException;
import com.rtca.common.ids.PublicIds;
import com.rtca.conversation.ConversationParticipant;
import com.rtca.conversation.MembershipService;
import com.rtca.conversation.ParticipantRepository;
import com.rtca.websocket.ChatEvent;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.ChatEventPublisher;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class ReceiptService {

    private final ParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final MembershipService membershipService;
    private final ChatEventPublisher publisher;
    private final PublicIds ids;

    /** Marks everything up to messageId as read (which implies delivered). */
    @Transactional
    public void markRead(Long userId, Long conversationId, Long messageId) {
        ConversationParticipant p = membershipService.requireAccess(conversationId, userId);
        long id = visibleId(p, conversationId, messageId);
        if (id > 0 && participantRepository.advanceRead(conversationId, userId, id) > 0 && p.isActive()) {
            publishAfterCommit(conversationId, EventType.READ, receipt(conversationId, userId, id));
        }
    }

    @Transactional
    public void markDelivered(Long userId, Long conversationId, Long messageId) {
        ConversationParticipant p = membershipService.requireAccess(conversationId, userId);
        long id = visibleId(p, conversationId, messageId);
        if (id > 0 && participantRepository.advanceDelivered(conversationId, userId, id) > 0 && p.isActive()) {
            publishAfterCommit(conversationId, EventType.DELIVERED, receipt(conversationId, userId, id));
        }
    }

    // removed members only move pointers up to their cutoff, and nobody is told
    private long visibleId(ConversationParticipant p, Long conversationId, Long messageId) {
        if (!messageRepository.existsByIdAndConversationId(messageId, conversationId)) {
            throw new NotFoundException("Message not found");
        }
        return Math.min(messageId, p.visibleUpTo());
    }

    private void publishAfterCommit(Long conversationId, EventType type, Receipt receipt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.toConversation(conversationId, ChatEvent.of(type, receipt));
            }
        });
    }

    private Receipt receipt(Long conversationId, Long userId, long messageId) {
        return new Receipt(ids.conversation(conversationId), ids.user(userId), messageId);
    }

    public record Receipt(UUID conversationId, UUID userId, Long messageId) {
    }
}
