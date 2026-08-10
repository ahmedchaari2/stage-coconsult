package tn.coconsult.medtrack.appointment.model;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "appointments")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Appointment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long patientId;

    @Column(nullable = false)
    private Long medecinId;

    @Column(nullable = false)
    private LocalDateTime dateHeure;

    @Column(nullable = false)
    private String motif;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StatutRendezVous statut;

    @Column(columnDefinition = "TEXT")
    private String notes;

    // Anti-doublon pour le rappel WebSocket 30 min avant : sans ce flag le job (toutes les 5 min) renotifierait à chaque passage.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean rappelEnvoye;

    // Même principe pour le rappel email de la veille au patient, indépendant du rappel WebSocket ci-dessus.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean rappelVeilleEnvoye;
}
