package com.rtca.conversation;

import com.rtca.common.dto.PageResponse;
import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.ParticipantResponse;
import com.rtca.message.MessageRepository;
import com.rtca.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;
    private final MessageRepository messageRepository;

    @Transactional
    public ConversationResponse getOrCreateDirect(Long me, Long otherId) {
        if (me.equals(otherId)) {
            throw new BadRequestException("Cannot start a conversation with yourself");
        }
        if (!userRepository.existsById(otherId)) {
            throw new NotFoundException("User not found");
        }

        String key = Conversation.directKey(me, otherId);
        Conversation conversation = conversationRepository.findByDirectKey(key)
                .orElseGet(() -> createDirect(key, me, otherId));

        return toResponse(conversation);
    }

    private Conversation createDirect(String key, Long me, Long otherId) {
        return conversationRepository.insertDirectIfAbsent(key, me)
                .map(id -> {
                    Conversation c = conversationRepository.getReferenceById(id);
                    // stays out of both lists until the first message, like telegram
                    addParticipant(c, me, ParticipantRole.MEMBER, true);
                    addParticipant(c, otherId, ParticipantRole.MEMBER, true);
                    return c;
                })
                // lost the race, the other tx has committed by now
                .or(() -> conversationRepository.findByDirectKey(key))
                .orElseThrow(() -> new IllegalStateException("Direct conversation missing for " + key));
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Long me, Long conversationId) {
        ConversationParticipant viewer = membershipService.requireAccess(conversationId, me);
        Conversation c = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        List<ParticipantResponse> participants = participantRepository.findWithUsers(c.getId()).stream()
                .map(ParticipantResponse::from)
                .toList();
        return ConversationResponse.from(c, participants,
                unreadCounts(me, List.of(conversationId)).getOrDefault(conversationId, 0L),
                viewer.getRemovedAt());
    }

    /** Hides all current messages for this user only. */
    @Transactional
    public void clearForUser(Long me, Long conversationId) {
        membershipService.requireAccess(conversationId, me)
                .clear(messageRepository.findLastId(conversationId));
    }

    /**
     * Direct chats are cleared and hidden, the next message brings them back.
     * Groups can only be deleted after leaving.
     */
    @Transactional
    public void deleteForUser(Long me, Long conversationId) {
        ConversationParticipant p = membershipService.requireAccess(conversationId, me);
        if (!p.getConversation().isGroup()) {
            p.clear(messageRepository.findLastId(conversationId));
            p.setHidden(true);
            return;
        }
        if (p.isActive()) {
            throw new BadRequestException("Leave the group before deleting it");
        }
        participantRepository.delete(p);
    }

    @Transactional(readOnly = true)
    public PageResponse<ConversationResponse> listForUser(Long userId, Pageable pageable) {
        Page<Conversation> page = conversationRepository.findForUser(userId, pageable);
        List<Long> ids = page.getContent().stream().map(Conversation::getId).toList();

        // one query for all participants on the page
        Map<Long, List<ParticipantResponse>> participants = ids.isEmpty() ? Map.of()
                : participantRepository.findWithUsers(ids).stream()
                .collect(Collectors.groupingBy(p -> p.getConversation().getId(),
                        Collectors.mapping(ParticipantResponse::from, Collectors.toList())));

        Map<Long, Long> unread = unreadCounts(userId, ids);
        Map<Long, Instant> removed = ids.isEmpty() ? Map.of()
                : participantRepository.findRemoved(userId, ids).stream()
                .collect(Collectors.toMap(p -> p.getConversation().getId(), ConversationParticipant::getRemovedAt));

        return PageResponse.of(page, c -> ConversationResponse.from(c,
                participants.getOrDefault(c.getId(), List.of()),
                unread.getOrDefault(c.getId(), 0L),
                removed.get(c.getId())));
    }

    private Map<Long, Long> unreadCounts(Long userId, List<Long> conversationIds) {
        if (conversationIds.isEmpty()) {
            return Map.of();
        }
        return messageRepository.countUnread(userId, conversationIds).stream()
                .collect(Collectors.toMap(r -> (Long) r[0], r -> (Long) r[1]));
    }

    ConversationParticipant addParticipant(Conversation c, Long userId, ParticipantRole role) {
        return addParticipant(c, userId, role, false);
    }

    ConversationParticipant addParticipant(Conversation c, Long userId, ParticipantRole role, boolean hidden) {
        ConversationParticipant p = participantRepository.save(ConversationParticipant.builder()
                .conversation(c)
                .user(userRepository.getReferenceById(userId))
                .role(role)
                .hidden(hidden)
                .build());
        membershipService.evict(c.getId(), userId);
        return p;
    }

    ConversationResponse toResponse(Conversation c) {
        List<ParticipantResponse> participants = participantRepository.findWithUsers(c.getId()).stream()
                .map(ParticipantResponse::from)
                .toList();
        return ConversationResponse.from(c, participants);
    }
}
