package tn.coconsult.medtrack.prescription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;

@FeignClient(name = "medicalrecord-service", contextId = "medicalRecordServiceClient")
public interface MedicalRecordServiceClient {

    @GetMapping("/api/consultations/{id}/summary")
    ConsultationSummaryResponse getSummary(@PathVariable("id") Long consultationId);
}
