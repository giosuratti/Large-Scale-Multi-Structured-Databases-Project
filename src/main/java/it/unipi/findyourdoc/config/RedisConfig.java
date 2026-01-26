package it.unipi.findyourdoc.config;


import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching // Abilita l'uso di @Cacheable, @CacheEvict, ecc.
public class RedisConfig {

    /**
     * Configurazione del RedisTemplate.
     * Serve per le operazioni manuali (es. Locking degli slot, gestione token, ecc.).
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Serializer per le Chiavi (Stringa semplice)
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        // Serializer per i Valori (JSON)
        // Usiamo GenericJackson2JsonRedisSerializer per salvare gli oggetti come JSON leggibile
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(objectMapper());

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();
        return template;
    }

    /**
     * Configurazione del CacheManager.
     * Serve per gestire automaticamente la cache tramite annotazioni Spring.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        // Configurazione di default per tutte le cache
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                // Durata di default della cache: 60 minuti
                .entryTtl(Duration.ofMinutes(60))
                // Non cachare valori nulli
                .disableCachingNullValues()
                // Usa la serializzazione JSON anche per la cache (fondamentale per leggere i DTO)
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper())));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                // Esempio: Se vuoi configurazioni specifiche per cache diverse
                // .withCacheConfiguration("doctor_appointments", config.entryTtl(Duration.ofMinutes(15))) // Scade prima
                // .withCacheConfiguration("static_data", config.entryTtl(Duration.ofHours(24))) // Scade dopo
                .build();
    }

    /**
     * Configurazione custom dell'ObjectMapper per Jackson.
     * Necessario per gestire correttamente le date (LocalDateTime) e i tipi polimorfici.
     */
    private ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // Modulo per gestire LocalDateTime, LocalDate, ecc. (Java 8 Time API)
        mapper.registerModule(new JavaTimeModule());

        // Attiva il salvataggio del tipo di classe nel JSON.
        // Questo permette a Redis di sapere che quel JSON corrisponde alla classe AppointmentReadDTO quando deserializza.
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }
}