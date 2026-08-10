package tn.coconsult.medtrack.appointment.service;

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
import tn.coconsult.medtrack.appointment.client.AppointmentEventPublisher;
import tn.coconsult.medtrack.appointment.client.PatientReferentClient;
import tn.coconsult.medtrack.appointment.dto.AppointmentResponse;
import tn.coconsult.medtrack.appointment.mapper.AppointmentMapper;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceAccessLogTests {

    @Mock AppointmentRepository appointmentRepository;
    @Mock PatientRepository patientRepository;
    @Mock UserRepository userRepository;
    @Mock PatientReferentClient patientReferentClient;
    @Mock AppointmentEventPublisher appointmentEventPublisher;
    @Mock AccesLogService accesLogService;
    @Mock AppointmentMapper appointmentMapper;

    @InjectMocks AppointmentService appointmentService;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void explicitGetByIdLogsTheViewedAppointmentAndPatient() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));

        Appointment appointment = new Appointment();
        appointment.setId(31L);
        appointment.setPatientId(7L);
        appointment.setMedecinId(4L);

        AppointmentResponse response = new AppointmentResponse();
        response.setId(31L);
        response.setPatientId(7L);
        response.setMedecinId(4L);

        when(appointmentRepository.findById(31L)).thenReturn(Optional.of(appointment));
        when(appointmentMapper.toResponse(appointment)).thenReturn(response);
        appointmentService.findById(31L);

        verify(accesLogService).enregistrerAcces(
                1L, TypeRessource.RENDEZ_VOUS, 31L, 7L, TypeAction.VIEW);
    }
}
