package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.repository.mongo.AppointmentRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.service.DoctorService;
import it.unipi.findyourdoc.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
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
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DoctorServiceImplementation implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;

    // REDIS: Ci serve il template per cancellare chiavi specifiche (es. slot)
    private final StringRedisTemplate redisTemplate;

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);

    // Deve corrispondere a quello usato nel Controller o dove leggi gli slot
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    @Override
    public DoctorReadDTO updateDoctorLocation(String email, LocationDTO locationDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 1. Mappatura DTO -> Entity
        Location location = new Location();
        location.setAddress(locationDTO.getAddress());
        location.setCity(locationDTO.getCity());
        location.setState(locationDTO.getState());
        location.setZipCode(locationDTO.getZipCode());
        // Se hai latitudine/longitudine, settale qui

        // 2. Aggiornamento Documento
        doctor.setLocation(location);
        doctor.setUpdated(Boolean.TRUE);
        Doctor savedDoctor = doctorRepository.save(doctor);

        // 3. Invalida Cache Profilo
        invalidateDoctorCache(email);

        return Mapper.mapToReadDTO(savedDoctor);
    }

    @Override
    public DoctorReadDTO getDoctorByEmail(String email) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return Mapper.mapToReadDTO(doctor);
    }

    @Override
    // CACHE: Usiamo l'email come chiave
    @Cacheable(value = "doctor_appointments", key = "#email")
    public List<AppointmentFullDTO> getAppointmentsByEmail(String email) {
        log.info("Cache Miss: Recupero appuntamenti dal DB per {}", email);

        // 1. Recupera ID Dottore (Correzione Logica: Appointment usa ID, non email)
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 2. Query corretta usando l'ID
        List<AppointmentFull> appointments = appointmentRepository.findByDoctorId(doctor.getId());

        return appointments.stream()
                .map(Mapper::toAppointmentDTO)
                .collect(Collectors.toList());
    }

    @Override
    public Page<Integer> getRatingsByDoctorEmail(String email, Pageable pageable) {
        // 1. Recupera il Dottore
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 2. Recupera la lista grezza di interi (gestione null-safe)
        List<Integer> allRatings = doctor.getRatings();
        if (allRatings == null) {
            allRatings = new ArrayList<>();
        }

        // 3. Calcola gli indici per "tagliare" la lista (Paginazione in-memory)
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allRatings.size());

        // 4. Estrae la sottolista per la pagina corrente
        List<Integer> pageContent;
        if (start > allRatings.size()) {
            pageContent = new ArrayList<>(); // Pagina vuota se chiedi una pagina inesistente
        } else {
            pageContent = allRatings.subList(start, end);
        }

        // 5. Restituisce l'oggetto Page<Integer>
        // (Contenuto: [5, 4, ...], Pageable info, Totale elementi)
        return new PageImpl<>(pageContent, pageable, allRatings.size());
    }

    // Metodo helper per normalizzare l'input del frontend
    private LocalDateTime normalizeInputDate(LocalDateTime inputRaw) {
        // 1. Tronca i millisecondi (es. 14:00:00.984 -> 14:00:00.000)
        LocalDateTime cleanRaw = inputRaw.truncatedTo(ChronoUnit.MINUTES);

        // 2. Converti da UTC (Input) a System Default (DB)
        // Interpretiamo l'input come se fosse UTC e lo spostiamo nel fuso del server.
        return cleanRaw.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    @Override
    @Transactional
    public void addAvailabilitySlots(String email, List<SlotDTO> slotDTOs) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (doctor.getAvailableSlots() == null) {
            doctor.setAvailableSlots(new ArrayList<>());
        }

        List<LocalDateTime> newSlotsToAdd = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES); // Tronchiamo anche 'now'

        for (SlotDTO dto : slotDTOs) {
            // 1. Normalizziamo l'input (UTC -> Local + No Millisecondi)
            LocalDateTime normalizedSlot = normalizeInputDate(dto.getDateTime());

            // 2. Controllo validità temporale
            if (normalizedSlot.isBefore(now)) continue;

            // 3. Controllo duplicati usando l'orario normalizzato
            boolean exists = doctor.getAvailableSlots().stream()
                    .map(s -> s.truncatedTo(ChronoUnit.MINUTES)) // Tronchiamo anche quelli nel DB per sicurezza
                    .anyMatch(existingSlot -> existingSlot.isEqual(normalizedSlot));

            if (exists) continue;

            // 4. Aggiungiamo lo slot GIÀ CONVERTITO in locale
            newSlotsToAdd.add(normalizedSlot);
        }

        if (!newSlotsToAdd.isEmpty()) {
            doctor.getAvailableSlots().addAll(newSlotsToAdd);
            Collections.sort(doctor.getAvailableSlots());
            doctor.setUpdated(true); // Imposta flag per sync
            doctorRepository.save(doctor);

            // Redis Invalidation
            String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
            redisTemplate.delete(cacheKey);
            log.info("Aggiunti {} slot e invalidata cache per dottore ID: {}", newSlotsToAdd.size(), doctor.getId());
        }
    }

    @Override
    @Transactional
    public void removeAvailabilitySlot(String email, SlotDTO slotDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 1. Normalizziamo l'input (UTC -> Local Server)
        // Esempio: Input 14:00 (UTC) diventa 16:00 (Locale)
        LocalDateTime targetSlot = normalizeInputDate(slotDTO.getDateTime());

        log.info("Tentativo rimozione slot. Input Originale: {} -> Target Locale: {}",
                slotDTO.getDateTime(), targetSlot);

        // 2. Rimuoviamo usando match sui minuti
        boolean removed = doctor.getAvailableSlots().removeIf(existingSlot ->
                existingSlot.truncatedTo(ChronoUnit.MINUTES).isEqual(targetSlot)
        );

        if (!removed) {
            log.error("Slot non trovato! Target Locale: {} | DB Slots: {}", targetSlot, doctor.getAvailableSlots());
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
        }

        // 3. Salvataggio
        doctor.setUpdated(true);
        doctorRepository.save(doctor);

        // Redis Invalidation
        String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
        redisTemplate.delete(cacheKey);
        log.info("Rimosso slot e invalidata cache per dottore ID: {}", doctor.getId());
    }

    @Override
    // INVALIDAZIONE MULTIPLA
    // Quando chiamiamo questo metodo, puliamo sia gli appuntamenti che i rating
    @Caching(evict = {
            @CacheEvict(value = "doctor_appointments", key = "#email"),
            @CacheEvict(value = "doctor_ratings", key = "#email")
    })
    public void invalidateDoctorCache(String email) { // Nota: parametro rinominato in 'email' per chiarezza
        log.info("Manuale: Cache invalidata per email: {}", email);

        // Nota: Non invalidiamo qui 'doctor:slots:...' perché quella usa l'ID,
        // mentre qui stiamo passando l'email. Quella va gestita puntualmente o recuperando l'ID.
    }

    @Override
    public Page<SymptomReportBriefDTO> getPatientSymptomReports(String patientId, Pageable pageable) {
        // 1. Recupera il paziente
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 2. Recupera la lista grezza (gestione null-safe)
        List<SymptomReportBrief> rawReports = patient.getRecentSymptomReports();
        if (rawReports == null) {
            rawReports = new ArrayList<>();
        }

        // 3. Mappatura e Ordinamento (TUTTA la lista)
        // Nota: L'ordinamento lo facciamo qui. Se volessi usare pageable.getSort() servirebbe logica extra.
        List<SymptomReportBriefDTO> allReports = rawReports.stream()
                .map(Mapper::mapToSymptomBriefDTO)
                .sorted(Comparator.comparing(SymptomReportBriefDTO::getCreatedAt).reversed()) // Ordine decrescente
                .collect(Collectors.toList());

        // 4. Calcolo degli indici per la Paginazione In-Memory
        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allReports.size());

        // 5. Gestione casi limite (pagina richiesta fuori range)
        List<SymptomReportBriefDTO> pageContent;
        if (start > allReports.size()) {
            pageContent = new ArrayList<>();
        } else {
            // Taglia la lista per restituire solo gli elementi della pagina corrente
            pageContent = allReports.subList(start, end);
        }

        // 6. Restituisce l'oggetto Page
        // (Contenuto della pagina, info sulla paginazione, dimensione totale della lista originale)
        return new PageImpl<>(pageContent, pageable, allReports.size());
    }

    @Override
    public DoctorReadDTO updateDoctorPhone(String email, TelephoneUpdateDTO phoneDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // Aggiorna solo il telefono
        doctor.setTelephone(phoneDTO.getTelephone());
        doctor.setUpdated(Boolean.TRUE);
        Doctor savedDoctor = doctorRepository.save(doctor);

        // Invalida cache profilo
        invalidateDoctorCache(email);

        return Mapper.mapToReadDTO(savedDoctor);
    }

    @Override
    public void updateDoctorPassword(String email, PasswordChangeDTO newPassword) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // Hash della nuova password (FONDAMENTALE)
        String encodedPassword = passwordEncoder.encode(newPassword.getNewPassword());

        doctor.setPassword(encodedPassword);
        doctorRepository.save(doctor);

        // Nota: Qui non serve invalidare cache o sync admin.
    }
}