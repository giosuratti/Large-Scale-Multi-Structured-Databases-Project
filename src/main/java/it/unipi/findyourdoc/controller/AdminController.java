package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import it.unipi.findyourdoc.dto.mongo.*;
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
 * Controller for Administrative operations.
 * Handles user management, password overrides, and cross-database synchronization.
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

    /**
     * Registers a new administrator.
     */
    @Operation(summary = "Create a new Administrator")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Admin created successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminReadDTO.class))),
            @ApiResponse(responseCode = "400", description = "Email already in use")
    })
    @PostMapping
    public ResponseEntity<AdminReadDTO> createAdmin(@RequestBody AdminCreateDTO createDTO) {
        return ResponseEntity.ok(adminService.createAdmin(createDTO));
    }

    /**
     * Updates profile data for the calling administrator.
     * Identity is extracted from the JWT to prevent ID spoofing.
     */
    @Operation(summary = "Update current Administrator profile")
    @PutMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminReadDTO> updateAdmin(
            HttpServletRequest request,
            @RequestBody AdminUpdateDTO updateDTO) {

        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(adminService.updateAdmin(email, updateDTO));
    }

    /**
     * Retrieves the authenticated administrator's profile.
     */
    @Operation(summary = "Get current Admin profile")
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminReadDTO> getAdminMe(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(adminService.getAdminByEmail(email));
    }

    /**
     * Paginated list of all administrators.
     */
    @Operation(summary = "List all Administrators")
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminReadDTO>> getAllAdmins(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllAdmins(pageable));
    }

    /**
     * Prefix-based search for administrators.
     */
    @Operation(summary = "Search Administrators by Email Prefix")
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminReadDTO>> searchAdmins(@RequestParam String email) {
        return ResponseEntity.ok(adminService.searchAdmins(email));
    }

    /**
     * Deletes a user account based on email.
     */
    @Operation(summary = "Delete a user by email")
    @DeleteMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdmin(
            @Parameter(description = "The email of the admin to delete") @RequestBody String email) {
        adminService.deleteUser(email);
        return ResponseEntity.noContent().build();
    }

    /**
     * Administrative password override for any account type.
     */
    @Operation(summary = "Force password change for any user")
    @PatchMapping("/accounts/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> forcePasswordChange(
            @Parameter(description = "The unique ID of the account") @PathVariable String id,
            @RequestBody @Valid PasswordChangeDTO passwordDTO) {

        adminService.changeUserPassword(id, passwordDTO.getNewPassword());
        return ResponseEntity.ok("Password successfully updated for user ID: " + id);
    }

    /**
     * Triggers synchronization of doctor ratings and invalidates relevant caches.
     */
    @Operation(summary = "Sync doctor ratings and invalidate cache")
    @PostMapping("/sync-ratings")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> syncRatings() {
        long start = System.currentTimeMillis();
        adminService.syncDoctorRatings();
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Synchronization completed in " + duration + " ms. Cache invalidated.");
    }

    /**
     * Onboards a new doctor into the system.
     */
    @Operation(summary = "Register a new Doctor")
    @PostMapping("/registerdoctor")
    public ResponseEntity<DoctorReadDTO> registerDoctor(@RequestBody DoctorCreateDTO createDTO) {
        return ResponseEntity.ok(adminService.registerDoctor(createDTO));
    }

    /**
     * Batch process to synchronize all doctor data across platforms.
     * Intended for scheduled maintenance or heavy updates.
     */
    @PostMapping("/sync/all-doctors")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Synchronize ALL doctors (Batch)")
    public ResponseEntity<String> syncAllDoctors() {
        long start = System.currentTimeMillis();
        adminService.syncAllDoctors();
        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Batch synchronization completed in " + duration + " ms.");
    }
}