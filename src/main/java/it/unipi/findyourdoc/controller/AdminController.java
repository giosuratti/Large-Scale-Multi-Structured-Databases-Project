package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.AdminCreateDTO;
import it.unipi.findyourdoc.dto.mongo.AdminReadDTO;
import it.unipi.findyourdoc.dto.mongo.AdminUpdateDTO;
import it.unipi.findyourdoc.dto.mongo.PasswordChangeDTO;
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for managing Administrator resources in FindYourDoc.
 * Identification is handled via email throughout all endpoints.
 */
@RestController
@RequestMapping("/api/admins")
@RequiredArgsConstructor
@Tag(
        name = "Admin Management",
        description = "APIs for managing admin accounts using email as the primary resource identifier.")
public class AdminController {

    private final AdminService adminService;
    private final JwtTokenProvider jwtTokenProvider;

    @Operation(summary = "Create a new Administrator")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Admin created successfully",
                    content = @Content(schema = @Schema(implementation = AdminReadDTO.class))),
            @ApiResponse(responseCode = "400", description = "Email already in use")
    })
    @PostMapping
    public ResponseEntity<AdminReadDTO> createAdmin(@RequestBody AdminCreateDTO createDTO) {
        return ResponseEntity.ok(adminService.createAdmin(createDTO));
    }

    @Operation(summary = "Update current Administrator profile",
            description = "Updates the profile of the administrator currently authenticated via JWT.")
    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminReadDTO> updateAdmin(
            HttpServletRequest request,
            @RequestBody AdminUpdateDTO updateDTO) {

        // Estraiamo l'identità dal token in modo sicuro
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(adminService.updateAdmin(email, updateDTO));
    }

    @Operation(summary = "Get current Admin profile",
            description = "Retrieves the profile data of the administrator currently authenticated.")
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')") // Solo un admin può vedere il proprio profilo admin
    public ResponseEntity<AdminReadDTO> getAdminMe(HttpServletRequest request) {

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(adminService.getAdminByEmail(email));
    }

    @Operation(summary = "List all Administrators")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminReadDTO>> getAllAdmins(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllAdmins(pageable));
    }

    @Operation(summary = "Search Administrators by Email Prefix")
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminReadDTO>> searchAdmins(@RequestParam String email) {
        return ResponseEntity.ok(adminService.searchAdmins(email));
    }


    @Operation(summary = "Delete a user by Id")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdmin(
            @Parameter(description = "The email of the admin to delete") @PathVariable String id) {
        adminService.deleteUser(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Force password change for any user",
            description = "Allows an Admin to change the password of any Admin, Doctor, or Patient using their unique ID.")
    @PatchMapping("/accounts/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> forcePasswordChange(
            @Parameter(description = "The unique ID of the account") @PathVariable String id,
            @RequestBody @Valid PasswordChangeDTO passwordDTO) {

        adminService.changeUserPassword(id, passwordDTO.getNewPassword());
        return ResponseEntity.ok("Password successfully updated for user ID: " + id);
    }

    @PostMapping("/sync-ratings")
    @PreAuthorize("hasRole('ADMIN')") // Assicurati che solo l'admin possa farlo
    public ResponseEntity<String> syncRatings() {

        long start = System.currentTimeMillis();

        adminService.syncDoctorRatings();

        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Sincronizzazione completata in " + duration + " ms. Cache invalidata.");
    }

    @PostMapping("/refresh-weekly-slots")
    @PreAuthorize("hasRole('ADMIN')") // Solo l'admin può lanciarlo
    public ResponseEntity<String> refreshWeeklySlots() {
        long start = System.currentTimeMillis();

        adminService.refreshWeeklySlots();

        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Slot settimanali aggiornati con successo in " + duration + " ms.");
    }


}