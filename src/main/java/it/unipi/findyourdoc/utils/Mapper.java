package it.unipi.findyourdoc.utils;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.model.mongo.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for object-to-object mapping.
 * Handles the transformation between domain entities and DTOs to maintain layer isolation.
 */
public class Mapper {

    /**
     * Maps a master Appointment entity and specific doctor info to a Patient view DTO.
     */
    public static AppointmentPatientDTO mapToPatientDTO(AppointmentFull entity, String doctorFirstName, String doctorLastName, String doctorNpi) {
        AppointmentPatientDTO dto = new AppointmentPatientDTO();

        dto.setId(entity.getAppointmentId());
        dto.setDateTime(entity.getDateTime());
        dto.setStatus(entity.getStatus());

        if (entity.getSpecialties() != null && !entity.getSpecialties().isEmpty()) {
            dto.setDoctorSpecialties(entity.getSpecialties());
        }

        if (entity.getLocation() != null) {
            LocationDTO locDto = new LocationDTO();
            locDto.setCity(entity.getLocation().getCity());
            locDto.setAddress(entity.getLocation().getAddress());
            locDto.setState(entity.getLocation().getState());
            locDto.setZipCode(entity.getLocation().getZipCode());
            dto.setLocation(locDto);
        }

        dto.setDoctorFirstName(doctorFirstName);
        dto.setDoctorLastName(doctorLastName);
        dto.setDoctorNpi(doctorNpi);

        return dto;
    }

    /**
     * Maps a denormalized AppointmentPatient embedded entity to its DTO equivalent.
     */
    public static AppointmentPatientDTO mapToPatientDTO(AppointmentPatient entity) {
        if (entity == null) return null;

        AppointmentPatientDTO dto = new AppointmentPatientDTO();
        dto.setDateTime(entity.getDateTime());
        dto.setId(entity.getAppointmentId());
        if (entity.getLocation() != null) {
            LocationDTO locDto = new LocationDTO();
            locDto.setCity(entity.getLocation().getCity());
            locDto.setAddress(entity.getLocation().getAddress());
            locDto.setState(entity.getLocation().getState());
            locDto.setZipCode(entity.getLocation().getZipCode());
            dto.setLocation(locDto);
        }
        dto.setStatus(entity.getStatus());

        dto.setDoctorFirstName(entity.getDoctorFirstName());
        dto.setDoctorLastName(entity.getDoctorLastName());
        dto.setDoctorNpi(entity.getDoctorNpi());
        dto.setDoctorSpecialties(entity.getDoctorSpecialties());

        return dto;
    }

    /**
     * Maps symptom report summaries for brief history views.
     */
    public static SymptomReportBriefDTO mapToSymptomBriefDTO(SymptomReportBrief entity) {
        SymptomReportBriefDTO dto = new SymptomReportBriefDTO();
        dto.setContext(entity.getContext());
        dto.setSymptoms(entity.getSymptoms());
        dto.setPossibleDiagnosies(entity.getPossibleDiagnosies());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    /**
     * Maps a SymptomReportBrief to a DTO, ensuring context and symptoms are preserved.
     */
    public static SymptomReportBriefDTO mapToBriefDTO(SymptomReportBrief report) {
        if (report == null) return null;

        SymptomReportBriefDTO dto = new SymptomReportBriefDTO();
        dto.setCreatedAt(report.getCreatedAt());
        dto.setPossibleDiagnosies(report.getPossibleDiagnosies());
        dto.setContext(report.getContext());
        dto.setSymptoms(report.getSymptoms());

        return dto;
    }

    /**
     * Maps a Rating entity to a DTO for patient feedback history.
     */
    public static RatingDTO mapToPatientRatingDTO(Rating rating) {
        RatingDTO dto = new RatingDTO();
        dto.setDoctorNpi(rating.getDoctorNpi());
        dto.setRating(rating.getRating());
        dto.setDoctorFirstName(rating.getDoctorFirstName());
        dto.setDoctorLastName(rating.getDoctorLastName());
        return dto;
    }

    /**
     * Maps a Patient domain object to a profile read DTO.
     */
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

        return dto;
    }

    /**
     * Maps location DTOs back to domain entities for persistence.
     */
    public static Location mapLocationDTOToEntity(LocationDTO dto) {
        Location loc = new Location();
        loc.setAddress(dto.getAddress());
        loc.setCity(dto.getCity());
        loc.setState(dto.getState());
        loc.setZipCode(dto.getZipCode());
        return loc;
    }

    /**
     * Maps a Doctor domain object to a public profile read DTO.
     */
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
        dto.setAvgRating(d.getAvgRating());
        dto.setRatingCount(d.getRatingCount());
        if (d.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    d.getLocation().getAddress(),
                    d.getLocation().getCity(),
                    d.getLocation().getState(),
                    d.getLocation().getZipCode()
            ));
        }

        dto.setBookedThisWeek(d.getBookedThisWeek());
        return dto;
    }



    /**
     * Maps a Doctor domain object to a DTO containing availability slots.
     */
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
        dto.setAvgRating(d.getAvgRating());
        dto.setRatingCount(d.getRatingCount());
        return dto;
    }

    /**
     * Maps a master Appointment entity to a full summary DTO.
     */
    public static AppointmentFullDTO toAppointmentDTO(AppointmentFull entity) {
        if (entity == null) return null;

        AppointmentFullDTO dto = new AppointmentFullDTO();
        dto.setId(entity.getAppointmentId());
        dto.setDoctorId(entity.getDoctorId());
        dto.setPatientFirstName(entity.getPatientFirstName());
        dto.setPatientLastName(entity.getPatientLastName());
        dto.setDateTime(entity.getDateTime());
        dto.setStatus(entity.getStatus());
        dto.setSpecialties(entity.getSpecialties());
        dto.setDoctorRating(entity.getDoctorRating());
        if (entity.getLocation() != null) {
            dto.setLocation(new LocationDTO(
                    entity.getLocation().getAddress(),
                    entity.getLocation().getCity(),
                    entity.getLocation().getState(),
                    entity.getLocation().getZipCode()
            ));
        }
        dto.setPatientTelephone(entity.getPatientTelephone());
        dto.setPatientEmail(entity.getPatientEmail());
        return dto;
    }

    /**
     * Maps an Admin domain object to a read DTO.
     */
    public static AdminReadDTO mapToReadDTO(Admin admin) {
        AdminReadDTO dto = new AdminReadDTO();
        dto.setId(String.valueOf(admin.getId()));
        dto.setEmail(admin.getEmail());
        dto.setTelephone(admin.getTelephone());
        return dto;
    }

    /**
     * Maps a Rating entity to a standard RatingDTO.
     */
    public static RatingDTO mapToRatingDTO(Rating source) {
        if (source == null) return null;
        RatingDTO dto = new RatingDTO();
        dto.setDoctorNpi(source.getDoctorNpi());
        dto.setDoctorFirstName(source.getDoctorFirstName());
        dto.setDoctorLastName(source.getDoctorLastName());
        dto.setRating(source.getRating());
        return dto;
    }

    /**
     * Maps a DoctorProjection to a generic Map for Neo4j bulk operations.
     */
    public static Map<String, Object> mapToMap(DoctorProjection doc) {
        Map<String, Object> entry = new HashMap<>();
        entry.put("npi", doc.npi());
        entry.put("avgRating", doc.avgRating());
        entry.put("ratingCount", doc.ratingCount());
        return entry;
    }
}