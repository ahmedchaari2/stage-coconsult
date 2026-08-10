package tn.coconsult.medtrack.appointment.service;

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
import tn.coconsult.medtrack.appointment.client.AppointmentEventPublisher;
import tn.coconsult.medtrack.appointment.client.PatientReferentClient;
import tn.coconsult.medtrack.appointment.dto.CreateAppointmentRequest;
import tn.coconsult.medtrack.appointment.dto.UpdateAppointmentRequest;
import tn.coconsult.medtrack.appointment.mapper.AppointmentMapper;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceValidationTests {

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
    void createRejectsAnAdminUsedAsDoctor() {
        authenticateAdmin();
        Patient patient = new Patient();
        patient.setId(7L);
        User selectedUser = new User();
        selectedUser.setId(9L);
        selectedUser.setRole(Role.ADMIN);
        selectedUser.setActif(true);
        when(patientRepository.findById(7L)).thenReturn(Optional.of(patient));
        when(userRepository.findById(9L)).thenReturn(Optional.of(selectedUser));

        CreateAppointmentRequest request = new CreateAppointmentRequest(
                7L, 9L, LocalDateTime.now().plusDays(1), "Contrôle", null);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(422));
    }

    @Test
    void updateRejectsMovingAnAppointmentToThePast() {
        authenticateAdmin();
        Appointment appointment = new Appointment();
        appointment.setId(31L);
        appointment.setPatientId(7L);
        appointment.setMedecinId(4L);
        appointment.setDateHeure(LocalDateTime.now().plusDays(1));
        appointment.setStatut(StatutRendezVous.PLANIFIE);
        when(appointmentRepository.findById(31L)).thenReturn(Optional.of(appointment));

        UpdateAppointmentRequest request = new UpdateAppointmentRequest(
                4L, LocalDateTime.now().minusDays(1), "Contrôle", StatutRendezVous.PLANIFIE, null);

        assertThatThrownBy(() -> appointmentService.update(31L, request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void assignedDoctorKeepsAccessEvenWhenNotTheReferent() {
        Appointment appointment = new Appointment();
        appointment.setId(31L);
        appointment.setPatientId(7L);
        appointment.setMedecinId(4L);
        when(appointmentRepository.findById(31L)).thenReturn(Optional.of(appointment));

        assertThat(appointmentService.isMedecinReferentForAppointment(31L, 4L)).isTrue();
    }

    @Test
    void createRejectsSundayAppointments() {
        authenticateAdmin();
        Patient patient = new Patient();
        patient.setId(7L);
        when(patientRepository.findById(7L)).thenReturn(Optional.of(patient));

        LocalDate sunday = LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        CreateAppointmentRequest request = new CreateAppointmentRequest(
                7L, 9L, LocalDateTime.of(sunday, LocalTime.of(10, 0)), "Contrôle", null);

        assertThatThrownBy(() -> appointmentService.create(request))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(error -> assertThat(((ResponseStatusException) error).getStatusCode().value()).isEqualTo(400));
    }

    @Test
    void sundayHasNoAvailableTimeSlots() {
        LocalDate sunday = LocalDate.now().with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        assertThat(appointmentService.disponibilites(4L, sunday)).isEmpty();
    }

    private void authenticateAdmin() {
        User admin = new User();
        admin.setId(1L);
        admin.setRole(Role.ADMIN);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(admin, null, List.of()));
    }
}
