package tn.coconsult.medtrack.prescription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.common.dto.ReferentCheckResponse;
import tn.coconsult.medtrack.prescription.dto.PatientDetailsResponse;

/** Appel direct patient-service via Eureka, pas par la Gateway (service-à-service). contextId distinct pour éviter un conflit de bean FeignClientSpecification au démarrage. */
@FeignClient(name = "patient-service", contextId = "patientServiceClient")
public interface PatientServiceClient {

    @GetMapping("/api/patients/internal/{id}/is-referent")
    ReferentCheckResponse isReferent(@PathVariable("id") Long patientId, @RequestParam("medecinId") Long medecinId);

    /** Endpoint déjà exposé par patient-service, réutilisé tel quel : même règle d'accès, identité repropagée par FeignAuthHeaderInterceptor. */
    @GetMapping("/api/patients/{id}")
    PatientDetailsResponse getById(@PathVariable("id") Long patientId);
}
