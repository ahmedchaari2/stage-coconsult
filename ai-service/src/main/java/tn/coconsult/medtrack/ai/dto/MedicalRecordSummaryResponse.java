package tn.coconsult.medtrack.ai.dto;

/** Clinical subset of MedicalRecordResponse; id is only used to fetch related consultations. */
public record MedicalRecordSummaryResponse(
        Long id,
        String groupeSanguin,
        String allergies,
        String antecedents,
        String traitementsChroniques,
        String antecedentsFamiliaux,
        String vaccinations
) {
}
