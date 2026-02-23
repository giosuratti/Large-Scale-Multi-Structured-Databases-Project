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

import static it.unipi.findyourdoc.utils.Mapper.mapLocationDTOToEntity;
import static it.unipi.findyourdoc.utils.Mapper.mapToReadDTO;

/**
 * Service implementation for Patient-related operations.
 * Orchestrates MongoDB for persistence, Neo4j for diagnostics, and Redis for concurrency.
 */
@Service
@RequiredArgsConstructor
public class PatientServiceImplementation implements PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;
    private final DoctorRepository doctorRepository;
    private final AppointmentRepository appointmentRepository;
    private final SymptomReportRepository symptomReportRepository;
    private final DiseaseRepository diseaseRepository;
    private final DoctorGraphRepository doctorGraphRepository;
    private final DoctorService doctorService;

    /** * Redis template for manual key operations (e.g., slot eviction). */
    private final StringRedisTemplate redisTemplate;

    /** * Service for managing distributed locks on appointment slots. */
    @Autowired
    private RedisSlotService redisSlotService;

    private static final Logger log = LoggerFactory.getLogger(PatientServiceImplementation.class);
    private static final String DOCTOR_SLOTS_CACHE_PREFIX = "doctor:slots:";

    /**
     * Books a medical appointment using Redis distributed locks to prevent race conditions.
     */
    @Override
    @Transactional
    @CacheEvict(value = "patient_appointments", key = "#patientEmail")
    public AppointmentPatientDTO bookAppointmentByEmail(String patientEmail, AppointmentBookDTO appointmentBookDTO) {

        // 1. Retrieve Patient
        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 2. Retrieve Doctor
        Doctor doctor = doctorRepository.findById(appointmentBookDTO.getDoctorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // Normalize date immediately
        LocalDateTime localDateTime = appointmentBookDTO.getDateTime().toLocalDateTime().truncatedTo(ChronoUnit.MINUTES);

        // 3. --- REDIS LOCK ---
        boolean locked = redisSlotService.acquireSlotLock(doctor.getId(), localDateTime, patient.getId());

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This slot is temporarily locked by another user. Please try again in a few minutes.");
        }

        try {
            // 4. Double Check MongoDB
            boolean alreadyBooked = appointmentRepository.existsByDoctorIdAndDateTime(doctor.getId(), localDateTime);

            if (alreadyBooked) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot already booked (Database).");
            }

            // 5. Create MASTER Appointment
            AppointmentFull appointment = new AppointmentFull();
            appointment.setDoctorId(doctor.getId());
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

            // 6. Save MASTER to MongoDB
            AppointmentFull savedAppointment = appointmentRepository.save(appointment);

            // 7. PATIENT UPDATE (Embedded)
            if (patient.getBookedAppointments() == null) {
                patient.setBookedAppointments(new ArrayList<>());
            }

            AppointmentPatient embeddedPatientAppt = new AppointmentPatient();
            embeddedPatientAppt.setAppointmentId(savedAppointment.getAppointmentId());
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

            // 8. DOCTOR UPDATE (Bifurcated logic + Slot removal)
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime oneWeekFromNow = now.plusDays(7);

            // CASE A: Near appointment (<= 7 days) -> Embedded in BookedThisWeek
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
            // CASE B: Distant appointment (> 7 days) -> ID only in FutureAppointments
            else if (localDateTime.isAfter(oneWeekFromNow)) {
                if (doctor.getFutureAppointments() == null) doctor.setFutureAppointments(new ArrayList<>());
                doctor.getFutureAppointments().add(savedAppointment.getAppointmentId());
            }

            /// B. SLOT REMOVAL (Removes only ONE)
            LocalDateTime target = localDateTime.truncatedTo(ChronoUnit.MINUTES);
            long offsetSeconds = ZoneId.systemDefault().getRules().getOffset(target).getTotalSeconds();
            LocalDateTime targetMinusOffset = target.minusSeconds(offsetSeconds);
            LocalDateTime targetPlusOffset = target.plusSeconds(offsetSeconds);

            boolean removed = false;
            Iterator<LocalDateTime> iterator = doctor.getAvailableSlots().iterator();

            while (iterator.hasNext()) {
                LocalDateTime slotRaw = iterator.next();
                LocalDateTime slot = slotRaw.truncatedTo(ChronoUnit.MINUTES);

                boolean isExactMatch = slot.isEqual(target);
                boolean isZoneMatch = slot.isEqual(targetMinusOffset) || slot.isEqual(targetPlusOffset);

                if (isExactMatch || isZoneMatch) {
                    iterator.remove();
                    removed = true;
                    log.info("Slot removed: {} (Target was: {})", slotRaw, target);
                    break;
                }
            }

            if (!removed) {
                log.error("CRITICAL: Slot not found. Target: {}, DB List: {}", target, doctor.getAvailableSlots());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Slot is no longer available.");
            }

            doctorRepository.save(doctor);

            // 9. Cache Invalidation
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + doctor.getId());
            redisSlotService.releaseSlotLock(doctor.getId(), localDateTime);
            doctorService.invalidateDoctorCache(doctor.getEmail());

            return Mapper.mapToPatientDTO(savedAppointment, doctor.getFirstName(), doctor.getLastName(), doctor.getNpi());

        } catch (Exception e) {
            redisSlotService.releaseSlotLock(doctor.getId(), localDateTime);
            throw e;
        }
    }

    /**
     * Cancels an appointment, restores availability with timezone fixes, and cleans embedded references.
     */
    @Override
    @Transactional
    public void cancelAppointment(String appointmentId) {

        // 1. Retrieve Appointment (Lightweight summary)
        AppointmentFullSummary appointment = appointmentRepository.findSummaryById(appointmentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));

        // 2. State Check
        AppointmentStatus currentStatus = appointment.status();
        if (currentStatus == AppointmentStatus.CANCELLED || currentStatus == AppointmentStatus.COMPLETED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot cancel: current status is " + currentStatus);
        }

        // 3. --- REDIS LOCK ---
        boolean locked = redisSlotService.acquireSlotLock(
                appointment.doctorId(),
                appointment.dateTime(),
                "SYSTEM_CANCEL_" + appointmentId
        );

        if (!locked) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Operation in progress on this slot. Try again in a moment.");
        }

        try {
            // 4. UPDATE MASTER COLLECTION
            appointmentRepository.updateStatus(appointmentId, AppointmentStatus.CANCELLED);

            // 5. UPDATE DOCTOR (Slot restoration + list cleanup)
            Doctor doctor = doctorRepository.findById(appointment.doctorId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

            // --- A. RESTORE AVAILABLE SLOT (Timezone Fix) ---
            if (doctor.getAvailableSlots() == null) doctor.setAvailableSlots(new ArrayList<>());

            LocalDateTime rawDate = appointment.dateTime().truncatedTo(ChronoUnit.MINUTES);
            LocalDateTime restoredSlot = rawDate.atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime();

            boolean slotExists = doctor.getAvailableSlots().stream()
                    .anyMatch(s -> s.truncatedTo(ChronoUnit.MINUTES).isEqual(restoredSlot));

            if (!slotExists) {
                doctor.getAvailableSlots().add(restoredSlot);
                Collections.sort(doctor.getAvailableSlots());
                log.info("Slot restored: {}", restoredSlot);
            } else {
                log.warn("Slot already present, skipping restoration: {}", restoredSlot);
            }

            // --- B. REMOVE FROM BOOKED THIS WEEK ---
            if (doctor.getBookedThisWeek() != null) {
                boolean removed = doctor.getBookedThisWeek().removeIf(a ->
                        a.getAppointmentId() != null && a.getAppointmentId().equals(appointmentId)
                );
                if (removed) log.info("Appointment removed from bookedThisWeek");
            }

            // --- C. REMOVE FROM FUTURE APPOINTMENTS ---
            if (doctor.getFutureAppointments() != null) {
                doctor.getFutureAppointments().remove(appointmentId);
            }

            doctor.setUpdated(true);
            doctorRepository.save(doctor);

            // 6. UPDATE PATIENT
            updatePatientEmbeddedList(appointment);

            // 7. CACHE INVALIDATION
            redisTemplate.delete(DOCTOR_SLOTS_CACHE_PREFIX + appointment.doctorId());
            doctorService.invalidateDoctorCache(doctor.getEmail());

        } catch (Exception e) {
            redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
            throw e;
        }

        redisSlotService.releaseSlotLock(appointment.doctorId(), appointment.dateTime());
    }

    /** * Helper to update the embedded appointment status within the Patient document. */
    private void updatePatientEmbeddedList(AppointmentFullSummary appointment) {
        patientRepository.findById(appointment.patientId()).ifPresent(patient -> {
            if (patient.getBookedAppointments() != null) {
                boolean updated = false;
                for (var embeddedAppt : patient.getBookedAppointments()) {
                    if (embeddedAppt.getAppointmentId().equals(appointment.appointmentId())) {
                        embeddedAppt.setStatus(AppointmentStatus.CANCELLED);
                        updated = true;
                        break;
                    }
                }
                if (updated) {
                    patientRepository.save(patient);
                }
            }
        });
    }

    @Override
    @Cacheable(value = "patient_appointments", key = "#email")
    public Page<AppointmentPatientDTO> getAppointmentsByEmail(String email, Pageable pageable) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        if (patient.getBookedAppointments() == null || patient.getBookedAppointments().isEmpty()) {
            return Page.empty(pageable);
        }

        // 1. DTO Transformation and Sorting
        List<AppointmentPatientDTO> allItems = patient.getBookedAppointments().stream()
                .filter(a -> a != null && a.getDateTime() != null)
                .sorted(Comparator.comparing(AppointmentPatient::getDateTime).reversed())
                .map(Mapper::mapToPatientDTO)
                .collect(Collectors.toList());

        // 2. Create Page
        return createPageFromList(allItems, pageable);
    }

    /**
     * Creates a symptom report, performs Neo4j inference, and updates embedded history.
     */
    @Override
    @Transactional
    public SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO) {

        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // 1. Neo4j Diagnosis
        ArrayList<String> suggestedDiagnoses = new ArrayList<>();
        if (createDTO.getSymptoms() != null && !createDTO.getSymptoms().isEmpty()) {
            suggestedDiagnoses = diseaseRepository.findPossibleDiagnoses(createDTO.getSymptoms());
            log.info("Neo4j suggested diagnoses: {}", suggestedDiagnoses);
        }

        // 2. Save Standalone Report
        SymptomReport report = new SymptomReport();
        report.setPatientAge(patient.getAge());
        report.setPatientGender(patient.getGender());
        report.setPatientLocation(patient.getLocation());
        report.setSymptoms(createDTO.getSymptoms());
        report.setContext(createDTO.getContext());
        report.setCreatedAt(LocalDateTime.now());
        report.setPossibleDiagnosies(suggestedDiagnoses);

        symptomReportRepository.save(report);

        // 3. Update Patient Embedded List
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
                .sorted(Comparator.comparing(SymptomReportBrief::getCreatedAt).reversed())
                .map(Mapper::mapToBriefDTO)
                .collect(Collectors.toList());

        return createPageFromList(allItems, pageable);
    }

    /**
     * Records a new rating and updates doctor aggregate reputation.
     */
    @Override
    @Transactional
    public RatingDTO addRatingByEmail(String patientEmail, RatingDTO ratingDTO) {

        Patient patient = patientRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        Doctor doctor = doctorRepository.findByNpi(ratingDTO.getDoctorNpi())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        // 1. Duplicate Check
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
        // 2. Create Rating
        Rating newRating = new Rating();
        newRating.setDoctorNpi(doctor.getNpi());
        newRating.setDoctorFirstName(doctor.getFirstName());
        newRating.setDoctorLastName(doctor.getLastName());
        newRating.setRating(ratingDTO.getRating());

        // 3. Save to Patient
        patient.getRatings().add(newRating);
        patientRepository.save(patient);

        // 4. Update Doctor metrics
        double currentTotalScore = doctor.getAvgRating() * doctor.getRatingCount();
        double newTotalScore = currentTotalScore + ratingDTO.getRating();
        int newTotalRatings = doctor.getRatingCount() + 1;
        double newAverage = newTotalScore / newTotalRatings;
        ArrayList<Integer> ratings = doctor.getRatings();
        ratings.add(ratingDTO.getRating());

        double roundedAverage = (Math.round(newAverage * 10.0) / 10.0);

        doctor.setAvgRating(roundedAverage);
        doctor.setRatingCount(newTotalRatings);
        doctor.setRatings(ratings);
        doctorRepository.save(doctor);

        // 5. Invalidate doctor cache
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

        List<RatingDTO> allItems = patient.getRatings().stream()
                .filter(Objects::nonNull)
                .map(Mapper::mapToRatingDTO)
                .collect(Collectors.toList());

        return createPageFromList(allItems, pageable);
    }

    @Override
    @Cacheable(value = "specialist_search", key = "{#city, #diagnosis}")
    public List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis) {
        log.info("Neo4j Query - City: {}, Diagnosis: {}", city, diagnosis);
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
            patient.setLocation(mapLocationDTOToEntity(createDTO.getLocation()));
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
            patient.setLocation(mapLocationDTOToEntity(updateDTO.getLocation()));
        }

        return mapToReadDTO(patientRepository.save(patient));
    }

    @Override
    @Cacheable(value = "patient_profile", key = "#email")
    public PatientReadDTO getPatientByEmail(String email) {
        log.info("Cache Miss: Recover patient profile from DB for {}", email);
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient with email: " + email + " not found"));
        return Mapper.mapToReadDTO(patient);
    }

    /**
     * Retrieves doctor data and filters availability for future slots.
     */
    @Override
    public DoctorReadSlotsDTO getDoctorByNpi(String npi) {
        Doctor doctor = doctorRepository.findByNpi(npi)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        if (doctor.getAvailableSlots() != null) {
            List<LocalDateTime> sortedFutureSlots = doctor.getAvailableSlots().stream()
                    .filter(slot -> slot.isAfter(LocalDateTime.now()))
                    .sorted()
                    .toList();

            doctor.setAvailableSlots(new ArrayList<>(sortedFutureSlots));
        }

        return Mapper.mapToReadSlotsDTO(doctor);
    }

    /** * Paginates a list in-memory. */
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