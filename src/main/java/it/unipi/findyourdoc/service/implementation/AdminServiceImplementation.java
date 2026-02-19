package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.DoctorGraphUpdateProjection;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.model.mongo.enums.AppointmentStatus;
import it.unipi.findyourdoc.repository.mongo.AdminRepository;
import it.unipi.findyourdoc.repository.mongo.AppointmentRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.repository.neo4j.DoctorGraphRepository;
import it.unipi.findyourdoc.service.AdminService;
import it.unipi.findyourdoc.service.DoctorService;
import it.unipi.findyourdoc.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service implementation for administrative operations.
 * Manages cross-database synchronization, user lifecycle, and cache consistency.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImplementation implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorGraphRepository doctorGraphRepository;
    private final AppointmentRepository appointmentRepository;

    /** * Redis template for manual cache key eviction. */
    private final StringRedisTemplate redisTemplate;

    /** * Service for triggering standard doctor cache invalidations. */
    private final DoctorService doctorService;

    private final CacheManager cacheManager;

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImplementation.class);
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    @Override
    public AdminReadDTO createAdmin(AdminCreateDTO dto) {
        if (adminRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }
        Admin admin = new Admin();
        admin.setEmail(dto.getEmail());
        admin.setTelephone(dto.getTelephone());
        admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        admin.setCreatedAt(LocalDateTime.now());
        return Mapper.mapToReadDTO(adminRepository.save(admin));
    }

    @Override
    public AdminReadDTO updateAdmin(String email, AdminUpdateDTO dto) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        if (dto.getEmail() != null && !dto.getEmail().isBlank() && !dto.getEmail().equalsIgnoreCase(admin.getEmail())) {
            if (adminRepository.existsByEmail(dto.getEmail()))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            admin.setEmail(dto.getEmail());
        }
        if (dto.getTelephone() != null) admin.setTelephone(dto.getTelephone());
        if (dto.getPassword() != null && !dto.getPassword().isBlank())
            admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        return Mapper.mapToReadDTO(adminRepository.save(admin));
    }

    @Override
    public AdminReadDTO getAdminByEmail(String email) {
        return Mapper.mapToReadDTO(adminRepository.findByEmail(email).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found")));
    }

    @Override
    public Page<AdminReadDTO> getAllAdmins(Pageable pageable) {
        return adminRepository.findAll(pageable).map(Mapper::mapToReadDTO);
    }

    @Override
    public List<AdminReadDTO> searchAdmins(String emailPrefix) {
        return adminRepository.findByEmailStartingWith(emailPrefix).stream().map(Mapper::mapToReadDTO).collect(Collectors.toList());
    }

    /**
     * Handles cascading deletion of users across all collections and graph nodes.
     * Cancels future appointments and purges associated Redis entries.
     */
    @Override
    @Transactional
    public void deleteUser(String email) {
        boolean deleted = false;
        LocalDateTime now = LocalDateTime.now();

        // 1. CHECK IF DOCTOR
        Optional<Doctor> doctorOpt = doctorRepository.findByEmail(email);
        if (doctorOpt.isPresent()) {
            Doctor doctor = doctorOpt.get();

            List<AppointmentFull> futureAppointments = appointmentRepository
                    .findByDoctorIdAndDateTimeAfter(doctor.getId(), now);

            for (AppointmentFull appt : futureAppointments) {
                if (isActive(appt)) {
                    appt.setStatus(AppointmentStatus.CANCELLED);
                    appointmentRepository.save(appt);
                }
            }

            doctorRepository.deleteById(doctor.getId());

            // REDIS: Invalidate slots and standard cache
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
            doctorService.invalidateDoctorCache(doctor.getEmail());

            deleted = true;
        }

        // 2. CHECK IF PATIENT
        if (!deleted) {
            Optional<Patient> patientOpt = patientRepository.findByEmail(email);
            if (patientOpt.isPresent()) {
                Patient patient = patientOpt.get();

                List<AppointmentFull> futureAppointments = appointmentRepository
                        .findByPatientIdAndDateTimeAfter(patient.getId(), now);

                for (AppointmentFull appt : futureAppointments) {
                    if (isActive(appt)) {
                        appt.setStatus(AppointmentStatus.CANCELLED);
                        appointmentRepository.save(appt);

                        // Restore availability to doctor
                        restoreSlotToDoctor(appt);
                    }
                }

                patientRepository.deleteById(patient.getId());

                // REDIS: Purge patient-specific history and session caches
                redisTemplate.delete("patient_appointments::" + patient.getEmail());
                redisTemplate.delete("patient_ratings::" + patient.getEmail());
                redisTemplate.delete("patient_symptoms::" + patient.getEmail());

                deleted = true;
            }
        }

        // 3. CHECK IF ADMIN
        if (!deleted) {
            if (adminRepository.existsByEmail(email)) {
                adminRepository.deleteByEmail(email);
                deleted = true;
            }
        }

        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User with email " + email + " not found.");
        }
    }

    private boolean isActive(AppointmentFull appt) {
        return appt.getStatus() != AppointmentStatus.CANCELLED && appt.getStatus() != AppointmentStatus.COMPLETED;
    }

    /** * Restores a cancelled appointment slot to the doctor's available pool. */
    private void restoreSlotToDoctor(AppointmentFull appt) {
        doctorRepository.findById(appt.getDoctorId()).ifPresent(doc -> {
            if (doc.getAvailableSlots() == null) doc.setAvailableSlots(new ArrayList<>());

            if (!doc.getAvailableSlots().contains(appt.getDateTime())) {
                doc.getAvailableSlots().add(appt.getDateTime());
                Collections.sort(doc.getAvailableSlots());
                doctorRepository.save(doc);

                // Evict availability cache
                redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doc.getId());
            }
        });
    }

    /** * Updates user password and evicts session-related caches. */
    @Override
    @Transactional
    public void changeUserPassword(String id, String newPassword) {
        String encodedPassword = passwordEncoder.encode(newPassword);
        boolean userFound = false;

        Optional<Doctor> doctorOpt = doctorRepository.findById(id);
        if (doctorOpt.isPresent()) {
            Doctor doctor = doctorOpt.get();
            doctor.setPassword(encodedPassword);
            doctorRepository.save(doctor);
            redisTemplate.delete("doctor_appointments::" + doctor.getEmail());
            userFound = true;
        }

        if (!userFound) {
            Optional<Patient> patientOpt = patientRepository.findById(id);
            if (patientOpt.isPresent()) {
                Patient patient = patientOpt.get();
                patient.setPassword(encodedPassword);
                patientRepository.save(patient);
                redisTemplate.delete("patient_reports::" + patient.getEmail());
                userFound = true;
            }
        }

        if (!userFound) {
            Optional<Admin> adminOpt = adminRepository.findById(id);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                admin.setPassword(encodedPassword);
                adminRepository.save(admin);
                userFound = true;
            }
        }

        if (!userFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No user found with ID: " + id);
        }
    }

    /** * Synchronizes aggregate ratings from MongoDB to Neo4j using parallel batching. */
    @Override
    @CacheEvict(value = {"specialist_search", "doctors_search_city"}, allEntries = true)
    public void syncDoctorRatings() {
        log.info("Starting rating synchronization from MongoDB to Neo4j...");
        List<DoctorProjection> mongoDoctors = doctorRepository.findAllBy();

        List<Map<String, Object>> allUpdates = mongoDoctors.parallelStream()
                .map(doc -> {
                    Map<String, Object> entry = new HashMap<>();
                    entry.put("npi", doc.npi());
                    entry.put("avgRating", doc.avgRating());
                    entry.put("ratingCount", doc.ratingCount());
                    return entry;
                })
                .collect(Collectors.toList());

        List<List<Map<String, Object>>> batches = partitionList(allUpdates, 500);

        batches.parallelStream().forEach(batch -> {
            try {
                doctorGraphRepository.bulkUpdateRatings(batch);
            } catch (Exception e) {
                log.error("Neo4j batch error: {}", e.getMessage());
            }
        });
        log.info("Sync completed.");
    }

    public static <T> List<List<T>> partitionList(List<T> list, int pageSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += pageSize) {
            partitions.add(list.subList(i, Math.min(i + pageSize, list.size())));
        }
        return partitions;
    }

    @Override
    public DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO) {
        if (doctorRepository.existsByEmail(createDTO.getEmail()) || doctorRepository.existsByNpi(createDTO.getNpi()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");

        Doctor doctor = new Doctor();
        doctor.setEmail(createDTO.getEmail());
        doctor.setTelephone(createDTO.getTelephone());
        doctor.setPassword(passwordEncoder.encode(createDTO.getPassword()));
        doctor.setCreatedAt(LocalDateTime.now());
        doctor.setFirstName(createDTO.getFirstName());
        doctor.setLastName(createDTO.getLastName());
        doctor.setSpecialties(createDTO.getSpecializations());
        doctor.setGender(createDTO.getGender());
        if (createDTO.getLocation() != null) doctor.setLocation(Mapper.mapLocationDTOToEntity(createDTO.getLocation()));

        doctor.setAvailableSlots(new ArrayList<>());
        doctor.setBookedThisWeek(new ArrayList<>());
        doctor.setRatings(new ArrayList<>());
        doctor.setAvgRating(0.0);
        doctor.setRatingCount(0);

        Doctor savedDoctor = doctorRepository.save(doctor);

        try {
            doctorGraphRepository.createDoctorAndRelations(
                    savedDoctor.getNpi(),
                    savedDoctor.getFirstName(),
                    savedDoctor.getLastName(),
                    savedDoctor.getGender(),
                    savedDoctor.getLocation().getCity(),
                    savedDoctor.getSpecialties(),
                    savedDoctor.getAvgRating(),
                    savedDoctor.getRatingCount()
            );
        } catch (Exception e) {
            doctorRepository.deleteById(savedDoctor.getId());
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Error creating graph node: " + e.getMessage());
        }

        return Mapper.mapToReadDTO(savedDoctor);
    }

    /** * Asynchronously synchronizes profile updates across MongoDB and Neo4j. */
    @Override
    @Async
    public void syncAllDoctors() {
        log.info("Starting massive synchronization...");
        List<Doctor> allDoctors = doctorRepository.findAll();

        List<DoctorGraphUpdateProjection> neo4jBatch = new ArrayList<>();

        for (Doctor doc : allDoctors) {
            try {
                performSingleDoctorSync(doc, neo4jBatch);
            } catch (Exception e) {
                log.error("Error syncing doctor {}: {}", doc.getEmail(), e.getMessage());
            }
        }

        if (!neo4jBatch.isEmpty()) {
            List<Map<String, Object>> mapBatch = neo4jBatch.stream()
                    .map(dto -> {
                        Map<String, Object> map = new HashMap<>();
                        map.put("npi", dto.npi());
                        map.put("telephone", dto.telephone() != null ? dto.telephone() : "");
                        map.put("city", dto.city() != null ? dto.city() : "");
                        return map;
                    })
                    .collect(Collectors.toList());

            log.info("Updating Neo4j for {} doctors...", mapBatch.size());
            doctorGraphRepository.bulkUpdateDoctors(mapBatch);
            log.info("Neo4j Bulk Update completed.");
        }
    }

    /**
     * Internal logic for syncing a single doctor document.
     * Manages weekly schedule shifting and denormalized data propagation.
     */
    public void performSingleDoctorSync(Doctor doctor, List<DoctorGraphUpdateProjection> neo4jBatch) {
        boolean dirty = false;
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneWeekFromNow = now.plusDays(7);

        // A. CLEANUP: Remove past appointments from weekly view
        if (doctor.getBookedThisWeek() != null) {
            boolean removed = doctor.getBookedThisWeek().removeIf(appt -> appt.getDateTime().isBefore(now));
            if (removed) dirty = true;
        }

        // B. TIME SHIFT: Move appointments into the 7-day window
        if (doctor.getFutureAppointments() != null && !doctor.getFutureAppointments().isEmpty()) {
            List<AppointmentFull> futures = appointmentRepository.findAllById(doctor.getFutureAppointments());
            List<String> idsMoved = new ArrayList<>();

            for (AppointmentFull appt : futures) {
                if (appt.getDateTime().isBefore(oneWeekFromNow)) {
                    AppointmentDoctor embedded = new AppointmentDoctor();
                    embedded.setAppointmentId(appt.getAppointmentId());
                    embedded.setPatientFirstName(appt.getPatientFirstName());
                    embedded.setPatientLastName(appt.getPatientLastName());
                    embedded.setPatientTelephone(appt.getPatientTelephone());
                    embedded.setDateTime(appt.getDateTime());
                    embedded.setStatus(appt.getStatus());

                    if (doctor.getBookedThisWeek() == null) doctor.setBookedThisWeek(new ArrayList<>());
                    doctor.getBookedThisWeek().add(embedded);
                    idsMoved.add(appt.getAppointmentId());
                    dirty = true;
                }
            }
            if (!idsMoved.isEmpty()) {
                doctor.getFutureAppointments().removeAll(idsMoved);
            }
        }

        // C. INFO UPDATE: Propagate profile changes to embedded structures
        if (doctor.getUpdated()) {
            String newPhone = doctor.getTelephone();
            Location newLocation = doctor.getLocation();

            if (neo4jBatch != null) {
                neo4jBatch.add(new DoctorGraphUpdateProjection(
                        doctor.getNpi(),
                        newPhone,
                        newLocation.getCity()
                ));
            }

            // Bulk update in master collection and patient documents
            appointmentRepository.updateDoctorInfoBulk(doctor.getId(), newLocation, newPhone);

            List<AppointmentSyncProjection> targets = appointmentRepository.findFuturePatientIdsByDoctorId(doctor.getId());
            for (AppointmentSyncProjection target : targets) {
                patientRepository.updateEmbeddedDoctorData(target.patientId(), target.appointmentId(), newLocation, newPhone);
            }

            doctor.setUpdated(false);
            dirty = true;
        }

        // PERSISTENCE AND CACHE EVICTION
        if (dirty) {
            if (doctor.getBookedThisWeek() != null) {
                doctor.getBookedThisWeek().sort(Comparator.comparing(AppointmentDoctor::getDateTime));
            }
            doctorRepository.save(doctor);

            // Purge Redis keys
            String cacheKey = DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId();
            redisTemplate.delete(cacheKey);

            Objects.requireNonNull(cacheManager.getCache("doctor_details")).evict(doctor.getEmail());
        }
    }

}