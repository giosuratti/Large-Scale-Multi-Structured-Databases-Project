package it.unipi.findyourdoc.service.implementation;

import com.sun.tools.javac.Main;
import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.Doctor;
import it.unipi.findyourdoc.model.mongo.Location;
import it.unipi.findyourdoc.repository.mongo.DoctorRepository;
import it.unipi.findyourdoc.service.DoctorService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorServiceImplementation implements DoctorService {

    private final DoctorRepository doctorRepository;
    private final PasswordEncoder passwordEncoder;
    private static final Logger log = LoggerFactory.getLogger(DoctorServiceImplementation.class);

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
        doctor.setSpecializations(createDTO.getSpecializations());
        doctor.setGender(createDTO.getGender());

        // Mapping Location
        if (createDTO.getLocation() != null) {
            doctor.setLocation(mapLocationDtoToEntity(createDTO.getLocation()));
        }

        Doctor savedDoctor = doctorRepository.save(doctor);
        return mapToReadDTO(savedDoctor);
    }

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
        if (updateDTO.getSpecializations() != null) doctor.setSpecializations(updateDTO.getSpecializations());
        if (updateDTO.getGender() != null) doctor.setGender(updateDTO.getGender());

        if (updateDTO.getLocation() != null) {
            doctor.setLocation(mapLocationDtoToEntity(updateDTO.getLocation()));
        }

        return mapToReadDTO(doctorRepository.save(doctor));
    }

    @Override
    public DoctorReadDTO getDoctorByEmail(String email) {
        Doctor doctor = doctorRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        return mapToReadDTO(doctor);
    }

    // --- Metodi di Mapping ---

    private DoctorReadDTO mapToReadDTO(Doctor d) {
        DoctorReadDTO dto = new DoctorReadDTO();
        dto.setId(String.valueOf(d.getId()));
        dto.setEmail(d.getEmail());
        dto.setTelephone(d.getTelephone());
        dto.setCreatedAt(d.getCreatedAt());

        dto.setFirstName(d.getFirstName());
        dto.setLastName(d.getLastName());
        dto.setSpecializations(d.getSpecializations());
        dto.setGender(d.getGender());

        if (d.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    d.getLocation().getAddress(),
                    d.getLocation().getCity(),
                    d.getLocation().getState(),
                    d.getLocation().getZipCode()
            ));
        }
        return dto;
    }

    private Location mapLocationDtoToEntity(LocationDTO dto) {
        Location loc = new Location();
        loc.setAddress(dto.getAddress());
        loc.setCity(dto.getCity());
        loc.setState(dto.getState());
        loc.setZipCode(dto.getZipCode());
        return loc;
    }

    public List<AppointmentDTO> getAppointmentsByEmail(String email){

    }

    public List<RatingDTO> getRatingsByDoctorEmail(String email){

    }

    public void addAvailabilitySlots(String email, List<SlotDTO> slots) {

    }

    public void removeAvailabilitySlot(String email, SlotDTO slotDTO) {

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
        log.info("Cache invalidata per il dottore: {}. Al prossimo accesso i dati verranno ricaricati da MongoDB.", email);
    }
}