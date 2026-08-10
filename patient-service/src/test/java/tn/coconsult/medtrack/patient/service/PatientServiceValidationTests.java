package tn.coconsult.medtrack.patient.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.patient.dto.PatientRequest;
import tn.coconsult.medtrack.patient.mapper.PatientMapper;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceValidationTests {

    @Mock PatientRepository patientRepository;
    @Mock UserRepository userRepository;
    @Mock PatientMapper patientMapper;
    @Mock AccesLogService accesLogService;

    @InjectMocks PatientService patientService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNormalizesEmailAndTunisianPhone() {
        authenticateAdmin();
        when(patientRepository.findByEmailIgnoreCase("patient@example.test")).thenReturn(Optional.empty());
        when(patientRepository.findTopByNumeroDossierStartingWithOrderByNumeroDossierDesc(any())).thenReturn(Optional.empty());
        when(patientRepository.save(any(Patient.class))).thenAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            patient.setId(1L);
            return patient;
        });

        PatientRequest request = request(" Patient@Example.Test ", "20 123 456");
        patientService.create(request);

        assertThat(request.getEmail()).isEqualTo("patient@example.test");
        assertThat(request.getTelephone()).isEqualTo("20 123 456");
        org.mockito.Mockito.verify(patientRepository).save(org.mockito.ArgumentMatchers.argThat(patient ->
                "patient@example.test".equals(patient.getEmail()) && "+21620123456".equals(patient.getTelephone())));
    }

    @Test
    void missingPatientReturns404() {
        when(patientRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> patientService.findById(99L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(404));
    }

    private PatientRequest request(String email, String phone) {
        return new PatientRequest("Nom", "Prénom", LocalDate.of(1990, 1, 1), phone, email,
                "Adresse", null, null, null, null, null, null);
    }

    private void authenticateAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }
}
