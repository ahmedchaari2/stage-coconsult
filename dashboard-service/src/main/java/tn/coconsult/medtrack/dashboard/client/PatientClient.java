package tn.coconsult.medtrack.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.dashboard.dto.PatientPage;

/**
 * Recherche transversale de patient-service. Un MEDECIN est restreint à ses patients référents
 * côté serveur ; medecinReferentId=self le confirme explicitement. archived=true renvoie
 * uniquement les archivés (filtre exact, ADMIN seulement).
 */
@FeignClient(name = "patient-service", contextId = "patientClient")
public interface PatientClient {

    @GetMapping("/api/patients")
    PatientPage search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long medecinReferentId,
            @RequestParam(required = false) Boolean archived);
}
