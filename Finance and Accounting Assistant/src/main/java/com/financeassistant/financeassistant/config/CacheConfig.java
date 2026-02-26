package com.financeassistant.financeassistant.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Cache configuration.
 *
 * Current state: Uses Spring's in-memory ConcurrentMapCache (cache.type=simple).
 * No Redis required. Cache lives in JVM memory and resets on app restart.
 *
 * To upgrade to Redis later:
 *   1. Install Redis: https://redis.io/download
 *   2. Change application.yml: cache.type: redis
 *   3. Uncomment the RedisCacheManager bean below
 *   4. Remove the RabbitAutoConfiguration exclusion from application.yml
 */
@EnableCaching
@Configuration
public class CacheConfig {
    // Spring Boot auto-configures an in-memory cache when cache.type=simple.
    // No additional bean definition needed for development.
    //
    // ── Upgrade to Redis (Step 7 in roadmap) ──────────────────────────────
    // Uncomment the block below after installing Redis and changing yml:
    //
    // @Bean
    // public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
    //     RedisCacheConfiguration config = RedisCacheConfiguration
    //             .defaultCacheConfig()
    //             .entryTtl(Duration.ofMinutes(5))
    //             .serializeKeysWith(SerializationPair.fromSerializer(new StringRedisSerializer()))
    //             .serializeValuesWith(SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))
    //             .disableCachingNullValues();
    //
    //     return RedisCacheManager.builder(factory)
    //             .cacheDefaults(config)
    //             .withInitialCacheConfigurations(Map.of("pnl", config.entryTtl(Duration.ofMinutes(5))))
    //             .build();
    // }
}