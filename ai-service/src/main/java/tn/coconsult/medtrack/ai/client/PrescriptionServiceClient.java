package tn.coconsult.medtrack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.ai.dto.PrescriptionSummaryPage;

@FeignClient(name = "prescription-service", contextId = "prescriptionServiceClient")
public interface PrescriptionServiceClient {

    @GetMapping("/api/prescriptions")
    PrescriptionSummaryPage search(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("patientId") Long patientId,
            @RequestParam("statutCalcule") String statutCalcule);
}
