package it.unipi.findyourdoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Service responsible for managing distributed locks using Redis.
 * Prevents race conditions during appointment booking by locking specific time slots.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RedisSlotService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String SLOT_LOCK_KEY_PREFIX = "lock:slot:";
    private static final Duration LOCK_EXPIRATION = Duration.ofMinutes(10);

    /** * Attempts to acquire an exclusive lock on a specific doctor's time slot. */
    public boolean acquireSlotLock(String doctorId, LocalDateTime slotTime, String userId) {
        String key = generateKey(doctorId, slotTime);

        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, LOCK_EXPIRATION);

        if (Boolean.TRUE.equals(success)) {
            log.info("Lock acquired for slot {} by user {}", key, userId);
            return true;
        } else {
            log.warn("Booking attempt failed: Slot {} is already locked", key);
            return false;
        }
    }

    /** * Manually releases a previously acquired lock, typically used during transaction rollbacks. */
    public void releaseSlotLock(String doctorId, LocalDateTime slotTime) {
        String key = generateKey(doctorId, slotTime);
        Boolean deleted = redisTemplate.delete(key);

        if (deleted) {
            log.info("Lock manually released for slot {}", key);
        }
    }

    /** * Generates a unique Redis key for a specific slot. */
    private String generateKey(String doctorId, LocalDateTime slotTime) {
        return SLOT_LOCK_KEY_PREFIX + doctorId + ":" + slotTime.toString();
    }
}