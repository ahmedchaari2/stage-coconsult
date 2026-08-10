package tn.coconsult.medtrack.ai.dto;

import java.util.List;

/** Aggregated clinical context; patientRef stays technical and never exposes patient identity. */
public record PatientContext(
        String patientRef,
        Integer age,
        String sexe,
        String groupeSanguin,
        String allergies,
        String antecedents,
        String traitementsChroniques,
        String antecedentsFamiliaux,
        String vaccinations,
        List<ConsultationSummaryResponse> consultationsRecentes,
        List<AppointmentSummaryResponse> rendezVousRecents,
        List<PrescriptionSummaryResponse> prescriptionsActives
) {
}
