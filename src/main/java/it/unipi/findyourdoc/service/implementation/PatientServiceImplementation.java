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
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static it.unipi.findyourdoc.utils.Mapper.mapLocationDtoToEntity;
import static it.unipi.findyourdoc.utils.Mapper.mapToReadDTO;

@Service
@RequiredArgsConstructor
public class PatientServiceImplementation implements PatientService {

    @Autowired
    private MongoTemplate mongoTemplate;

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
    @CacheEvict(value = "patient_appointments", key = "#patientEmail")
    public AppointmentPatientDTO bookAppointmentByEmail(String patientEmail, AppointmentBookDTO appointmentBookDTO) {

        // 1. Recupero Paziente
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Recupero Dottore
        Doctor doctor = doctorRepository.findById(appointmentBookDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

        // Normalizziamo subito la data (troncando secondi/millisecondi) per evitare problemi di match
        LocalDateTime localDateTime = appointmentBookDTO.getDateTime().toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);

        // 3. --- REDIS LOCK ---
        boolean locked = redisSlotService.acquireSlotLock(
                doctor.getId(),
                localDateTime,
                patient.getId()
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Questo slot è momentaneamente bloccato da un altro utente. Riprova tra qualche minuto.");
        }

        try {
            // 4. Double Check MongoDB (Master Collection)
            boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndDateTime(
                    doctor.getId(),
                    localDateTime
            );

            if (alreadyBooked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot già prenotato (Database).");
            }

            // 5. Creazione Appuntamento MASTER
            AppointmentFull appointment = new AppointmentFull();
            appointment.setDoctorId(doctor.getId());
            appointment.setDoctorNpi(doctor.getNpi());
            appointment.setSpecialties(doctor.getSpecialties());
            appointment.setDoctorRating(doctor.getAvgRating());

            appointment.setPatientId(patient.getId());
            appointment.setPatientFirstName(patient.getFirstName());
            appointment.setPatientLastName(patient.getLastName());
            appointment.setPatientAge(patient.getAge());
            appointment.setPatientGender(patient.getGender());
            appointment.setPatientEmail(patient.getEmail());
            appointment.setPatientTelephone(patient.getTelephone());

            appointment.setLocation(doctor.getLocation());
            appointment.setDateTime(localDateTime);
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setStatus(AppointmentStatus.SCHEDULED);

            // 6. Salvataggio MASTER su MongoDB
            AppointmentFull savedAppointment = appointmentRepository.save(appointment);


            // 7. AGGIORNAMENTO PAZIENTE (Embedded)
            if (patient.getBookedAppointments() == null) {
                patient.setBookedAppointments(new ArrayList<>());
            }

            AppointmentPatient embeddedPatientAppt = new AppointmentPatient();
            embeddedPatientAppt.setAppointmentId(savedAppointment.getAppointmentId());
            embeddedPatientAppt.setDoctorNpi(doctor.getNpi());
            embeddedPatientAppt.setDoctorFirstName(doctor.getFirstName());
            embeddedPatientAppt.setDoctorLastName(doctor.getLastName());
            embeddedPatientAppt.setDoctorTelephone(doctor.getTelephone());
            embeddedPatientAppt.setDoctorSpecialties(doctor.getSpecialties());
            embeddedPatientAppt.setLocation(doctor.getLocation());
            embeddedPatientAppt.setDateTime(localDateTime);
            embeddedPatientAppt.setStatus(AppointmentStatus.SCHEDULED);

            patient.getBookedAppointments().add(embeddedPatientAppt);
            patientRepository.save(patient); // Salvataggio esplicito Paziente


            // 8. AGGIORNAMENTO DOTTORE (Embedded + Rimozione Slot)

            // A. Aggiungi a "Booked This Week" se rientra nei prossimi 7 giorni
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneWeekFromNow = now.plusDays(7);

            if (localDateTime.isAfter(now) && localDateTime.isBefore(oneWeekFromNow)) {
                if (doctor.getBookedThisWeek() == null) doctor.setBookedThisWeek(new ArrayList<>());

                AppointmentDoctor embeddedDoctorAppt = new AppointmentDoctor();
                embeddedDoctorAppt.setAppointmentId(savedAppointment.getAppointmentId());
                embeddedDoctorAppt.setPatientFirstName(patient.getFirstName());
                embeddedDoctorAppt.setPatientLastName(patient.getLastName());
                embeddedDoctorAppt.setPatientTelephone(patient.getTelephone());
                embeddedDoctorAppt.setDateTime(localDateTime);
                embeddedDoctorAppt.setStatus(AppointmentStatus.SCHEDULED); // Importante settare lo status

                doctor.getBookedThisWeek().add(embeddedDoctorAppt);
            }

            // B. RIMOZIONE SLOT "BLINDATA" (Fix per Timezone)
            // Tronchiamo ai minuti l'orario target (es. 15:00)
            LocalDateTime target = localDateTime.truncatedTo(ChronoUnit.MINUTES);

            // Calcoliamo anche la versione UTC (es. 14:00 se siamo in inverno, 13:00 in estate)
            // Serve perché Mongo salva in UTC e a volte Spring ricarica il dato "nudo"
            long offsetSeconds = ZoneId.systemDefault().getRules().getOffset(target).getTotalSeconds();
            LocalDateTime targetMinusOffset = target.minusSeconds(offsetSeconds); // Es: 15:00 -> 14:00
            LocalDateTime targetPlusOffset = target.plusSeconds(offsetSeconds);   // Es: 15:00 -> 16:00 (Caso opposto)

            // LOG DI DEBUG: Se fallisce ancora, mandami queste righe!
            log.info("DEBUG REMOVE - Target: {} | Target-Offset: {} | DB Slots: {}",
                    target, targetMinusOffset, doctor.getAvailableSlots());

            boolean removed = doctor.getAvailableSlots().removeIf(slot -> {
                LocalDateTime s = slot.truncatedTo(ChronoUnit.MINUTES);
                // Proviamo a matchare:
                // 1. L'orario esatto (15:00 == 15:00)
                // 2. L'orario meno l'offset (14:00 == 14:00)
                // 3. L'orario più l'offset (caso raro di errata conversione inversa)
                return s.isEqual(target) || s.isEqual(targetMinusOffset) || s.isEqual(targetPlusOffset);
            });

            if (!removed) {
                // Se fallisce qui, guarda i LOG sopra per capire che numeri c'erano
                log.error("CRITICAL: Slot non trovato. Target: {}, Lista DB: {}", target, doctor.getAvailableSlots());
                // Opzionale: Se vuoi forzare il successo anche se non trova lo slot (per evitare rollback),
                // commenta la riga sotto. Ma meglio lasciare l'errore per coerenza.
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo slot non è più disponibile (Errore sync orario).");
            }

            // C. SALVATAGGIO FINALE DOTTORE
            // Questo salva SIA l'aggiunta nell'array bookedThisWeek SIA la rimozione dallo slot
            doctorRepository.save(doctor); // <--- ECCO IL SALVATAGGIO CHE MANCAVA!


            // 9. Invalidazione Cache
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
            doctorService.invalidateDoctorCache(doctor.getEmail());

            return Mapper.mapToPatientDTO(savedAppointment, doctor.getFirstName(), doctor.getLastName());

        } catch (Exception e) {
            // Rilascio immediato del lock in caso di errore
            redisSlotService.releaseSlotLock(doctor.getId(), localDateTime);
            throw e;
        }
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

    // Assicurati di averlo iniettato

    private void removeSlotFromDoctorAvailability(Doctor doctor, LocalDateTime slotTime) {
        Query query = Query.query(Criteria.where("_id").is(doctor.getId()));

        // TRUCCO: Convertiamo il LocalDateTime in Date forzando la zona UTC.
        // In questo modo 10:00 diventa 10:00 UTC (e non 09:00 UTC)
        Date dateForMongo = Date.from(slotTime.toInstant(ZoneOffset.UTC));

        // Log di debug per essere sicuri
        System.out.println("🔧 FIX TIMEZONE: Cerco di rimuovere slot: " + dateForMongo);

        Update update = new Update().pull("availableSlots", dateForMongo);

        // Eseguiamo l'update
        var result = mongoTemplate.updateFirst(query, update, Doctor.class);

        if (result.getModifiedCount() > 0) {
            System.out.println("✅ Slot rimosso correttamente!");
        } else {
            System.err.println("❌ Slot NON rimosso. Verifica ancora la data nel DB.");
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