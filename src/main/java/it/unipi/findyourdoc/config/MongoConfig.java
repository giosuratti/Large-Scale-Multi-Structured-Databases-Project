package it.unipi.findyourdoc.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

/**
 * Configuration class for MongoDB custom settings.
 * This class is responsible for customizing how Spring Data MongoDB maps Java
 * objects to MongoDB documents and vice versa.
 */
@Configuration
@EnableMongoAuditing
public class MongoConfig {

    /**
     * Configures a custom {@link MappingMongoConverter} bean to override default MongoDB mapping behaviors.
     * @param factory     The factory used to create connections to the MongoDB database.
     * @param context     The context containing the mapping metadata of the entities.
     * @param conversions Custom type conversions registered in the Spring context.
     * @return A configured {@link MappingMongoConverter} instance.
     */
    @Bean
    public MappingMongoConverter mappingMongoConverter(MongoDatabaseFactory factory,
                                                       MongoMappingContext context,
                                                       MongoCustomConversions conversions) {

        // Resolves MongoDB DBRefs (database references) using the provided database factory
        DbRefResolver dbRefResolver = new DefaultDbRefResolver(factory);
        MappingMongoConverter converter = new MappingMongoConverter(dbRefResolver, context);

        // Apply any registered custom converters (e.g., specific date or enum converters)
        converter.setCustomConversions(conversions);

        // By passing 'null' to the DefaultMongoTypeMapper, we prevent Spring Data from
        // automatically writing the fully qualified Java class name into a "_class" field
        // within the MongoDB documents. This keeps the database schema cleaner.
        converter.setTypeMapper(new DefaultMongoTypeMapper(null));

        return converter;
    }

}