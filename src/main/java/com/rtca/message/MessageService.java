package com.rtca.message;

import com.rtca.common.exception.BadRequestException;
import com.rtca.conversation.ConversationRepository;
import com.rtca.conversation.MembershipService;
import com.rtca.message.dto.MessageResponse;
import com.rtca.message.dto.SendMessageRequest;
import com.rtca.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;
    private final MembershipService membershipService;

    @Transactional
    public MessageResponse send(Long senderId, Long conversationId, SendMessageRequest request) {
        membershipService.requireMember(conversationId, senderId);

        String content = request.content().strip();
        if (content.isEmpty()) {
            throw new BadRequestException("Message must not be blank");
        }

        Message message = messageRepository.saveAndFlush(Message.builder()
                .conversationId(conversationId)
                .sender(userRepository.getReferenceById(senderId))
                .content(content)
                .clientMessageId(request.clientMessageId())
                .build());

        conversationRepository.touchLastMessageAt(conversationId, message.getCreatedAt());
        return MessageResponse.from(message);
    }
}
