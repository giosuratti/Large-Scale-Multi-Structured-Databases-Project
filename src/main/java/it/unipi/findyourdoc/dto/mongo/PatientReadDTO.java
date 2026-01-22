package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true) // Importante perché estende UserDTO
@Schema(description = "DTO for reading patient profile information")
public class PatientReadDTO extends UserDTO {

    @Schema(description = "The user's first name.", example = "Mario")
    private String firstName;

    @Schema(description = "The user's last name.", example = "Rossi")
    private String lastName;

    @Schema(description = "The user's age.", example = "25")
    private Integer age;

    @Schema(description = "The user's gender.", example = "Male")
    private String gender;

    @Schema(description = "The patient's geographic location")
    private LocationDTO location;

    @Schema(description = "List of brief summaries of booked appointments")
    private List<AppointmentBriefDTO> bookedAppointments;

    @Schema(description = "List of ratings given by the patient")
    private List<RatingDTO> ratings;

    @Schema(description = "List of recent symptom reports for quick access")
    private List<SymptomReportBriefDTO> recentSymptomReports;
}