package com.rtca.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rtca.user.dto.UserSummary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;

import java.time.Duration;

@Slf4j
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    public static final String USERS = "users:v3";
    public static final String MEMBERSHIP = "membership";

    @Bean
    public RedisCacheManagerBuilderCustomizer cacheCustomizer(ObjectMapper objectMapper) {
        return builder -> builder
                .withCacheConfiguration(USERS, config(Duration.ofMinutes(30),
                        new Jackson2JsonRedisSerializer<>(objectMapper, UserSummary.class)))
                .withCacheConfiguration(MEMBERSHIP, config(Duration.ofMinutes(10),
                        new Jackson2JsonRedisSerializer<>(objectMapper, Boolean.class)));
    }

    private RedisCacheConfiguration config(Duration ttl, Jackson2JsonRedisSerializer<?> serializer) {
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl)
                .disableCachingNullValues()
                .prefixCacheNameWith("rtca:")
                .serializeValuesWith(SerializationPair.fromSerializer(serializer));
    }

    /** Cache is an optimisation, on redis errors fall through to the db. */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache get failed [{}:{}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache put failed [{}:{}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache evict failed [{}:{}]: {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache clear failed [{}]: {}", cache.getName(), e.getMessage());
            }
        };
    }
}
