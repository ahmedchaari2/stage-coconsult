package tn.coconsult.medtrack.accesslog.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.coconsult.medtrack.accesslog.model.AccesLog;

import java.time.LocalDateTime;

public interface AccesLogRepository extends JpaRepository<AccesLog, Long>, JpaSpecificationExecutor<AccesLog> {

    /** Purge planifiée (voir AccesLogCleanupService). */
    long deleteByDateHeureBefore(LocalDateTime threshold);
}
