package com.rtca.conversation;

import com.rtca.auth.AuthUser;
import com.rtca.common.dto.PageResponse;
import com.rtca.conversation.dto.ConversationResponse;
import com.rtca.conversation.dto.CreateDirectRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    public PageResponse<ConversationResponse> list(@AuthenticationPrincipal AuthUser me,
                                                   @PageableDefault(size = 20) Pageable pageable) {
        return conversationService.listForUser(me.id(), pageable);
    }

    @PostMapping("/direct")
    public ConversationResponse direct(@AuthenticationPrincipal AuthUser me,
                                       @Valid @RequestBody CreateDirectRequest request) {
        return conversationService.getOrCreateDirect(me.id(), request.userId());
    }
}
