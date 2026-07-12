package com.rtca.websocket;

import com.rtca.conversation.MembershipService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/** Only members may subscribe to a conversation topic. */
@Component
@RequiredArgsConstructor
public class SubscriptionGuardInterceptor implements ChannelInterceptor {

    private final MembershipService membershipService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || !StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            return message;
        }

        String destination = accessor.getDestination();
        if (destination == null) {
            throw new MessageDeliveryException("Missing destination");
        }
        if (destination.startsWith("/user/")) {
            return message;
        }

        var conversationId = Destinations.conversationId(destination);
        if (conversationId.isEmpty()) {
            throw new MessageDeliveryException("Unknown destination");
        }

        Long userId = Long.valueOf(accessor.getUser().getName());
        if (!membershipService.isMember(conversationId.getAsLong(), userId)) {
            throw new MessageDeliveryException("Not a member of this conversation");
        }
        return message;
    }
}
