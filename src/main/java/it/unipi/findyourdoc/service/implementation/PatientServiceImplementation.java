package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.Location;
import it.unipi.findyourdoc.model.mongo.Patient;
import it.unipi.findyourdoc.repository.mongo.PatientRepository;
import it.unipi.findyourdoc.service.PatientService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PatientServiceImplementation implements PatientService {

    private final PatientRepository patientRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public PatientReadDTO registerPatient(PatientCreateDTO createDTO) {
        // Verifica unicità email (ereditata da UserDTO nel CreateDTO)
        if (patientRepository.existsByEmail(createDTO.getEmail())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email already in use");
        }

        Patient patient = new Patient();
        // Campi ereditati da User
        patient.setEmail(createDTO.getEmail());
        patient.setTelephone(createDTO.getTelephone());
        patient.setPassword(passwordEncoder.encode(createDTO.getPassword()));
        patient.setCreatedAt(LocalDateTime.now());

        // Campi specifici di Patient
        patient.setFirstName(createDTO.getFirstName());
        patient.setLastName(createDTO.getLastName());
        patient.setAge(createDTO.getAge());
        patient.setGender(createDTO.getGender());

        // Mapping manuale della Location
        if (createDTO.getLocation() != null) {
            patient.setLocation(mapLocationDtoToEntity(createDTO.getLocation()));
        }

        Patient saved = patientRepository.save(patient);
        return mapToReadDTO(saved);
    }

    @Override
    public PatientReadDTO updatePatient(String id, PatientUpdateDTO updateDTO) {
        // Nota: Qui usiamo l'ID come da tua interfaccia
        Patient patient = patientRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        // Aggiornamento campi base (User)
        if (updateDTO.getEmail() != null) {
            if (!updateDTO.getEmail().equals(patient.getEmail()) && patientRepository.existsByEmail(updateDTO.getEmail())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email already in use");
            }
            patient.setEmail(updateDTO.getEmail());
        }
        if (updateDTO.getPassword() != null && !updateDTO.getPassword().isBlank()) {
            patient.setPassword(passwordEncoder.encode(updateDTO.getPassword()));
        }
        if (updateDTO.getTelephone() != null) patient.setTelephone(updateDTO.getTelephone());

        // Aggiornamento campi specifici (Patient)
        if (updateDTO.getFirstName() != null) patient.setFirstName(updateDTO.getFirstName());
        if (updateDTO.getLastName() != null) patient.setLastName(updateDTO.getLastName());
        if (updateDTO.getAge() != null) patient.setAge(updateDTO.getAge());
        if (updateDTO.getGender() != null) patient.setGender(updateDTO.getGender());
        if (updateDTO.getLocation() != null) {
            patient.setLocation(mapLocationDtoToEntity(updateDTO.getLocation()));
        }

        return mapToReadDTO(patientRepository.save(patient));
    }

    @Override
    public PatientReadDTO getUserByEmail(String email) {
        Patient patient = patientRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found with email: " + email));
        return mapToReadDTO(patient);
    }

    // --- Metodi di Mapping Helper ---

    private PatientReadDTO mapToReadDTO(Patient p) {
        PatientReadDTO dto = new PatientReadDTO();
        dto.setId(String.valueOf(p.getId()));
        dto.setEmail(p.getEmail());
        dto.setTelephone(p.getTelephone());
        dto.setCreatedAt(p.getCreatedAt());

        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setAge(p.getAge());
        dto.setGender(p.getGender());

        if (p.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    p.getLocation().getAddress(),
                    p.getLocation().getCity(),
                    p.getLocation().getState(),
                    p.getLocation().getZipCode()
            ));
        }

        // Mapping delle liste (Brief oggetti)
        if (p.getBookedAppointments() != null) {
            // Qui dovresti avere un metodo di mapping per AppointmentBrief -> AppointmentBriefDTO
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
}