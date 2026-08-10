package tn.coconsult.medtrack.ai.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.PrescriptionSummaryResponse;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PrescriptionGateway {

    private static final int PAGE_SIZE = 50;

    private final PrescriptionServiceClient prescriptionServiceClient;

    @CircuitBreaker(name = "prescriptionService", fallbackMethod = "activeFallback")
    public List<PrescriptionSummaryResponse> active(Long patientId) {
        return prescriptionServiceClient.search(0, PAGE_SIZE, patientId, "ACTIVE").safeContent();
    }

    private List<PrescriptionSummaryResponse> activeFallback(Long patientId, Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "prescription-service indisponible, impossible de recuperer les prescriptions actives");
    }
}
