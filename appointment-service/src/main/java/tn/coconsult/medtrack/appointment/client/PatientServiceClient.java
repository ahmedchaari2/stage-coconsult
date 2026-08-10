package tn.coconsult.medtrack.appointment.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.common.dto.ReferentCheckResponse;

/**
 * Appel direct patient-service via Eureka (lb://), pas par la Gateway : service-a-service.
 * contextId distinct de NotificationServiceClient : deux @FeignClient sur le meme "name"
 * enregistrent sinon un bean FeignClientSpecification en double (conflit au demarrage).
 */
@FeignClient(name = "patient-service", contextId = "patientServiceClient")
public interface PatientServiceClient {

    @GetMapping("/api/patients/internal/{id}/is-referent")
    ReferentCheckResponse isReferent(@PathVariable("id") Long patientId, @RequestParam("medecinId") Long medecinId);
}
