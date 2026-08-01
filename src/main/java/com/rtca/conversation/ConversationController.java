package com.rtca.conversation;

import com.rtca.auth.AuthUser;
import com.rtca.common.dto.PageResponse;
import com.rtca.common.ids.PublicIds;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.CreateDirectRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final PublicIds ids;

    @GetMapping
    public PageResponse<ConversationResponse> list(@AuthenticationPrincipal AuthUser me,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return conversationService.listForUser(me.id(), pageable);
    }

    @GetMapping("/{id}")
    public ConversationResponse get(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        return conversationService.get(me.id(), ids.conversationId(id));
    }

    @PostMapping("/direct")
    public ConversationResponse direct(@AuthenticationPrincipal AuthUser me,
                                       @Valid @RequestBody CreateDirectRequest request) {
        return conversationService.getOrCreateDirect(me.id(), ids.userId(request.userId()));
    }

    @PostMapping("/{id}/clear")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        conversationService.clearForUser(me.id(), ids.conversationId(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID id) {
        conversationService.deleteForUser(me.id(), ids.conversationId(id));
    }
}
