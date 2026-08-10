package tn.coconsult.medtrack.dashboard.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import tn.coconsult.medtrack.dashboard.dto.AppointmentPage;

@FeignClient(name = "appointment-service", contextId = "appointmentClient")
public interface AppointmentClient {

    @GetMapping("/api/appointments")
    AppointmentPage search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long medecinId,
            @RequestParam(required = false) String statut,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo);
}
