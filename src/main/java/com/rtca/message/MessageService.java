package com.rtca.message;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.ConflictException;
import com.rtca.conversation.ConversationRepository;
import com.rtca.conversation.MembershipService;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MembershipService membershipService;

    /**
     * Idempotent on (sender, clientMessageId). A retried send returns the
     * original message instead of creating a duplicate.
     */
    @Transactional
    public SendResult send(Long senderId, Long conversationId, SendMessageRequest request) {
        membershipService.requireMember(conversationId, senderId);

        String content = request.content().strip();
        if (content.isEmpty()) {
            throw new BadRequestException("Message must not be blank");
        }

        var insertedId = messageRepository.insertIfAbsent(
                conversationId, senderId, content, request.clientMessageId());

        if (insertedId.isEmpty()) {
            Message existing = messageRepository.findByClientId(senderId, request.clientMessageId())
                    .orElseThrow(() -> new IllegalStateException("Duplicate message vanished"));
            if (!existing.getConversationId().equals(conversationId)) {
                throw new ConflictException("clientMessageId already used in another conversation");
            }
            log.debug("Duplicate send {} from user {}", request.clientMessageId(), senderId);
            return new SendResult(MessageResponse.from(existing), false);
        }

        Message message = messageRepository.findWithSender(insertedId.get()).orElseThrow();
        conversationRepository.touchLastMessageAt(conversationId, message.getCreatedAt());
        return new SendResult(MessageResponse.from(message), true);
    }
}
