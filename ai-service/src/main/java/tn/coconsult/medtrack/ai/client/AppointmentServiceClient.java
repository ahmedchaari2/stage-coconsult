package tn.coconsult.medtrack.ai.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.ai.dto.AppointmentSummaryPage;

@FeignClient(name = "appointment-service", contextId = "appointmentServiceClient")
public interface AppointmentServiceClient {

    @GetMapping("/api/appointments")
    AppointmentSummaryPage search(
            @RequestParam("page") int page,
            @RequestParam("size") int size,
            @RequestParam("patientId") Long patientId);
}
