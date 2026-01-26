package it.unipi.findyourdoc.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j; // Utile per il logging
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j // Lombok genera automaticamente un oggetto 'log'
public class RedisSlotService {

    // Deve corrispondere al Bean configurato in RedisConfig
    private final RedisTemplate<String, Object> redisTemplate;

    // Prefisso per le chiavi, per non mischiarle con altri dati (es. cache)
    private static final String SLOT_LOCK_KEY_PREFIX = "lock:slot:";

    // Tempo di vita del lock (es. 10 minuti).
    // Se il server crasha durante la prenotazione, il lock scade da solo dopo questo tempo.
    private static final Duration LOCK_EXPIRATION = Duration.ofMinutes(10);

    /**
     * Tenta di acquisire un lock esclusivo su uno slot specifico.
     * * @param doctorId L'ID del dottore
     * @param slotTime L'orario dello slot
     * @param userId L'ID dell'utente che sta provando a prenotare (utile per debug)
     * @return true se il lock è stato acquisito (slot libero), false se è già occupato.
     */
    public boolean acquireSlotLock(String doctorId, LocalDateTime slotTime, String userId) {
        String key = generateKey(doctorId, slotTime);

        // setIfAbsent corrisponde al comando Redis "SETNX"
        // Imposta la chiave SOLO se non esiste già.
        // Imposta anche la scadenza (TTL) atomica.
        Boolean success = redisTemplate.opsForValue()
                .setIfAbsent(key, userId, LOCK_EXPIRATION);

        if (Boolean.TRUE.equals(success)) {
            log.info("Lock acquisito per slot {} da utente {}", key, userId);
            return true;
        } else {
            log.warn("Tentativo di prenotazione fallito: Slot {} già bloccato", key);
            return false;
        }
    }

    /**
     * Rilascia manualmente il lock.
     * Da chiamare obbligatoriamente se l'operazione su MongoDB fallisce (rollback).
     */
    public void releaseSlotLock(String doctorId, LocalDateTime slotTime) {
        String key = generateKey(doctorId, slotTime);
        Boolean deleted = redisTemplate.delete(key);

        if (Boolean.TRUE.equals(deleted)) {
            log.info("Lock rilasciato manualmente per slot {}", key);
        }
    }

    /**
     * Genera una chiave univoca per Redis.
     * Formato: lock:slot:{doctorId}:{dateTime}
     */
    private String generateKey(String doctorId, LocalDateTime slotTime) {
        // Usiamo toString() di LocalDateTime che genera formato ISO-8601 (es. 2026-05-20T10:00)
        // Sostituiamo i due punti del tempo se necessario, ma Redis li accetta.
        return SLOT_LOCK_KEY_PREFIX + doctorId + ":" + slotTime.toString();
    }
}