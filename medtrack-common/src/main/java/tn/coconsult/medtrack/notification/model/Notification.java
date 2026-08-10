package tn.coconsult.medtrack.notification.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.common.entity.BaseEntity;
import tn.coconsult.medtrack.notification.dto.NotificationType;

import java.time.LocalDateTime;

// Persistance en complément du push WebSocket temps réel : le broker STOMP perd les messages si le
@Entity
@Table(name = "notifications")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Notification extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long destinataireId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private Long appointmentId;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean lu;

    private LocalDateTime luAt;
}
