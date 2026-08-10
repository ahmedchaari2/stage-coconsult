package tn.coconsult.medtrack.patient.model;

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

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "patients")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class Patient extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nom;

    @Column(nullable = false)
    private String prenom;

    @Column(nullable = false)
    private LocalDate dateNaissance;

    @Column(nullable = false)
    private String telephone;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String adresse;

    @Enumerated(EnumType.STRING)
    private SexePatient sexe;

    private String contactUrgenceNom;

    private String contactUrgenceTelephone;

    @Column(unique = true)
    private String cin;

    @Column(unique = true)
    private String numeroDossier;

    private String numeroAssurance;

    private Long medecinReferentId;

    // Soft delete (conformité logiciel médical, jamais de suppression physique) : DELETE archive au lieu de supprimer.
    @Column(nullable = false, columnDefinition = "boolean default false")
    private boolean archived;

    // Nullable : renseigné uniquement au moment de l'archivage.
    private LocalDateTime archivedAt;
}
