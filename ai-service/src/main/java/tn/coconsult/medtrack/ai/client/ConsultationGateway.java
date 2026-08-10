package tn.coconsult.medtrack.ai.client;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.ai.dto.ConsultationSummaryResponse;

import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConsultationGateway {

    private static final int PAGE_SIZE = 50;
    private static final int RECENT_LIMIT = 5;

    private final ConsultationServiceClient consultationServiceClient;

    @CircuitBreaker(name = "medicalRecordService", fallbackMethod = "recentFallback")
    public List<ConsultationSummaryResponse> recent(Long medicalRecordId) {
        List<ConsultationSummaryResponse> content = consultationServiceClient.search(medicalRecordId, 0, PAGE_SIZE).safeContent();
        return content.stream()
                .sorted(Comparator.comparing(ConsultationSummaryResponse::consultationDate, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(RECENT_LIMIT)
                .toList();
    }

    private List<ConsultationSummaryResponse> recentFallback(Long medicalRecordId, Throwable throwable) {
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "medicalrecord-service indisponible, impossible de recuperer les consultations");
    }
}
