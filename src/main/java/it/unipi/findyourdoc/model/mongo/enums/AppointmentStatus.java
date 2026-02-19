package it.unipi.findyourdoc.model.mongo.enums;

/**
 * Enumeration representing the lifecycle stages of a medical appointment.
 * Used to track the transition from initial booking to final resolution.
 */
public enum AppointmentStatus {
    /** * The appointment was cancelled by either the patient or the doctor. */
    CANCELLED,

    /** * The appointment is booked but the date and time have not yet arrived. */
    SCHEDULED,

    /** * The visit has been successfully concluded. */
    COMPLETED,

    /** * The patient failed to attend the scheduled appointment without notice. */
    NO_SHOW,

    /** * The original appointment was moved to a different time slot. */
    RESCHEDULED,

    /** * Initial state, awaiting confirmation from the doctor or system. */
    PENDING,

    /** * Formally confirmed and locked into the professional's schedule. */
    CONFIRMED
}