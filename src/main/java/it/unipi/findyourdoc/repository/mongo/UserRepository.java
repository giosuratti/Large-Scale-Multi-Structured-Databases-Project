package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.User;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.Optional;

@NoRepositoryBean
public interface UserRepository<T extends User> extends MongoRepository<T, String> {
    boolean existsByEmail(String email);
    void deleteByEmail(String email);
    Optional<T> findByEmail(String email);
}
