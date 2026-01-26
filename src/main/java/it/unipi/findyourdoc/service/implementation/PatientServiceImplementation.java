package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.repository.mongo.AppointmentRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.repository.mongo.SymptomReportRepository;
import it.unipi.findyourdoc.service.DoctorService;
import it.unipi.findyourdoc.service.PatientService;
import it.unipi.findyourdoc.service.RedisSlotService;
import it.unipi.findyourdoc.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static it.unipi.findyourdoc.utils.Mapper.*;

@Service
@RequiredArgsConstructor
public class PatientServiceImplementation implements PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PatientReadDTO registerPatient(PatientCreateDTO createDTO) {
        // Verifica unicità email (ereditata da UserDTO nel CreateDTO)
        if (patientRepository.existsByEmail(createDTO.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        Patient patient = new Patient();
        // Campi ereditati da User
        patient.setEmail(createDTO.getEmail());
        patient.setTelephone(createDTO.getTelephone());
        patient.setPassword(passwordEncoder.encode(createDTO.getPassword()));
        patient.setCreatedAt(LocalDateTime.now());

        // Campi specifici di Patient
        patient.setFirstName(createDTO.getFirstName());
        patient.setLastName(createDTO.getLastName());
        patient.setAge(createDTO.getAge());
        patient.setGender(createDTO.getGender());

        // Mapping manuale della Location
        if (createDTO.getLocation() != null) {
            patient.setLocation(mapLocationDtoToEntity(createDTO.getLocation()));
        }

        Patient saved = patientRepository.save(patient);
        return mapToReadDTO(saved);
    }

    @Override
    public PatientReadDTO updatePatient(String id, PatientUpdateDTO updateDTO) {
        // Nota: Qui usiamo l'ID come da tua interfaccia
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // Aggiornamento campi base (User)
        if (updateDTO.getEmail() != null) {
            if (!updateDTO.getEmail().equals(patient.getEmail()) && patientRepository.existsByEmail(updateDTO.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            }
            patient.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
            patient.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
        }
        if (updateDTO.getTelephone() != null) patient.setTelephone(updateDTO.getTelephone());

        // Aggiornamento campi specifici (Patient)
        if (updateDTO.getFirstName() != null) patient.setFirstName(updateDTO.getFirstName());
        if (updateDTO.getLastName() != null) patient.setLastName(updateDTO.getLastName());
        if (updateDTO.getAge() != null) patient.setAge(updateDTO.getAge());
        if (updateDTO.getGender() != null) patient.setGender(updateDTO.getGender());
        if (updateDTO.getLocation() != null) {
            patient.setLocation(mapLocationDtoToEntity(updateDTO.getLocation()));
        }

        return mapToReadDTO(patientRepository.save(patient));
    }

    @Override
    public PatientReadDTO getUserByEmail(String email) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found with email: " + email));
        return mapToReadDTO(patient);
    }

    @Override
    public PatientReadDTO getUserById(String id) {
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found with email: " + id));
        return mapToReadDTO(patient);
    }


    public void cancelAppointment(String id){

    }

    @Override
    // Cache: Salva il risultato in Redis con chiave l'email del paziente.
    // Se il paziente aggiorna la pagina 10 volte, interroghiamo Mongo solo la prima volta.
    @Cacheable(value = "patient_appointments", key = "#email")
    public List<AppointmentPatientDTO> getAppointmentsByEmail(String email) {

        // 1. Recupera gli appuntamenti dal Repository
        // Consiglio: Aggiungi "OrderBy...Desc" nel repository per avere i più recenti in alto
        List<AppointmentFull> appointments = appointmentRepository.findByPatientEmailOrderByAppointmentDateTimeDesc(email);

        // 2. Mappa le entità in DTO
        return appointments.stream()
                .map(Mapper::mapToPatientDTO)
                .collect(Collectors.toList());
    }

    /**
     * Helper per convertire l'entità complessa del DB in un DTO semplice per il paziente.
     */


    public List<SymptomReportBriefDTO> getSymptomReportsByEmail(String email){

    }

    public SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO){

    }


    @Override
    @Transactional // Importante: deve salvare sia il Paziente che il Dottore
    public RatingDTO addRatingByEmail(String patientEmail, RatingDTO ratingDTO) {

        // 1. Recupera il Paziente
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Recupera il Dottore
        Doctor doctor = doctorRepository.findById(ratingDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

        // 3. Controllo duplicati INTERNO al paziente
        // Scorriamo la lista dei voti del paziente per vedere se ha già votato questo dottore
        boolean alreadyRated = patient.getRatings().stream()
                .anyMatch(r -> r.getDoctorId().equals(doctor.getId()));

        if (alreadyRated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hai già recensito questo dottore.");
        }

        // 4. Crea l'oggetto Rating (ora è solo un oggetto interno, non un documento a parte)
        Rating newRating = new Rating();
        newRating.setDoctorId(doctor.getId());
        newRating.setDoctorFirstName(doctor.getFirstName()); // Opzionale, per storico
        newRating.setDoctorLastName(doctor.getLastName());
        newRating.setRating(ratingDTO.getRating());

        // 5. Aggiungi il rating alla lista del Paziente e salva il Paziente
        patient.getRatings().add(newRating);
        patientRepository.save(patient);

        // 6. AGGIORNA LA MEDIA DEL DOTTORE (Matematica incrementale)
        // Non possiamo ricalcolare tutto da zero perché i voti sono sparsi nei vari pazienti.
        // Usiamo la formula: NuovaMedia = ((VecchiaMedia * TotaleVoti) + NuovoVoto) / (TotaleVoti + 1)

        double currentTotalScore = doctor.getAvgRating() * doctor.getRatingCount();
        double newTotalScore = currentTotalScore + ratingDTO.getRating();
        int newTotalRatings = doctor.getRatingCount() + 1;

        double newAverage = newTotalScore / newTotalRatings;

        // Arrotondiamo a 1 decimale per pulizia (es. 4.5)
        float roundedAverage = (float) (Math.round(newAverage * 10.0) / 10.0);

        doctor.setAvgRating(roundedAverage);
        doctor.setRatingCount(newTotalRatings);

        // Se vuoi mostrare i commenti anche sul profilo del dottore, dovresti aggiungere
        // il rating anche a una lista dentro Doctor (es. doctor.getReceivedRatings().add(newRating))
        // Altrimenti il dottore avrà la media aggiornata, ma non vedrà il testo del commento sul suo profilo.

        doctorRepository.save(doctor);

        // 7. Ritorna il DTO
        return mapToRatingDTO(newRating);
    }


    /**
     * Mapper da Entity a DTO
     */


    public List<RatingDTO> getAllRatingsByEmail(String email){

    }

    // --- Metodi di Mapping Helper ---

    private PatientReadDTO mapToReadDTO(Patient p) {
        PatientReadDTO dto = new PatientReadDTO();
        dto.setId(String.valueOf(p.getId()));
        dto.setEmail(p.getEmail());
        dto.setTelephone(p.getTelephone());
        dto.setCreatedAt(p.getCreatedAt());

        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setAge(p.getAge());
        dto.setGender(p.getGender());

        if (p.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    p.getLocation().getAddress(),
                    p.getLocation().getCity(),
                    p.getLocation().getState(),
                    p.getLocation().getZipCode()
            ));
        }

        // Mapping delle liste (Brief oggetti)
        if (p.getBookedAppointments() != null) {
            // Qui dovresti avere un metodo di mapping per AppointmentBrief -> AppointmentBriefDTO
        }

        return dto;
    }

    private Location mapLocationDtoToEntity(LocationDTO dto) {
        Location loc = new Location();
        loc.setAddress(dto.getAddress());
        loc.setCity(dto.getCity());
        loc.setState(dto.getState());
        loc.setZipCode(dto.getZipCode());
        return loc;
    }

    public List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis) {
        
    }

}