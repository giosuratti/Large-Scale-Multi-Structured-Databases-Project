package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;

/**
 * DTO for retrieving a professional's full rating history.
 * Aggregates all numeric feedback for statistical analysis or distribution charting.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing all the ratings received by a doctor")
public class DoctorRatingDTO {

    /** * Collection of integer scores (typically 1-5) submitted by patients. */
    @Schema(description = "List of all numeric ratings received")
    private ArrayList<Integer> rating;
}