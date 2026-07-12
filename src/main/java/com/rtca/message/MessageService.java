package com.rtca.message;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.ConflictException;
import com.rtca.conversation.ConversationRepository;
import com.rtca.conversation.MembershipService;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MessageService {

    static final int MAX_PAGE_SIZE = 100;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final MembershipService membershipService;
    private final ApplicationEventPublisher eventPublisher;

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

        MessageResponse response = MessageResponse.from(message);
        // broadcast happens after commit, see ChatEventPublisher
        eventPublisher.publishEvent(new MessageCreatedEvent(response));
        return new SendResult(response, true);
    }

    /**
     * Newest first by default. "before" pages backwards, "after" returns
     * newer messages oldest first (used to catch up after reconnecting).
     */
    @Transactional(readOnly = true)
    public MessagePage history(Long userId, Long conversationId, Long before, Long after, int limit) {
        membershipService.requireMember(conversationId, userId);
        if (before != null && after != null) {
            throw new BadRequestException("Use either 'before' or 'after', not both");
        }

        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        // fetch one extra row to know if there is more
        var page = PageRequest.of(0, size + 1);

        List<Message> rows;
        if (after != null) {
            rows = messageRepository.findAfter(conversationId, after, page);
        } else if (before != null) {
            rows = messageRepository.findBefore(conversationId, before, page);
        } else {
            rows = messageRepository.findLatest(conversationId, page);
        }

        boolean hasMore = rows.size() > size;
        List<MessageResponse> items = rows.stream().limit(size).map(MessageResponse::from).toList();
        Long nextCursor = items.isEmpty() ? null : items.getLast().id();
        return new MessagePage(items, nextCursor, hasMore);
    }
}
