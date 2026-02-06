package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.repository.mongo.AppointmentRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.repository.mongo.SymptomReportRepository;
import it.unipi.findyourdoc.repository.neo4j.DiseaseRepository;
import it.unipi.findyourdoc.repository.neo4j.DoctorGraphRepository;
import it.unipi.findyourdoc.service.DoctorService;
import it.unipi.findyourdoc.service.PatientService;
import it.unipi.findyourdoc.service.RedisSlotService;
import it.unipi.findyourdoc.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static it.unipi.findyourdoc.utils.Mapper.mapLocationDtoToEntity;
import static it.unipi.findyourdoc.utils.Mapper.mapToReadDTO;

@Service
@RequiredArgsConstructor
public class PatientServiceImplementation implements PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomReportRepository symptomReportRepository;
    private final DiseaseRepository diseaseRepository;
    private final DoctorGraphRepository doctorGraphRepository;
    private final DoctorService doctorService; // Serve per invalidare la cache del dottore

    // Redis Template per operazioni manuali sulle chiavi (es. cancellare slot o cache specifiche)
    private final StringRedisTemplate redisTemplate;

    // Servizio per gestire i Lock distribuiti sugli slot
    @Autowired
    private RedisSlotService redisSlotService;

    private static final Logger log = LoggerFactory.getLogger(PatientServiceImplementation.class);
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    // ===================================================================================
    //                                  PRENOTAZIONI
    // ===================================================================================

    @Override
    @Transactional
    // Quando prenoto, invalido la cache "personale" degli appuntamenti del paziente
    @CacheEvict(value = "patient_appointments", key = "#patientEmail")
    public AppointmentPatientDTO bookAppointmentByEmail(String patientEmail, AppointmentDTO appointmentDTO) {

        // 1. Recupero Paziente
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Recupero Dottore
        Doctor doctor = doctorRepository.findById(appointmentDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

        // 3. --- REDIS LOCK ---
        // Tentiamo di acquisire il lock per evitare race conditions (doppie prenotazioni)
        boolean locked = redisSlotService.acquireSlotLock(
                doctor.getId(),
                appointmentDTO.getDateTime(),
                patient.getId()
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Questo slot è momentaneamente bloccato da un altro utente. Riprova tra qualche minuto.");
        }

        try {
            // 4. Controllo di sicurezza su MongoDB (Double check)
            boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndDateTime(
                    doctor.getId(),
                    appointmentDTO.getDateTime()
            );

            if (alreadyBooked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot già prenotato (Database).");
            }

            // 5. Creazione Appuntamento
            AppointmentFull appointment = new AppointmentFull();
            appointment.setDoctorId(doctor.getId());
            appointment.setSpecialties(doctor.getSpecialties());
            appointment.setDoctorRating(doctor.getAvgRating()); // Snapshot rating attuale

            appointment.setPatientId(patient.getId());
            appointment.setPatientFirstName(patient.getFirstName());
            appointment.setPatientLastName(patient.getLastName());
            appointment.setPatientAge(patient.getAge());
            appointment.setPatientGender(patient.getGender());
            appointment.setPatientTelephone(patient.getTelephone());

            appointment.setDateTime(appointmentDTO.getDateTime());
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setStatus(AppointmentStatus.SCHEDULED);

            // 6. Salvataggio su MongoDB
            appointmentRepository.save(appointment);

            // 7. RIMOZIONE SLOT DAL DOTTORE (MongoDB)
            removeSlotFromDoctorAvailability(doctor, appointmentDTO.getDateTime());

            // 8. 🔥 REDIS: Invalida cache slot pubblici del dottore
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());

            // 9. 🔥 REDIS: Invalida cache appuntamenti privati del dottore
            // (Il dottore deve vedere che ha un nuovo appuntamento nella sua dashboard)
            doctorService.invalidateDoctorCache(doctor.getEmail());

            return Mapper.mapToPatientDTO(appointment);

        } catch (Exception e) {
            // Se fallisce, rilasciamo il lock subito
            redisSlotService.releaseSlotLock(doctor.getId(), appointmentDTO.getDateTime());
            throw e;
        }
        // In caso di successo, il lock scadrà da solo (TTL) o verrà sovrascritto dalla persistenza DB
    }

    @Override
    @Transactional
    public void cancelAppointment(String appointmentId) {
        // 1. Recupero Appuntamento
        AppointmentFull appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Check Stato
        AppointmentStatus currentStatus = appointment.getStatus();
        if (currentStatus != AppointmentStatus.SCHEDULED &&
                currentStatus != AppointmentStatus.PENDING &&
                currentStatus != AppointmentStatus.RESCHEDULED &&
                currentStatus != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible to cancel: status " + currentStatus + " does not allow it.");
        }

        // 2. Redis Lock
        boolean locked = redisSlotService.acquireSlotLock(
                appointment.getDoctorId(),
                appointment.getDateTime(),
                "SYSTEM_CANCEL_" + appointmentId
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot currently being used.");
        }

        try {
            // 3. UPDATE COLLECTION 'APPOINTMENTS' (Master Data)
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appointment);

            // 4. UPDATE COLLECTION 'DOCTORS' -> Restore Slot
            restoreSlotToDoctor(appointment);

            // 5. UPDATE COLLECTION 'PATIENTS' -> Embedded List
            updatePatientEmbeddedList(appointment);

            // 6. UPDATE COLLECTION 'DOCTORS' -> Embedded List (bookedThisWeek) <--- NUOVO!
            updateDoctorBookedThisWeek(appointment);

            // 7. REDIS: Invalida Slot
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + appointment.getDoctorId());

            // 8. REDIS: Invalida Cache Profilo Dottore (dove c'è bookedThisWeek)
            Doctor doctor = doctorRepository.findById(appointment.getDoctorId()).orElse(null);
            if (doctor != null) {
                doctorService.invalidateDoctorCache(doctor.getEmail());
            }

            // 9. REDIS: Invalida Cache Paziente
            Patient patient = patientRepository.findById(appointment.getPatientId()).orElse(null);
            if (patient != null) {
                redisTemplate.delete("patient_appointments::" + patient.getEmail());
            }

        } catch (Exception e) {
            redisSlotService.releaseSlotLock(appointment.getDoctorId(), appointment.getDateTime());
            throw e;
        }

        redisSlotService.releaseSlotLock(appointment.getDoctorId(), appointment.getDateTime());
    }

    // --- Helper 1: Aggiorna lista Paziente ---
    private void updatePatientEmbeddedList(AppointmentFull appointment) {
        patientRepository.findById(appointment.getPatientId()).ifPresent(patient -> {
            if (patient.getBookedAppointments() != null) {
                boolean updated = false;
                for (var embeddedAppt : patient.getBookedAppointments()) {
                    // Confrontiamo gli ID (Assicurati che embeddedAppt abbia appointmentId)
                    if (embeddedAppt.getAppointmentId().equals(appointment.getAppointmentId())) {
                        embeddedAppt.setStatus(AppointmentStatus.CANCELLED);
                        updated = true;
                        break;
                    }
                }
                if (updated) patientRepository.save(patient);
            }
        });
    }

    // --- Helper 2: Aggiorna lista Dottore (bookedThisWeek) ---
    private void updateDoctorBookedThisWeek(AppointmentFull appointment) {
        doctorRepository.findById(appointment.getDoctorId()).ifPresent(doctor -> {
            // Controlliamo se la lista esiste e non è vuota
            if (doctor.getBookedThisWeek() != null && !doctor.getBookedThisWeek().isEmpty()) {
                boolean updated = false;

                for (var embeddedAppt : doctor.getBookedThisWeek()) {
                    // Cerchiamo l'appuntamento tramite ID
                    if (embeddedAppt.getAppointmentId().equals(appointment.getAppointmentId())) {
                        embeddedAppt.setStatus(AppointmentStatus.CANCELLED);
                        updated = true;
                        break; // Trovato, usciamo dal ciclo
                    }
                }

                // Salviamo il dottore SOLO se abbiamo effettivamente modificato qualcosa
                // (Se l'appuntamento era tra un mese, non sarà in questa lista, quindi non salviamo inutilemente)
                if (updated) {
                    doctorRepository.save(doctor);
                }
            }
        });
    }

    @Override
    @Cacheable(value = "patient_appointments", key = "#email")
    public List<AppointmentPatientDTO> getAppointmentsByEmail(String email) {
        // 1. Recupera ID Paziente (Fix: usiamo l'email per trovare l'ID)
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 2. Query su Appointment Collection usando l'ID
        List<AppointmentFull> appointments = appointmentRepository.findByPatientIdOrderByDateTimeDesc(patient.getId());

        return appointments.stream()
                .map(Mapper::mapToPatientDTO)
                .collect(Collectors.toList());
    }

    // --- Helper Methods per Slot ---

    private void removeSlotFromDoctorAvailability(Doctor doctor, LocalDateTime slotTime) {
        if (doctor.getAvailableSlots() != null) {
            boolean removed = doctor.getAvailableSlots().removeIf(slot -> slot.equals(slotTime));
            if (removed) {
                doctorRepository.save(doctor);
            }
        }
    }

    private void restoreSlotToDoctor(AppointmentFull appointment) {
        Doctor doctor = doctorRepository.findById(appointment.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found to restore the slot"));

        LocalDateTime slotRestored = appointment.getDateTime();

        if (doctor.getAvailableSlots() == null) {
            doctor.setAvailableSlots(new ArrayList<>());
        }

        if (!doctor.getAvailableSlots().contains(slotRestored)) {
            doctor.getAvailableSlots().add(slotRestored);
            Collections.sort(doctor.getAvailableSlots());
            doctorRepository.save(doctor);
        }
    }

    // ===================================================================================
    //                                  SYMPTOM REPORTS
    // ===================================================================================

    @Override
    @Transactional
    // Quando creo un report, invalido la cache per forzare la rilettura
    @CacheEvict(value = "patient_symptoms", key = "#email")
    public SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO) {

        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 1. Neo4j AI Diagnosis
        ArrayList<String> suggestedDiagnoses = new ArrayList<>();
        if (createDTO.getSymptoms() != null && !createDTO.getSymptoms().isEmpty()) {
            suggestedDiagnoses = diseaseRepository.findPossibleDiagnoses(createDTO.getSymptoms());
            log.info("Neo4j suggested diagnoses: {}", suggestedDiagnoses);
        }

        // 2. Save Standalone Report (Collection)
        SymptomReport report = new SymptomReport();
        report.setPatientAge(patient.getAge());
        report.setPatientGender(patient.getGender());
        report.setPatientLocation(patient.getLocation());
        report.setSymptoms(createDTO.getSymptoms());
        report.setContext(createDTO.getContext());
        report.setCreatedAt(LocalDateTime.now());
        report.setPossibleDiagnosies(suggestedDiagnoses);

        symptomReportRepository.save(report);

        // 3. Update Patient Embedded List (Fondamentale per la lettura veloce!)
        SymptomReportBrief brief = new SymptomReportBrief();
        brief.setSymptoms(report.getSymptoms());
        brief.setPossibleDiagnosies(report.getPossibleDiagnosies());
        brief.setContext(report.getContext());
        brief.setCreatedAt(report.getCreatedAt());

        if (patient.getRecentSymptomReports() == null) {
            patient.setRecentSymptomReports(new ArrayList<>());
        }
        patient.getRecentSymptomReports().add(brief);
        patientRepository.save(patient);

        return Mapper.mapToSymptomBriefDTO(report);
    }

    @Override
    @Cacheable(value = "patient_symptoms", key = "#email")
    public List<SymptomReportBriefDTO> getSymptomReportsByEmail(String email) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getRecentSymptomReports() == null || patient.getRecentSymptomReports().isEmpty()) {
            return new ArrayList<>();
        }

        return patient.getRecentSymptomReports().stream()
                .map(Mapper::mapToSymptomBriefDTO)
                .sorted(Comparator.comparing(SymptomReportBriefDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // ===================================================================================
    //                                  RATINGS
    // ===================================================================================

    @Override
    @Transactional
    // Invalida la cache dei rating del paziente
    @CacheEvict(value = "patient_ratings", key = "#patientEmail")
    public RatingDTO addRatingByEmail(String patientEmail, RatingDTO ratingDTO) {

        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        Doctor doctor = doctorRepository.findByNpi(ratingDTO.getDoctorNpi())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 1. Check duplicati
        if (patient.getRatings() == null) {
            patient.setRatings(new ArrayList<>());
        }
        boolean alreadyRated = patient.getRatings().stream()
                .anyMatch(r -> r.getDoctorNpi().equals(doctor.getNpi()));

        if (alreadyRated) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "You've already rated this doctor.");
        }

        // 2. Crea Rating
        Rating newRating = new Rating();
        newRating.setDoctorNpi(doctor.getNpi());
        newRating.setDoctorFirstName(doctor.getFirstName());
        newRating.setDoctorLastName(doctor.getLastName());
        newRating.setRating(ratingDTO.getRating());

        // 3. Salva su Paziente
        patient.getRatings().add(newRating);
        patientRepository.save(patient);

        // 4. Aggiorna media Dottore
        double currentTotalScore = doctor.getAvgRating() * doctor.getRatingCount();
        double newTotalScore = currentTotalScore + ratingDTO.getRating();
        int newTotalRatings = doctor.getRatingCount() + 1;
        double newAverage = newTotalScore / newTotalRatings;
        ArrayList<Integer> ratings = doctor.getRatings();
        ratings.add(ratingDTO.getRating());

        // Arrotondamento 1 decimale
        float roundedAverage = (float) (Math.round(newAverage * 10.0) / 10.0);

        doctor.setAvgRating(roundedAverage);
        doctor.setRatingCount(newTotalRatings);
        doctor.setRatings(ratings);
        doctorRepository.save(doctor);

        // 5. 🔥 REDIS: Invalida cache dei rating del Dottore
        // Così il profilo pubblico del dottore mostrerà la nuova media aggiornata
        doctorService.invalidateDoctorCache(doctor.getEmail());

        return Mapper.mapToPatientRatingDTO(newRating);
    }

    @Override
    @Cacheable(value = "patient_ratings", key = "#email")
    public List<RatingDTO> getAllRatingsByEmail(String email) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getRatings() == null) return new ArrayList<>();

        return patient.getRatings().stream()
                .map(Mapper::mapToPatientRatingDTO)
                .collect(Collectors.toList());
    }

    // ===================================================================================
    //                                  SEARCH & CRUD
    // ===================================================================================

    @Override
    @Cacheable(value = "specialist_search", key = "{#city, #diagnosis}")
    public List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis) {
        log.info("Neo4j Query - City: {}, Diagnosy: {}", city, diagnosis);
        return doctorGraphRepository.findSpecialistsByDiagnosisAndCity(city, diagnosis);
    }

    @Override
    public PatientReadDTO registerPatient(PatientCreateDTO createDTO) {
        if (patientRepository.existsByEmail(createDTO.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        Patient patient = new Patient();
        patient.setEmail(createDTO.getEmail());
        patient.setTelephone(createDTO.getTelephone());
        patient.setPassword(passwordEncoder.encode(createDTO.getPassword()));
        patient.setCreatedAt(LocalDateTime.now());

        patient.setFirstName(createDTO.getFirstName());
        patient.setLastName(createDTO.getLastName());
        patient.setAge(createDTO.getAge());
        patient.setGender(createDTO.getGender());

        if (createDTO.getLocation() != null) {
            patient.setLocation(mapLocationDtoToEntity(createDTO.getLocation()));
        }

        patient.setRatings(new ArrayList<>());
        patient.setBookedAppointments(new ArrayList<>());
        patient.setRecentSymptomReports(new ArrayList<>());

        return mapToReadDTO(patientRepository.save(patient));
    }

    @Override
    public PatientReadDTO updatePatient(String email, PatientUpdateDTO updateDTO) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (updateDTO.getEmail() != null) {
            if (!updateDTO.getEmail().equals(patient.getEmail()) &&
                    patientRepository.existsByEmail(updateDTO.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
            }
            patient.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
            patient.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
        }
        if (updateDTO.getTelephone() != null) patient.setTelephone(updateDTO.getTelephone());

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
    // REDIS: Cachiamo il profilo intero.
    // Chiave: "patient_profile::<email>"
    @Cacheable(value = "patient_profile", key = "#email")
    public PatientReadDTO getPatientByEmail(String email) {

        log.info("Cache Miss: Recover patient profile from DB for {}", email);

        // 1. Query su MongoDB
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient with email: " + email + " not found"));

        // 2. Mapping Entity -> DTO completo
        return Mapper.mapToReadDTO(patient);
    }

}