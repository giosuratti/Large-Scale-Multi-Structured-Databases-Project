package it.unipi.findyourdoc.model.mongo;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "DTO representing an Admin, containing base user information.")
public class Location {
    private String address;
    private String state;
    private String city;
    private String zipCode;
}
