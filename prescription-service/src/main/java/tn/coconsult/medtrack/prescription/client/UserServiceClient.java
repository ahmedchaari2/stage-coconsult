package tn.coconsult.medtrack.prescription.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import tn.coconsult.medtrack.common.dto.MedecinSummaryResponse;

@FeignClient(name = "user-service", contextId = "userServiceClient")
public interface UserServiceClient {

    @GetMapping("/api/users/{id}/summary")
    MedecinSummaryResponse getMedecinSummary(@PathVariable("id") Long medecinId);
}
