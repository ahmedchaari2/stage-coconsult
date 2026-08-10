package tn.coconsult.medtrack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.ai.dto.PatientSummaryResponse;
import tn.coconsult.medtrack.common.dto.ReferentCheckResponse;

@FeignClient(name = "patient-service", contextId = "patientServiceClient")
public interface PatientServiceClient {

    @GetMapping("/api/patients/{id}")
    PatientSummaryResponse getById(@PathVariable("id") Long id);

    @GetMapping("/api/patients/internal/{id}/is-referent")
    ReferentCheckResponse isReferent(@PathVariable("id") Long id, @RequestParam("medecinId") Long medecinId);
}
