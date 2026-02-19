package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.ArrayList;

/**
 * Extended DTO for medical profile retrieval.
 * Includes complete professional details and the full history of numeric ratings.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "DTO for reading doctor profile and professional information")
public class DoctorReadDTO extends DoctorReadDTOBase {

    /** * Collection of all integer feedback scores submitted by patients. */
    @Schema(description = "Historical list of numeric ratings")
    private ArrayList<Integer> ratings;
}