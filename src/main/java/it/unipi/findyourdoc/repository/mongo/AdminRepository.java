package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.Admin;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AdminRepository extends UserRepository<Admin>{
    // Questo metodo serve al JwtTokenProvider per validare l'Admin
    boolean existsByEmail(String email);

    Optional<Admin> findByEmail(String email);

    List<Admin> findByEmailStartingWith(String prefix);

    //void deleteByEmail(String email);

}
