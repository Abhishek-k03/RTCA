package com.rtca.message;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.ConversationParticipant;
import com.rtca.conversation.MembershipService;
import com.rtca.message.dto.Reaction;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ReactionService {

    static final Set<String> ALLOWED = Set.of("👍", "❤️", "😂", "😮", "😢", "🙏");

    private final ReactionRepository reactionRepository;
    private final MessageRepository messageRepository;
    private final MembershipService membershipService;
    private final ApplicationEventPublisher eventPublisher;

    /** Sets this member's reaction, replacing any earlier one. */
    @Transactional
    public List<Reaction> react(Long userId, Long conversationId, Long messageId, String emoji) {
        if (!ALLOWED.contains(emoji)) {
            throw new BadRequestException("Unsupported reaction");
        }
        requireReactable(userId, conversationId, messageId);
        reactionRepository.upsert(messageId, userId, emoji);
        return changed(conversationId, messageId);
    }

    @Transactional
    public List<Reaction> unreact(Long userId, Long conversationId, Long messageId) {
        requireReactable(userId, conversationId, messageId);
        reactionRepository.remove(messageId, userId);
        return changed(conversationId, messageId);
    }

    @Transactional(readOnly = true)
    public List<Reaction> forMessage(Long messageId) {
        return forMessages(List.of(messageId)).getOrDefault(messageId, List.of());
    }

    /** One query for a whole page of messages. */
    @Transactional(readOnly = true)
    public Map<Long, List<Reaction>> forMessages(Collection<Long> messageIds) {
        if (messageIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, Map<String, List<UUID>>> grouped = new HashMap<>();
        for (Object[] row : reactionRepository.findForMessages(messageIds)) {
            grouped.computeIfAbsent((Long) row[0], id -> new LinkedHashMap<>())
                    .computeIfAbsent((String) row[1], e -> new ArrayList<>())
                    .add((UUID) row[2]);
        }
        Map<Long, List<Reaction>> result = new HashMap<>();
        grouped.forEach((id, byEmoji) -> result.put(id, byEmoji.entrySet().stream()
                .map(e -> new Reaction(e.getKey(), List.copyOf(e.getValue())))
                .toList()));
        return result;
    }

    // active members only, on messages they can see and that weren't deleted
    private void requireReactable(Long userId, Long conversationId, Long messageId) {
        ConversationParticipant p = membershipService.getParticipant(conversationId, userId);
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getConversationId().equals(conversationId) && m.getId() > p.getClearedUpToMessageId())
                .orElseThrow(() -> new NotFoundException("Message not found"));
        if (message.isDeleted()) {
            throw new BadRequestException("Message was deleted");
        }
    }

    // everyone gets the full new list, so the order events arrive in doesn't matter much
    private List<Reaction> changed(Long conversationId, Long messageId) {
        List<Reaction> reactions = forMessage(messageId);
        eventPublisher.publishEvent(new ReactionsChangedEvent(conversationId, messageId, reactions));
        return reactions;
    }
}
