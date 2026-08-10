package tn.coconsult.medtrack.medicalrecord.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import tn.coconsult.medtrack.common.entity.BaseEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "consultations")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Consultation extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long medicalRecordId;

    @Column(nullable = false)
    private Long medecinId;

    private Long appointmentId;

    @Column(nullable = false)
    private LocalDate consultationDate;

    @Column(nullable = false)
    private String motif;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String diagnostic;

    @Column(columnDefinition = "TEXT")
    private String notes;

    private Integer tensionArterielleSystolique;

    private Integer tensionArterielleDiastolique;

    private Double poids;

    private Double temperature;

    private Integer pouls;

    // Soft delete (conformité logiciel médical) : DELETE archive au lieu de supprimer, même traitement que Patient.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived;

    // Nullable : renseigné uniquement au moment de l'archivage.
    private LocalDateTime archivedAt;
}
