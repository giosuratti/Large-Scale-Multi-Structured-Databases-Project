package it.unipi.findyourdoc.dto.mongo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing all the ratings received by a doctor")
public class DoctorRatingDTO {
    @Schema(description = "All the ratings received")
    private ArrayList<Integer> rating;
}
