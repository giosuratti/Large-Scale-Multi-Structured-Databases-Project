package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
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
    public AppointmentPatientDTO bookAppointmentByEmail(String patientEmail, AppointmentFullDTO appointmentFullDTO) {

        // 1. Recupero Paziente
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Recupero Dottore
        Doctor doctor = doctorRepository.findById(appointmentFullDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

        // 3. --- REDIS LOCK ---
        // Tentiamo di acquisire il lock per evitare race conditions (doppie prenotazioni)
        boolean locked = redisSlotService.acquireSlotLock(
                doctor.getId(),
                appointmentFullDTO.getDateTime(),
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
                    appointmentFullDTO.getDateTime()
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

            appointment.setDateTime(appointmentFullDTO.getDateTime());
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setStatus(AppointmentStatus.SCHEDULED);

            // 6. Salvataggio su MongoDB
            appointmentRepository.save(appointment);

            // 7. RIMOZIONE SLOT DAL DOTTORE (MongoDB)
            removeSlotFromDoctorAvailability(doctor, appointmentFullDTO.getDateTime());

            // 8. 🔥 REDIS: Invalida cache slot pubblici del dottore
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());

            // 9. 🔥 REDIS: Invalida cache appuntamenti privati del dottore
            // (Il dottore deve vedere che ha un nuovo appuntamento nella sua dashboard)
            doctorService.invalidateDoctorCache(doctor.getEmail());

            return Mapper.mapToPatientDTO(appointment);

        } catch (Exception e) {
            // Se fallisce, rilasciamo il lock subito
            redisSlotService.releaseSlotLock(doctor.getId(), appointmentFullDTO.getDateTime());
            throw e;
        }
        // In caso di successo, il lock scadrà da solo (TTL) o verrà sovrascritto dalla persistenza DB
    }

    @Override
    @Transactional
    public void cancelAppointment(String appointmentId) {
        // 1. Recupero Appuntamento
        AppointmentFullSummary appointment = appointmentRepository.findSummaryById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // Check Stato
        AppointmentStatus currentStatus = appointment.status();
        if (currentStatus != AppointmentStatus.SCHEDULED &&
                currentStatus != AppointmentStatus.PENDING &&
                currentStatus != AppointmentStatus.RESCHEDULED &&
                currentStatus != AppointmentStatus.CONFIRMED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossible to cancel: status " + currentStatus + " does not allow it.");
        }

        // 2. Redis Lock
        boolean locked = redisSlotService.acquireSlotLock(
                appointment.doctorId(),
                appointment.dateTime(),
                "SYSTEM_CANCEL_" + appointmentId
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot currently being used.");
        }
        
        try {

            appointmentRepository.updateStatus(appointment.appointmentId(), AppointmentStatus.CANCELLED);
            // 3. UPDATE COLLECTION 'APPOINTMENTS' (Master Data)

            // 4. UPDATE COLLECTION 'DOCTORS' -> Restore Slot
            restoreSlotToDoctor(appointment);

            // 5. UPDATE COLLECTION 'PATIENTS' -> Embedded List
            updatePatientEmbeddedList(appointment);

            // 6. UPDATE COLLECTION 'DOCTORS' -> Embedded List (bookedThisWeek) <--- NUOVO!
            updateDoctorBookedThisWeek(appointment);

            // 7. REDIS: Invalida Slot
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + appointment.doctorId());

            // 8. REDIS: Invalida Cache Profilo Dottore (dove c'è bookedThisWeek)
            Doctor doctor = doctorRepository.findById(appointment.doctorId()).orElse(null);
            if (doctor != null) {
                doctorService.invalidateDoctorCache(doctor.getEmail());
            }

            // 9. REDIS: Invalida Cache Paziente
            Patient patient = patientRepository.findById(appointment.patientId()).orElse(null);
            if (patient != null) {
                redisTemplate.delete("patient_appointments::" + patient.getEmail());
            }

        } catch (Exception e) {
            redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
            throw e;
        }

        redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
    }

    // --- Helper 1: Aggiorna lista Paziente ---
    private void updatePatientEmbeddedList(AppointmentFullSummary appointment) {
        patientRepository.findById(appointment.patientId()).ifPresent(patient -> {
            if (patient.getBookedAppointments() != null) {
                boolean updated = false;
                for (var embeddedAppt : patient.getBookedAppointments()) {
                    // Confrontiamo gli ID (Assicurati che embeddedAppt abbia appointmentId)
                    if (embeddedAppt.getAppointmentId().equals(appointment.appointmentId())) {
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
    private void updateDoctorBookedThisWeek(AppointmentFullSummary appointment) {
        doctorRepository.findById(appointment.doctorId()).ifPresent(doctor -> {
            // Controlliamo se la lista esiste e non è vuota
            if (doctor.getBookedThisWeek() != null && !doctor.getBookedThisWeek().isEmpty()) {
                boolean updated = false;

                for (var embeddedAppt : doctor.getBookedThisWeek()) {
                    // Cerchiamo l'appuntamento tramite ID
                    if (embeddedAppt.getAppointmentId().equals(appointment.appointmentId())) {
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

    public Page<AppointmentPatientDTO> getAppointmentsByEmail(String email, Pageable pageable) {
        // Recuperiamo il documento paziente intero
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getBookedAppointments() == null || patient.getBookedAppointments().isEmpty()) {
            return Page.empty(pageable);
        }

        // 1. Trasformazione in DTO e Ordinamento
        List<AppointmentPatientDTO> allItems = patient.getBookedAppointments().stream()
                .filter(a -> a != null && a.getDateTime() != null)
                // Usiamo Comparator.reversed() per avere i più recenti in alto
                .sorted(Comparator.comparing(AppointmentPatient::getDateTime).reversed())
                .map(Mapper::mapToPatientDTO)
                .collect(Collectors.toList());

        // 2. Creiamo la Pagina
        return createPageFromList(allItems, pageable);
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

    private void restoreSlotToDoctor(AppointmentFullSummary appointment) {
        Doctor doctor = doctorRepository.findById(appointment.doctorId())
                .orElseThrow(() -> new RuntimeException("Doctor not found to restore the slot"));

        LocalDateTime slotRestored = appointment.dateTime();

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
    public Page<SymptomReportBriefDTO> getSymptomReportsByEmail(String email, Pageable pageable) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getRecentSymptomReports() == null || patient.getRecentSymptomReports().isEmpty()) {
            return Page.empty(pageable);
        }

        List<SymptomReportBriefDTO> allItems = patient.getRecentSymptomReports().stream()
                .filter(Objects::nonNull)
                // 1. FIX: Usa la classe dell'ENTITÀ (SymptomReportBrief) per il confronto, non il DTO
                .sorted(Comparator.comparing(SymptomReportBrief::getCreatedAt).reversed())
                // 2. FIX: Aggiungi il mapping da Entità -> DTO
                .map(Mapper::mapToBriefDTO)
                .collect(Collectors.toList());

        return createPageFromList(allItems, pageable);
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
        else {
            boolean alreadyRated = patient.getRatings().stream()
                    .anyMatch(r -> r.getDoctorNpi().equals(doctor.getNpi()));

            if (alreadyRated) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "You've already rated this doctor.");
            }
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
        double roundedAverage = (double) (Math.round(newAverage * 10.0) / 10.0);

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
    public Page<RatingDTO> getAllRatingsByEmail(String email, Pageable pageable) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getRatings() == null || patient.getRatings().isEmpty()) {
            return Page.empty(pageable);
        }

        // I rating solitamente non hanno data nell'embedded, se ce l'hanno ordina qui
        List<RatingDTO> allItems = patient.getRatings().stream()
                .filter(Objects::nonNull)
                .map(Mapper::mapToRatingDTO)
                .collect(Collectors.toList());

        return createPageFromList(allItems, pageable);
    }

    // ===================================================================================
    //                                  SEARCH & CRUD
    // ===================================================================================

    @Override
    @Cacheable(value = "specialist_search", key = "{#city, #diagnosis}")
    public List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis) {
        log.info("Neo4j Query - City: {}, Diagnosys: {}", city, diagnosis);
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

    @Override
    public DoctorReadSlotsDTO getDoctorByNpi(String npi) {
        // 1. Recupera il documento da MongoDB
        Doctor doctor = doctorRepository.findByNpi(npi)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 2. Logica di Business sugli Slot
        // Se il dottore ha slot, vogliamo mostrare solo quelli FUTURI e ORDINATI
        if (doctor.getAvailableSlots() != null) {
            List<LocalDateTime> sortedFutureSlots = doctor.getAvailableSlots().stream()
                    // Filtra via gli slot già passati (es. stamattina o ieri)
                    .filter(slot -> slot.isAfter(LocalDateTime.now()))
                    // Ordina dal più vicino al più lontano
                    .sorted()
                    // Raccoglie tutto in una List
                    .collect(Collectors.toList());

            // Aggiorniamo la lista temporanea dell'oggetto (senza salvare su DB, solo per il DTO)
            doctor.setAvailableSlots(new ArrayList<>(sortedFutureSlots));
        }

        // 3. Mappa in DTO
        return Mapper.mapToReadSlotsDTO(doctor);
    }

    private <T> Page<T> createPageFromList(List<T> list, Pageable pageable) {
        if (list == null) return Page.empty(pageable);

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), list.size());

        if (start > list.size()) {
            return new PageImpl<>(Collections.emptyList(), pageable, list.size());
        }

        List<T> pageContent = list.subList(start, end);
        return new PageImpl<>(pageContent, pageable, list.size());
    }

}