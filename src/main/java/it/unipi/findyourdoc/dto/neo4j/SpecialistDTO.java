package it.unipi.findyourdoc.dto.neo4j;

import io.swagger.v3.oas.annotations.media.Schema;
import it.unipi.findyourdoc.dto.mongo.LocationDTO;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO che rappresenta uno specialista trovato in base alla diagnosi e alla posizione.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Oggetto contenente le informazioni dettagliate dello specialista trovato vicino all'utente.")
public class SpecialistDTO {

    @Schema(description = "ID univoco del medico nel database", example = "65af12345")
    private String doctorId;

    @Schema(description = "Nome del medico", example = "Giulia")
    private String firstName;

    @Schema(description = "Cognome del medico", example = "Bianchi")
    private String lastName;

    @Schema(description = "Valutazione media ricevuta dai pazienti (da 1 a 5)", example = "4.8")
    private float rating;

    @Schema(description = "Dati relativi alla posizione geografica e indirizzo dello studio medico")
    private String city;
}
