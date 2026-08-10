package tn.coconsult.medtrack.ai.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.AppointmentSummaryResponse;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AppointmentGateway {

    private static final int PAGE_SIZE = 50;
    private static final int RECENT_LIMIT = 5;

    private final AppointmentServiceClient appointmentServiceClient;

    @CircuitBreaker(name = "appointmentService", fallbackMethod = "recentFallback")
    public List<AppointmentSummaryResponse> recent(Long patientId) {
        List<AppointmentSummaryResponse> content = appointmentServiceClient.search(0, PAGE_SIZE, patientId).safeContent();
        return content.stream()
                .sorted(Comparator.comparing(AppointmentSummaryResponse::dateHeure, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT)
                .toList();
    }

    private List<AppointmentSummaryResponse> recentFallback(Long patientId, Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "appointment-service indisponible, impossible de recuperer l'historique des rendez-vous");
    }
}
