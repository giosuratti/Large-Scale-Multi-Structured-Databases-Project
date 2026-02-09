package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.model.mongo.Location;
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
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminServiceImplementation implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorGraphRepository doctorGraphRepository;
    private final AppointmentRepository appointmentRepository;

    // CORREZIONE 1: Coerenza con gli altri service per le chiavi
    private final StringRedisTemplate redisTemplate;

    // CORREZIONE 2: Serve per pulire le cache standard (rating, appuntamenti)
    private final DoctorService doctorService;

    private final MongoTemplate mongoTemplate; // Spostato qui per @RequiredArgsConstructor

    private static final Logger log = LoggerFactory.getLogger(AdminServiceImplementation.class);
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    // ... [METODI CREATE, UPDATE, GET, SEARCH SONO OK E IDENTICI AL TUO CODICE] ...

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

    // ... [FINE METODI STANDARD] ...

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

            // REDIS: Invalida slot (manuale) + cache standard (via service)
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
            doctorService.invalidateDoctorCache(doctor.getEmail()); // CORREZIONE IMPORTANTE

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

                        // Ripristina slot al dottore
                        restoreSlotToDoctor(appt);
                    }
                }

                patientRepository.deleteById(patient.getId());

                // REDIS: Invalida cache specifiche del paziente (che non ha un service con metodo publico di invalidazione)
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

    // Helper per pulizia codice
    private boolean isActive(AppointmentFull appt) {
        return appt.getStatus() != AppointmentStatus.CANCELLED && appt.getStatus() != AppointmentStatus.COMPLETED;
    }

    // Helper per ripristino slot con logica Redis coerente
    private void restoreSlotToDoctor(AppointmentFull appt) {
        doctorRepository.findById(appt.getDoctorId()).ifPresent(doc -> {
            if (doc.getAvailableSlots() == null) doc.setAvailableSlots(new ArrayList<>());

            if (!doc.getAvailableSlots().contains(appt.getDateTime())) {
                doc.getAvailableSlots().add(appt.getDateTime());
                Collections.sort(doc.getAvailableSlots());
                doctorRepository.save(doc);

                // Invalida cache slot dottore
                redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doc.getId());
            }
        });
    }

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

            // CORREZIONE: Uso redisTemplate con stringhe corrette
            redisTemplate.delete("doctor_appointments::" + doctor.getEmail());
            userFound = true;
        }

        if (!userFound) {
            Optional<Patient> patientOpt = patientRepository.findById(id);
            if (patientOpt.isPresent()) {
                Patient patient = patientOpt.get();
                patient.setPassword(encodedPassword);
                patientRepository.save(patient);

                // CORREZIONE: Uso redisTemplate con stringhe corrette
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
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nessun utente trovato con ID: " + id);
        }
    }

    @Override
    @CacheEvict(value = {"specialist_search", "doctors_search_city"}, allEntries = true)
    public void syncDoctorRatings() {
        log.info("Inizio sincronizzazione rating da MongoDB a Neo4j...");
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
                log.error("Errore batch Neo4j: {}", e.getMessage());
            }
        });
        log.info("Sync completata.");
    }

    public static <T> List<List<T>> partitionList(List<T> list, int pageSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += pageSize) {
            partitions.add(list.subList(i, Math.min(i + pageSize, list.size())));
        }
        return partitions;
    }

    @Override
    @Transactional
    @CacheEvict(value = {"doctor_details", "doctors_search_city"}, allEntries = true)
    public void refreshWeeklySlots() {
        // [CODICE IDENTICO AL TUO, VA BENE]
        log.info("Inizio refresh agenda settimanale...");
        mongoTemplate.updateMulti(new Query(), new Update().set("bookedThisWeek", new ArrayList<>()), Doctor.class);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextWeek = now.plusDays(7).withHour(23).withMinute(59);

        Criteria criteria = Criteria.where("dateTime").gte(now).lte(nextWeek)
                .and("status").in(AppointmentStatus.CONFIRMED, AppointmentStatus.SCHEDULED, AppointmentStatus.PENDING);
        List<AppointmentFull> upcomingAppointments = mongoTemplate.find(new Query(criteria), AppointmentFull.class);

        Map<String, List<AppointmentDoctor>> appsByDoctor = upcomingAppointments.stream()
                .collect(Collectors.groupingBy(AppointmentFull::getDoctorId, Collectors.mapping(Mapper::mapToAppointmentDoctor, Collectors.toList())));

        if (!appsByDoctor.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Doctor.class);
            for (Map.Entry<String, List<AppointmentDoctor>> entry : appsByDoctor.entrySet()) {
                bulkOps.updateOne(new Query(Criteria.where("_id").is(entry.getKey())), new Update().set("bookedThisWeek", entry.getValue()));
            }
            bulkOps.execute();
        }
    }

    @Override
    public DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO) {
        // [CODICE IDENTICO AL TUO, VA BENE]
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
        if (createDTO.getLocation() != null) doctor.setLocation(Mapper.mapLocationDtoToEntity(createDTO.getLocation()));

        doctor.setAvailableSlots(new ArrayList<>());
        doctor.setBookedThisWeek(new ArrayList<>());
        doctor.setRatings(new ArrayList<>());
        doctor.setAvgRating(0.0);
        doctor.setRatingCount(0);
        doctor.setTotalAppointments(0);

        Doctor savedDoctor = doctorRepository.save(doctor);

        try {
            doctorGraphRepository.createDoctorAndRelations(
                    savedDoctor.getId(),
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

    @Override
    public List<String> syncAllChangedDoctors() {

        // 1. Recupera la lista leggera (Solo NPI e dati da cambiare)
        List<DoctorUpdateProjection> pendingSyncs = doctorRepository.findAllPendingSyncs();

        List<String> successfullySyncedNpis = new ArrayList<>();

        if (pendingSyncs.isEmpty()) return successfullySyncedNpis;

        log.info("Found {} doctors pending sync.", pendingSyncs.size());

        for (DoctorUpdateProjection docInfo : pendingSyncs) {
            try {
                performSingleDoctorSync(docInfo);
                successfullySyncedNpis.add(docInfo.npi());
            } catch (Exception e) {
                log.error("Failed to sync doctor NPI: {}", docInfo.npi(), e);
            }
        }

        return successfullySyncedNpis;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    protected void performSingleDoctorSync(DoctorUpdateProjection docInfo) {
        String npi = docInfo.npi();
        String newPhone = docInfo.telephone();
        Location newLocation = docInfo.location();

        if (newPhone == null || newLocation == null) {
            throw new IllegalStateException("Sync data incomplete for NPI: " + npi);
        }

        // --- FASE 1: Aggiornamento NEO4J ---
        doctorGraphRepository.updateDoctorDataByNpi(npi, newPhone, newLocation.getCity());

        // --- FASE 2: Aggiornamento MASTER COLLECTION (Bulk Update) ---
        // Invece di scaricare 1000 oggetti e salvarli 1000 volte, facciamo un solo update.
        appointmentRepository.updateFutureAppointmentsDataBulk(npi, newLocation, newPhone);

        // --- FASE 3: Aggiornamento EMBEDDED PAZIENTI ---
        // Qui ci servono gli ID per sapere QUALI pazienti toccare.
        // Scarichiamo solo la proiezione (pochi KB).
        List<AppointmentSyncProjection> futureAppointments = appointmentRepository.findFutureSummariesByDoctorNpi(npi);

        for (AppointmentSyncProjection appt : futureAppointments) {
            // Usiamo i dati della proiezione per mirare al paziente giusto
            patientRepository.updateEmbeddedDoctorData(
                    appt.patientId(),
                    appt.appointmentId(),
                    newLocation,
                    newPhone
            );
        }

        // --- FASE 4: RESET FLAG ---
        doctorRepository.markAsSyncedByNpi(npi);

        log.info("Successfully synced doctor NPI: {} (Updated {} patient records)", npi, futureAppointments.size());
    }

}