package tn.coconsult.medtrack.medicalrecord.model;

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

@Entity
@Table(name = "medical_records")
@Data
@EqualsAndHashCode(callSuper = false)
@NoArgsConstructor
@AllArgsConstructor
public class MedicalRecord extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private Long patientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupeSanguin groupeSanguin;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String allergies;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String antecedents;


    @Column(columnDefinition = "TEXT")
    private String traitementsChroniques;

    @Column(columnDefinition = "TEXT")
    private String antecedentsFamiliaux;

    @Column(columnDefinition = "TEXT")
    private String vaccinations;
}
