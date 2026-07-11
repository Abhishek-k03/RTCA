package com.rtca.websocket;

import com.rtca.auth.jwt.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Component;

/** Authenticates the STOMP session from the Authorization header on CONNECT. */
@Component
@RequiredArgsConstructor
public class AuthChannelInterceptor implements ChannelInterceptor {

    private static final String BEARER = "Bearer ";

    private final JwtService jwtService;

    @Override
    public Message<?> preSend(@NonNull Message<?> message, @NonNull MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String header = accessor.getFirstNativeHeader("Authorization");
            if (header == null || !header.startsWith(BEARER)) {
                throw new MessageDeliveryException("Missing bearer token");
            }
            var user = jwtService.authenticate(header.substring(BEARER.length()))
                    .orElseThrow(() -> new MessageDeliveryException("Invalid token"));
            accessor.setUser(new UsernamePasswordAuthenticationToken(user, null, user.authorities()));
        } else if (requiresAuth(accessor.getCommand()) && accessor.getUser() == null) {
            throw new MessageDeliveryException("Not authenticated");
        }

        return message;
    }

    private boolean requiresAuth(StompCommand command) {
        return command == StompCommand.SEND || command == StompCommand.SUBSCRIBE;
    }
}
