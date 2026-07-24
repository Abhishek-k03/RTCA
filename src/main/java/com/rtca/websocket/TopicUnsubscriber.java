package com.rtca.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.broker.AbstractBrokerMessageHandler;
import org.springframework.messaging.simp.broker.SimpleBrokerMessageHandler;
import org.springframework.messaging.simp.user.SimpSession;
import org.springframework.messaging.simp.user.SimpSubscription;
import org.springframework.messaging.simp.user.SimpUser;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * Drops a user's live subscriptions to a topic on this instance, server side.
 * The subscribe guard only runs on SUBSCRIBE, so without this a removed member
 * would keep receiving the conversation until they reconnect.
 */
@Slf4j
@Component
public class TopicUnsubscriber {

    private final SimpUserRegistry userRegistry;
    private final AbstractBrokerMessageHandler broker;

    public TopicUnsubscriber(SimpUserRegistry userRegistry,
                             @Qualifier("simpleBrokerMessageHandler") AbstractBrokerMessageHandler broker) {
        this.userRegistry = userRegistry;
        this.broker = broker;
    }

    public void unsubscribe(String user, String destination) {
        SimpUser simpUser = userRegistry.getUser(user);
        if (simpUser == null || !(broker instanceof SimpleBrokerMessageHandler simple)) {
            return;
        }
        for (SimpSession session : simpUser.getSessions()) {
            for (SimpSubscription sub : session.getSubscriptions()) {
                if (destination.equals(sub.getDestination())) {
                    var accessor = SimpMessageHeaderAccessor.create(SimpMessageType.UNSUBSCRIBE);
                    accessor.setSessionId(session.getId());
                    accessor.setSubscriptionId(sub.getId());
                    simple.getSubscriptionRegistry().unregisterSubscription(
                            MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()));
                    log.debug("Unsubscribed session {} from {}", session.getId(), destination);
                }
            }
        }
    }
}
