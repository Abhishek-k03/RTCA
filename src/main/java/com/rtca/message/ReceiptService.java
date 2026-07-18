package com.rtca.message;

import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.MembershipService;
import com.rtca.conversation.ParticipantRepository;
import com.rtca.websocket.ChatEvent;
import com.rtca.websocket.ChatEvent.EventType;
import com.rtca.websocket.ChatEventPublisher;
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

    /** Marks everything up to messageId as read (which implies delivered). */
    @Transactional
    public void markRead(Long userId, Long conversationId, Long messageId) {
        validate(userId, conversationId, messageId);
        if (participantRepository.advanceRead(conversationId, userId, messageId) > 0) {
            publishAfterCommit(conversationId, EventType.READ, new Receipt(conversationId, userId, messageId));
        }
    }

    @Transactional
    public void markDelivered(Long userId, Long conversationId, Long messageId) {
        validate(userId, conversationId, messageId);
        if (participantRepository.advanceDelivered(conversationId, userId, messageId) > 0) {
            publishAfterCommit(conversationId, EventType.DELIVERED, new Receipt(conversationId, userId, messageId));
        }
    }

    private void validate(Long userId, Long conversationId, Long messageId) {
        membershipService.requireMember(conversationId, userId);
        if (!messageRepository.existsByIdAndConversationId(messageId, conversationId)) {
            throw new NotFoundException("Message not found");
        }
    }

    private void publishAfterCommit(Long conversationId, EventType type, Receipt receipt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publisher.toConversation(conversationId, ChatEvent.of(type, receipt));
            }
        });
    }

    public record Receipt(Long conversationId, Long userId, Long messageId) {
    }
}
