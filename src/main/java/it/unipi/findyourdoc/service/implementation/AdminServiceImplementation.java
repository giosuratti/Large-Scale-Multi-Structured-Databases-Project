package it.unipi.findyourdoc.service.implementation;

import it.unipi.findyourdoc.dto.mongo.AdminCreateDTO;
import it.unipi.findyourdoc.dto.mongo.AdminReadDTO;
import it.unipi.findyourdoc.dto.mongo.AdminUpdateDTO;
import it.unipi.findyourdoc.model.mongo.Admin;
import it.unipi.findyourdoc.repository.mongo.AdminRepository;
import it.unipi.findyourdoc.service.AdminService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/**
 * Implementation of the AdminService for FindYourDoc.
 * Handles business logic using email as the primary identifier.
 */
@Service
@RequiredArgsConstructor
public class AdminServiceImplementation implements AdminService {

    private final AdminRepository adminRepository;
    private final PasswordEncoder passwordEncoder;

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
        return mapToReadDTO(savedAdmin);
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

        return mapToReadDTO(adminRepository.save(admin));
    }

    @Override
    public AdminReadDTO getAdminByEmail(String email) {
        Admin admin = adminRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Admin not found"));
        return mapToReadDTO(admin);
    }

    @Override
    public Page<AdminReadDTO> getAllAdmins(Pageable pageable) {
        return adminRepository.findAll(pageable).map(this::mapToReadDTO);
    }

    @Override
    public List<AdminReadDTO> searchAdmins(String emailPrefix) {
        return adminRepository.findByEmailStartingWith(emailPrefix).stream()
                .map(this::mapToReadDTO)
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
    private AdminReadDTO mapToReadDTO(Admin admin) {
        AdminReadDTO dto = new AdminReadDTO();
        // Convertiamo l'ID int in String per il DTO
        dto.setId(String.valueOf(admin.getId()));
        dto.setEmail(admin.getEmail());
        dto.setTelephone(admin.getTelephone());
        dto.setCreatedAt(admin.getCreatedAt());
        return dto;
    }
}