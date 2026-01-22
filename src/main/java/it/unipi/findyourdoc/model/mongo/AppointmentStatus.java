package it.unipi.findyourdoc.model.mongo;

public enum AppointmentStatus {
    CANCELLED,      // L'appuntamento è stato annullato
    SCHEDULED,      // Appuntamento programmato ma non ancora iniziato
    COMPLETED,      // Appuntamento terminato con successo
    NO_SHOW,        // Il cliente non si è presentato
    RESCHEDULED,    // Appuntamento spostato a un'altra data/ora
    PENDING,        // In attesa di conferma
    CONFIRMED       // Confermato dal cliente
}

