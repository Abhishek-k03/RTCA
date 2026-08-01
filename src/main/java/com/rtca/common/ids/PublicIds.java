package com.rtca.common.ids;

import com.rtca.common.exception.NotFoundException;
import com.rtca.conversation.ConversationRepository;
import com.rtca.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maps the random public ids clients see to internal numeric ids and back.
 * The mapping never changes, so it is cached in memory per instance.
 */
@Component
@RequiredArgsConstructor
public class PublicIds {

    private static final int MAX_CACHED = 10_000;

    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    private final Map<UUID, Long> conversationIds = lru();
    private final Map<Long, UUID> conversationPublicIds = lru();
    private final Map<UUID, Long> userIds = lru();
    private final Map<Long, UUID> userPublicIds = lru();

    public Optional<Long> findConversationId(UUID publicId) {
        return cached(conversationIds, publicId, conversationRepository::findIdByPublicId);
    }

    public Long conversationId(UUID publicId) {
        return findConversationId(publicId).orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    public UUID conversation(Long id) {
        return cached(conversationPublicIds, id, conversationRepository::findPublicIdById)
                .orElseThrow(() -> new NotFoundException("Conversation not found"));
    }

    public Optional<Long> findUserId(UUID publicId) {
        return cached(userIds, publicId, userRepository::findIdByPublicId);
    }

    public Long userId(UUID publicId) {
        return findUserId(publicId).orElseThrow(() -> new NotFoundException("User not found"));
    }

    public Set<Long> userIds(Collection<UUID> publicIds) {
        return publicIds.stream().map(this::userId).collect(Collectors.toSet());
    }

    public UUID user(Long id) {
        return cached(userPublicIds, id, userRepository::findPublicIdById)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    // only hits are cached, so an id created later is still found
    private static <K, V> Optional<V> cached(Map<K, V> cache, K key, Function<K, Optional<V>> load) {
        V hit = cache.get(key);
        if (hit != null) {
            return Optional.of(hit);
        }
        Optional<V> loaded = load.apply(key);
        loaded.ifPresent(v -> cache.put(key, v));
        return loaded;
    }

    private static <K, V> Map<K, V> lru() {
        return Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
                return size() > MAX_CACHED;
            }
        });
    }
}
