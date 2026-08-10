package tn.coconsult.medtrack.user.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import tn.coconsult.medtrack.user.model.Invitation;
import tn.coconsult.medtrack.user.model.InvitationStatus;

import java.util.List;
import java.util.Optional;

public interface InvitationRepository extends JpaRepository<Invitation, Long> {

    Optional<Invitation> findByToken(String token);

    Optional<Invitation> findByEmailAndStatus(String email, InvitationStatus status);

    List<Invitation> findByEmailAndStatusNot(String email, InvitationStatus status);
}
