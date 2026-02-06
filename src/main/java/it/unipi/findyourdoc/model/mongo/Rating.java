package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Rating {
    private String doctorNpi;
    private String doctorFirstName;
    private String doctorLastName;
    private int rating;
}
