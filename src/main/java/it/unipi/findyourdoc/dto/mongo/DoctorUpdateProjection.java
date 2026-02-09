package it.unipi.findyourdoc.dto.mongo;
import it.unipi.findyourdoc.model.mongo.Location;

public record DoctorUpdateProjection(String npi, String telephone, Location location) {
}
