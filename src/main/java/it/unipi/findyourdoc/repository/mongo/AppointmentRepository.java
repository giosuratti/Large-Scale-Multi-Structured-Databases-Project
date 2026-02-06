package it.unipi.findyourdoc.repository.mongo;

import it.unipi.findyourdoc.model.mongo.AppointmentFull;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AppointmentRepository extends MongoRepository<AppointmentFull, String> {

    boolean existsByDoctorIdAndDateTime(String id, LocalDateTime dateTime);

    List<AppointmentFull> findByPatientIdOrderByDateTimeDesc(String patientId);

    List<AppointmentFull> findByDoctorId(String Id);

    List<AppointmentFull> findByDoctorIdAndDateTimeAfter(String doctorId, LocalDateTime now);

    List<AppointmentFull> findByPatientIdAndDateTimeAfter(String patientId, LocalDateTime now);


}
