package tn.coconsult.medtrack.prescription.dto;

import java.time.LocalDate;

public record PrescriptionFilter(
        String medicament,
        Long patientId,
        Long medecinId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Boolean archived,
        String q,
        String statutCalcule
) {
}
