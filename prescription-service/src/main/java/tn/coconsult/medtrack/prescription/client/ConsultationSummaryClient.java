package tn.coconsult.medtrack.prescription.client;

import feign.FeignException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tn.coconsult.medtrack.common.dto.ConsultationSummaryResponse;

@Service
@RequiredArgsConstructor
public class ConsultationSummaryClient {

    private final MedicalRecordServiceClient medicalRecordServiceClient;

    @CircuitBreaker(name = "medicalRecordService", fallbackMethod = "getSummaryFallback")
    public ConsultationSummaryResponse getSummary(Long consultationId) {
        try {
            return medicalRecordServiceClient.getSummary(consultationId);
        } catch (FeignException.NotFound notFound) {
            // Vraie 404, pas une panne : ignorée par le circuit breaker (ignore-exceptions dans la config) pour ne pas compter comme un échec.
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Consultation introuvable");
        }
    }

    private ConsultationSummaryResponse getSummaryFallback(Long consultationId, Throwable throwable) {
        // ignore-exceptions empêche le 404 d'ouvrir le circuit mais pas d'arriver ici (resilience4j route tout vers le fallback), d'où ce relais.
        if (throwable instanceof ResponseStatusException statusException) {
            throw statusException;
        }
        throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "medicalrecord-service indisponible, impossible de recuperer la consultation");
    }
}
