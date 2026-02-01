package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;
import it.unipi.findyourdoc.repository.mongo.AdminRepository;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.repository.neo4j.DoctorGraphRepository;
import it.unipi.findyourdoc.service.AdminService;
import it.unipi.findyourdoc.utils.Mapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of the AdminService for FindYourDoc.
 * Handles business logic using email as the primary identifier.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImplementation implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final DoctorGraphRepository doctorGraphRepository;
    private static final Logger log = LoggerFactory.getLogger(AdminServiceImplementation.class);

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Override
    public AdminReadDTO createAdmin(AdminCreateDTO dto) {
        // Controllo unicità email
        if (adminRepository.existsByEmail(dto.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        Admin admin = new Admin();
        admin.setEmail(dto.getEmail());
        admin.setTelephone(dto.getTelephone());
        admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        admin.setCreatedAt(LocalDateTime.now());

        // Nota: Se l'ID è int, assicurati di avere una logica di generazione ID
        // o che MongoDB sia configurato per gestirlo.

        Admin savedAdmin = adminRepository.save(admin);
        return Mapper.mapToReadDTO(savedAdmin);
    }

    @Override
    public AdminReadDTO updateAdmin(String email, AdminUpdateDTO dto) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));

        // 1. Check Email Uniqueness if changing
        if (dto.getEmail() != null && !dto.getEmail().isBlank() && !dto.getEmail().equalsIgnoreCase(admin.getEmail())) {
            if (adminRepository.existsByEmail(dto.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            }
            admin.setEmail(dto.getEmail());
        }

        // 2. Update Telephone
        if (dto.getTelephone() != null) {
            admin.setTelephone(dto.getTelephone());
        }

        // 3. Update Password only if provided
        if (dto.getPassword() != null && !dto.getPassword().isBlank()) {
            admin.setPassword(passwordEncoder.encode(dto.getPassword()));
        }

        return Mapper.mapToReadDTO(adminRepository.save(admin));
    }

    @Override
    public AdminReadDTO getAdminByEmail(String email) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        return Mapper.mapToReadDTO(admin);
    }

    @Override
    public Page<AdminReadDTO> getAllAdmins(Pageable pageable) {
        return adminRepository.findAll(pageable).map(Mapper::mapToReadDTO);
    }

    @Override
    public List<AdminReadDTO> searchAdmins(String emailPrefix) {
        return adminRepository.findByEmailStartingWith(emailPrefix).stream()
                .map(Mapper::mapToReadDTO)
                .collect(Collectors.toList());
    }

    @Override
    public void deleteAdmin(String email) {
        if (!adminRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found");
        }
        adminRepository.deleteByEmail(email);
    }

    /**
     * Helper method to map Admin Entity to AdminReadDTO.
     */



    @Override
    public void deleteUser(String id) {
        boolean deleted = false;

        // 1. Attempt to find and delete if the ID belongs to a DOCTOR
        if (doctorRepository.existsById(id)) {
            doctorRepository.deleteById(id);
            deleted = true;
            // Note: ideally, you should also implement a cascade delete here
            // to remove appointments associated with this doctor.
        }

        // 2. If not found yet, check if the ID belongs to a PATIENT
        if (!deleted && patientRepository.existsById(id)) {
            patientRepository.deleteById(id);
            deleted = true;
        }

        // 3. If not found yet, check if the ID belongs to an ADMIN
        if (!deleted && adminRepository.existsById(id)) {
            adminRepository.deleteById(id);
            deleted = true;
        }

        // 4. If the ID was not found in any of the three collections
        if (!deleted) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User with ID " + id + " not found in any repository (Doctor, Patient, or Admin).");
        }

        // Log the action for security auditing
        // log.info("Admin deleted user with ID: {}", id);
    }


    // Fondamentale per pulire Redis manualmente

    @Override
    @Transactional
    public void changeUserPassword(String id, String newPassword) {
        // 1. Criptiamo SEMPRE la password prima di toccare qualsiasi cosa
        String encodedPassword = passwordEncoder.encode(newPassword);
        boolean userFound = false;

        // --- TENTATIVO 1: È UN DOTTORE? ---
        Optional<Doctor> doctorOpt = doctorRepository.findById(id);
        if (doctorOpt.isPresent()) {
            Doctor doctor = doctorOpt.get();
            doctor.setPassword(encodedPassword);
            doctorRepository.save(doctor);

            // REDIS: Se cambiamo la password, per sicurezza cancelliamo la sua cache appuntamenti.
            // Nota: La chiave deve corrispondere a quella usata in @Cacheable (es. "doctor_appointments::email")
            String cacheKey = "doctor_appointments::" + doctor.getEmail();
            redisTemplate.delete(cacheKey);

            log.info("Password aggiornata e cache invalidata per il dottore: {}", doctor.getEmail());
            userFound = true;
        }

        // --- TENTATIVO 2: È UN PAZIENTE? ---
        if (!userFound) {
            Optional<Patient> patientOpt = patientRepository.findById(id);
            if (patientOpt.isPresent()) {
                Patient patient = patientOpt.get();
                patient.setPassword(encodedPassword);
                patientRepository.save(patient);

                // REDIS: Cancelliamo la cache dei report o della storia clinica
                String cacheKey = "patient_reports::" + patient.getEmail();
                redisTemplate.delete(cacheKey);

                log.info("Password aggiornata e cache invalidata per il paziente: {}", patient.getEmail());
                userFound = true;
            }
        }

        // --- TENTATIVO 3: È UN ADMIN? ---
        if (!userFound) {
            Optional<Admin> adminOpt = adminRepository.findById(id);
            if (adminOpt.isPresent()) {
                Admin admin = adminOpt.get();
                admin.setPassword(encodedPassword);
                adminRepository.save(admin);

                // Gli admin solitamente non hanno cache pesanti, ma se ne avessero, vanno pulite qui.
                log.info("Password aggiornata per l'admin ID: {}", id);
                userFound = true;
            }
        }

        // Se non abbiamo trovato nessuno con quell'ID
        if (!userFound) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Nessun utente trovato con ID: " + id);
        }

        // OPZIONALE MA CONSIGLIATO CON REDIS (Blacklist Token):
        // Se gestissi una Blacklist dei token JWT su Redis, qui dovresti aggiungere
        // una logica per invalidare tutti i token attivi di questo utente.
        // Es: tokenBlacklistService.invalidateAllTokensForUser(id);
    }

    @Override
    @Transactional
    // REDIS: Tasto nucleare. Stiamo cambiando i criteri di ordinamento globali.
    // Dobbiamo invalidare TUTTE le ricerche salvate in cache, non solo una città specifica.
    @CacheEvict(value = {"specialist_search", "doctors_search_city"}, allEntries = true)
    public void syncDoctorRatings() {
        log.info("Inizio sincronizzazione rating da MongoDB a Neo4j...");

        // 1. Recuperiamo tutti i dottori da Mongo
        // Ottimizzazione: se hai tanti dati, usa una proiezione per prendere solo ID e Rating
        List<Doctor> mongoDoctors = doctorRepository.findAll();

        // 2. Prepariamo la lista per il Bulk Update di Neo4j
        List<Map<String, Object>> batchUpdates = new ArrayList<>();

        for (Doctor doc : mongoDoctors) {
            Map<String, Object> updateEntry = new HashMap<>();
            updateEntry.put("id", doc.getId()); // Assicurati che questo ID corrisponda all'NPI su Neo4j
            updateEntry.put("rating", doc.getAvgRating());
            batchUpdates.add(updateEntry);
        }

        // 3. Eseguiamo l'aggiornamento su Neo4j
        if (!batchUpdates.isEmpty()) {
            // Eseguiamo a blocchi di 500 per non intasare la memoria se hai 1 milione di dottori
            int batchSize = 500;
            for (int i = 0; i < batchUpdates.size(); i += batchSize) {
                int end = Math.min(i + batchSize, batchUpdates.size());
                List<Map<String, Object>> subList = batchUpdates.subList(i, end);

                doctorGraphRepository.bulkUpdateRatings(subList);
                log.info("Aggiornati {} dottori su Neo4j...", end);
            }
        }

        log.info("Sincronizzazione completata con successo.");
    }



    @Override
    @Transactional
    // REDIS: Invalidiamo la cache poiché stiamo cambiando i dati visualizzati nei profili/agenda dei dottori
    @CacheEvict(value = {"doctor_details", "doctors_search_city"}, allEntries = true)
    public void refreshWeeklySlots() {
        log.info("Inizio refresh agenda settimanale dei dottori (AppointmentDoctor)...");

        // 1. RESET: Svuotiamo il campo 'bookedThisWeek' per TUTTI i dottori.
        // Questo rimuove appuntamenti passati o cancellati.
        Query updateAllQuery = new Query();
        Update updateReset = new Update().set("bookedThisWeek", new ArrayList<>());
        mongoTemplate.updateMulti(updateAllQuery, updateReset, Doctor.class);

        // 2. DEFINIZIONE FINESTRA TEMPORALE (Prossimi 7 giorni)
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime nextWeek = now.plusDays(7).withHour(23).withMinute(59);

        // 3. QUERY: Prendiamo gli AppointmentFull attivi dalla collection "appointments"
        Criteria criteria = Criteria.where("dateTime").gte(now).lte(nextWeek)
                // Includiamo solo quelli confermati o programmati (escludiamo cancellati)
                .and("status").in(AppointmentStatus.CONFIRMED, AppointmentStatus.SCHEDULED, AppointmentStatus.PENDING);

        List<AppointmentFull> upcomingAppointments = mongoTemplate.find(new Query(criteria), AppointmentFull.class);

        // 4. MAPPING & RAGGRUPPAMENTO
        // Convertiamo AppointmentFull -> AppointmentDoctor e raggruppiamo per DoctorId
        Map<String, List<AppointmentDoctor>> appsByDoctor = upcomingAppointments.stream()
                .collect(Collectors.groupingBy(
                        AppointmentFull::getDoctorId, // Chiave della mappa
                        Collectors.mapping(Mapper::mapToAppointmentDoctor, Collectors.toList()) // Valore: Lista convertita
                ));

        // 5. BULK UPDATE SU MONGO
        if (!appsByDoctor.isEmpty()) {
            BulkOperations bulkOps = mongoTemplate.bulkOps(BulkOperations.BulkMode.UNORDERED, Doctor.class);

            for (Map.Entry<String, List<AppointmentDoctor>> entry : appsByDoctor.entrySet()) {
                String doctorId = entry.getKey();
                List<AppointmentDoctor> weeklyAppointments = entry.getValue();

                Query query = new Query(Criteria.where("_id").is(doctorId));
                Update update = new Update().set("bookedThisWeek", weeklyAppointments);

                bulkOps.updateOne(query, update);
            }

            bulkOps.execute();
            log.info("Agenda aggiornata per {} dottori.", appsByDoctor.size());
        } else {
            log.info("Nessun appuntamento trovato per la prossima settimana.");
        }
    }

    @Override
    public DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO) {
        // 1. Verifica unicità email
        if (doctorRepository.existsByEmail(createDTO.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        Doctor doctor = new Doctor();
        // Campi base User
        doctor.setEmail(createDTO.getEmail());
        doctor.setTelephone(createDTO.getTelephone());
        doctor.setPassword(passwordEncoder.encode(createDTO.getPassword()));
        doctor.setCreatedAt(LocalDateTime.now());

        // Campi specifici Doctor
        doctor.setFirstName(createDTO.getFirstName());
        doctor.setLastName(createDTO.getLastName());
        doctor.setSpecialties(createDTO.getSpecializations());
        doctor.setGender(createDTO.getGender());

        // Mapping Location
        if (createDTO.getLocation() != null) {
            doctor.setLocation(Mapper.mapLocationDtoToEntity(createDTO.getLocation()));
        }

        Doctor savedDoctor = doctorRepository.save(doctor);
        return Mapper.mapToReadDTO(savedDoctor);
    }

}