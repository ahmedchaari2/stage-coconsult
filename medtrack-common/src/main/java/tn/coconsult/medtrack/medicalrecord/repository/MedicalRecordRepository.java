package tn.coconsult.medtrack.medicalrecord.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coconsult.medtrack.medicalrecord.model.MedicalRecord;

import java.util.List;
import java.util.Optional;

public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, Long> {
    Optional<MedicalRecord> findByPatientId(Long patientId);

    List<MedicalRecord> findByPatientIdIn(List<Long> patientIds);
}
