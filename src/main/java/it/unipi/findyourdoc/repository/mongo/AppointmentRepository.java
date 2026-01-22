package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.AppointmentFull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AppointmentRepository extends MongoRepository<AppointmentFull, String> {

}
