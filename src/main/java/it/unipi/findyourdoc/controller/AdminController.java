package it.unipi.findyourdoc.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
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
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = AdminReadDTO.class))),
            @ApiResponse(responseCode = "400", description = "Email already in use")
    })
    @PostMapping
    public ResponseEntity<AdminReadDTO> createAdmin(@RequestBody AdminCreateDTO createDTO) {
        return ResponseEntity.ok(adminService.createAdmin(createDTO));
    }

    @Operation(summary = "Update current Administrator profile",
            description = "Updates the profile of the administrator currently authenticated via JWT.")
    @ApiResponses(value = {

            @ApiResponse(
                    responseCode = "200",
                    description = "Profile updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AdminReadDTO.class)
                    )
            ),

            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid request data"
            ),

            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Invalid or missing JWT"
            ),

            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            ),

            @ApiResponse(
                    responseCode = "404",
                    description = "Administrator not found"
            )
    })
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

    @Operation(
            summary = "Get current Admin profile",
            description = "Retrieves the profile data of the administrator currently authenticated via JWT token."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Current Admin profile retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = AdminReadDTO.class))
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - JWT token is missing, expired or invalid"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - The authenticated user does not have the ADMIN role"
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Not Found - Admin profile not found for the provided token"
            )
    })
    @GetMapping("/me")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminReadDTO> getAdminMe(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token);

        return ResponseEntity.ok(adminService.getAdminByEmail(email));
    }

    @Operation(
            summary = "List all Administrators",
            description = "Retrieves a paginated list of all administrators. You can specify the page number, size, and sorting criteria. Restricted to ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Paginated list retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            // Nota: SpringDoc gestisce automaticamente il wrapping di Page,
                            // ma specifichiamo l'entità contenuta per chiarezza.
                            schema = @Schema(implementation = AdminReadDTO.class)
                    )
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Valid JWT token required"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Admin role required"
            )
    })
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Page<AdminReadDTO>> getAllAdmins(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(adminService.getAllAdmins(pageable));
    }

    @Operation(
            summary = "Search Administrators by Email Prefix",
            description = "Returns a list of administrators whose email starts with the provided string. Restricted to users with ADMIN role."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Search completed successfully",
                    content = @Content(
                            mediaType = "application/json",
                            array = @ArraySchema(schema = @Schema(implementation = AdminReadDTO.class))
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid email prefix provided"
            ),
            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized - Authentication token is missing or invalid"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Access restricted to Administrators"
            )
    })
    @GetMapping("/search")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AdminReadDTO>> searchAdmins(@RequestParam String email) {
        return ResponseEntity.ok(adminService.searchAdmins(email));
    }


    @Operation(summary = "Delete a user by email")
    @ApiResponses({

            @ApiResponse(
                    responseCode = "204",
                    description = "User deleted successfully"
            ),

            @ApiResponse(
                    responseCode = "401",
                    description = "Unauthorized"
            ),

            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden"
            ),

            @ApiResponse(
                    responseCode = "404",
                    description = "User not found"
            )
    })
    @DeleteMapping("/")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdmin(
            @Parameter(description = "The email of the admin to delete") @RequestBody String email) {
        adminService.deleteUser(email);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Force password change for any user",
            description = "Allows an Admin to change the password of any Admin, Doctor, or Patient using their unique ID.")
    @ApiResponse(
            responseCode = "200",
            description = "Password updated",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(type = "string")
            )
    )
    @PatchMapping("/accounts/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<String> forcePasswordChange(
            @Parameter(description = "The unique ID of the account") @PathVariable String id,
            @RequestBody @Valid PasswordChangeDTO passwordDTO) {

        adminService.changeUserPassword(id, passwordDTO.getNewPassword());
        return ResponseEntity.ok("Password successfully updated for user ID: " + id);
    }

    @PostMapping("/sync-ratings")
    @ApiResponse(
            responseCode = "200",
            description = "Ratings synchronized successfully",
            content = @Content(
                    mediaType = "text/plain",
                    schema = @Schema(type = "string")
            )
    )

    @PreAuthorize("hasRole('ADMIN')") // Assicurati che solo l'admin possa farlo
    public ResponseEntity<String> syncRatings() {

        long start = System.currentTimeMillis();

        adminService.syncDoctorRatings();

        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Sincronizzazione completata in " + duration + " ms. Cache invalidata.");
    }

    @PostMapping("/refresh-weekly-slots")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Weekly slots refreshed successfully",
                    content = @Content(
                            mediaType = "text/plain",
                            schema = @Schema(type = "string")
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Unauthorized"),
            @ApiResponse(responseCode = "403", description = "Forbidden")
    })
    @PreAuthorize("hasRole('ADMIN')") // Solo l'admin può lanciarlo
    public ResponseEntity<String> refreshWeeklySlots() {
        long start = System.currentTimeMillis();

        adminService.refreshWeeklySlots();

        long duration = System.currentTimeMillis() - start;
        return ResponseEntity.ok("Slot settimanali aggiornati con successo in " + duration + " ms.");
    }

    @Operation(summary = "Register a new Doctor", description = "Allows an administrator to register a new doctor into the system.")
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Doctor registered successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = DoctorReadDTO.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid input data or email already in use"
            ),
            @ApiResponse(
                    responseCode = "403",
                    description = "Forbidden - Only administrators can perform this action"
            )
    })
    @PostMapping("/registerdoctor")
    public ResponseEntity<DoctorReadDTO> registerDoctor(@RequestBody DoctorCreateDTO createDTO) {
        return ResponseEntity.ok(adminService.registerDoctor(createDTO));
    }


}