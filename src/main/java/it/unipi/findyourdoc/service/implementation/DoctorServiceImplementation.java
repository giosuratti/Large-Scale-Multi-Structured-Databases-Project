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


@Service
@RequiredArgsConstructor
public class DoctorServiceImplementation implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);



    @Override
    public DoctorReadDTO updateDoctor(String email, DoctorUpdateDTO updateDTO) {
        // Cerchiamo il dottore esistente tramite la mail (identificativo scelto nel controller)
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // Aggiornamento selettivo con null-check (Logica Integer/Object)
        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equalsIgnoreCase(doctor.getEmail())) {
            if (doctorRepository.existsByEmail(updateDTO.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            }
            doctor.setEmail(updateDTO.getEmail());
        }

        if (updateDTO.getTelephone() != null) doctor.setTelephone(updateDTO.getTelephone());
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
            doctor.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
        }

        if (updateDTO.getFirstName() != null) doctor.setFirstName(updateDTO.getFirstName());
        if (updateDTO.getLastName() != null) doctor.setLastName(updateDTO.getLastName());
        if (updateDTO.getSpecializations() != null) doctor.setSpecialties(updateDTO.getSpecializations());
        if (updateDTO.getGender() != null) doctor.setGender(updateDTO.getGender());

        if (updateDTO.getLocation() != null) {
            doctor.setLocation(Mapper.mapLocationDtoToEntity(updateDTO.getLocation()));
        }

        return Mapper.mapToReadDTO(doctorRepository.save(doctor));
    }

    @Override
    public DoctorReadDTO getDoctorByEmail(String email) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return Mapper.mapToReadDTO(doctor);
    }




    @Override
    // REDIS: Qui attiviamo la cache.
    // 1. Spring controlla se in Redis esiste la chiave "doctor_appointments::<email>"
    // 2. SE ESISTE: Restituisce subito la lista (senza eseguire il codice sotto).
    // 3. SE NON ESISTE: Esegue la query su Mongo, salva il risultato in Redis e lo restituisce.
    @Cacheable(value = "doctor_appointments", key = "#email")
    public List<AppointmentDTO> getAppointmentsByEmail(String email) {

        // Questo log apparirà in console SOLO se i dati vengono letti dal Database (Cache Miss).
        // Se non lo vedi, significa che Redis ha risposto (Cache Hit).
        log.info("Cache Miss: Recupero appuntamenti dal Database per il dottore {}", email);

        // 1. Query su MongoDB
        // Assumiamo che tu abbia un metodo nel repository per cercare per email del dottore
        List<AppointmentFull> appointments = appointmentRepository.findByDoctorId(email);

        // 2. Mapping Entity -> DTO
        // Usiamo il mapper centralizzato per trasformare la lista
        return appointments.stream()
                .map(Mapper::toAppointmentDTO)
                .collect(Collectors.toList());
    }

    @Override
    // ⚡ REDIS: Cache fondamentale qui. Un dottore famoso potrebbe avere migliaia di letture al profilo.
    @Cacheable(value = "doctor_ratings", key = "#email")
    public DoctorRatingDTO getRatingsByDoctorEmail(String email) {

        // 1. Recuperiamo il dottore dal DB
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dottore non trovato con email: " + email));


        // 3. Mapping dei risultati
        return Mapper.mapToDoctorRatingDTO(doctor.getRatings());
    }


    @Override
    @Transactional // Ensures the update is atomic
    public void addAvailabilitySlots(String email, List<SlotDTO> slotDTOs) {

        // 1. Retrieve the Doctor from the database
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 2. Initialize the slots list if it doesn't exist to avoid NullPointerException
        if (doctor.getAvailableSlots() == null) {
            doctor.setAvailableSlots(new ArrayList<>());
        }

        List<LocalDateTime> newSlotsToAdd = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        // 3. Process incoming slots
        for (SlotDTO dto : slotDTOs) {

            // A. TIME VALIDATION: Ignore slots in the past
            if (dto.getDateTime().isBefore(now)) {
                continue; // Skip this slot
            }

            // B. DUPLICATE CHECK: Ensure no slot exists at the exact same time
            boolean exists = doctor.getAvailableSlots().stream()
                    .anyMatch(existingSlot -> existingSlot.isEqual(dto.getDateTime()));

            if (exists) {
                continue; // Skip duplicates to maintain data integrity
            }

            // C. MAPPING (DTO -> Entity)
            Slot slot = new Slot();
            slot.setDateTime(dto.getDateTime());


            newSlotsToAdd.add(dto.getDateTime());
        }

        // 4. Save and Sort only if there are valid new slots
        if (!newSlotsToAdd.isEmpty()) {
            // Add the new valid slots to the doctor's schedule
            doctor.getAvailableSlots().addAll(newSlotsToAdd);

            // SORTING: Re-order the entire list chronologically (Oldest -> Newest)
            // This ensures the frontend receives an ordered list without needing extra logic
            Collections.sort(doctor.getAvailableSlots());

            // Persist changes to MongoDB
            doctorRepository.save(doctor);
        }
    }

    @Override
    @Transactional // Ensures data consistency during the delete operation
    public void removeAvailabilitySlot(String email, SlotDTO slotDTO) {

        // 1. Retrieve the Doctor from the database
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 2. Check if the doctor has any slots to remove
        if (doctor.getAvailableSlots() == null || doctor.getAvailableSlots().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No availability slots found to remove");
        }

        // 3. Perform removal using a Lambda predicate
        // 'removeIf' iterates through the list and removes elements that match the condition.
        // It returns 'true' if any elements were removed.
        boolean removed = doctor.getAvailableSlots().removeIf(slot -> slot.isEqual(slotDTO.getDateTime()));

        // 4. Handle the case where the slot was not found in the list
        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "The specified slot was not found in the doctor's agenda");
        }

        // 5. Persist the updated doctor document to MongoDB
        doctorRepository.save(doctor);
    }
    @Override
    // NOTA BENE:
    // value = "doctor_appointments" deve coincidere con quello usato in getAppointmentsByEmail
    // key = "#email" indica che l'email passata come argomento è la chiave da cancellare
    @CacheEvict(value = "doctor_appointments", key = "#id")
    public void invalidateDoctorCache(String id) {
        // Il corpo del metodo può essere vuoto!
        // L'annotazione fa tutto il lavoro sporco su Redis prima (o dopo) l'esecuzione.
        // Mettiamo un log solo per debug.
        log.info("Cache invalidata per il dottore: {}. Al prossimo accesso i dati verranno ricaricati da MongoDB.", id);
    }

    @Override
    public List<SymptomReportBriefDTO> getPatientSymptomReports(String patientId) {
        // 1. Recupera il paziente intero dal DB
        Patient patient = patientRepository.findById(patientId)
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
}