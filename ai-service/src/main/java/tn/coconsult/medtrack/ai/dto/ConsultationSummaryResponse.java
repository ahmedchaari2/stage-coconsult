package tn.coconsult.medtrack.ai.dto;

import java.time.LocalDate;

public record ConsultationSummaryResponse(
        LocalDate consultationDate,
        String motif,
        String diagnostic,
        String notes,
        Integer tensionArterielleSystolique,
        Integer tensionArterielleDiastolique,
        Double poids,
        Double temperature,
        Integer pouls
) {
}
