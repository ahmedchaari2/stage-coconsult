package tn.coconsult.medtrack.notification.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import tn.coconsult.medtrack.notification.model.Notification;

import java.time.LocalDateTime;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Page<Notification> findByDestinataireIdOrderByCreatedAtDesc(Long destinataireId, Pageable pageable);

    long countByDestinataireIdAndLuFalse(Long destinataireId);

    List<Notification> findByDestinataireIdAndLuFalse(Long destinataireId);

    /** Purge planifiée (voir NotificationCleanupService) : notifications lues avant une date seuil. */
    long deleteByLuTrueAndLuAtBefore(LocalDateTime threshold);
}
