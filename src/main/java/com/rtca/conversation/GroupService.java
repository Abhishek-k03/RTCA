package com.rtca.conversation;

import com.rtca.common.exception.BadRequestException;
import com.rtca.common.exception.ForbiddenException;
import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.CreateGroupRequest;
import com.rtca.message.MessageRepository;
import com.rtca.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GroupService {

    static final int MAX_MEMBERS = 256;

    private final ConversationRepository conversationRepository;
    private final ParticipantRepository participantRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final MembershipService membershipService;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public ConversationResponse create(Long me, CreateGroupRequest request) {
        Set<Long> memberIds = new HashSet<>(request.memberIds());
        memberIds.remove(me);
        if (memberIds.isEmpty()) {
            throw new BadRequestException("A group needs at least one other member");
        }
        if (memberIds.size() + 1 > MAX_MEMBERS) {
            throw new BadRequestException("Group cannot have more than " + MAX_MEMBERS + " members");
        }
        ensureUsersExist(memberIds);

        Conversation group = conversationRepository.save(Conversation.builder()
                .type(ConversationType.GROUP)
                .name(request.name().trim())
                .createdBy(me)
                .build());

        conversationService.addParticipant(group, me, ParticipantRole.OWNER);
        memberIds.forEach(id -> conversationService.addParticipant(group, id, ParticipantRole.MEMBER));
        memberIds.forEach(id -> eventPublisher.publishEvent(new MemberAddedEvent(group.getId(), id)));

        return conversationService.toResponse(group);
    }

    @Transactional
    public ConversationResponse rename(Long me, Long groupId, String name) {
        Conversation group = getGroup(groupId);
        membershipService.requireManager(groupId, me);
        group.setName(name.trim());
        return conversationService.toResponse(group);
    }

    @Transactional
    public ConversationResponse addMembers(Long me, Long groupId, Set<Long> userIds) {
        Conversation group = getGroup(groupId);
        membershipService.requireManager(groupId, me);
        ensureUsersExist(userIds);

        Set<Long> existing = new HashSet<>(participantRepository.findUserIds(groupId));
        List<Long> toAdd = userIds.stream().filter(id -> !existing.contains(id)).toList();
        if (existing.size() + toAdd.size() > MAX_MEMBERS) {
            throw new BadRequestException("Group cannot have more than " + MAX_MEMBERS + " members");
        }
        // removed members keep their row, so re-adding restores it
        toAdd.forEach(id -> participantRepository.findByConversationIdAndUserId(groupId, id)
                .ifPresentOrElse(p -> {
                    p.restore();
                    membershipService.evict(groupId, id);
                }, () -> conversationService.addParticipant(group, id, ParticipantRole.MEMBER)));
        toAdd.forEach(id -> eventPublisher.publishEvent(new MemberAddedEvent(groupId, id)));

        return conversationService.toResponse(group);
    }

    /**
     * Removing (or leaving) keeps the row so the user can still read history
     * up to this point. They stop receiving live events after commit.
     */
    @Transactional
    public void removeMember(Long me, Long groupId, Long userId) {
        Conversation group = getGroup(groupId);
        ConversationParticipant target = activeParticipant(groupId, userId);

        if (!me.equals(userId)) {
            ConversationParticipant actor = membershipService.requireManager(groupId, me);
            if (target.getRole() == ParticipantRole.OWNER
                    || (target.getRole() == ParticipantRole.ADMIN && actor.getRole() != ParticipantRole.OWNER)) {
                throw new ForbiddenException("Not allowed to remove this member");
            }
        }

        boolean wasOwner = target.getRole() == ParticipantRole.OWNER;
        target.remove(messageRepository.findLastId(groupId));
        participantRepository.flush();
        membershipService.evict(groupId, userId);
        eventPublisher.publishEvent(new MemberRemovedEvent(groupId, userId, target.getRemovedAt()));

        if (wasOwner) {
            transferOwnership(group);
        }
    }

    @Transactional
    public ConversationResponse changeRole(Long me, Long groupId, Long userId, ParticipantRole role) {
        Conversation group = getGroup(groupId);
        ConversationParticipant actor = membershipService.requireManager(groupId, me);
        if (actor.getRole() != ParticipantRole.OWNER) {
            throw new ForbiddenException("Only the owner can change roles");
        }
        if (role == ParticipantRole.OWNER || me.equals(userId)) {
            throw new BadRequestException("Invalid role change");
        }
        ConversationParticipant target = activeParticipant(groupId, userId);
        target.setRole(role);
        return conversationService.toResponse(group);
    }

    // oldest admin, else oldest member, becomes owner. groups with no active members are deleted
    private void transferOwnership(Conversation group) {
        List<ConversationParticipant> remaining = participantRepository.findWithUsers(group.getId());
        if (remaining.isEmpty()) {
            conversationRepository.delete(group);
            return;
        }
        remaining.stream()
                .min(Comparator.comparing((ConversationParticipant p) -> p.getRole() != ParticipantRole.ADMIN)
                        .thenComparing(ConversationParticipant::getJoinedAt))
                .ifPresent(p -> p.setRole(ParticipantRole.OWNER));
    }

    private ConversationParticipant activeParticipant(Long groupId, Long userId) {
        return participantRepository.findByConversationIdAndUserId(groupId, userId)
                .filter(ConversationParticipant::isActive)
                .orElseThrow(() -> new NotFoundException("User is not a member"));
    }

    private Conversation getGroup(Long id) {
        Conversation c = conversationRepository.findForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
        if (!c.isGroup()) {
            throw new BadRequestException("Not a group conversation");
        }
        return c;
    }

    private void ensureUsersExist(Set<Long> ids) {
        if (userRepository.findAllById(ids).size() != ids.size()) {
            throw new NotFoundException("One or more users not found");
        }
    }
}
