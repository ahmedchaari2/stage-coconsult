package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.notification.dto.NotificationType;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AppointmentReminderService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderService.class);
    private static final int REMINDER_WINDOW_MINUTES = 30;

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentNotificationService appointmentNotificationService;

    @Scheduled(cron = "0 */5 * * * *")
    @Transactional
    public void sendUpcomingReminders() {
        LocalDateTime now = LocalDateTime.now();
        List<Appointment> upcoming = appointmentRepository.findByDateHeureBetweenAndRappelEnvoyeFalseAndStatutIn(
                now, now.plusMinutes(REMINDER_WINDOW_MINUTES),
                List.of(StatutRendezVous.PLANIFIE, StatutRendezVous.CONFIRME));

        if (upcoming.isEmpty()) {
            return;
        }

        Map<Long, Patient> patients = patientRepository.findAllById(
                upcoming.stream().map(Appointment::getPatientId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Patient::getId, p -> p));
        Map<Long, User> medecins = userRepository.findAllById(
                upcoming.stream().map(Appointment::getMedecinId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(User::getId, u -> u));

        for (Appointment appointment : upcoming) {
            Patient patient = patients.get(appointment.getPatientId());
            User medecin = medecins.get(appointment.getMedecinId());

            // Rappel déclenché localement (pas de message RabbitMQ) : le job tourne déjà dans
            appointmentNotificationService.notify(NotificationType.RAPPEL, appointment.getMedecinId(),
                    appointment.getId(), AppointmentNotificationService.toData(patient, medecin, appointment.getDateHeure()));

            appointment.setRappelEnvoye(true);
            appointmentRepository.save(appointment);
        }

        log.info("{} rappel(s) de rendez-vous envoyé(s).", upcoming.size());
    }
}
