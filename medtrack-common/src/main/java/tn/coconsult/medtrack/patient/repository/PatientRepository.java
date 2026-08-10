package tn.coconsult.medtrack.patient.repository;

import tn.coconsult.medtrack.patient.model.Patient;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.List;

public interface PatientRepository extends JpaRepository<Patient, Long>, JpaSpecificationExecutor<Patient> {

    List<Patient> findByMedecinReferentId(Long medecinReferentId);

    Optional<Patient> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    Optional<Patient> findTopByNumeroDossierStartingWithOrderByNumeroDossierDesc(String prefix);
}
