package com.rtca.message;

import com.rtca.auth.AuthUser;
import com.rtca.common.exception.BadRequestException;
import com.rtca.common.ids.PublicIds;
import com.rtca.message.dto.EditMessageRequest;
import com.rtca.message.dto.MessagePage;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.ReceiptRequest;
import com.rtca.message.dto.SendImageRequest;
import com.rtca.message.dto.SendMessageRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/conversations/{conversationId}/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final ReceiptService receiptService;
    private final PublicIds ids;

    @GetMapping
    public MessagePage history(@AuthenticationPrincipal AuthUser me,
                               @PathVariable UUID conversationId,
                               @RequestParam(required = false) Long before,
                               @RequestParam(required = false) Long after,
                               @RequestParam(defaultValue = "50") int limit) {
        return messageService.history(me.id(), ids.conversationId(conversationId), before, after, limit);
    }

    @PostMapping("/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
                         @Valid @RequestBody ReceiptRequest request) {
        receiptService.markRead(me.id(), ids.conversationId(conversationId), request.messageId());
    }

    @PatchMapping("/{messageId}")
    public MessageResponse edit(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
                                @PathVariable Long messageId, @Valid @RequestBody EditMessageRequest request) {
        return messageService.edit(me.id(), ids.conversationId(conversationId), messageId, request.content());
    }

    /** scope=me hides it for you, scope=everyone deletes it for all members. */
    @DeleteMapping("/{messageId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal AuthUser me, @PathVariable UUID conversationId,
                       @PathVariable Long messageId, @RequestParam(defaultValue = "me") String scope) {
        switch (scope) {
            case "me" -> messageService.deleteForMe(me.id(), ids.conversationId(conversationId), messageId);
            case "everyone" -> messageService.deleteForEveryone(me.id(), ids.conversationId(conversationId), messageId);
            default -> throw new BadRequestException("scope must be 'me' or 'everyone'");
        }
    }

    @PostMapping
    public ResponseEntity<MessageResponse> send(@AuthenticationPrincipal AuthUser me,
                                                @PathVariable UUID conversationId,
                                                @Valid @RequestBody SendMessageRequest request) {
        return created(messageService.send(me.id(), ids.conversationId(conversationId), request));
    }

    @PostMapping(path = "/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MessageResponse> sendImage(@AuthenticationPrincipal AuthUser me,
                                                     @PathVariable UUID conversationId,
                                                     @Valid @ModelAttribute SendImageRequest request,
                                                     @RequestPart("file") MultipartFile file) {
        return created(messageService.sendImage(me.id(), ids.conversationId(conversationId), request, file));
    }

    private ResponseEntity<MessageResponse> created(SendResult result) {
        return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(result.message());
    }
}
