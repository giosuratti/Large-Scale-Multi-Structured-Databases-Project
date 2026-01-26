package it.unipi.findyourdoc.utils;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;

public class Mapper {

    public static AppointmentPatientDTO mapToPatientDTO(AppointmentFull entity) {
        AppointmentPatientDTO dto = new AppointmentPatientDTO();

        // 1. Dati tecnici
        dto.setId(entity.getId());
        dto.setDateTime(entity.getDateTime()); // O getAppointmentDateTime() a seconda del tuo modello
        dto.setStatus(entity.getStatus()); // Es. "BOOKED"

        // 2. Chi incontrerò? (Dati Dottore snapshot)
        dto.setDoctorFirstName(entity.getDoctorFirstName());
        dto.setDoctorLastName(entity.getDoctorLastName());

        // Gestione sicura della lista specializzazioni
        if (entity.getSpecialties() != null && !entity.getSpecialties().isEmpty()) {
            dto.setDoctorSpecialties(entity.getSpecialties());
        }

        // 3. Dove devo andare? (Location snapshot)
        if (entity.getLocation() != null) {
            LocationDTO locDto = new LocationDTO();
            locDto.setCity(entity.getLocation().getCity());
            locDto.setAddress(entity.getLocation().getAddress());
            // Aggiungi latitudine/longitudine se il frontend deve mostrare la mappa
            dto.setLocation(locDto);
        }

        return dto;
    }

    public static SymptomReportBriefDTO mapToSymptomBriefDTO(SymptomReportBrief entity) {
        SymptomReportBriefDTO dto = new SymptomReportBriefDTO();
        dto.setContext(entity.getContext());
        dto.setSymptoms(entity.getSymptoms());
        dto.setPossibleDiagnosies(entity.getPossibleDiagnosies());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public static SymptomReportBriefDTO mapToBriefDTO(SymptomReport report) {
        SymptomReportBriefDTO dto = new SymptomReportBriefDTO();
        dto.setCreatedAt(report.getCreatedAt());
        // Se non ci sono diagnosi stimate, restituiamo "In attesa di valutazione" o simile
        dto.setPossibleDiagnosies(report.getPossibleDiagnosies());
        return dto;
    }

    public static RatingDTO mapToRatingDTO(Rating rating) {
        RatingDTO dto = new RatingDTO();
        dto.setDoctorId(rating.getDoctorId());
        dto.setRating(rating.getRating());
        dto.setDoctorName(rating.getDoctorFirstName());       // Usa il setter corretto di Lombok
        dto.setDoctorSurname(rating.getDoctorLastName()); // Usa il setter corretto di Lombok
        return dto;
    }

    public static PatientReadDTO mapToReadDTO(Patient p) {
        PatientReadDTO dto = new PatientReadDTO();
        dto.setId(String.valueOf(p.getId()));
        dto.setEmail(p.getEmail());
        dto.setTelephone(p.getTelephone());
        dto.setCreatedAt(p.getCreatedAt());

        dto.setFirstName(p.getFirstName());
        dto.setLastName(p.getLastName());
        dto.setAge(p.getAge());
        dto.setGender(p.getGender());

        if (p.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    p.getLocation().getAddress(),
                    p.getLocation().getCity(),
                    p.getLocation().getState(),
                    p.getLocation().getZipCode()
            ));
        }

        // Mapping delle liste (Brief oggetti)
        if (p.getBookedAppointments() != null) {
            // Qui dovresti avere un metodo di mapping per AppointmentBrief -> AppointmentBriefDTO
        }

        return dto;
    }

    public static Location mapLocationDtoToEntity(LocationDTO dto) {
        Location loc = new Location();
        loc.setAddress(dto.getAddress());
        loc.setCity(dto.getCity());
        loc.setState(dto.getState());
        loc.setZipCode(dto.getZipCode());
        return loc;
    }

}
