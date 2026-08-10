package tn.coconsult.medtrack.appointment.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import tn.coconsult.medtrack.appointment.model.Appointment;
import tn.coconsult.medtrack.appointment.model.StatutRendezVous;

import java.time.LocalDateTime;
import java.util.List;

public interface AppointmentRepository extends JpaRepository<Appointment, Long>, JpaSpecificationExecutor<Appointment> {

    // Rendez-vous à venir pas encore rappelés (WebSocket), statut PLANIFIE/CONFIRME.
    List<Appointment> findByDateHeureBetweenAndRappelEnvoyeFalseAndStatutIn(
            LocalDateTime from, LocalDateTime to, List<StatutRendezVous> statuts);

    // Même chose pour le rappel email du lendemain, indépendant du rappel WebSocket.
    List<Appointment> findByDateHeureBetweenAndRappelVeilleEnvoyeFalseAndStatutIn(
            LocalDateTime from, LocalDateTime to, List<StatutRendezVous> statuts);

    List<Appointment> findByMedecinIdAndStatutNotAndDateHeureAfterAndDateHeureBefore(
            Long medecinId, StatutRendezVous statut, LocalDateTime from, LocalDateTime to);
}
