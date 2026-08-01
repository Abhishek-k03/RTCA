package com.rtca.conversation;

import com.rtca.auth.AuthUser;
import com.rtca.common.ids.PublicIds;
import com.rtca.conversation.dto.AddMembersRequest;
import com.rtca.conversation.dto.ChangeParticipantRoleRequest;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.CreateGroupRequest;
import com.rtca.conversation.dto.UpdateGroupRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final PublicIds ids;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse create(@AuthenticationPrincipal AuthUser me,
                                       @Valid @RequestBody CreateGroupRequest request) {
        return groupService.create(me.id(), request.name(), ids.userIds(request.memberIds()));
    }

    @PatchMapping("/{id}")
    public ConversationResponse rename(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                       @Valid @RequestBody UpdateGroupRequest request) {
        return groupService.rename(me.id(), ids.conversationId(id), request.name());
    }

    @PostMapping("/{id}/members")
    public ConversationResponse addMembers(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                           @Valid @RequestBody AddMembersRequest request) {
        return groupService.addMembers(me.id(), ids.conversationId(id), ids.userIds(request.userIds()));
    }

    @DeleteMapping("/{id}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                             @PathVariable UUID userId) {
        groupService.removeMember(me.id(), ids.conversationId(id), ids.userId(userId));
    }

    @PatchMapping("/{id}/members/{userId}/role")
    public ConversationResponse changeRole(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id,
                                           @PathVariable UUID userId,
                                           @Valid @RequestBody ChangeParticipantRoleRequest request) {
        return groupService.changeRole(me.id(), ids.conversationId(id), ids.userId(userId), request.role());
    }
}
