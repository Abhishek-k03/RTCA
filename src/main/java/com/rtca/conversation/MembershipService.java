package com.rtca.conversation;

import com.rtca.common.config.CacheConfig;
import com.rtca.common.exception.ForbiddenException;
import com.rtca.common.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Access checks for conversations. Non-members get 404 so ids don't leak. */
@Service
@RequiredArgsConstructor
public class MembershipService {

    private final ParticipantRepository participantRepository;
    private final CacheManager cacheManager;

    public boolean isMember(Long conversationId, Long userId) {
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

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

    /** Evict after commit so a concurrent reader can't re-cache stale state. */
    public void evict(Long conversationId, Long userId) {
        Runnable evict = () -> {
            Cache cache = cacheManager.getCache(CacheConfig.MEMBERSHIP);
            if (cache != null) {
                cache.evict(conversationId + ":" + userId);
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    evict.run();
                }
            });
        } else {
            evict.run();
        }
    }
}
