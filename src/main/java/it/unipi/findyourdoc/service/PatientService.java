package it.unipi.findyourdoc.service;

import it.unipi.findyourdoc.dto.mongo.*;
import it.unipi.findyourdoc.dto.neo4j.SpecialistDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * Service interface for managing Patient entities.
 * Handles registration, profile updates, appointment booking, and specialist discovery.
 */
public interface PatientService {

    /** * Registers a new patient in the system. */
    PatientReadDTO registerPatient(PatientCreateDTO createDTO);

    /** * Updates an existing patient's profile details. */
    PatientReadDTO updatePatient(String email, PatientUpdateDTO updateDTO);

    /** * Retrieves a patient's profile by their unique email address. */
    PatientReadDTO getPatientByEmail(String email);

    /** * Books a new appointment for the patient. */
    AppointmentPatientDTO bookAppointmentByEmail(String id, AppointmentBookDTO appointmentBookDTO);

    /** * Cancels an existing appointment. */
    void cancelAppointment(String id);

    /** * Retrieves a paginated list of the patient's appointments. */
    Page<AppointmentPatientDTO> getAppointmentsByEmail(String email, Pageable pageable);

    /** * Retrieves a paginated list of symptom reports submitted by the patient. */
    Page<SymptomReportBriefDTO> getSymptomReportsByEmail(String email, Pageable pageable);

    /** * Creates and saves a new symptom report for the patient. */
    SymptomReportBriefDTO createSymptomReportByEmail(String email, SymptomReportCreateDTO createDTO);

    /** * Submits a rating for a doctor. */
    RatingDTO addRatingByEmail(String email, RatingDTO ratingDTO);

    /** * Retrieves a paginated list of all ratings submitted by the patient. */
    Page<RatingDTO> getAllRatingsByEmail(String email, Pageable pageable);

    /** * Queries the Neo4j graph to find specialists in a given city for a specific diagnosis. */
    List<SpecialistDTO> findSpecialistsByDiagnosisAndCity(String city, String diagnosis);

    /** * Retrieves a doctor's profile and their available booking slots using their NPI. */
    DoctorReadSlotsDTO getDoctorByNpi(String id);

}