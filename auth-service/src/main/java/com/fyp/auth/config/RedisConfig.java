package com.fyp.auth.config;

import com.fyp.auth.service.RevocationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for token revocation caching and Pub/Sub.
 * Only activates when Redis is available and enabled.
 */
@Slf4j
@Configuration
@ConditionalOnClass(RedisConnectionFactory.class)
@ConditionalOnProperty(name = "spring.data.redis.enabled", havingValue = "true", matchIfMissing = false)
public class RedisConfig {

    /**
     * Redis template for token revocation storage.
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        
        log.info("Redis template configured for token revocation");
        return template;
    }

    /**
     * Redis message listener container for Pub/Sub.
     */
    @Bean
    @ConditionalOnBean(RedisConnectionFactory.class)
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RevocationService revocationService) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        
        // Subscribe to revocation channel
        container.addMessageListener(
            (message, pattern) -> {
                String tokenId = new String(message.getBody());
                log.debug("Received revocation message for token: {}", tokenId);
                // The Gateway will also subscribe to this channel for instant logout
            },
            revocationService.getRevocationChannel()
        );
        
        log.info("Redis Pub/Sub listener configured for token revocation channel");
        return container;
    }
}
