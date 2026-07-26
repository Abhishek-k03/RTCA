package com.rtca.message;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.ConflictException;
import com.rtca.common.ratelimit.RateLimiter;
import com.rtca.conversation.ConversationParticipant;
import com.rtca.conversation.ConversationRepository;
import com.rtca.conversation.MemberAddedEvent;
import com.rtca.conversation.MembershipService;
import com.rtca.conversation.ParticipantRepository;
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
    private final ParticipantRepository participantRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter;

    /**
     * Idempotent on (sender, clientMessageId). A retried send returns the
     * original message instead of creating a duplicate.
     */
    @Transactional
    public SendResult send(Long senderId, Long conversationId, SendMessageRequest request) {
        rateLimiter.checkMessageSend(senderId);
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
        revealToHiddenParticipants(conversationId);

        MessageResponse response = MessageResponse.from(message);
        // broadcast happens after commit, see ChatEventPublisher
        eventPublisher.publishEvent(new MessageCreatedEvent(response));
        return new SendResult(response, true);
    }

    // a new direct chat shows up for the receiver with its first message
    private void revealToHiddenParticipants(Long conversationId) {
        List<Long> hidden = participantRepository.findHiddenUserIds(conversationId);
        if (hidden.isEmpty()) {
            return;
        }
        participantRepository.unhideAll(conversationId);
        hidden.forEach(userId -> eventPublisher.publishEvent(new MemberAddedEvent(conversationId, userId)));
    }

    /**
     * Newest first by default. "before" pages backwards, "after" returns
     * newer messages oldest first (used to catch up after reconnecting).
     */
    @Transactional(readOnly = true)
    public MessagePage history(Long userId, Long conversationId, Long before, Long after, int limit) {
        // removed members can still read up to the point they were removed
        ConversationParticipant p = membershipService.requireAccess(conversationId, userId);
        long minId = p.getClearedUpToMessageId();
        long maxId = p.visibleUpTo();
        if (before != null && after != null) {
            throw new BadRequestException("Use either 'before' or 'after', not both");
        }

        int size = Math.clamp(limit, 1, MAX_PAGE_SIZE);
        // fetch one extra row to know if there is more
        var page = PageRequest.of(0, size + 1);

        List<Message> rows;
        if (after != null) {
            rows = messageRepository.findAfter(conversationId, after, minId, maxId, page);
        } else if (before != null) {
            rows = messageRepository.findBefore(conversationId, before, minId, maxId, page);
        } else {
            rows = messageRepository.findLatest(conversationId, minId, maxId, page);
        }

        boolean hasMore = rows.size() > size;
        List<MessageResponse> items = rows.stream().limit(size).map(MessageResponse::from).toList();
        Long nextCursor = items.isEmpty() ? null : items.getLast().id();
        return new MessagePage(items, nextCursor, hasMore);
    }
}
