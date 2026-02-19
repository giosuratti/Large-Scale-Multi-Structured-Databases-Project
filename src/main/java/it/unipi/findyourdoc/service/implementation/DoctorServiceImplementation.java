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
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Service implementation for professional medical users.
 * Manages availability slots, agendas, and profile synchronization between MongoDB and Neo4j.
 */
@Service
@RequiredArgsConstructor
public class DoctorServiceImplementation implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;

    /** * Redis template for targeted eviction of professional availability caches. */
    private final StringRedisTemplate redisTemplate;

    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    /**
     * Updates doctor's practice location and flags the document for cross-database synchronization.
     */
    @Override
    public DoctorReadDTO updateDoctorLocation(String email, LocationDTO locationDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        Location location = Mapper.mapLocationDTOToEntity(locationDTO);

        doctor.setLocation(location);
        doctor.setUpdated(Boolean.TRUE);
        Doctor savedDoctor = doctorRepository.save(doctor);

        invalidateDoctorCache(email);
        return Mapper.mapToReadDTO(savedDoctor);
    }

    /**
     * Fetches doctor profile data by email.
     */
    @Override
    public DoctorReadDTO getDoctorByEmail(String email) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return Mapper.mapToReadDTO(doctor);
    }

    /**
     * Retrieves the doctor's agenda. Result is cached to optimize dashboard performance.
     */
    @Override
    @Cacheable(value = "doctor_appointments", key = "#email")
    public List<AppointmentFullDTO> getAppointmentsByEmail(String email) {
        log.info("Cache Miss: Fetching appointments from DB for doctor: {}", email);
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        List<AppointmentFull> appointments = appointmentRepository.findByDoctorId(doctor.getId());
        return appointments.stream()
                .map(Mapper::toAppointmentDTO)
                .collect(Collectors.toList());
    }

    /**
     * Paginates the internal list of ratings for a specific doctor.
     */
    @Override
    public Page<Integer> getRatingsByDoctorEmail(String email, Pageable pageable) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        List<Integer> allRatings = doctor.getRatings() != null ? doctor.getRatings() : new ArrayList<>();

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allRatings.size());

        List<Integer> pageContent = (start > allRatings.size()) ? new ArrayList<>() : allRatings.subList(start, end);
        return new PageImpl<>(pageContent, pageable, allRatings.size());
    }

    /**
     * Normalizes frontend UTC timestamps to server local time and removes millisecond noise.
     */
    private LocalDateTime normalizeInputDate(LocalDateTime inputRaw) {
        LocalDateTime cleanRaw = inputRaw.truncatedTo(ChronoUnit.MINUTES);
        return cleanRaw.atZone(ZoneOffset.UTC)
                .withZoneSameInstant(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    /**
     * Performs bulk addition of availability slots.
     * Validates chronological consistency and prevents duplicates in the document array.
     */
    @Override
    @Transactional
    public void addAvailabilitySlots(String email, List<SlotDTO> slotDTOs) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (doctor.getAvailableSlots() == null) doctor.setAvailableSlots(new ArrayList<>());

        List<LocalDateTime> newSlotsToAdd = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);

        for (SlotDTO dto : slotDTOs) {
            LocalDateTime normalizedSlot = normalizeInputDate(dto.getDateTime());
            if (normalizedSlot.isBefore(now)) continue;

            boolean exists = doctor.getAvailableSlots().stream()
                    .anyMatch(existingSlot -> existingSlot.truncatedTo(ChronoUnit.MINUTES).isEqual(normalizedSlot));

            if (!exists) newSlotsToAdd.add(normalizedSlot);
        }

        if (!newSlotsToAdd.isEmpty()) {
            doctor.getAvailableSlots().addAll(newSlotsToAdd);
            Collections.sort(doctor.getAvailableSlots());
            doctor.setUpdated(true);
            doctorRepository.save(doctor);

            // Manual Redis eviction for slot availability views
            String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
            redisTemplate.delete(cacheKey);
            log.info("Added {} slots and invalidated cache for doctor ID: {}", newSlotsToAdd.size(), doctor.getId());
        }
    }

    /**
     * Removes a specific availability slot and clears the associated Redis cache.
     */
    @Override
    @Transactional
    public void removeAvailabilitySlot(String email, SlotDTO slotDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        LocalDateTime targetSlot = normalizeInputDate(slotDTO.getDateTime());
        boolean removed = doctor.getAvailableSlots().removeIf(existingSlot ->
                existingSlot.truncatedTo(ChronoUnit.MINUTES).isEqual(targetSlot)
        );

        if (!removed) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Slot not found");

        doctor.setUpdated(true);
        doctorRepository.save(doctor);

        String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
        redisTemplate.delete(cacheKey);
    }

    /**
     * Clears cached views for appointments and ratings to ensure data consistency.
     */
    @Override
    @Caching(evict = {
            @CacheEvict(value = "doctor_appointments", key = "#email"),
            @CacheEvict(value = "doctor_ratings", key = "#email")
    })
    public void invalidateDoctorCache(String email) {
        log.info("Manual cache eviction triggered for email: {}", email);
    }

    /**
     * Retrieves paginated symptom reports for a patient, ordered by date.
     */
    @Override
    public Page<SymptomReportBriefDTO> getPatientSymptomReports(String patientId, Pageable pageable) {
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        List<SymptomReportBrief> rawReports = patient.getRecentSymptomReports() != null ?
                patient.getRecentSymptomReports() : new ArrayList<>();

        List<SymptomReportBriefDTO> allReports = rawReports.stream()
                .map(Mapper::mapToSymptomBriefDTO)
                .sorted(Comparator.comparing(SymptomReportBriefDTO::getCreatedAt).reversed())
                .collect(Collectors.toList());

        int start = (int) pageable.getOffset();
        int end = Math.min((start + pageable.getPageSize()), allReports.size());

        List<SymptomReportBriefDTO> pageContent = (start > allReports.size()) ?
                new ArrayList<>() : allReports.subList(start, end);

        return new PageImpl<>(pageContent, pageable, allReports.size());
    }

    /**
     * Updates doctor's contact number and invalidates the profile cache.
     */
    @Override
    public DoctorReadDTO updateDoctorPhone(String email, TelephoneUpdateDTO phoneDTO) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        doctor.setTelephone(phoneDTO.getTelephone());
        doctor.setUpdated(Boolean.TRUE);
        Doctor savedDoctor = doctorRepository.save(doctor);

        invalidateDoctorCache(email);
        return Mapper.mapToReadDTO(savedDoctor);
    }

    /**
     * Updates doctor's account password.
     */
    @Override
    public void updateDoctorPassword(String email, PasswordChangeDTO newPassword) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        doctor.setPassword(passwordEncoder.encode(newPassword.getNewPassword()));
        doctorRepository.save(doctor);
    }
}