package xyz.mcutils.backend.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;

/**
 * Stores one opaque value per key with an expiry.
 * <p>
 * Redis is an optimisation here, never a dependency: a failed read is reported as a miss and a failed write is
 * logged and dropped, so callers keep serving from the source of truth during an outage.
 */
@Component
@Slf4j
public class RedisBlobCache {

    private final RedisTemplate<String, byte[]> redis;

    public RedisBlobCache(RedisTemplate<String, byte[]> blobRedisTemplate) {
        this.redis = blobRedisTemplate;
    }

    public Optional<byte[]> get(String key) {
        try {
            return Optional.ofNullable(redis.opsForValue().get(key));
        } catch (DataAccessException e) {
            log.warn("Cache read failed for {}: {}", key, e.getMessage());
            return Optional.empty();
        }
    }

    public void set(String key, byte[] value, Duration ttl) {
        try {
            redis.opsForValue().set(key, value, ttl);
        } catch (DataAccessException e) {
            log.warn("Cache write failed for {}: {}", key, e.getMessage());
        }
    }
}
