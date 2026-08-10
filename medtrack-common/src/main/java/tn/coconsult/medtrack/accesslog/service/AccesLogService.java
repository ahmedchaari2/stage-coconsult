package tn.coconsult.medtrack.accesslog.service;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tn.coconsult.medtrack.accesslog.model.AccesLog;
import tn.coconsult.medtrack.accesslog.model.TypeAction;
import tn.coconsult.medtrack.accesslog.model.TypeRessource;
import tn.coconsult.medtrack.accesslog.repository.AccesLogRepository;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AccesLogService {

    private static final Logger log = LoggerFactory.getLogger(AccesLogService.class);

    private final AccesLogRepository accesLogRepository;

    @Async("accesLogExecutor")
    public void enregistrerAcces(Long utilisateurId, TypeRessource typeRessource, Long ressourceId, Long patientId) {
        enregistrerAcces(utilisateurId, typeRessource, ressourceId, patientId, TypeAction.VIEW);
    }

    @Async("accesLogExecutor")
    public void enregistrerAcces(Long utilisateurId, TypeRessource typeRessource, Long ressourceId,
                                 Long patientId, TypeAction action) {
        try {
            AccesLog entry = new AccesLog();
            entry.setUtilisateurId(utilisateurId);
            entry.setTypeRessource(typeRessource);
            entry.setAction(action);
            entry.setRessourceId(ressourceId);
            entry.setPatientId(patientId);
            entry.setDateHeure(LocalDateTime.now());
            accesLogRepository.save(entry);
        } catch (Exception e) {
            log.error("Échec de journalisation d'accès (utilisateur={}, type={}, action={}, ressource={}, patient={})",
                    utilisateurId, typeRessource, action, ressourceId, patientId, e);
        }
    }
}
