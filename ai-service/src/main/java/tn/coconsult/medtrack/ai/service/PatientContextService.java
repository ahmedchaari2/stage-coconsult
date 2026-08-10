package tn.coconsult.medtrack.ai.service;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.client.AppointmentGateway;
import tn.coconsult.medtrack.ai.client.ConsultationGateway;
import tn.coconsult.medtrack.ai.client.MedicalRecordGateway;
import tn.coconsult.medtrack.ai.client.PatientGateway;
import tn.coconsult.medtrack.ai.client.PrescriptionGateway;
import tn.coconsult.medtrack.ai.dto.AppointmentSummaryResponse;
import tn.coconsult.medtrack.ai.dto.ConsultationSummaryResponse;
import tn.coconsult.medtrack.ai.dto.MedicalRecordSummaryResponse;
import tn.coconsult.medtrack.ai.dto.PatientContext;
import tn.coconsult.medtrack.ai.dto.PatientSummaryResponse;
import tn.coconsult.medtrack.ai.dto.PrescriptionSummaryResponse;

import java.time.LocalDate;
import java.time.Period;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/** Aggregates the clinical context needed by Gemini by fetching the independent services in parallel. */
@Service
public class PatientContextService {

    private final PatientGateway patientGateway;
    private final MedicalRecordGateway medicalRecordGateway;
    private final ConsultationGateway consultationGateway;
    private final AppointmentGateway appointmentGateway;
    private final PrescriptionGateway prescriptionGateway;
    private final Executor aiContextExecutor;

    public PatientContextService(
            PatientGateway patientGateway,
            MedicalRecordGateway medicalRecordGateway,
            ConsultationGateway consultationGateway,
            AppointmentGateway appointmentGateway,
            PrescriptionGateway prescriptionGateway,
            @Qualifier("aiContextExecutor") Executor aiContextExecutor) {
        this.patientGateway = patientGateway;
        this.medicalRecordGateway = medicalRecordGateway;
        this.consultationGateway = consultationGateway;
        this.appointmentGateway = appointmentGateway;
        this.prescriptionGateway = prescriptionGateway;
        this.aiContextExecutor = aiContextExecutor;
    }

    public PatientContext build(Long patientId) {
        CompletableFuture<PatientSummaryResponse> patientFuture = supplyAsync(() -> patientGateway.getById(patientId));
        CompletableFuture<MedicalRecordSummaryResponse> recordFuture =
                supplyAsync(() -> medicalRecordGateway.getByPatientId(patientId).orElse(null));
        CompletableFuture<List<AppointmentSummaryResponse>> appointmentsFuture = supplyAsync(() -> appointmentGateway.recent(patientId));
        CompletableFuture<List<PrescriptionSummaryResponse>> prescriptionsFuture = supplyAsync(() -> prescriptionGateway.active(patientId));

        try {
            MedicalRecordSummaryResponse record = recordFuture.join();
            CompletableFuture<List<ConsultationSummaryResponse>> consultationsFuture = record == null
                    ? CompletableFuture.completedFuture(List.of())
                    : supplyAsync(() -> consultationGateway.recent(record.id()));

            PatientSummaryResponse patient = patientFuture.join();

            return new PatientContext(
                    "PATIENT-" + patientId,
                    age(patient.dateNaissance()),
                    patient.sexe(),
                    record == null ? null : record.groupeSanguin(),
                    record == null ? null : record.allergies(),
                    record == null ? null : record.antecedents(),
                    record == null ? null : record.traitementsChroniques(),
                    record == null ? null : record.antecedentsFamiliaux(),
                    record == null ? null : record.vaccinations(),
                    consultationsFuture.join(),
                    appointmentsFuture.join(),
                    prescriptionsFuture.join());
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof ResponseStatusException statusException) {
                throw statusException;
            }
            throw exception;
        }
    }

    private <T> CompletableFuture<T> supplyAsync(Supplier<T> supplier) {
        return CompletableFuture.supplyAsync(supplier, aiContextExecutor);
    }

    private Integer age(LocalDate dateNaissance) {
        return dateNaissance == null ? null : Period.between(dateNaissance, LocalDate.now()).getYears();
    }
}
