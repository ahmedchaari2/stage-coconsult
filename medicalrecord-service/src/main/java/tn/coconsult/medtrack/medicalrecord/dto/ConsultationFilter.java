package tn.coconsult.medtrack.medicalrecord.dto;

import java.time.LocalDate;

public record ConsultationFilter(
        String motif,
        Long medecinId,
        LocalDate dateFrom,
        LocalDate dateTo,
        // consultations archivées ; passer true les inclut (historique, pas de restriction de
        Boolean archived,
        String q
) {
}
