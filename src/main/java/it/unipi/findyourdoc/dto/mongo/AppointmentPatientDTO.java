package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;

/**
 * DTO per la visualizzazione dell'appuntamento dal punto di vista del Paziente.
 * Utilizzato tipicamente nella lista "I miei appuntamenti".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO sintetico dell'appuntamento per la visualizzazione lato paziente.")
public class AppointmentPatientDTO {

    @Schema(description = "ID univoco dell'appuntamento", example = "1024")
    private String id;

    @Schema(description = "Data e ora dell'appuntamento", example = "2026-05-15T10:30:00")
    private LocalDateTime appointmentDateTime;

    @Schema(description = "Stato dell'appuntamento (es. PENDING, CONFIRMED, CANCELLED)", example = "CONFIRMED")
    private String status;

    // --- Informazioni sul Medico ---
    @Schema(description = "ID del medico (utile per link al profilo)", example = "501")
    private String doctorId;

    @Schema(description = "Nome del medico", example = "Giulia")
    private String doctorFirstName;

    @Schema(description = "Cognome del medico", example = "Bianchi")
    private String doctorLastName;

    @Schema(description = "Specializzazioni del medico", example = "['Cardiologia']")
    private ArrayList<String> doctorSpecialties;

    // --- Luogo della visita ---
    @Schema(description = "Indirizzo dello studio medico")
    private LocationDTO location;
}