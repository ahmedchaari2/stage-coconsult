package tn.coconsult.medtrack.medicalrecord.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.coconsult.medtrack.medicalrecord.model.Consultation;

import java.util.Optional;

public interface ConsultationRepository extends JpaRepository<Consultation, Long>, JpaSpecificationExecutor<Consultation> {

    Optional<Consultation> findByAppointmentId(Long appointmentId);
}
