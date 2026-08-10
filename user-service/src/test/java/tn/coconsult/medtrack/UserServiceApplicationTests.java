package tn.coconsult.medtrack;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import tn.coconsult.medtrack.accesslog.model.AccesLog;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.repository.AccesLogRepository;
import tn.coconsult.medtrack.user.model.Invitation;
import tn.coconsult.medtrack.user.model.InvitationStatus;
import tn.coconsult.medtrack.user.model.Role;
import tn.coconsult.medtrack.user.repository.InvitationRepository;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

@SpringBootTest
class UserServiceApplicationTests {

	@Autowired
	private AccesLogRepository accesLogRepository;

	@Autowired
	private InvitationRepository invitationRepository;

	@Test
	void contextLoads() {
	}

	@Test
	@Transactional
	void auditResourceWithoutPatientCanBePersisted() {
		AccesLog entry = new AccesLog();
		entry.setUtilisateurId(1L);
		entry.setTypeRessource(TypeRessource.MEDECIN);
		entry.setAction(TypeAction.VIEW);
		entry.setRessourceId(4L);
		entry.setPatientId(null);
		entry.setDateHeure(LocalDateTime.now());

		assertDoesNotThrow(() -> accesLogRepository.saveAndFlush(entry));
	}

	@Test
	@Transactional
	void revokedInvitationStatusCanBePersisted() {
		Invitation invitation = new Invitation();
		invitation.setEmail("revoked-invitation-test@medtrack.tn");
		invitation.setRole(Role.MEDECIN);
		invitation.setStatus(InvitationStatus.REVOKED);

		assertDoesNotThrow(() -> invitationRepository.saveAndFlush(invitation));
	}

}
