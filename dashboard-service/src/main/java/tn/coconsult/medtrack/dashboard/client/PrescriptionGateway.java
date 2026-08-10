package tn.coconsult.medtrack.dashboard.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.dashboard.dto.PrescriptionPage;

@Service
@RequiredArgsConstructor
public class PrescriptionGateway {

    private static final int PAGE_SIZE = 100;

    private final PrescriptionClient prescriptionClient;

    @CircuitBreaker(name = "prescriptionService", fallbackMethod = "unavailable")
    public PrescriptionPage search(Long medecinId, String dateFrom, String dateTo, Boolean archived) {
        return prescriptionClient.search(0, PAGE_SIZE, medecinId, dateFrom, dateTo, archived);
    }

    @CircuitBreaker(name = "prescriptionService", fallbackMethod = "countUnavailable")
    public long count(Long medecinId, String dateFrom, String dateTo, Boolean archived) {
        return prescriptionClient.count(medecinId, dateFrom, dateTo, archived);
    }

    private PrescriptionPage unavailable(Long medecinId, String dateFrom, String dateTo, Boolean archived, Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "prescription-service indisponible");
    }

    private long countUnavailable(Long medecinId, String dateFrom, String dateTo, Boolean archived, Throwable t) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "prescription-service indisponible");
    }
}
