package com.docflow.security;

import lombok.extern.slf4j.Slf4j;
import com.docflow.common.BusinessException;
import com.docflow.common.ErrorCode;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Component
public class SecurityStateStore {
    private static final String PREFIX = "docflow:security:";

    private final StringRedisTemplate redis;
    private final boolean failClosed;
    private final Map<String, LocalValue> local = new ConcurrentHashMap<>();
    private final AtomicBoolean fallbackLogged = new AtomicBoolean();

    public SecurityStateStore(ObjectProvider<StringRedisTemplate> redisProvider,
                              @Value("${app.security.state.fail-closed:false}") boolean failClosed) {
        this.redis = redisProvider.getIfAvailable();
        this.failClosed = failClosed;
    }

    public long increment(String key, Duration ttl) {
        String namespaced = PREFIX + key;
        try {
            if (redis != null) {
                Long value = redis.opsForValue().increment(namespaced);
                if (value != null && value == 1L) redis.expire(namespaced, ttl);
                return value == null ? 0L : value;
            }
        } catch (RuntimeException exception) {
            handleUnavailable(exception);
        }
        requireSharedStateIfConfigured();
        long now = System.currentTimeMillis();
        return local.compute(namespaced, (ignored, existing) -> {
            if (existing == null || existing.expiresAt() <= now) {
                return new LocalValue("1", now + ttl.toMillis());
            }
            return new LocalValue(Long.toString(parseLong(existing.value()) + 1L), existing.expiresAt());
        }).asLong();
    }

    public void put(String key, String value, Duration ttl) {
        String namespaced = PREFIX + key;
        try {
            if (redis != null) {
                redis.opsForValue().set(namespaced, value, ttl);
                return;
            }
        } catch (RuntimeException exception) {
            handleUnavailable(exception);
        }
        requireSharedStateIfConfigured();
        local.put(namespaced, new LocalValue(value, System.currentTimeMillis() + ttl.toMillis()));
    }

    public String get(String key) {
        String namespaced = PREFIX + key;
        try {
            if (redis != null) return redis.opsForValue().get(namespaced);
        } catch (RuntimeException exception) {
            handleUnavailable(exception);
        }
        requireSharedStateIfConfigured();
        LocalValue value = local.get(namespaced);
        if (value == null) return null;
        if (value.expiresAt() <= System.currentTimeMillis()) {
            local.remove(namespaced, value);
            return null;
        }
        return value.value();
    }

    public long getLong(String key) {
        return parseLong(get(key));
    }

    public long ttlSeconds(String key) {
        String namespaced = PREFIX + key;
        try {
            if (redis != null) {
                Long ttl = redis.getExpire(namespaced);
                return ttl == null || ttl < 0 ? 0L : ttl;
            }
        } catch (RuntimeException exception) {
            handleUnavailable(exception);
        }
        requireSharedStateIfConfigured();
        LocalValue value = local.get(namespaced);
        if (value == null) return 0L;
        long remaining = value.expiresAt() - System.currentTimeMillis();
        if (remaining <= 0) {
            local.remove(namespaced, value);
            return 0L;
        }
        return Math.max(1L, remaining / 1000L);
    }

    public void delete(String key) {
        String namespaced = PREFIX + key;
        try {
            if (redis != null) redis.delete(namespaced);
        } catch (RuntimeException exception) {
            handleUnavailable(exception);
        }
        requireSharedStateIfConfigured();
        local.remove(namespaced);
    }

    private long parseLong(String value) {
        try {
            return value == null ? 0L : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0L;
        }
    }

    private void handleUnavailable(RuntimeException exception) {
        if (failClosed) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "安全状态服务暂时不可用，请稍后重试");
        }
        if (fallbackLogged.compareAndSet(false, true)) {
            log.warn("Redis security state is unavailable; using this-node memory fallback ({})",
                    exception.getClass().getSimpleName());
        }
    }

    private void requireSharedStateIfConfigured() {
        if (redis == null && failClosed) {
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE,
                    "安全状态服务暂时不可用，请稍后重试");
        }
    }

    private record LocalValue(String value, long expiresAt) {
        long asLong() {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException ignored) {
                return 0L;
            }
        }
    }
}
