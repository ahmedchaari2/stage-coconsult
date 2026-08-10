package tn.coconsult.medtrack.medicalrecord.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.common.dto.ReferentCheckResponse;

@FeignClient(name = "patient-service")
public interface PatientServiceClient {

    @GetMapping("/api/patients/internal/{id}/is-referent")
    ReferentCheckResponse isReferent(@PathVariable("id") Long patientId, @RequestParam("medecinId") Long medecinId);
}
