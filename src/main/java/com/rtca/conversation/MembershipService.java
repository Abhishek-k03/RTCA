package com.rtca.conversation;

import com.rtca.common.exception.ForbiddenException;
import com.rtca.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Access checks for conversations. Non-members get 404 so ids don't leak. */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final ParticipantRepository participantRepository;

    @Transactional(readOnly = true)
    public boolean isMember(Long conversationId, Long userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    @Transactional(readOnly = true)
    public void requireMember(Long conversationId, Long userId) {
        if (!isMember(conversationId, userId)) {
            throw new NotFoundException("Conversation not found");
        }
    }

    @Transactional(readOnly = true)
    public ConversationParticipant getParticipant(Long conversationId, Long userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    @Transactional(readOnly = true)
    public ConversationParticipant requireManager(Long conversationId, Long userId) {
        ConversationParticipant p = getParticipant(conversationId, userId);
        if (!p.getRole().canManageMembers()) {
            throw new ForbiddenException("Only group admins can do this");
        }
        return p;
    }
}
