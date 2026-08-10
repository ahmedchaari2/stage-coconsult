package tn.coconsult.medtrack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.ai.dto.ConsultationSummaryPage;

@FeignClient(name = "medicalrecord-service", contextId = "consultationServiceClient")
public interface ConsultationServiceClient {

    @GetMapping("/api/medical-records/{id}/consultations")
    ConsultationSummaryPage search(
            @PathVariable("id") Long medicalRecordId,
            @RequestParam("page") int page,
            @RequestParam("size") int size);
}
