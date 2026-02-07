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
import org.springframework.data.redis.core.StringRedisTemplate; // IMPORTANTE
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

    // REDIS: Ci serve il template per cancellare chiavi specifiche (es. slot)
    private final StringRedisTemplate redisTemplate;

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);

    // Deve corrispondere a quello usato nel Controller o dove leggi gli slot
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    @Override
    public DoctorReadDTO updateDoctor(String email, DoctorUpdateDTO updateDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (updateDTO.getEmail() != null && !updateDTO.getEmail().equalsIgnoreCase(doctor.getEmail())) {
            if (doctorRepository.existsByEmail(updateDTO.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            }
            // TODO: Se cambia email, dovremmo invalidare la cache vecchia!
            // redisTemplate.delete("doctor_profile::" + doctor.getEmail());
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

        Doctor savedDoctor = doctorRepository.save(doctor);

        // INVALIDAZIONE CACHE PROFILE (se la usi)
        // redisTemplate.delete("doctor_ratings::" + email);

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
    @Cacheable(value = "doctor_ratings", key = "#email")
    public DoctorRatingDTO getRatingsByDoctorEmail(String email) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return Mapper.mapToDoctorRatingDTO(doctor.getRatings());
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
        LocalDateTime now = LocalDateTime.now();

        for (SlotDTO dto : slotDTOs) {
            if (dto.getDateTime().isBefore(now)) continue;

            boolean exists = doctor.getAvailableSlots().stream()
                    .anyMatch(existingSlot -> existingSlot.isEqual(dto.getDateTime()));

            if (exists) continue;
            newSlotsToAdd.add(dto.getDateTime());
        }

        if (!newSlotsToAdd.isEmpty()) {
            doctor.getAvailableSlots().addAll(newSlotsToAdd);
            Collections.sort(doctor.getAvailableSlots());
            doctorRepository.save(doctor);

            // 🔥 REDIS INVALIDATION: Slot cambiati -> Pulisci la cache!
            String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
            redisTemplate.delete(cacheKey);
            log.info("Invalidata cache slot per dottore ID: {}", doctor.getId());
        }
    }

    @Override
    @Transactional
    public void removeAvailabilitySlot(String email, SlotDTO slotDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (doctor.getAvailableSlots() == null || doctor.getAvailableSlots().isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No availability slots found");
        }

        boolean removed = doctor.getAvailableSlots().removeIf(slot -> slot.isEqual(slotDTO.getDateTime()));

        if (!removed) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");
        }

        doctorRepository.save(doctor);

        // 🔥 REDIS INVALIDATION: Slot cambiati -> Pulisci la cache!
        String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
        redisTemplate.delete(cacheKey);
        log.info("Invalidata cache slot (remove) per dottore ID: {}", doctor.getId());
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
    public List<SymptomReportBriefDTO> getPatientSymptomReports(String patientId) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getRecentSymptomReports() == null || patient.getRecentSymptomReports().isEmpty()) {
            return new ArrayList<>();
        }

        return patient.getRecentSymptomReports().stream()
                .map(Mapper::mapToSymptomBriefDTO)
                .sorted(Comparator.comparing(SymptomReportBriefDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }
}