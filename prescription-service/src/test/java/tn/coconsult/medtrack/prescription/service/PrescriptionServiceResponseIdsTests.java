package tn.coconsult.medtrack.prescription.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;
import tn.coconsult.medtrack.medicalrecord.repository.ConsultationRepository;
import tn.coconsult.medtrack.medicalrecord.repository.MedicalRecordRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.prescription.client.ConsultationSummaryClient;
import tn.coconsult.medtrack.prescription.client.MedecinSummaryClient;
import tn.coconsult.medtrack.prescription.client.PatientDetailsClient;
import tn.coconsult.medtrack.prescription.client.PatientReferentClient;
import tn.coconsult.medtrack.prescription.dto.PrescriptionFilter;
import tn.coconsult.medtrack.prescription.dto.PrescriptionResponse;
import tn.coconsult.medtrack.prescription.mapper.PrescriptionMapper;
import tn.coconsult.medtrack.prescription.model.Prescription;
import tn.coconsult.medtrack.prescription.repository.PrescriptionRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceResponseIdsTests {

    @Mock PrescriptionRepository prescriptionRepository;
    @Mock ConsultationRepository consultationRepository;
    @Mock MedicalRecordRepository medicalRecordRepository;
    @Mock ConsultationSummaryClient consultationSummaryClient;
    @Mock PatientReferentClient patientReferentClient;
    @Mock PatientDetailsClient patientDetailsClient;
    @Mock MedecinSummaryClient medecinSummaryClient;
    @Mock OrdonnancePdfGenerator ordonnancePdfGenerator;
    @Mock PatientRepository patientRepository;
    @Mock UserRepository userRepository;
    @Mock AccesLogService accesLogService;
    @Mock PrescriptionMapper prescriptionMapper;

    @InjectMocks PrescriptionService prescriptionService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listResponseIncludesResolvedPatientAndMedecinIds() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        Prescription prescription = new Prescription();
        prescription.setId(19L);
        prescription.setConsultationId(31L);

        Consultation consultation = new Consultation();
        consultation.setId(31L);
        consultation.setMedicalRecordId(12L);
        consultation.setMedecinId(4L);
        consultation.setConsultationDate(LocalDate.of(2026, 7, 28));

        MedicalRecord record = new MedicalRecord();
        record.setId(12L);
        record.setPatientId(7L);

        Patient patient = new Patient();
        patient.setId(7L);
        patient.setNom("Martin");
        patient.setPrenom("Alice");

        User medecin = new User();
        medecin.setId(4L);
        medecin.setNom("Bernard");
        medecin.setPrenom("Sophie");

        PrescriptionResponse mapped = new PrescriptionResponse();
        mapped.setId(19L);
        mapped.setConsultationId(31L);

        when(consultationSummaryClient.getSummary(31L))
                .thenReturn(new ConsultationSummaryResponse(12L, 7L, 4L));
        when(prescriptionRepository.findAll(ArgumentMatchers.<Specification<Prescription>>any(), any(Sort.class)))
                .thenReturn(List.of(prescription));
        when(prescriptionMapper.toResponse(prescription)).thenReturn(mapped);
        when(consultationRepository.findAllById(Set.of(31L))).thenReturn(List.of(consultation));
        when(medicalRecordRepository.findAllById(Set.of(12L))).thenReturn(List.of(record));
        when(patientRepository.findAllById(Set.of(7L))).thenReturn(List.of(patient));
        when(userRepository.findAllById(Set.of(4L))).thenReturn(List.of(medecin));

        PrescriptionResponse response = prescriptionService.listForConsultation(31L, false).get(0);

        assertThat(response.getPatientId()).isEqualTo(7L);
        assertThat(response.getMedecinId()).isEqualTo(4L);
        verifyNoInteractions(accesLogService);
    }

    @Test
    void transverseSearchDoesNotLogEveryDisplayedRowAsAnIndividualView() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        Prescription prescription = new Prescription();
        prescription.setId(19L);
        prescription.setConsultationId(31L);

        Consultation consultation = new Consultation();
        consultation.setId(31L);
        consultation.setMedicalRecordId(12L);
        consultation.setMedecinId(4L);

        MedicalRecord record = new MedicalRecord();
        record.setId(12L);
        record.setPatientId(7L);

        Patient patient = new Patient();
        patient.setId(7L);

        User medecin = new User();
        medecin.setId(4L);

        PrescriptionResponse mapped = new PrescriptionResponse();
        mapped.setId(19L);
        mapped.setConsultationId(31L);

        when(prescriptionRepository.findAll(ArgumentMatchers.<Specification<Prescription>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(prescription)));
        when(prescriptionMapper.toResponse(prescription)).thenReturn(mapped);
        when(consultationRepository.findAllById(Set.of(31L))).thenReturn(List.of(consultation));
        when(medicalRecordRepository.findAllById(Set.of(12L))).thenReturn(List.of(record));
        when(patientRepository.findAllById(Set.of(7L))).thenReturn(List.of(patient));
        when(userRepository.findAllById(Set.of(4L))).thenReturn(List.of(medecin));

        prescriptionService.search(new PrescriptionFilter(null, null, null, null, null, false, null, null), 0, 10, null, null);

        verifyNoInteractions(accesLogService);
    }

    @Test
    void explicitGetByIdLogsOnlyTheViewedPrescription() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        Prescription prescription = new Prescription();
        prescription.setId(19L);
        prescription.setConsultationId(31L);

        Consultation consultation = new Consultation();
        consultation.setId(31L);
        consultation.setMedicalRecordId(12L);
        consultation.setMedecinId(4L);

        MedicalRecord record = new MedicalRecord();
        record.setId(12L);
        record.setPatientId(7L);

        Patient patient = new Patient();
        patient.setId(7L);

        User medecin = new User();
        medecin.setId(4L);

        PrescriptionResponse mapped = new PrescriptionResponse();
        mapped.setId(19L);
        mapped.setConsultationId(31L);

        when(prescriptionRepository.findById(19L)).thenReturn(java.util.Optional.of(prescription));
        when(prescriptionMapper.toResponse(prescription)).thenReturn(mapped);
        when(consultationRepository.findAllById(Set.of(31L))).thenReturn(List.of(consultation));
        when(medicalRecordRepository.findAllById(Set.of(12L))).thenReturn(List.of(record));
        when(patientRepository.findAllById(Set.of(7L))).thenReturn(List.of(patient));
        when(userRepository.findAllById(Set.of(4L))).thenReturn(List.of(medecin));

        PrescriptionResponse response = prescriptionService.getById(19L);

        assertThat(response.getPatientId()).isEqualTo(7L);
        verify(accesLogService).enregistrerAcces(
                1L, tn.coconsult.medtrack.accesslog.model.TypeRessource.PRESCRIPTION, 19L, 7L,
                tn.coconsult.medtrack.accesslog.model.TypeAction.VIEW);
    }

    @Test
    void aggregateCountDoesNotCreatePatientAccessLogs() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        when(prescriptionRepository.count(ArgumentMatchers.<Specification<Prescription>>any()))
                .thenReturn(12L);

        long result = prescriptionService.count(
                new PrescriptionFilter(null, null, null, null, null, false, null, null));

        assertThat(result).isEqualTo(12L);
        verifyNoInteractions(accesLogService);
    }
}
