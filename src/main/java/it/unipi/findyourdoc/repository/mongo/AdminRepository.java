package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Admin;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface AdminRepository extends MongoRepository<Admin, String> {
    // Questo metodo serve al JwtTokenProvider per validare l'Admin
    boolean existsByEmail(String email);
}
