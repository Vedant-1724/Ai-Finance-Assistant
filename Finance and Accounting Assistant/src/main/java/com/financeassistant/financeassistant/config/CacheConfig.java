package com.financeassistant.financeassistant.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * NEW FILE — did not exist in finance-backend.
 *
 * Activates Spring's caching infrastructure (@Cacheable, @CacheEvict).
 * Without @EnableCaching, the @Cacheable annotation on ReportingService
 * does NOTHING — every call hits the database even with cache configured.
 *
 * Current mode: in-memory (ConcurrentMapCache) — no Redis required.
 * application.yml sets spring.cache.type=simple for this mode.
 *
 * To upgrade to Redis (when you reach that stage):
 * 1. Install Redis and start it.
 * 2. Change application.yml: spring.cache.type: redis
 * 3. Uncomment the RedisCacheManager bean below.
 */
@EnableCaching
@Configuration
public class CacheConfig {
        // Spring Boot auto-configures ConcurrentMapCache when cache.type=simple.
        // No additional bean needed for development mode.

}