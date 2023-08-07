package cn.wolfcode.lock;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Component
public class RedisDistributedLock {
    private static final String LOCK_KEY_PREFIX="lock:";
    private static final long DEFAULT_LOCK_EXPIRATION = 30;// 锁的默认过期时间，单位：秒
    private StringRedisTemplate redisTemplate;

    @Autowired
    public RedisDistributedLock(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean acquireLock(String lockKey) {
        String fullLockKey = LOCK_KEY_PREFIX + lockKey;
        return redisTemplate.opsForValue().setIfAbsent(fullLockKey, "locked", DEFAULT_LOCK_EXPIRATION, TimeUnit.SECONDS);
    }
    public void releaseLock(String lockKey) {
        String fullLockKey = LOCK_KEY_PREFIX + lockKey;
        redisTemplate.delete(fullLockKey);
    }

}
