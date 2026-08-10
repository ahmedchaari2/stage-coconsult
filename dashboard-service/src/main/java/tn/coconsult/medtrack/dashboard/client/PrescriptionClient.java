package tn.coconsult.medtrack.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.dashboard.dto.PrescriptionPage;

@FeignClient(name = "prescription-service", contextId = "prescriptionClient")
public interface PrescriptionClient {

    @GetMapping("/api/prescriptions")
    PrescriptionPage search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Boolean archived);

    @GetMapping("/api/prescriptions/internal/count")
    long count(
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) Boolean archived);
}
