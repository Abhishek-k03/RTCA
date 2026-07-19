package com.rtca.websocket.relay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

@Configuration
@ConditionalOnProperty(name = "app.relay.enabled", havingValue = "true", matchIfMissing = true)
public class RelayConfig {

    @Bean
    public RedisMessageListenerContainer relayListenerContainer(RedisConnectionFactory factory,
                                                                RedisEventRelay relay) {
        var container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(relay, new ChannelTopic(RedisEventRelay.CHANNEL));
        return container;
    }
}
