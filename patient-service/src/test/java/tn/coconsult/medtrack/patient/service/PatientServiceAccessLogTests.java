package tn.coconsult.medtrack.patient.service;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.service.AccesLogService;
import tn.coconsult.medtrack.patient.dto.PatientResponse;
import tn.coconsult.medtrack.patient.mapper.PatientMapper;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatientServiceAccessLogTests {

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
    void successfulDetailReadLogsPatientResource() {
        User admin = new User();
        admin.setId(2L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        Patient patient = new Patient();
        patient.setId(7L);
        PatientResponse response = new PatientResponse();
        response.setId(7L);

        when(patientRepository.findById(7L)).thenReturn(Optional.of(patient));
        when(patientMapper.toResponse(patient)).thenReturn(response);

        assertThat(patientService.findById(7L)).isSameAs(response);
        verify(accesLogService).enregistrerAcces(2L, TypeRessource.PATIENT, 7L, 7L, TypeAction.VIEW);
    }
}
