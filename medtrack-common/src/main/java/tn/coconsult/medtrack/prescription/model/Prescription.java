package tn.coconsult.medtrack.prescription.model;

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

import java.time.LocalDateTime;

@Entity
@Table(name = "prescriptions")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Prescription extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long consultationId;

    @Column(nullable = false)
    private String medicament;

    @Column(nullable = false)
    private String posologie;

    private Integer dureeJours;

    @Column(columnDefinition = "TEXT")
    private String instructions;

    // Renouvelable = traitement au long cours vs ponctuel. Défaut false, même traitement que archived.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean renouvelable;

    // Soft delete (donnée médicale, même traitement que Patient/Consultation).
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived;

    private LocalDateTime archivedAt;
}
