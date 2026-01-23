package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Admin;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AdminRepository extends MongoRepository<Admin, String> {
    // Questo metodo serve al JwtTokenProvider per validare l'Admin
    boolean existsByEmail(String email);

    Optional<Admin> findByEmail(String email);

    List<Admin> findByEmailStartingWith(String prefix);

    void deleteByEmail(String email);
}
