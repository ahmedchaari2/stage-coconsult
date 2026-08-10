package tn.coconsult.medtrack.accesslog.model;

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
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "acces_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AccesLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long utilisateurId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TypeRessource typeRessource;

    @Enumerated(EnumType.STRING)
    private TypeAction action;

    @Column(nullable = false)
    private Long ressourceId;

    private Long patientId;

    @Column(nullable = false)
    private LocalDateTime dateHeure;
}
