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

        // Normalizziamo subito la data
        LocalDateTime localDateTime = appointmentBookDTO.getDateTime().toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);

        // 3. --- REDIS LOCK ---
        boolean locked = redisSlotService.acquireSlotLock(doctor.getId(), localDateTime, patient.getId());

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Questo slot è momentaneamente bloccato da un altro utente. Riprova tra qualche minuto.");
        }

        try {
            // 4. Double Check MongoDB
            boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndDateTime(doctor.getId(), localDateTime);

            if (alreadyBooked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot già prenotato (Database).");
            }

            // 5. Creazione Appuntamento MASTER
            AppointmentFull appointment = new AppointmentFull();
            appointment.setDoctorId(doctor.getId());
            // appointment.setDoctorNpi(doctor.getNpi()); // <--- RIMOSSO (Come richiesto)
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
            embeddedPatientAppt.setAppointmentId(savedAppointment.getAppointmentId()); // Usa getId() standard
            embeddedPatientAppt.setDoctorNpi(doctor.getNpi());
            embeddedPatientAppt.setDoctorFirstName(doctor.getFirstName());
            embeddedPatientAppt.setDoctorLastName(doctor.getLastName());
            embeddedPatientAppt.setDoctorTelephone(doctor.getTelephone());
            embeddedPatientAppt.setDoctorSpecialties(doctor.getSpecialties());
            embeddedPatientAppt.setLocation(doctor.getLocation());
            embeddedPatientAppt.setDateTime(localDateTime);
            embeddedPatientAppt.setStatus(AppointmentStatus.SCHEDULED);

            patient.getBookedAppointments().add(embeddedPatientAppt);
            patientRepository.save(patient);

            // 8. AGGIORNAMENTO DOTTORE (Logica Biforcata + Rimozione Slot)

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneWeekFromNow = now.plusDays(7);

            // CASO A: Appuntamento VICINO (<= 7 giorni) -> Embedded in BookedThisWeek
            if (localDateTime.isAfter(now) && localDateTime.isBefore(oneWeekFromNow)) {
                if (doctor.getBookedThisWeek() == null) doctor.setBookedThisWeek(new ArrayList<>());

                AppointmentDoctor embeddedDoctorAppt = new AppointmentDoctor();
                embeddedDoctorAppt.setAppointmentId(savedAppointment.getAppointmentId());
                embeddedDoctorAppt.setPatientFirstName(patient.getFirstName());
                embeddedDoctorAppt.setPatientLastName(patient.getLastName());
                embeddedDoctorAppt.setPatientTelephone(patient.getTelephone());
                embeddedDoctorAppt.setDateTime(localDateTime);
                embeddedDoctorAppt.setStatus(AppointmentStatus.SCHEDULED);

                doctor.getBookedThisWeek().add(embeddedDoctorAppt);
            }
            // CASO B: Appuntamento LONTANO (> 7 giorni) -> Solo ID in FutureAppointments
            else if (localDateTime.isAfter(oneWeekFromNow)) {
                if (doctor.getFutureAppointments() == null) doctor.setFutureAppointments(new ArrayList<>());

                // Salviamo solo l'ID (Stringa)
                doctor.getFutureAppointments().add(savedAppointment.getAppointmentId());
            }

            /// B. RIMOZIONE SLOT (Correzione: Ne rimuove UNO solo)
            LocalDateTime target = localDateTime.truncatedTo(ChronoUnit.MINUTES);

            // Calcolo offset
            long offsetSeconds = ZoneId.systemDefault().getRules().getOffset(target).getTotalSeconds();
            LocalDateTime targetMinusOffset = target.minusSeconds(offsetSeconds);
            LocalDateTime targetPlusOffset = target.plusSeconds(offsetSeconds);

            boolean removed = false;
            Iterator<LocalDateTime> iterator = doctor.getAvailableSlots().iterator();

            while (iterator.hasNext()) {
                LocalDateTime slotRaw = iterator.next();
                LocalDateTime slot = slotRaw.truncatedTo(ChronoUnit.MINUTES);

                // Cerchiamo il match
                boolean isExactMatch = slot.isEqual(target);
                boolean isZoneMatch = slot.isEqual(targetMinusOffset) || slot.isEqual(targetPlusOffset);

                if (isExactMatch || isZoneMatch) {
                    iterator.remove(); // Rimuovi l'elemento corrente
                    removed = true;
                    log.info("Slot rimosso: {} (Target era: {})", slotRaw, target);
                    break; // <--- FONDAMENTALE: Ci fermiamo al primo che troviamo!
                }
            }

            if (!removed) {
                log.error("CRITICAL: Slot non trovato. Target: {}, Lista DB: {}", target, doctor.getAvailableSlots());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Lo slot non è più disponibile.");
            }

            // SALVATAGGIO FINALE DOTTORE (Salva: Slot rimosso + BookedThisWeek o FutureAppt aggiornati)
            doctorRepository.save(doctor);

            // 9. Invalidazione Cache
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
            redisSlotService.releaseSlotLock(doctor.getId(), localDateTime);
            doctorService.invalidateDoctorCache(doctor.getEmail());

            return Mapper.mapToPatientDTO(savedAppointment, doctor.getFirstName(), doctor.getLastName(), doctor.getNpi());

        } catch (Exception e) {
            redisSlotService.releaseSlotLock(doctor.getId(), localDateTime);
            throw e;
        }
    }

    @Override
    @Transactional
    public void cancelAppointment(String appointmentId) {

        // 1. Recupero Appuntamento (Summary leggero)
        AppointmentFullSummary appointment = appointmentRepository.findSummaryById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // 2. Controllo Stato (Non si può cancellare se già cancellato o completato)
        AppointmentStatus currentStatus = appointment.status();
        if (currentStatus == AppointmentStatus.CANCELLED || currentStatus == AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossibile cancellare: lo stato attuale è " + currentStatus);
        }

        // 3. --- REDIS LOCK ---
        // Blocchiamo lo slot per evitare che qualcuno lo prenoti mentre lo stiamo liberando/modificando
        boolean locked = redisSlotService.acquireSlotLock(
                appointment.doctorId(),
                appointment.dateTime(),
                "SYSTEM_CANCEL_" + appointmentId
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operazione in corso sullo slot. Riprova tra un attimo.");
        }

        try {
            // 4. UPDATE MASTER COLLECTION: Set Status CANCELLED
            appointmentRepository.updateStatus(appointmentId, AppointmentStatus.CANCELLED);

            // 5. UPDATE DOCTOR (Logica Completa: Slot + Pulizia Liste)
            Doctor doctor = doctorRepository.findById(appointment.doctorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

            // --- A. RIPRISTINO SLOT DISPONIBILE (Fix Timezone) ---
            if (doctor.getAvailableSlots() == null) doctor.setAvailableSlots(new ArrayList<>());

            // 1. Data Raw (UTC)
            LocalDateTime rawDate = appointment.dateTime().truncatedTo(ChronoUnit.MINUTES);

            // 2. Conversione a Local Time (Server Time)
            LocalDateTime restoredSlot = rawDate.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();

            // 3. Aggiunta (No duplicati)
            boolean slotExists = doctor.getAvailableSlots().stream()
                    .anyMatch(s -> s.truncatedTo(ChronoUnit.MINUTES).isEqual(restoredSlot));

            if (!slotExists) {
                doctor.getAvailableSlots().add(restoredSlot);
                Collections.sort(doctor.getAvailableSlots()); // Mantiene l'ordine
                log.info("Slot ripristinato: {}", restoredSlot);
            } else {
                log.warn("Slot già presente, salto ripristino: {}", restoredSlot);
            }

            // --- B. RIMOZIONE DA BOOKED THIS WEEK (Dashboard) ---
            // FONDAMENTALE: Se non lo rimuovi, il dottore vede sia lo slot libero che l'appuntamento vecchio.
            if (doctor.getBookedThisWeek() != null) {
                // Rimuove l'elemento se l'ID corrisponde
                boolean removed = doctor.getBookedThisWeek().removeIf(a ->
                        a.getAppointmentId() != null && a.getAppointmentId().equals(appointmentId)
                );
                if (removed) log.info("Appuntamento rimosso da bookedThisWeek");
            }

            // --- C. RIMOZIONE DA FUTURE APPOINTMENTS (Logica Professore) ---
            // Se l'appuntamento era lontano (salvato solo come ID stringa), lo rimuoviamo da qui.
            if (doctor.getFutureAppointments() != null) {
                doctor.getFutureAppointments().remove(appointmentId);
            }

            // Segnaliamo che il dottore è stato modificato (utile per i sync)
            doctor.setUpdated(true);

            // SALVATAGGIO ATOMICO DEL DOTTORE
            // Salva simultaneamente: Slot aggiunto, Rimozione da Booked, Rimozione da Future
            doctorRepository.save(doctor);


            // 6. UPDATE PATIENT (Uso del tuo metodo helper esistente)
            updatePatientEmbeddedList(appointment);


            // 7. INVALIDAZIONE CACHE
            // A. Slot Pubblici
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + appointment.doctorId());

            // B. Profilo Dottore (Dashboard)
            doctorService.invalidateDoctorCache(doctor.getEmail());

            // C. Appuntamenti Paziente (Opzionale, se hai l'email nel summary)
            // if (appointment.patientEmail() != null) {
            //    redisTemplate.delete("patient_appointments::" + appointment.patientEmail());
            // }

        } catch (Exception e) {
            // Rilascia lock in caso di errore
            redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
            throw e;
        }

        // Rilascia lock in caso di successo
        redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
    }

    // IL TUO METODO HELPER (Incluso per completezza)
    private void updatePatientEmbeddedList(AppointmentFullSummary appointment) {
        patientRepository.findById(appointment.patientId()).ifPresent(patient -> {
            if (patient.getBookedAppointments() != null) {
                boolean updated = false;
                for (var embeddedAppt : patient.getBookedAppointments()) {
                    // Confrontiamo gli ID
                    if (embeddedAppt.getAppointmentId().equals(appointment.appointmentId())) {
                        embeddedAppt.setStatus(AppointmentStatus.CANCELLED);
                        updated = true;
                        break;
                    }
                }
                if (updated) {
                    patientRepository.save(patient);
                    // Opzionale: Se vuoi invalidare la cache paziente qui dentro
                    // redisTemplate.delete("patient_appointments::" + patient.getEmail());
                }
            }
        });
    }

    // --- Helper 2: Aggiorna lista Dottore (bookedThisWeek) ---
    /*private void updateDoctorBookedThisWeek(AppointmentFullSummary appointment) {
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
    }*/

    @Override
    @Cacheable(value = "patient_appointments", key = "#email")
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

    /*private void removeSlotFromDoctorAvailability(Doctor doctor, LocalDateTime slotTime) {
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
    }*/

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