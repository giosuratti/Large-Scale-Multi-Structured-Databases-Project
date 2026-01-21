package it.unipi.findyourdoc.model.mongo;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends User{


    private boolean gender;
    private int age;

    private String firstName;
    private String lastName;

    private String location;

    // private ArrayList<>

}
