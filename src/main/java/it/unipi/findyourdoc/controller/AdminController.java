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
import it.unipi.findyourdoc.security.JwtTokenProvider;
import it.unipi.findyourdoc.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

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

    @Operation(summary = "Update an Administrator by Email")
    @PutMapping("/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<AdminReadDTO> updateAdmin(
            @Parameter(description = "The email of the admin to update") @PathVariable String email,
            @RequestBody AdminUpdateDTO updateDTO) {
        return ResponseEntity.ok(adminService.updateAdmin(email, updateDTO));
    }

    @Operation(summary = "Get Admin by Email")
    @GetMapping("/{email}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AdminReadDTO> getAdminByEmail(
            @Parameter(description = "The email of the admin to retrieve") @PathVariable String email) {
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

    @Operation(summary = "Get Current Admin Info from Token")
    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> getCurrentUser(HttpServletRequest request) {
        String token = jwtTokenProvider.resolveToken(request);
        String email = jwtTokenProvider.getEmailFromToken(token); // In questo caso il "username" è la mail

        Map<String, String> response = new HashMap<>();
        response.put("email", email);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Delete an Administrator by Email")
    @DeleteMapping("/{email}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteAdmin(
            @Parameter(description = "The email of the admin to delete") @PathVariable String email) {
        adminService.deleteAdmin(email);
        return ResponseEntity.noContent().build();
    }
}