package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Rappel de rendez-vous la veille, par email au patient — distinct d'AppointmentReminderService
 * (rappel médecin, 30 min avant, WebSocket). Recherche une fois par jour les rendez-vous PLANIFIE/
 * CONFIRME du lendemain, anti-doublon via Appointment.rappelVeilleEnvoye (voir le modèle pour la
 * distinction avec rappelEnvoye).
 * <p>
 * Heure d'envoi et activation configurables depuis config-repo (medtrack.mail.rappel-veille.*),
 * pas codées en dur : un cabinet peut vouloir un autre horaire, ou couper le rappel sans redéployer.
 */
@Service
@RequiredArgsConstructor
public class AppointmentReminderMailService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentReminderMailService.class);

    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final AppointmentEmailService appointmentEmailService;

    @Value("${medtrack.mail.rappel-veille.active:true}")
    private boolean active;

    @Scheduled(cron = "${medtrack.mail.rappel-veille.cron:0 0 18 * * *}")
    @Transactional
    public void sendReminders() {
        if (!active) {
            log.debug("Rappel veille par email désactivé (medtrack.mail.rappel-veille.active=false).");
            return;
        }

        LocalDate demain = LocalDate.now().plusDays(1);
        LocalDateTime from = demain.atStartOfDay();
        LocalDateTime to = demain.atTime(LocalTime.MAX);

        List<Appointment> upcoming = appointmentRepository.findByDateHeureBetweenAndRappelVeilleEnvoyeFalseAndStatutIn(
                from, to, List.of(StatutRendezVous.PLANIFIE, StatutRendezVous.CONFIRME));

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

            appointmentEmailService.sendReminder(appointment, patient, medecin);

            appointment.setRappelVeilleEnvoye(true);
            appointmentRepository.save(appointment);
        }

        log.info("{} rappel(s) de rendez-vous (veille) traité(s) pour demain.", upcoming.size());
    }
}
