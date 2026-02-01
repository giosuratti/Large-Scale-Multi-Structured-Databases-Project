package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AdminService {
    AdminReadDTO createAdmin(AdminCreateDTO createDTO);
    List<AdminReadDTO> searchAdmins(String emailPrefix);
    Page<AdminReadDTO> getAllAdmins(Pageable pageable);
    AdminReadDTO updateAdmin(String email, AdminUpdateDTO updateDTO);
    AdminReadDTO getAdminByEmail(String email);
    void deleteUser(String id);
    void changeUserPassword(String id, String password);

    void syncDoctorRatings();

    void refreshWeeklySlots();

    DoctorReadDTO registerDoctor(DoctorCreateDTO createDTO);

    void deleteAdmin(String email);
}
