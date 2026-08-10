package tn.coconsult.medtrack.prescription.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import tn.coconsult.medtrack.prescription.model.Prescription;

import java.util.List;

public interface PrescriptionRepository extends JpaRepository<Prescription, Long>, JpaSpecificationExecutor<Prescription> {

    @Query(value = "SELECT p.id FROM prescriptions p JOIN consultations c ON p.consultation_id = c.id "
            + "WHERE p.duree_jours IS NOT NULL AND c.consultation_date IS NOT NULL "
            + "AND (c.consultation_date + p.duree_jours) >= CURRENT_DATE", nativeQuery = true)
    List<Long> findActiveIds();

    @Query(value = "SELECT p.id FROM prescriptions p JOIN consultations c ON p.consultation_id = c.id "
            + "WHERE p.duree_jours IS NOT NULL AND c.consultation_date IS NOT NULL "
            + "AND (c.consultation_date + p.duree_jours) < CURRENT_DATE", nativeQuery = true)
    List<Long> findExpiredIds();
}
