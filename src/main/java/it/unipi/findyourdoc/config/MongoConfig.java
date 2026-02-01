package it.unipi.findyourdoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

@Configuration
public class MongoConfig {

    @Bean
    public MongoCustomConversions customConversions() {
        return new MongoCustomConversions(Arrays.asList(
                new StringToLocalDateTimeConverter(),
                new LocalDateTimeToStringConverter()
        ));
    }

    /**
     * LEGGE dal Database: Trasforma la Stringa (salvata da Python) in LocalDateTime
     */
    static class StringToLocalDateTimeConverter implements Converter<String, LocalDateTime> {
        @Override
        public LocalDateTime convert(String source) {
            // Usa il parser pubblico standard, evitando la Reflection privata che fa crashare Java 21
            return LocalDateTime.parse(source, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }

    /**
     * SCRIVE nel Database: Trasforma LocalDateTime in Stringa (per mantenere compatibilità con lo script)
     */
    static class LocalDateTimeToStringConverter implements Converter<LocalDateTime, String> {
        @Override
        public String convert(LocalDateTime source) {
            return source.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        }
    }
}