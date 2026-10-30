package com.sigmav.sigmavbackend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /**
     * Treats cache failures as cache misses instead of propagating them.
     * Without this, a Redis outage or an unreadable cached entry turns every
     * @Cacheable endpoint into a 500; with it, requests fall back to the database.
     */
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache GET failed on '{}' key '{}', falling back to source: {}",
                        cache.getName(), key, exception.getMessage());
                // Drop the entry so a corrupt/unreadable value gets rewritten on the next put
                try {
                    cache.evict(key);
                } catch (RuntimeException evictException) {
                    log.warn("Could not evict entry '{}' from cache '{}': {}",
                            key, cache.getName(), evictException.getMessage());
                }
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache PUT failed on '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache EVICT failed on '{}' key '{}': {}", cache.getName(), key, exception.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.warn("Cache CLEAR failed on '{}': {}", cache.getName(), exception.getMessage());
            }
        };
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        ObjectMapper redisMapper = JsonMapper.builder()
                .findAndAddModules()
                .activateDefaultTyping(
                        // Restrict deserializable types to JDK and project classes:
                        // allowing Object.class would accept any class name from Redis
                        BasicPolymorphicTypeValidator.builder()
                                .allowIfSubType("java.")
                                .allowIfSubType("com.sigmav.")
                                .build(),
                        ObjectMapper.DefaultTyping.EVERYTHING,
                        JsonTypeInfo.As.PROPERTY)
                .build();

        RedisSerializer<Object> valueSerializer = new RedisSerializer<>() {
            @Override
            public byte[] serialize(Object value) throws SerializationException {
                if (value == null) return null;
                try {
                    return redisMapper.writeValueAsBytes(value);
                } catch (JsonProcessingException e) {
                    throw new SerializationException("Redis serialization failed", e);
                }
            }

            @Override
            public Object deserialize(byte[] bytes) throws SerializationException {
                if (bytes == null || bytes.length == 0) return null;
                try {
                    return redisMapper.readValue(bytes, Object.class);
                } catch (IOException e) {
                    throw new SerializationException("Redis deserialization failed", e);
                }
            }
        };

        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues()
                .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(SerializationPair.fromSerializer(valueSerializer));

        Map<String, RedisCacheConfiguration> configs = Map.of(
                "branding",         base.entryTtl(Duration.ofMinutes(10)),
                "branding-email",   base.entryTtl(Duration.ofMinutes(10)),
                "user-principals",  base.entryTtl(Duration.ofMinutes(5)),
                "exam-max-score",   base.entryTtl(Duration.ofMinutes(30)),
                "dashboard",        base.entryTtl(Duration.ofMinutes(1)),
                "academy-limits",   base.entryTtl(Duration.ofMinutes(5)),
                "academy-features", base.entryTtl(Duration.ofMinutes(10))
        );

        return RedisCacheManager.builder(factory)
                .cacheDefaults(base.entryTtl(Duration.ofMinutes(5)))
                .withInitialCacheConfigurations(configs)
                .build();
    }
}
