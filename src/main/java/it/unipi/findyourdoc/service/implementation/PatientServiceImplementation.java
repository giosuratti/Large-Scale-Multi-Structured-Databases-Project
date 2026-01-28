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
import org.springframework.transaction.annotation.Transactional;
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
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomReportRepository symptomReportRepository;

    @Autowired
    private RedisSlotService redisSlotService; // Il servizio di locking che abbiamo creato

    @Override
    @Transactional // Garantisce l'atomicità (se supportato dal cluster Mongo)

    public AppointmentPatientDTO bookAppointmentByEmail(String patientEmail, AppointmentDTO appointmentDTO) {

        // 1. Recupero Dati Paziente
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Recupero Dati Dottore
        Doctor doctor = doctorRepository.findById(appointmentDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

        // --- FASE REDIS: LOCKING ---
        // 3. Tentiamo di acquisire il lock sullo slot.
        // Se restituisce false, qualcuno ci ha battuto sul tempo (concorrenza).
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
            // --- FASE MONGODB: PERSISTENZA ---

            // 4. Controllo di sicurezza finale su DB (nel caso il lock fosse scaduto ma il db fosse stato scritto)
            boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndAppointmentDateTime(
                    doctor.getId(),
                    appointmentDTO.getDateTime()
            );

            if (alreadyBooked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot già prenotato.");
            }

            // 5. Creazione dell'Entità Appuntamento (Snapshot dei dati)
            AppointmentFull appointment = new AppointmentFull();
            appointment.setDoctorId(doctor.getId());
            appointment.setDoctorFirstName(doctor.getFirstName());
            appointment.setDoctorLastName(doctor.getLastName());
            appointment.setSpecialties(doctor.getSpecialties());
            appointment.setDoctorRating(doctor.getAvgRating()); // Snapshot del rating attuale

            appointment.setPatientId(patient.getId());
            appointment.setPatientFirstName(patient.getFirstName());
            appointment.setPatientLastName(patient.getLastName());
            appointment.setPatientAge(patient.getAge());       // Snapshot età
            appointment.setPatientGender(patient.getGender()); // Snapshot genere
            appointment.setPatientTelephone(patient.getTelephone());

            appointment.setDateTime(appointmentDTO.getDateTime());
            appointment.setCreatedAt(LocalDateTime.now());
            appointment.setStatus(AppointmentStatus.SCHEDULED);

            // 6. Salvataggio su MongoDB
            appointmentRepository.save(appointment);

            // --- FASE REDIS: CACHE EVICTION ---
            // 7. Invalidiamo la cache degli appuntamenti del dottore.
            // La prossima volta che il dottore chiederà i suoi appuntamenti, Redis sarà vuoto
            // e forzerà una nuova lettura aggiornata da MongoDB.
            doctorService.invalidateDoctorCache(doctor.getId());

            // (Opzionale) 8. Rimuoviamo fisicamente lo slot dalla lista delle disponibilità nel documento Doctor
            removeSlotFromDoctorAvailability(doctor, appointmentDTO.getDateTime());

            // 9. Mapping e Ritorno
            return Mapper.mapToPatientDTO(appointment);

        } catch (Exception e) {
            // SE QUALCOSA VA STORTO:
            // È fondamentale rilasciare il lock immediatamente per non lasciare lo slot "appeso"
            // per 10 minuti (o quanto è il TTL impostato) inutilmente.
            redisSlotService.releaseSlotLock(doctor.getId(), appointmentDTO.getDateTime());
            throw e;
        }
        // Nota: In caso di successo, non rilasciamo esplicitamente il lock.
        // Lasciamo che scada da solo (TTL), tanto il controllo su DB (punto 4) protegge dai duplicati futuri.
    }


    // Metodo helper per rimuovere lo slot dall'array del dottore
    private void removeSlotFromDoctorAvailability(Doctor doctor, LocalDateTime slotTime) {
        if (doctor.getAvailableSlots() != null) {
            doctor.getAvailableSlots().removeIf(slot -> slot.getDateTime().equals(slotTime));
            doctorRepository.save(doctor);
        }
    }
    private DoctorService doctorService; // Serve per invalidare la cache del dottore

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


    @Override
    @Transactional
    public void cancelAppointment(String appointmentId) {
        // 1. Recuperiamo l'appuntamento
        AppointmentFull appointment = appointmentRepository.findById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appuntamento non trovato"));

        // Controllo di coerenza: non possiamo cancellare visite già fatte o cancellate
        if (!"BOOKED".equals(appointment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Impossibile cancellare un appuntamento già completato o disdetto.");
        }

        // 2. REDIS LOCK: Acquisiamo il lock sullo slot specifico.
        // Usiamo l'ID dell'appuntamento come "userId" fittizio per il lock, o una costante "SYSTEM_CANCEL"
        boolean locked = redisSlotService.acquireSlotLock(
                appointment.getDoctorId(),
                appointment.getDateTime(),
                "CANCEL_ACTION_" + appointmentId
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Impossibile cancellare ora: l'appuntamento è in fase di modifica da parte del sistema o del medico.");
        }

        try {
            // 3. LOGICA DI BUSINESS: Cambio stato
            appointment.setStatus(AppointmentStatus.CANCELLED);
            appointmentRepository.save(appointment);

            // 4. RESTITUZIONE SLOT: Il dottore torna disponibile a quell'ora
            restoreSlotToDoctor(appointment);

            // 5. REDIS CACHE EVICTION:
            // La cache del dottore è vecchia (contiene ancora l'appuntamento attivo). Cancelliamola.
            // Nota: serve l'email del dottore per la chiave della cache, ma nell'AppointmentFull l'abbiamo salvata?
            // Se non l'abbiamo nell'AppointmentFull, dobbiamo fare una query al DoctorRepository.
            Doctor doctor = doctorRepository.findById(appointment.getDoctorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato"));

            doctorService.invalidateDoctorCache(doctor.getEmail());

        } catch (Exception e) {
            // Se qualcosa fallisce, rilasciamo il lock
            redisSlotService.releaseSlotLock(appointment.getDoctorId(), appointment.getDateTime());
            throw e;
        }

        // Rilascio lock in caso di successo (opzionale se lasci scadere il TTL, ma buona prassi farlo qui)
        redisSlotService.releaseSlotLock(appointment.getDoctorId(), appointment.getDateTime());
    }

    /**
     * Metodo helper per riaggiungere lo slot alla lista delle disponibilità del dottore.
     */
    private void restoreSlotToDoctor(AppointmentFull appointment) {
        Doctor doctor = doctorRepository.findById(appointment.getDoctorId())
                .orElseThrow(() -> new RuntimeException("Dottore non trovato durante il ripristino slot"));

        // Creiamo il nuovo slot da reinserire
        Slot slotRestored = new Slot();
        slotRestored.setDateTime(appointment.getDateTime());

        // Recuperiamo la location (dobbiamo essere sicuri che sia la stessa dell'appuntamento)
        // Se nell'appuntamento hai salvato la Location completa, usala.
        // Altrimenti, assumiamo che il dottore sia nello stesso posto (semplificazione).
        // Per precisione, dovresti salvare la Location esatta dentro AppointmentFull.
        // Qui assumo che AppointmentFull abbia un campo Location o recupero quella del dottore.
        slotRestored.setLocation(doctor.getLocation()); // O appointment.getLocation() se esiste

        if (doctor.getAvailableSlots() == null) {
            doctor.setAvailableSlots(new ArrayList<>());
        }

        // Evitiamo duplicati (caso raro ma possibile)
        boolean exists = doctor.getAvailableSlots().stream()
                .anyMatch(s -> s.getDateTime().equals(slotRestored.getDateTime()));

        if (!exists) {
            doctor.getAvailableSlots().add(slotRestored);
            // Opzionale: riordiniamo gli slot per data
            doctor.getAvailableSlots().sort(Comparator.comparing(Slot::getDateTime));
            doctorRepository.save(doctor);
        }
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


    @Override
    @Cacheable(value = "patient_symptoms", key = "#email")
    public List<SymptomReportBriefDTO> getSymptomReportsByEmail(String email) {

        // 1. Recupera il paziente intero dal DB
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 2. Controllo di sicurezza: se la lista è null, restituisci lista vuota
        if (patient.getRecentSymptomReports() == null || patient.getRecentSymptomReports().isEmpty()) {
            return new ArrayList<>();
        }

        // 3. Mapping della lista Brief dal modello al DTO
        return patient.getRecentSymptomReports().stream()
                .map(Mapper::mapToSymptomBriefDTO)
                // Ordinamento: dal più recente (Oggi) al più vecchio
                .sorted(Comparator.comparing(SymptomReportBriefDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    // --- Metodo di utilità per il mapping ---




    @Override
    @Transactional
    public SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO) {

        // 1. Recupero Paziente
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Creazione dell'Entità
        SymptomReport report = new SymptomReport();

        // --- DATA SNAPSHOT PER ANALYTICS ---
        // Anche senza Redis, questo passaggio rimane CRUCIALE.
        // Stiamo congelando i dati demografici attuali nel report per le query statistiche future.
        report.setPatientAge(patient.getAge());
        report.setPatientGender(patient.getGender());
        report.setPatientLocation(patient.getLocation());

        // 3. Popolamento dati dal DTO
        report.setSymptoms(createDTO.getSymptoms());
        report.setContext(createDTO.getContext());
        report.setCreatedAt(LocalDateTime.now());

        /*


        PARTE DI NEO4J CON POSSIBILI DIAGNOSI!!!!!!!!

         */

        // 5. Persistenza su MongoDB
        symptomReportRepository.save(report);

        // 6. Return DTO
        return Mapper.mapToBriefDTO(report);
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
        return Mapper.mapToPatientRatingDTO(newRating);
    }



    @Override
    // ⚡ REDIS: Mettiamo in cache i rating del paziente per letture istantanee
    @Cacheable(value = "patient_ratings", key = "#email")
    public List<RatingDTO> getAllRatingsByEmail(String email) {

        // 1. Recupera il paziente dal DB
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Paziente non trovato"));

        // 2. Controllo di sicurezza: se la lista è null, restituisci lista vuota
        if (patient.getRatings() == null || patient.getRatings().isEmpty()) {
            return new ArrayList<>();
        }

        // 3. Mapping: convertiamo ogni Rating (Entity) nel RatingDTO per il frontend
        return patient.getRatings().stream()
                .map(Mapper::mapToPatientRatingDTO)
                .collect(Collectors.toList());
    }


    public List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis) {
        
    }



}