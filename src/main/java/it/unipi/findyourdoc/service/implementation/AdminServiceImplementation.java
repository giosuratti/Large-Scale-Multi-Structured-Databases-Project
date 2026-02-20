package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
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
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
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
import java.util.stream.Stream;

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

    private final MongoTemplate mongoTemplate;

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

    /** * Synchronizes aggregate ratings from MongoDB to Neo4j using batching. */
    @Override
    @CacheEvict(value = {"specialist_search", "doctors_search_city"}, allEntries = true)
    public void syncDoctorRatings() {
        log.info("Starting rating synchronization from MongoDB to Neo4j...");
        //List<DoctorProjection> mongoDoctors = doctorRepository.findAllBy();
        // Questo approccio non carica tutto il DB in RAM
        // 1. Recuperiamo il totale per calcolare la percentuale
        long totalDoctors = doctorRepository.count();
        log.info("Starting sync for {} doctors. Batch size: 500.", totalDoctors);

        try (Stream<DoctorProjection> doctorStream = doctorRepository.streamAllBy()) {
            List<Map<String, Object>> currentBatch = new ArrayList<>();
            int processedCount = 0;

            // Usiamo un iteratore o un riferimento esterno per il conteggio
            Iterable<DoctorProjection> iterable = doctorStream::iterator;
            for (DoctorProjection doc : iterable) {
                currentBatch.add(Mapper.mapToMap(doc));
                processedCount++;

                if (currentBatch.size() == 500) {
                    doctorGraphRepository.bulkUpdateRatings(currentBatch);

                    // Stampa il progresso a ogni batch completato
                    double percentage = (processedCount * 100.0) / totalDoctors;
                    log.info("Progress: {}/{} doctors processed ({})",
                            processedCount, totalDoctors, String.format("%.2f%%", percentage));

                    currentBatch.clear();
                }
            }

            // Ultimo batch (se rimangono elementi < 500)
            if (!currentBatch.isEmpty()) {
                doctorGraphRepository.bulkUpdateRatings(currentBatch);
                log.info("Final batch processed. Total doctors synchronized: {}", processedCount);
            }
        } catch (Exception e) {
            log.error("Sync failed during processing: {}", e.getMessage());
        }

        log.info("Sync task finished successfully.");

        /*log.info("Preparing updates from MongoDB data...");
        int totalDoctors = mongoDoctors.size();
        List<Map<String, Object>> allUpdates = new ArrayList<>(totalDoctors);

        for (int i = 0; i < totalDoctors; i++) {
            var doc = mongoDoctors.get(i);

            Map<String, Object> entry = Mapper.mapToMap(doc);
            allUpdates.add(entry);

            // Stampa ogni 1000 elementi per non intasare i log ma avere un feedback
            if ((i + 1) % 1000 == 0 || (i + 1) == totalDoctors) {
                log.info("Mapping progress: {}/{} ({}%)",
                        (i + 1),
                        totalDoctors,
                        ((i + 1) * 100) / totalDoctors);
            }
        }
        log.info("Mapping completed. Prepared {} updates.", allUpdates.size());

        List<List<Map<String, Object>>> batches = partitionList(allUpdates, 500);
        int totalBatches = batches.size();
        log.info("Starting sequential sync: {} batches to process.", totalBatches);

        for (int i = 0; i < totalBatches; i++) {
            List<Map<String, Object>> batch = batches.get(i);
            int currentBatchNumber = i + 1;

            try {
                log.info("Processing batch {} of {} ({}%)",
                        currentBatchNumber,
                        totalBatches,
                        (currentBatchNumber * 100) / totalBatches);

                doctorGraphRepository.bulkUpdateRatings(batch);

            } catch (Exception e) {
                log.error("Error during batch {}: {}", currentBatchNumber, e.getMessage());
            }
        }

        log.info("Sync completed successfully.");*/
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
    /**
     * Asynchronously synchronizes doctor profiles and appointments across MongoDB and Neo4j.
     * Uses chunking to optimize memory and network I/O.
     */
    @Override
    @Async
    public void syncAllDoctors() {
        long totalDbDoctors = doctorRepository.count();
        log.info("Starting massive sync. Total doctors in DB: {}. Scanning for active ones...", totalDbDoctors);

        long startTime = System.currentTimeMillis();
        int processedCount = 0;
        List<DoctorBulkUpdateDTO> doctorChunk = new ArrayList<>(500);

        try (Stream<DoctorBulkUpdateDTO> doctorStream = doctorRepository.streamDoctorsForSync()) {
            for (DoctorBulkUpdateDTO doc : (Iterable<DoctorBulkUpdateDTO>) doctorStream::iterator) {
                doctorChunk.add(doc);
                processedCount++;

                // Process in chunks of 500
                if (doctorChunk.size() == 500) {
                    processDoctorChunk(doctorChunk);
                    log.info("Sync in progress: {} active doctors processed...", processedCount);
                    doctorChunk.clear();
                }
            }

            // Process remaining elements
            if (!doctorChunk.isEmpty()) {
                processDoctorChunk(doctorChunk);
                log.info("⏳ Sync in progress: {} active doctors processed...", processedCount);
            }

            long durationMs = System.currentTimeMillis() - startTime;
            log.info("Massive sync completed in {} ms! Processed {} active doctors out of {}.",
                    durationMs, processedCount, totalDbDoctors);

        } catch (Exception e) {
            log.error("Critical failure during massive sync: {}", e.getMessage(), e);
        }
    }

    /**
     * Processes a single chunk of doctors, performing in-memory data shifts and bulk database updates.
     *
     * @param chunk List of DoctorBulkUpdateDTO to process.
     */
    private void processDoctorChunk(List<DoctorBulkUpdateDTO> chunk) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime oneWeekFromNow = now.plusDays(7);

        // 1. PRE-FETCHING

        // Fetch future appointments
        Set<String> allFutureApptIds = chunk.stream()
                .filter(d -> d.getFutureAppointments() != null)
                .flatMap(d -> d.getFutureAppointments().stream())
                .collect(Collectors.toSet());

        Map<String, AppointmentFull> appointmentsMap = new HashMap<>();
        if (!allFutureApptIds.isEmpty()) {
            appointmentRepository.findAllById(allFutureApptIds)
                    .forEach(appt -> appointmentsMap.put(appt.getAppointmentId(), appt));
        }

        // Fetch future patients for updated doctors
        Set<String> updatedDoctorIds = chunk.stream()
                .filter(d -> Boolean.TRUE.equals(d.getUpdated()))
                .map(DoctorBulkUpdateDTO::getId)
                .collect(Collectors.toSet());

        Map<String, List<AppointmentSyncProjection>> patientsToUpdateMap = new HashMap<>();
        if (!updatedDoctorIds.isEmpty()) {
            appointmentRepository.findFuturePatientIdsByDoctorIdIn(updatedDoctorIds)
                    .forEach(target -> patientsToUpdateMap
                            .computeIfAbsent(target.doctorId(), k -> new ArrayList<>())
                            .add(target)
                    );
        }

        // Setup bulk operations
        BulkOperations doctorBulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Doctor.class);
        BulkOperations appointmentBulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, AppointmentFull.class);
        BulkOperations patientBulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Patient.class);

        List<Map<String, Object>> neo4jBatch = new ArrayList<>();
        List<String> redisKeysToDelete = new ArrayList<>();

        int mongoDoctorUpdatesCount = 0;
        int extraUpdatesCount = 0;

        // 2. IN-MEMORY PROCESSING
        for (DoctorBulkUpdateDTO doctor : chunk) {
            boolean dirty = false;

            // Clean up past or corrupted appointments
            if (doctor.getBookedThisWeek() != null) {
                boolean removed = doctor.getBookedThisWeek().removeIf(appt ->
                        appt == null || appt.getDateTime() == null || appt.getDateTime().isBefore(now)
                );
                if (removed) dirty = true;
            }

            // Shift future appointments to current week
            if (doctor.getFutureAppointments() != null && !doctor.getFutureAppointments().isEmpty()) {
                List<String> idsMoved = new ArrayList<>();
                for (String apptId : doctor.getFutureAppointments()) {
                    AppointmentFull appt = appointmentsMap.get(apptId);
                    if (appt != null && appt.getDateTime() != null && appt.getDateTime().isBefore(oneWeekFromNow)) {
                        AppointmentDoctor embedded = new AppointmentDoctor();
                        embedded.setAppointmentId(appt.getAppointmentId());
                        embedded.setPatientFirstName(appt.getPatientFirstName());
                        embedded.setPatientLastName(appt.getPatientLastName());
                        embedded.setPatientTelephone(appt.getPatientTelephone());
                        embedded.setDateTime(appt.getDateTime());
                        embedded.setStatus(appt.getStatus());

                        if (doctor.getBookedThisWeek() == null) doctor.setBookedThisWeek(new ArrayList<>());
                        doctor.getBookedThisWeek().add(embedded);
                        idsMoved.add(apptId);
                        dirty = true;
                    }
                }
                if (!idsMoved.isEmpty()) {
                    doctor.getFutureAppointments().removeAll(idsMoved);
                }
            }

            // Propagate profile updates
            if (Boolean.TRUE.equals(doctor.getUpdated())) {
                Map<String, Object> neo4jData = new HashMap<>();
                neo4jData.put("npi", doctor.getNpi());
                neo4jData.put("telephone", doctor.getTelephone() != null ? doctor.getTelephone() : "");
                neo4jData.put("city", (doctor.getLocation() != null) ? doctor.getLocation().getCity() : "");
                neo4jBatch.add(neo4jData);

                // Queue appointment updates
                org.springframework.data.mongodb.core.query.Update apptUpdate = new org.springframework.data.mongodb.core.query.Update()
                        .set("location", doctor.getLocation())
                        .set("doctorTelephone", doctor.getTelephone());

                appointmentBulkOps.updateMulti(
                        org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where("doctorId").is(doctor.getId())
                        ), apptUpdate
                );

                // Queue patient embedded document updates
                List<AppointmentSyncProjection> targets = patientsToUpdateMap.getOrDefault(doctor.getId(), new ArrayList<>());
                for (AppointmentSyncProjection target : targets) {
                    org.springframework.data.mongodb.core.query.Update patientUpdate = new org.springframework.data.mongodb.core.query.Update()
                            .set("bookedAppointments.$.location", doctor.getLocation())
                            .set("bookedAppointments.$.doctorTelephone", doctor.getTelephone());

                    patientBulkOps.updateOne(
                            org.springframework.data.mongodb.core.query.Query.query(
                                    org.springframework.data.mongodb.core.query.Criteria.where("_id").is(target.patientId())
                                            .and("bookedAppointments.appointmentId").is(target.appointmentId())
                            ), patientUpdate
                    );
                }

                extraUpdatesCount++;
                dirty = true;
            }

            // 3. PREPARE SAVE OPERATIONS
            if (dirty) {
                // Safely sort appointments
                if (doctor.getBookedThisWeek() != null) {
                    doctor.getBookedThisWeek().sort(Comparator.comparing(
                            AppointmentDoctor::getDateTime, Comparator.nullsLast(Comparator.naturalOrder())
                    ));
                }

                org.springframework.data.mongodb.core.query.Update partialUpdate = new org.springframework.data.mongodb.core.query.Update()
                        .set("bookedThisWeek", doctor.getBookedThisWeek())
                        .set("futureAppointments", doctor.getFutureAppointments())
                        .set("updated", false);

                doctorBulkOps.updateOne(
                        org.springframework.data.mongodb.core.query.Query.query(
                                org.springframework.data.mongodb.core.query.Criteria.where("_id").is(doctor.getId())
                        ), partialUpdate
                );

                mongoDoctorUpdatesCount++;

                // Accumulate Redis keys for eviction
                redisKeysToDelete.add(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
                if (doctor.getEmail() != null) {
                    redisKeysToDelete.add("doctor_details::" + doctor.getEmail());
                }
            }
        }

        // 4. EXECUTE BULK I/O OPERATIONS
        if (mongoDoctorUpdatesCount > 0) {
            doctorBulkOps.execute();
        }
        if (extraUpdatesCount > 0) {
            appointmentBulkOps.execute();
            patientBulkOps.execute();
        }
        if (!neo4jBatch.isEmpty()) {
            doctorGraphRepository.bulkUpdateDoctors(neo4jBatch);
        }
        if (!redisKeysToDelete.isEmpty()) {
            redisTemplate.delete(redisKeysToDelete);
        }
    }
}