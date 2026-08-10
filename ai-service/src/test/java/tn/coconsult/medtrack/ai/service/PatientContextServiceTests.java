package tn.coconsult.medtrack.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * L'executor direct (Runnable::run) rend l'agregation deterministe en test : chaque supplyAsync
 * s'execute immediatement sur le thread appelant, comme un appel synchrone classique.
 */
@ExtendWith(MockitoExtension.class)
class PatientContextServiceTests {

    @Mock PatientGateway patientGateway;
    @Mock MedicalRecordGateway medicalRecordGateway;
    @Mock ConsultationGateway consultationGateway;
    @Mock AppointmentGateway appointmentGateway;
    @Mock PrescriptionGateway prescriptionGateway;

    private PatientContextService service;

    @BeforeEach
    void setUp() {
        // Construit apres l'injection des @Mock par MockitoExtension : un champ initialise en
        // ligne capturerait des mocks encore null (l'extension tourne apres le constructeur).
        service = new PatientContextService(
                patientGateway, medicalRecordGateway, consultationGateway, appointmentGateway, prescriptionGateway, Runnable::run);
    }

    @Test
    void aggregatesAllFiveSourcesIntoOneContext() {
        LocalDate dateNaissance = LocalDate.now().minusYears(40);
        when(patientGateway.getById(1L)).thenReturn(new PatientSummaryResponse(dateNaissance, "FEMME"));
        when(medicalRecordGateway.getByPatientId(1L)).thenReturn(
                Optional.of(new MedicalRecordSummaryResponse(99L, "A_POSITIF", "Aspirine", "RAS", "Aucun", null, "A jour")));
        List<ConsultationSummaryResponse> consultations =
                List.of(new ConsultationSummaryResponse(LocalDate.now(), "Controle", "RAS", "Rien a signaler", 120, 80, 70.0, 37.0, 72));
        List<AppointmentSummaryResponse> appointments = List.of(new AppointmentSummaryResponse(null, "Controle", "CONFIRME"));
        List<PrescriptionSummaryResponse> prescriptions = List.of(new PrescriptionSummaryResponse("Doliprane", "1x/j", 5, null, false));
        when(consultationGateway.recent(99L)).thenReturn(consultations);
        when(appointmentGateway.recent(1L)).thenReturn(appointments);
        when(prescriptionGateway.active(1L)).thenReturn(prescriptions);

        PatientContext context = service.build(1L);

        assertThat(context.patientRef()).isEqualTo("PATIENT-1");
        assertThat(context.age()).isEqualTo(40);
        assertThat(context.sexe()).isEqualTo("FEMME");
        assertThat(context.groupeSanguin()).isEqualTo("A_POSITIF");
        assertThat(context.allergies()).isEqualTo("Aspirine");
        assertThat(context.consultationsRecentes()).isEqualTo(consultations);
        assertThat(context.rendezVousRecents()).isEqualTo(appointments);
        assertThat(context.prescriptionsActives()).isEqualTo(prescriptions);
    }

    @Test
    void neverLeaksTheRealPatientIdentityInTheRef() {
        when(patientGateway.getById(42L)).thenReturn(new PatientSummaryResponse(null, "HOMME"));
        when(medicalRecordGateway.getByPatientId(42L)).thenReturn(Optional.empty());
        when(appointmentGateway.recent(42L)).thenReturn(List.of());
        when(prescriptionGateway.active(42L)).thenReturn(List.of());

        PatientContext context = service.build(42L);

        assertThat(context.patientRef()).isEqualTo("PATIENT-42");
    }

    @Test
    void treatsAMissingMedicalRecordAsAnEmptyClinicalSectionNotAnError() {
        when(patientGateway.getById(2L)).thenReturn(new PatientSummaryResponse(LocalDate.now().minusYears(30), "HOMME"));
        when(medicalRecordGateway.getByPatientId(2L)).thenReturn(Optional.empty());
        when(appointmentGateway.recent(2L)).thenReturn(List.of());
        when(prescriptionGateway.active(2L)).thenReturn(List.of());

        PatientContext context = service.build(2L);

        assertThat(context.groupeSanguin()).isNull();
        assertThat(context.allergies()).isNull();
        assertThat(context.antecedents()).isNull();
        assertThat(context.traitementsChroniques()).isNull();
        assertThat(context.antecedentsFamiliaux()).isNull();
        assertThat(context.vaccinations()).isNull();
        assertThat(context.consultationsRecentes()).isEmpty();
    }

    // Sans dossier médical il n'y a pas d'id à passer : appeler quand même consultationGateway
    // planterait pour rien, autant ne jamais le solliciter.
    @Test
    void neverCallsConsultationsWhenThereIsNoMedicalRecord() {
        when(patientGateway.getById(2L)).thenReturn(new PatientSummaryResponse(LocalDate.now().minusYears(30), "HOMME"));
        when(medicalRecordGateway.getByPatientId(2L)).thenReturn(Optional.empty());
        when(appointmentGateway.recent(2L)).thenReturn(List.of());
        when(prescriptionGateway.active(2L)).thenReturn(List.of());

        service.build(2L);

        verify(consultationGateway, never()).recent(anyLong());
    }

    @Test
    void returnsANullAgeWhenTheBirthDateIsUnknown() {
        when(patientGateway.getById(3L)).thenReturn(new PatientSummaryResponse(null, "HOMME"));
        when(medicalRecordGateway.getByPatientId(3L)).thenReturn(Optional.empty());
        when(appointmentGateway.recent(3L)).thenReturn(List.of());
        when(prescriptionGateway.active(3L)).thenReturn(List.of());

        assertThat(service.build(3L).age()).isNull();
    }

    @Test
    void computesAgeFromTheBirthDate() {
        LocalDate dateNaissance = LocalDate.now().minusYears(27).minusDays(1);
        when(patientGateway.getById(4L)).thenReturn(new PatientSummaryResponse(dateNaissance, "FEMME"));
        when(medicalRecordGateway.getByPatientId(4L)).thenReturn(Optional.empty());
        when(appointmentGateway.recent(4L)).thenReturn(List.of());
        when(prescriptionGateway.active(4L)).thenReturn(List.of());

        assertThat(service.build(4L).age()).isEqualTo(Period.between(dateNaissance, LocalDate.now()).getYears());
    }

    @Test
    void unwrapsTheOriginalResponseStatusExceptionWhenASourceFails() {
        when(medicalRecordGateway.getByPatientId(5L)).thenReturn(Optional.empty());
        when(patientGateway.getById(5L))
                .thenThrow(new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "patient-service indisponible"));

        assertThatThrownBy(() -> service.build(5L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("patient-service indisponible");
    }
}
