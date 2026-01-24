package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data point showing the link between a doctor's reputation and their workload.")
public class RatingCorrelationDTO {

    @Schema(description = "Unique ID of the doctor", example = "doc_77")
    private String doctorId;

    @Schema(description = "Full name of the doctor", example = "Dr. Gregory House")
    private String doctorFullName;

    @Schema(description = "Average rating score (1-5)", example = "4.8")
    private float averageRating;

    @Schema(description = "Total number of booked appointments", example = "1250")
    private Long appointmentCount;
}