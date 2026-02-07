package it.unipi.findyourdoc.utils;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;

import java.util.ArrayList;

public class Mapper {

    public static AppointmentPatientDTO mapToPatientDTO(AppointmentFull entity) {
        AppointmentPatientDTO dto = new AppointmentPatientDTO();

        // 1. Dati tecnici
        dto.setId(entity.getAppointmentId());
        dto.setDateTime(entity.getDateTime()); // O getAppointmentDateTime() a seconda del tuo modello
        dto.setStatus(entity.getStatus()); // Es. "BOOKED"

        // 2. Chi incontrerò? (Dati Dottore snapshot)

        // Gestione sicura della lista specializzazioni
        if (entity.getSpecialties() != null && !entity.getSpecialties().isEmpty()) {
            dto.setDoctorSpecialties(entity.getSpecialties());
        }

        // 3. Dove devo andare? (Location snapshot)
        if (entity.getLocation() != null) {
            LocationDTO locDto = new LocationDTO();
            locDto.setCity(entity.getLocation().getCity());
            locDto.setAddress(entity.getLocation().getAddress());
            locDto.setState(entity.getLocation().getState());
            locDto.setZipCode(entity.getLocation().getZipCode());
            dto.setLocation(locDto);
        }


        return dto;
    }

    // Nel file Mapper.java

    public static AppointmentPatientDTO mapToPatientDTO(AppointmentPatient entity) {
        if (entity == null) return null;

        AppointmentPatientDTO dto = new AppointmentPatientDTO();// O source.getId() se hai corretto
        dto.setDateTime(entity.getDateTime());
        dto.setId(entity.getAppointmentId());
        if (entity.getLocation() != null) {
            LocationDTO locDto = new LocationDTO();
            locDto.setCity(entity.getLocation().getCity());
            locDto.setAddress(entity.getLocation().getAddress());
            locDto.setState(entity.getLocation().getState());
            locDto.setZipCode(entity.getLocation().getZipCode());
            dto.setLocation(locDto);
        } // Se hai un mapper per location
        dto.setStatus(entity.getStatus());

        // Campi specifici presenti nell'embedded del paziente
        dto.setDoctorFirstName(entity.getDoctorFirstName());
        dto.setDoctorLastName(entity.getDoctorLastName());
        dto.setDoctorId(entity.getDoctorId());

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

    public static SymptomReportBriefDTO mapToBriefDTO(SymptomReportBrief report) {
        if (report == null) return null;

        SymptomReportBriefDTO dto = new SymptomReportBriefDTO();

        // Copia i campi base
        dto.setCreatedAt(report.getCreatedAt());
        dto.setPossibleDiagnosies(report.getPossibleDiagnosies());

        // IMPORTANTE: Copia anche contesto e sintomi (altrimenti arrivano vuoti al frontend)
        dto.setContext(report.getContext());
        dto.setSymptoms(report.getSymptoms());

        return dto;
    }

    public static RatingDTO mapToPatientRatingDTO(Rating rating) {
        RatingDTO dto = new RatingDTO();
        dto.setDoctorNpi(rating.getDoctorNpi());
        dto.setRating(rating.getRating());
        dto.setDoctorFirstName(rating.getDoctorFirstName());       // Usa il setter corretto di Lombok
        dto.setDoctorLastName(rating.getDoctorLastName()); // Usa il setter corretto di Lombok
        return dto;
    }

    public static PatientReadDTO mapToReadDTO(Patient p) {
        PatientReadDTO dto = new PatientReadDTO();
        dto.setId(String.valueOf(p.getId()));
        dto.setEmail(p.getEmail());
        dto.setTelephone(p.getTelephone());

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

    public static DoctorReadDTO mapToReadDTO(Doctor d) {
        DoctorReadDTO dto = new DoctorReadDTO();
        dto.setId(String.valueOf(d.getId()));
        dto.setEmail(d.getEmail());
        dto.setTelephone(d.getTelephone());

        dto.setFirstName(d.getFirstName());
        dto.setLastName(d.getLastName());
        dto.setSpecializations(d.getSpecialties());
        dto.setGender(d.getGender());
        dto.setNpi(d.getNpi());
        dto.setRatings(d.getRatings());

        if (d.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    d.getLocation().getAddress(),
                    d.getLocation().getCity(),
                    d.getLocation().getState(),
                    d.getLocation().getZipCode()
            ));
        }
        return dto;
    }
    public static DoctorReadSlotsDTO mapToReadSlotsDTO(Doctor d) {
        DoctorReadSlotsDTO dto = new DoctorReadSlotsDTO();
        dto.setId(String.valueOf(d.getId()));
        dto.setEmail(d.getEmail());
        dto.setTelephone(d.getTelephone());

        dto.setFirstName(d.getFirstName());
        dto.setLastName(d.getLastName());
        dto.setSpecializations(d.getSpecialties());
        dto.setGender(d.getGender());
        dto.setNpi(d.getNpi());

        if (d.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    d.getLocation().getAddress(),
                    d.getLocation().getCity(),
                    d.getLocation().getState(),
                    d.getLocation().getZipCode()
            ));
        }
        dto.setAvailableSlots(d.getAvailableSlots());
        dto.setRatings(d.getRatings());
        return dto;
    }

    public static AppointmentFullDTO toAppointmentDTO(AppointmentFull entity) {
        if (entity == null) return null;

        AppointmentFullDTO dto = new AppointmentFullDTO();
        dto.setId(entity.getAppointmentId());
        dto.setDoctorId(entity.getDoctorId());
        // Mappiamo i nomi completi per comodità di visualizzazione
        dto.setPatientFirstName(entity.getPatientFirstName());
        dto.setPatientLastName(entity.getPatientLastName());

        dto.setDateTime(entity.getDateTime());
        dto.setStatus(entity.getStatus());

        return dto;
    }

    public static DoctorRatingDTO mapToDoctorRatingDTO(ArrayList<Integer> ratings) {
        // Gestione null safety: se la lista è null, restituisci un DTO con lista vuota
        if (ratings == null) {
            return new DoctorRatingDTO(new ArrayList<>());
        }

        return new DoctorRatingDTO(ratings);
    }

    public static AdminReadDTO mapToReadDTO(Admin admin) {
        AdminReadDTO dto = new AdminReadDTO();
        // Convertiamo l'ID int in String per il DTO
        dto.setId(String.valueOf(admin.getId()));
        dto.setEmail(admin.getEmail());
        dto.setTelephone(admin.getTelephone());
        return dto;
    }

    public static AppointmentDoctor mapToAppointmentDoctor(AppointmentFull full) {
        AppointmentDoctor docAppt = new AppointmentDoctor();

        // Campi ereditati da AppointmentBrief
        docAppt.setAppointmentId(full.getAppointmentId());
        docAppt.setDateTime(full.getDateTime());
        docAppt.setLocation(full.getLocation());
        docAppt.setStatus(full.getStatus());

        // Campi specifici di AppointmentDoctor (Dati Paziente)
        docAppt.setPatientFirstName(full.getPatientFirstName());
        docAppt.setPatientLastName(full.getPatientLastName());
        docAppt.setPatientTelephone(full.getPatientTelephone());

        return docAppt;
    }

    public static RatingDTO mapToRatingDTO(Rating source) {
        if (source == null) return null;

        RatingDTO dto = new RatingDTO();
        // Assicurati che i nomi dei campi corrispondano alla tua classe Rating
        dto.setDoctorNpi(source.getDoctorNpi());
        dto.setDoctorFirstName(source.getDoctorFirstName());
        dto.setDoctorLastName(source.getDoctorLastName());
        dto.setRating(source.getRating());

        return dto;
    }
}
