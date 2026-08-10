package tn.coconsult.medtrack.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.repository.AppointmentRepository;
import tn.coconsult.medtrack.common.dto.PageDTO;
import tn.coconsult.medtrack.notification.dto.NotificationResponse;
import tn.coconsult.medtrack.notification.mapper.NotificationMapper;
import tn.coconsult.medtrack.notification.model.Notification;
import tn.coconsult.medtrack.notification.repository.NotificationRepository;
import tn.coconsult.medtrack.patient.model.Patient;
import tn.coconsult.medtrack.patient.repository.PatientRepository;
import tn.coconsult.medtrack.user.model.User;
import tn.coconsult.medtrack.user.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final AppointmentRepository appointmentRepository;
    private final PatientRepository patientRepository;
    private final UserRepository userRepository;
    private final NotificationMapper notificationMapper;

    public PageDTO<NotificationResponse> search(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        PageRequest pageRequest = PageRequest.of(safePage, safeSize);

        Page<Notification> result = notificationRepository
                .findByDestinataireIdOrderByCreatedAtDesc(currentUserId(), pageRequest);
        List<NotificationResponse> content = enrichBatch(result.getContent());
        return new PageDTO<>(content, result.getNumber(), result.getSize(), result.getTotalPages(), result.getTotalElements());
    }

    public long unreadCount() {
        return notificationRepository.countByDestinataireIdAndLuFalse(currentUserId());
    }

    /**
     * Marque une notification lue. La requête findById ne filtre volontairement pas par
     * destinataireId : l'appartenance est vérifiée explicitement ci-dessous (403 sinon), pour que
     * ce contrôle reste visible et ne dépende pas uniquement d'une condition de requête.
     */
    public void markAsRead(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification introuvable"));

        if (!currentUserId().equals(notification.getDestinataireId())) {
            throw new AccessDeniedException("Cette notification ne vous appartient pas");
        }

        if (!notification.isLu()) {
            notification.setLu(true);
            notification.setLuAt(LocalDateTime.now());
            notificationRepository.save(notification);
        }
    }

    @Transactional
    public void markAllAsRead() {
        List<Notification> unread = notificationRepository.findByDestinataireIdAndLuFalse(currentUserId());
        LocalDateTime now = LocalDateTime.now();
        unread.forEach(notification -> {
            notification.setLu(true);
            notification.setLuAt(now);
        });
        notificationRepository.saveAll(unread);
    }

    private Long currentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        User currentUser = (User) auth.getPrincipal();
        return currentUser.getId();
    }

    private List<NotificationResponse> enrichBatch(List<Notification> notifications) {
        List<NotificationResponse> responses = notifications.stream().map(notificationMapper::toResponse).toList();

        Set<Long> appointmentIds = notifications.stream().map(Notification::getAppointmentId).collect(Collectors.toSet());
        Map<Long, Appointment> appointments = appointmentRepository.findAllById(appointmentIds).stream()
                .collect(Collectors.toMap(Appointment::getId, a -> a));

        Set<Long> patientIds = appointments.values().stream().map(Appointment::getPatientId).collect(Collectors.toSet());
        Set<Long> medecinIds = appointments.values().stream().map(Appointment::getMedecinId).collect(Collectors.toSet());
        Map<Long, Patient> patients = patientRepository.findAllById(patientIds).stream()
                .collect(Collectors.toMap(Patient::getId, p -> p));
        Map<Long, User> medecins = userRepository.findAllById(medecinIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));

        for (int i = 0; i < notifications.size(); i++) {
            Appointment appointment = appointments.get(notifications.get(i).getAppointmentId());
            if (appointment == null) {
                continue;
            }
            Patient patient = patients.get(appointment.getPatientId());
            User medecin = medecins.get(appointment.getMedecinId());
            responses.get(i).setData(
                    AppointmentNotificationService.toData(patient, medecin, appointment.getDateHeure(), appointment.getStatut()));
        }

        return responses;
    }
}
