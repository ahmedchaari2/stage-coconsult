package tn.coconsult.medtrack.ai.dto;

public record PrescriptionSummaryResponse(
        String medicament,
        String posologie,
        Integer dureeJours,
        String instructions,
        boolean renouvelable
) {
}
