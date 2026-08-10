package tn.coconsult.medtrack.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import tn.coconsult.medtrack.dashboard.dto.MedecinOptionDto;

import java.util.List;

/**
 * Liste des médecins pour la vue admin. Endpoint interne (permitAll) et non /medecins/all
 * (réservé ADMIN par cookie) : user-service n'authentifie que par cookie JWT, pas par les
 * headers X-User-*, un appel service-à-service n'y a donc aucun rôle.
 */
@FeignClient(name = "user-service", contextId = "userClient")
public interface UserClient {

    @GetMapping("/api/users/internal/medecins")
    List<MedecinOptionDto> allMedecins();
}
