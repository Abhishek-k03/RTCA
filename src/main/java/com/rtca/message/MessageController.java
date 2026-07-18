package com.rtca.message;

import com.rtca.auth.AuthUser;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.ReceiptRequest;
import com.rtca.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ReceiptService receiptService;

    @GetMapping
    public MessagePage history(@AuthenticationPrincipal AuthUser me,
                               @PathVariable Long conversationId,
                               @RequestParam(required = false) Long before,
                               @RequestParam(required = false) Long after,
                               @RequestParam(defaultValue = "50") int limit) {
        return messageService.history(me.id(), conversationId, before, after, limit);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthUser me, @PathVariable Long conversationId,
                         @Valid @RequestBody ReceiptRequest request) {
        receiptService.markRead(me.id(), conversationId, request.messageId());
    }

    @PostMapping
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal AuthUser me,
                                                @PathVariable Long conversationId,
                                                @Valid @RequestBody SendMessageRequest request) {
        SendResult result = messageService.send(me.id(), conversationId, request);
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.message());
    }
}
