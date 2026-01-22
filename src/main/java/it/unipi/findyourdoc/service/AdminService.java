package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.AdminCreateDTO;
import it.unipi.findyourdoc.dto.mongo.AdminReadDTO;
import it.unipi.findyourdoc.dto.mongo.AdminUpdateDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface AdminService {
    AdminReadDTO createAdmin(AdminCreateDTO createDTO);
    void deleteAdmin(String email);
    List<AdminReadDTO> searchAdmins(String emailPrefix);
    Page<AdminReadDTO> getAllAdmins(Pageable pageable);
    AdminReadDTO updateAdmin(String email, AdminUpdateDTO updateDTO);
    AdminReadDTO getAdminByEmail(String email);
}
